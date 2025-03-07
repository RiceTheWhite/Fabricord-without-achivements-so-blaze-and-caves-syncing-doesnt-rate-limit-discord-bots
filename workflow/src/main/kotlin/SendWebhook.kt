import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Instant

fun main(args: Array<String>) {
	if (args.isEmpty()) {
		println("Usage: ./gradlew run --args=\"-commit\" or \"-issues\"")
		return
	}

	val commitWebhook = System.getenv("DISCORD_COMMIT_WEBHOOK") ?: error("DISCORD_COMMIT_WEBHOOK is not set")
	val issueWebhook = System.getenv("DISCORD_ISSUE_WEBHOOK") ?: error("DISCORD_ISSUE_WEBHOOK is not set")
	val prWebhook = System.getenv("DISCORD_PR_WEBHOOK") ?: error("DISCORD_PR_WEBHOOK is not set")

	val repo = System.getenv("GITHUB_REPOSITORY") ?: "Unknown"
	val branch = System.getenv("GITHUB_REF")?.split("/")?.last() ?: "unknown-branch"

	when (args[0]) {
		"-commit" -> sendCommitWebhook(commitWebhook, repo, branch)
		"-issues" -> sendIssueWebhook(issueWebhook, repo, branch)
		"-pull-request" -> sendPullRequestWebhook(prWebhook, repo, branch)
		else -> println("Unknown argument: ${args[0]}. Use -commit, -issues, or -pull-request")
	}
}

fun sendCommitWebhook(webhookUrl: String, repo: String, branch: String) {
	val commitSha = System.getenv("GITHUB_SHA") ?: "Unknown"
	val commitMessage = System.getenv("GITHUB_EVENT_HEAD_COMMIT_MESSAGE") ?: "No commit message"
	val commitAuthor = System.getenv("GITHUB_ACTOR") ?: "Unknown"
	val authorUrl = "https://github.com/$commitAuthor"
	val authorAvatar = "https://github.com/$commitAuthor.png"

	val embed = Embed(
		title = "[${repo}:${branch}] 1 new commit",
		description = "[`${commitSha.substring(0, 7)}`](https://github.com/${repo}/commit/${commitSha}) $commitMessage",
		color = 0x7289DA,
		timestamp = Instant.now().toString(),
		footer = Footer("GitHub", "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"),
		author = Author(commitAuthor, authorUrl, authorAvatar)
	)

	val payload = WebhookPayload(
		username = "GitHub",
		avatarUrl = "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png",
		embeds = listOf(embed)
	)

	sendDiscordWebhook(webhookUrl, payload)
}

fun sendIssueWebhook(webhookUrl: String, repo: String, branch: String) {
	val issueTitle = System.getenv("GITHUB_ISSUE_TITLE") ?: "Unknown Issue"
	val issueBody = System.getenv("GITHUB_ISSUE_BODY") ?: "No description"
	val issueUrl = System.getenv("GITHUB_ISSUE_URL") ?: "Unknown URL"
	val issueNumber = System.getenv("GITHUB_ISSUE_NUMBER") ?: "Unknown"
	val issueAuthor = System.getenv("GITHUB_ISSUE_AUTHOR") ?: "Unknown Author"
	val authorUrl = "https://github.com/$issueAuthor"
	val authorAvatar = "https://github.com/$issueAuthor.png"
	val issueState = System.getenv("GITHUB_ISSUE_STATE") ?: "Unknown State"
	val eventType = System.getenv("GITHUB_EVENT_TYPE")
		?.replaceFirstChar { it.uppercaseChar() }
		?: "Updated"

	val embedColor = when (issueState) {
		"open" -> 0x00FF00
		"edited", "reopened" -> 0x00CC99
		"closed" -> 0x0099FF
		else -> 0x808080
	}

	val issueStatusPrefix = if (issueState == "closed") "[Closed] " else ""

	val truncatedBody = if (issueBody.length > 550) issueBody.take(550) + "..." else issueBody

	val embed = Embed(
		title = "[${repo}:${branch}] $issueStatusPrefix$eventType: #$issueNumber $issueTitle",
		description = "[**$issueTitle**]($issueUrl)\n\n$truncatedBody",
		color = embedColor,
		timestamp = Instant.now().toString(),
		footer = Footer("GitHub Issues", "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"),
		author = Author(issueAuthor, authorUrl, authorAvatar)
	)

	val payload = WebhookPayload(
		username = "GitHub Issues",
		avatarUrl = "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png",
		embeds = listOf(embed)
	)

	sendDiscordWebhook(webhookUrl, payload)
}

fun sendPullRequestWebhook(webhookUrl: String, repo: String, branch: String) {
	val prTitle = System.getenv("GITHUB_PR_TITLE") ?: "Unknown PR"
	val prBody = System.getenv("GITHUB_PR_BODY") ?: "No description"
	val prUrl = System.getenv("GITHUB_PR_URL") ?: "Unknown URL"
	val prNumber = System.getenv("GITHUB_PR_NUMBER") ?: "Unknown"
	val prAuthor = System.getenv("GITHUB_PR_AUTHOR") ?: "Unknown Author"
	val authorUrl = "https://github.com/$prAuthor"
	val authorAvatar = "https://github.com/$prAuthor.png"
	val eventType = System.getenv("GITHUB_EVENT_TYPE")?.replaceFirstChar { it.uppercaseChar() } ?: "Updated"

	val isMerged = System.getenv("GITHUB_PR_MERGED")?.toBoolean() == true
	val prState = when {
		isMerged -> "merged"
		System.getenv("GITHUB_PR_STATE") == "closed" -> "closed"
		else -> "open"
	}

	val embedColor = when (prState) {
		"open" -> 0x007AFF
		"closed" -> 0xFF0000
		"merged" -> 0x800080
		else -> 0x808080
	}

	val truncatedBody = if (prBody.length > 550) prBody.take(550) + "..." else prBody

	val embed = Embed(
		title = "[${repo}:${branch}] $eventType: #$prNumber $prTitle",
		description = "[**$prTitle**]($prUrl)\n\n$truncatedBody",
		color = embedColor,
		timestamp = Instant.now().toString(),
		footer = Footer("GitHub PR", "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"),
		author = Author(prAuthor, authorUrl, authorAvatar)
	)

	val payload = WebhookPayload(
		username = "GitHub PR",
		avatarUrl = "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png",
		embeds = listOf(embed)
	)

	sendDiscordWebhook(webhookUrl, payload)
}

fun sendDiscordWebhook(webhookUrl: String, payload: WebhookPayload) {
	val gson = Gson()
	val jsonPayload = gson.toJson(payload)

	val url = URI(webhookUrl).toURL()
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

// > ==== For Data Structures ==== < \\
data class WebhookPayload(
	val username: String,
	val avatarUrl: String,
	val embeds: List<Embed>
)

data class Embed(
	val title: String,
	val description: String,
	val color: Int,
	val timestamp: String,
	val footer: Footer,
	val author: Author
)

data class Footer(
	val text: String,
	val iconUrl: String
)

data class Author(
	val name: String,
	val url: String,
	val iconUrl: String
)
