plugins {
	kotlin("jvm") version "2.1.10"
	application
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("net.dv8tion:JDA:5.3.0")
	implementation("com.google.code.gson:gson:2.12.1")
}

application {
	mainClass.set("SendWebhookKt")
}