package net.ririfa.fabricord.proxy

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import net.minecraft.text.Text
import net.ririfa.fabricord.ConsoleTrackerAppender
import net.ririfa.fabricord.Fabricord
import net.ririfa.fabricord.proxy.Fabricord.Companion.MOD_ID
import net.ririfa.fabricord.translation.FabricordMessageProvider
import net.ririfa.langman.LangMan
import org.slf4j.Logger
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import javax.inject.Inject

@Plugin(
	id = MOD_ID,
	name = "Fabricord",
	version = "1.0.0",
	description = "A modern message style like DiscordSRV will be reproduced as a Fabric version mod.",
	url = "https://github.com/ririf4/Fabricord",
	authors = ["RirFa"],
	dependencies = []
)
class Fabricord @Inject constructor(
	private val server: ProxyServer,
	private val logger: Logger,
	@DataDirectory
	private val dataFolder: Path
) {
	companion object {
		const val MOD_ID = "fabricord"

		lateinit var langMan: LangMan<FabricordMessageProvider, Text>
		var consoleAppender: ConsoleTrackerAppender? = null

		val thread: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
		val availableLang = listOf<String>("en", "ja")
	}

	val modDir: Path = dataFolder.resolve(Fabricord.Companion.MOD_ID)
	val langDir: Path = modDir.resolve("lang")

	@Subscribe
	fun onProxyInitialization(event: ProxyInitializeEvent) {

	}

	@Subscribe
	fun onProxyShutdown(event: ProxyShutdownEvent) {

	}
}