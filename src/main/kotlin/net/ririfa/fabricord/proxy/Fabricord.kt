package net.ririfa.fabricord.proxy

import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.nio.file.Path
import javax.inject.Inject

@Plugin(
	id = "fabricord",
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
	
}