package net.ririfa.fabricord

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType.greedyString
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.ririfa.fabricord.discord.DiscordBotManager
import net.ririfa.fabricord.discord.DiscordEmbed
import net.ririfa.fabricord.discord.DiscordPlayerEventHandler.handleMCMessage
import net.ririfa.fabricord.translation.FabricordMessageKey
import net.ririfa.fabricord.translation.FabricordMessageProvider
import net.ririfa.fabricord.translation.adapt
import net.ririfa.langman.InitType
import net.ririfa.langman.LangMan
import org.apache.logging.log4j.LogManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.stream.Stream

class Fabricord : DedicatedServerModInitializer {
	companion object {
		const val MOD_ID = "fabricord"

		lateinit var server: MinecraftServer
		lateinit var langMan: LangMan<FabricordMessageProvider, Text>
		lateinit var consoleAppender: ConsoleTrackerAppender

		val logger: Logger = LoggerFactory.getLogger(Fabricord::class.simpleName)
		val loader: FabricLoader = FabricLoader.getInstance()
		val serverDir: Path = loader.gameDir
		val modDir: Path = serverDir.resolve(MOD_ID)
		val langDir: Path = modDir.resolve("lang")

		val thread: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
		val availableLang = listOf<String>("en", "ja")

		private val localChatToggled = mutableListOf<UUID>()
	}

	override fun onInitializeServer() {
		LanguageAutoUpdater.checkForUpdatesAndExtract()
		langMan = LangMan.createNew(
			{ Text.of(it) },
			FabricordMessageKey::class,
			false
		)
		langMan.init(
			InitType.YAML,
			langDir.toFile(),
			availableLang
		)
		ConfigManager.init()
		registerServerEvents()
	}

	private fun registerServerEvents() {
		if (Config.enableConsoleLog == true && Config.consoleLogChannelID != null) {
			consoleAppender = ConsoleTrackerAppender("FabricordConsoleTracker")
			val rootLogger = LogManager.getRootLogger() as org.apache.logging.log4j.core.Logger
			rootLogger.addAppender(consoleAppender)
		}

		CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
			//			GroupManager.registerAll(dispatcher)
			registerLCCommand(dispatcher)
		}
		ServerLifecycleEvents.SERVER_STARTED.register { server ->
			Fabricord.server = server
			if (!(ConfigManager.isErrorOccurred)) {
				DiscordBotManager.start()
			}
		}
		ServerLifecycleEvents.SERVER_STOPPING.register { server ->
			if (DiscordBotManager.botIsInitialized) {
				DiscordBotManager.stop()
			}
			if (consoleAppender.isInitialized) {
				consoleAppender.stop()
			}
		}

		if (!Config.logChannelIDIsNotSet) {
			ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
				val player = handler.player

				if (DiscordBotManager.botIsInitialized) {
					FT {
						DiscordEmbed.sendPlayerJoinEmbed(player)
					}
				}
			}
			ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
				if (DiscordBotManager.botIsInitialized) {
					FT {
						val player = handler.player
						DiscordEmbed.sendPlayerLeftEmbed(player)
					}
				}
			}
			ServerMessageEvents.CHAT_MESSAGE.register { message, sender, params ->
				if (DiscordBotManager.botIsInitialized && Config.dontSendChatToDiscord == false) {
					val uuid = sender.uuid

					//TODO: Also consider group chats -> [GroupManager]
					// But probably controllable via Mixin.
					if (uuid in localChatToggled) return@register

					val content = message.content.string
					handleMCMessage(sender, content)
				}
			}
		}
	}

	private fun registerLCCommand(dispatcher: CommandDispatcher<ServerCommandSource>) {
		dispatcher.register(
			literal("lc")
				.executes { context ->
					val player = context.source.player ?: return@executes 0
					val uuid = player.uuid
					val current = uuid in localChatToggled
					val newState = !current
					if (newState) {
						localChatToggled.add(uuid)
					} else {
						localChatToggled.remove(uuid)
					}
					val stateMSG = if (newState == true) {
						"ON"
					} else {
						"OFF"
					}
					player.sendMessage(
						player.adapt().getMessage(FabricordMessageKey.Command.LC.SwitchedLocalChatState, stateMSG),
						false
					)
					return@executes 1
				}
				.then(
					argument("message", greedyString())
						.executes { context ->
							val player = context.source.player ?: return@executes 0
							val message = context.getArgument("message", String::class.java)

							player.server.playerManager.playerList.forEach {
								it.sendMessage(
									Text.of(
										"<${player.name.string}> $message"
									),
									false
								)
							}

							return@executes 1
						}
				)
		)

	}

	object LanguageAutoUpdater {
		private val yaml = Yaml()
		private const val DEFAULT_VERSION = "1.0.0"

		fun checkForUpdatesAndExtract() {
			try {
				if (!Files.exists(langDir)) {
					Files.createDirectories(langDir)
					extractLangFiles(langDir)
					return
				}

				val latestVersions = getLatestVersionsFromJar() ?: return
				val needsUpdate = Files.list(langDir).use { files ->
					files.toList().filter { it.toString().endsWith(".yml") }.any { file ->
						val langKey = file.fileName.toString().removeSuffix(".yml")
						val latestVersion = latestVersions[langKey] ?: DEFAULT_VERSION
						val currentVersion = getVersionFromYaml(file) ?: DEFAULT_VERSION
						isOlderVersion(currentVersion, latestVersion)
					}
				}

				if (needsUpdate) {
					extractLangFiles(langDir)
				}
			} catch (e: Exception) {
				logger.error("Failed to check for language file updates", e)
			}
		}

		private fun extractLangFiles(targetDir: Path) {
			try {
				val langPath = "assets/${MOD_ID}/lang/"
				val classLoader = this::class.java.classLoader
				val resourceStream = classLoader.getResourceAsStream(langPath) ?: run {
					logger.error("Failed to find language directory in JAR: $langPath")
					return
				}

				val zipFs = FileSystems.newFileSystem(URI.create("jar:${resourceStream}"), emptyMap<String, Any>())
				val langDirPath = zipFs.getPath(langPath)

				copyLanguageFiles(langDirPath, targetDir)

				zipFs.close()
			} catch (e: Exception) {
				logger.error("Failed to extract language files", e)
			}
		}

		private fun copyLanguageFiles(sourceDir: Path, targetDir: Path) {
			Files.walk(sourceDir).use { paths ->
				paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".yml") }.forEach { resourceFile ->
					val targetFile = targetDir.resolve(resourceFile.fileName.toString())
					Files.copy(resourceFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
				}
			}
		}

		private fun getVersionFromYaml(file: Path): String? {
			return try {
				Files.newBufferedReader(file).use { reader ->
					val data = yaml.load<Map<String, Any>>(reader)
					data["version"] as? String
				}
			} catch (e: Exception) {
				logger.warn("Failed to read version from ${file.fileName}", e)
				null
			}
		}

		private fun getLatestVersionsFromJar(): Map<String, String>? {
			return try {
				val classLoader = this::class.java.classLoader
				val resourceUrl = classLoader.getResource("assets/${MOD_ID}/lang/langversion.info") ?: return null
				resourceUrl.openStream().use { inputStream ->
					val data: Map<String, Any> = yaml.load(inputStream)
					@Suppress("UNCHECKED_CAST")
					data["latest"] as? Map<String, String>
				}
			} catch (e: Exception) {
				logger.error("Failed to read langversion.info", e)
				null
			}
		}

		private fun isOlderVersion(current: String, latest: String): Boolean {
			val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
			val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

			val maxLength = maxOf(currentParts.size, latestParts.size)
			val paddedCurrent = currentParts + List(maxLength - currentParts.size) { 0 }
			val paddedLatest = latestParts + List(maxLength - latestParts.size) { 0 }

			for (i in 0 until maxLength) {
				if (paddedCurrent[i] < paddedLatest[i]) return true
				if (paddedCurrent[i] > paddedLatest[i]) return false
			}
			return false
		}
	}
}