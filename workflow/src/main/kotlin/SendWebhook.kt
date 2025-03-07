import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant

fun main() {
	val webhookUrl = System.getenv("DISCORD_WEBHOOK_URL") ?: error("DISCORD_WEBHOOK_URL environment variable not set")

	val repo = System.getenv("GITHUB_REPOSITORY") ?: "Unknown"
	val commitSha = System.getenv("GITHUB_SHA") ?: "Unknown"
	val commitMessage = System.getenv("GITHUB_EVENT_HEAD_COMMIT_MESSAGE") ?: "No commit message"
	val commitAuthor = System.getenv("GITHUB_ACTOR") ?: "Unknown"

	val embed = Embed(
		title = "[${repo}] 1 new commit",
		description = "[`${commitSha.substring(0, 7)}`](https://github.com/${repo}/commit/${commitSha}) $commitMessage - $commitAuthor",
		color = 0x7289DA,
		timestamp = Instant.now().toString(),
		footer = Footer("GitHub", "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png")
	)

	val payload = WebhookPayload(
		username = "GitHub",
		avatar_url = "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png",
		embeds = listOf(embed)
	)

	sendDiscordWebhook(webhookUrl, payload)
}

data class WebhookPayload(
	val username: String,
	val avatar_url: String,
	val embeds: List<Embed>
)

data class Embed(
	val title: String,
	val description: String,
	val color: Int,
	val timestamp: String,
	val footer: Footer
)

data class Footer(
	val text: String,
	val icon_url: String
)

fun sendDiscordWebhook(webhookUrl: String, payload: WebhookPayload) {
	val gson = Gson()
	val jsonPayload = gson.toJson(payload)

	val url = URL(webhookUrl)
	val connection = url.openConnection() as HttpURLConnection
	connection.requestMethod = "POST"
	connection.doOutput = true
	connection.setRequestProperty("Content-Type", "application/json")

	connection.outputStream.use { it.write(jsonPayload.toByteArray(StandardCharsets.UTF_8)) }

	val responseCode = connection.responseCode
	if (responseCode !in 200..299) {
		error("Failed to send webhook: HTTP $responseCode")
	} else {
		println("Webhook sent successfully!")
	}
}
