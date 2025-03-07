package net.ririfa.fabricord

import net.ririfa.fabricord.annotations.Required
import net.ririfa.fabricord.translation.FabricordMessageKey
import net.ririfa.fabricord.util.copyResourceToFile
import net.ririfa.fabricord.util.extractWebhookIdFromUrl
import net.ririfa.fabricord.util.toBooleanOrNull
import org.jetbrains.annotations.Nullable
import org.yaml.snakeyaml.Yaml
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

object ConfigManager {
	lateinit var parsedConfig: Map<String, Any>
	lateinit var config: Config
	val yaml = Yaml()
	val configFile: Path = ModDir.resolve("config.yml")
	var isErrorOccurred = false

	fun init() {
		checkRequiredFilesAndDirectories()
		reloadConfig()
		loadConfig()
		validate()
	}

	inline fun <reified T> lc(key: String): T? {
		val value = resolveNestedKey(parsedConfig, key)
		return parseValue(value)
	}

	fun reloadConfig(force: Boolean = false) {
		if (force || !::parsedConfig.isInitialized) {
			parsedConfig = Files.newInputStream(configFile).use { yaml.load(it) }
		}
	}

	// >==================== Helpers ====================< \\

	private fun validate() {
		checkRequiredConfig()
		config.nullCheck()
	}

	private fun checkRequiredFilesAndDirectories() {
		try {
			if (!Files.exists(ModDir)) {
				Logger.info(LM.getSysMessage(FabricordMessageKey.System.Initialization.DirectoriesAndFiles.ModDirDoesNotExist, ModDir))
				Files.createDirectories(ModDir)
			}
			if (configFile.notExists()) {
				copyResourceToFile("config.yml", configFile)
			}
		} catch (e: SecurityException) {
			Logger.error(LM.getSysMessage(FabricordMessageKey.System.Initialization.FailedToCheckOrCreateRequiredDirOrFileBySec), e)
		} catch (e: IOException) {
			Logger.error(LM.getSysMessage(FabricordMessageKey.System.Initialization.FailedToCheckOrCreateRequiredDirOrFileByIO), e)
		} catch (e: Exception) {
			Logger.error(LM.getSysMessage(FabricordMessageKey.System.Initialization.FailedToCheckOrCreateRequiredDirOrFile), e)
		}
	}

	fun resolveNestedKey(config: Map<String, Any>, key: String): Any? {
		val keys = key.split(".") // "main.example" -> ["main", "example"]
		var current: Any? = config

		for (part in keys) {
			if (current !is Map<*, *>) {
				return null // 現在のノードがマップでない場合、探索を中止
			}
			current = current[part]
		}

		return current
	}

	inline fun <reified T> parseValue(value: Any?): T? {
		return when (T::class) {
			String::class -> value?.toString() as? T
			Int::class -> value?.toString()?.toIntOrNull() as? T
			Boolean::class -> value?.toString()?.toBooleanOrNull() as? T
			Double::class -> value?.toString()?.toDoubleOrNull() as? T
			Short::class -> value?.toString()?.toShortOrNull() as? T
			Long::class -> value?.toString()?.toLongOrNull() as? T
			Float::class -> value?.toString()?.toFloatOrNull() as? T
			Byte::class -> value?.toString()?.toByteOrNull() as? T
			Char::class -> (value as? String)?.singleOrNull() as? T
			BigInteger::class -> value?.toString()?.let { BigInteger(it) } as? T
			BigDecimal::class -> value?.toString()?.let { BigDecimal(it) } as? T
			List::class -> (value as? List<*>)?.filterIsInstance<T>() as? T
			Set::class -> (value as? List<*>)?.filterIsInstance<T>()?.toSet() as? T
			else -> value as? T
		}
	}

	/**
	 * If LogChannelID is not set, bot will do this
	 * - Handle discord command
	 * - Console bridge(if enabled)
	 * - Bot never sends any message (chat, start/stop, achieve/death) from minecraft.
	 *
	 * And, If dontSendChatToDiscord is true, bot will do this
	 * - Send startup/stop message
	 * - Send achievement and death messages
	 * - Handle discord command
	 * - Console bridge(if enabled)
	 * - But bot never sends any player's message from minecraft
	 */
	private fun checkRequiredConfig() {
		val clazz = config::class
		val properties = clazz.memberProperties

		Logger.debug("Found ${properties.size} properties in Config class.")

		for (property in properties) {
			val requiredAnnotation = property.findAnnotation<Required>()

			if (requiredAnnotation != null) {
				val value = property.getter.call(config) as? String
				if (value.isNullOrBlank()) {
					if (requiredAnnotation.soft && requiredAnnotation.named == "logChannelID") {
						Logger.warn(LM.getSysMessage(FabricordMessageKey.Exception.Config.RequiredPropertyIsNotConfigured, configFile, property.name))
						config.logChannelIDIsNotSet = true
					} else {
						Logger.error(LM.getSysMessage(FabricordMessageKey.Exception.Config.SoftRequiredPropertyIsNotConfigured, configFile, property.name))
						isErrorOccurred = true
					}
				}
			}
		}
	}

	private fun loadConfig() {
		try {
			if (!::parsedConfig.isInitialized) {
				throw IllegalStateException("parsedConfig is not initialized. Call reloadConfig() first.")
			}

			config = Config(
				botToken = lc("BotToken"),
				logChannelID = lc("LogChannelID"),
				dontSendChatToDiscord = lc("dontSendChatToDiscord"),
				botActivityMessage = lc("BotActivityMessage"),
				botActivityStatus = lc("BotActivityStatus"),
				botOnlineStatus = lc("BotOnlineStatus"),
				messageStyle = lc("MessageStyle"),
				webHookId = extractWebhookIdFromUrl(lc("WebhookUrl")),
				serverStartMessage = lc("ServerStartMessage"),
				serverStopMessage = lc("ServerStopMessage"),
				playerJoinMessage = lc("PlayerJoinMessage"),
				playerLeaveMessage = lc("PlayerLeaveMessage"),
				allowMentions = lc("AllowMentions"),
				useUserPermissionForMentions = lc("UseUserPermissionForMentions"),
				mentionBlockedUserID = lc("blockedUserIDs"),
				mentionBlockedRoleID = lc("blockedRoleIDs"),
				enableConsoleLog = lc("EnableConsoleLog"),
				consoleLogChannelID = lc("ConsoleLogChannelID")
			)
		} catch (e: Exception) {
			Logger.error("Failed to load config: ${e.message}", e)
		}
	}

	// >================================================< \\
	data class Config(
		@Required(named = "botToken")
		@JvmField
		val botToken: String?,
		@Required(soft = true, named = "logChannelID")
		@Nullable
		@JvmField
		var logChannelID: String?,

		@JvmField
		var dontSendChatToDiscord: Boolean? = false,
		@JvmField
		var botActivityMessage: String? = null,
		@JvmField
		var botActivityStatus: String? = null,
		@JvmField
		var botOnlineStatus: String? = null,
		@JvmField
		var messageStyle: String? = null,
		@JvmField
		val webHookId: String? = null,
		@JvmField
		var serverStartMessage: String? = null,
		@JvmField
		var serverStopMessage: String? = null,
		@JvmField
		var playerJoinMessage: String? = null,
		@JvmField
		var playerLeaveMessage: String? = null,

		@JvmField
		var allowMentions: Boolean? = true,
		@JvmField
		var useUserPermissionForMentions: Boolean? = false,
		@JvmField
		var mentionBlockedUserID: Set<String>? = emptySet(),
		@JvmField
		var mentionBlockedRoleID: Set<String>? = emptySet(),

		@JvmField
		var enableConsoleLog: Boolean?,
		@JvmField
		var consoleLogChannelID: String?,
	) {
		fun nullCheck() {
			if (logChannelID?.isEmpty() == true) logChannelID = null
			if (dontSendChatToDiscord == null) dontSendChatToDiscord = false
			if (botActivityMessage.isNullOrBlank()) botActivityMessage = "Minecraft Server"
			if (botActivityStatus.isNullOrBlank()) botActivityStatus = "playing"
			if (botOnlineStatus.isNullOrBlank()) botOnlineStatus = "online"
			if (messageStyle.isNullOrBlank()) messageStyle = "classic"
			if (serverStartMessage.isNullOrBlank()) serverStartMessage = ":white_check_mark: **Server has started!**"
			if (serverStopMessage.isNullOrBlank()) serverStopMessage = ":octagonal_sign: **Server has stopped!**"
			if (playerJoinMessage.isNullOrBlank()) playerJoinMessage = "%player% joined the server"
			if (playerLeaveMessage.isNullOrBlank()) playerLeaveMessage = "%player% left the server"

			if (allowMentions == null) allowMentions = true
			if (useUserPermissionForMentions == null) useUserPermissionForMentions = false
			if (mentionBlockedUserID == null) mentionBlockedUserID = emptySet()
			if (mentionBlockedRoleID == null) mentionBlockedRoleID = emptySet()
		}

		fun getFile(): Path {
			return configFile
		}

		/**
		 * If this true, [net.ririfa.fabricord.discord.DiscordBotManager.sendToDiscord] will not do anything
		 */
		@JvmField
		var logChannelIDIsNotSet = false
	}
}