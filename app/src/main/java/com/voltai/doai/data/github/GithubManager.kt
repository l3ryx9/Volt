package com.voltai.doai.data.github

import com.voltai.doai.data.terminal.ShellExecutor
import com.voltai.doai.domain.models.CommandResult
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gère la connexion GitHub : test du token, clonage de dépôts dans le
 * répertoire workspace/repos de l'application et push des modifications.
 *
 * git est déployé dans le rootfs Ubuntu proot (via le bundle d'outils) ;
 * le répertoire des dépôts (filesDir/workspace/repos) est exposé dans le
 * conteneur via --bind pour que les commandes git puissent y travailler.
 */
class GithubManager(
    private val settings: GithubSettings,
    private val reposDir: File
) {

    /** Vérifie le token auprès de l'API GitHub (/user). */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val token = settings.currentToken()
            if (token.isBlank()) return@withContext false
            val conn = URL("https://api.github.com/user").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("User-Agent", "VoltAI/1.0")
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    /** Déduit "owner/repo" depuis une URL GitHub (HTTPS ou SSH). */
    private fun extractOwnerRepo(url: String): Pair<String, String>? {
        val clean = url.trim().removeSuffix(".git").trimEnd('/')
        val candidate = when {
            clean.contains("github.com/") -> clean.substringAfter("github.com/")
            clean.contains("github.com:") -> clean.substringAfter("github.com:")
            clean.contains("@") -> clean.substringAfter("@")
            else -> clean
        }
        val parts = candidate.split("/")
        if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            return parts[0] to parts[1]
        }
        return null
    }

    /** URL HTTPS avec les identifiants intégrés (uniquement pour la commande). */
    private fun authUrl(owner: String, repo: String): String {
        val user = URLEncoder.encode(settings.currentUsername(), "UTF-8")
        val token = URLEncoder.encode(settings.currentToken(), "UTF-8")
        return "https://$user:$token@github.com/$owner/$repo.git"
    }

    /** Clone un dépôt GitHub dans workspace/repos/<repo>. */
    suspend fun cloneRepo(url: String): CommandResult {
        val pair = extractOwnerRepo(url)
            ?: return CommandResult(url, "", "URL GitHub invalide.", -1, 0)
        val (owner, repo) = pair
        return withContext(Dispatchers.IO) {
            reposDir.mkdirs()
            val dest = File(reposDir, repo).absolutePath
            val command = "mkdir -p '$dest' && git -C '$reposDir' clone '${authUrl(owner, repo)}' '$dest'"
            ShellExecutor.executeUbuntu(command, timeoutSeconds = 600, binds = listOf(reposDir.absolutePath))
        }
    }

    /** Liste les dépôts locaux (dossiers git) sous workspace/repos. */
    fun listLocalRepos(): List<File> {
        if (!reposDir.exists()) return emptyList()
        return reposDir.listFiles()
            ?.filter { it.isDirectory && File(it, ".git").exists() }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    /** Push des modifications du dépôt (add/commit/push). */
    suspend fun pushRepo(repoPath: String): CommandResult = withContext(Dispatchers.IO) {
        val repo = File(repoPath)
        val remote = getOrigin(repo) ?: return@withContext CommandResult(
            "git push", "", "Dépôt sans origine distante.", -1, 0
        )
        val pair = extractOwnerRepo(remote)
            ?: return@withContext CommandResult("git push", "", "Origine distante invalide.", -1, 0)
        val (owner, repoName) = pair

        val add = ShellExecutor.executeUbuntu(
            "git -C '$repoPath' add -A",
            timeoutSeconds = 120,
            binds = listOf(reposDir.absolutePath)
        )
        if (add.exitCode != 0) {
            return@withContext add
        }

        val commit = ShellExecutor.executeUbuntu(
            "git -C '$repoPath' commit -m 'VoltAI : modifications'",
            timeoutSeconds = 120,
            binds = listOf(reposDir.absolutePath)
        )
        // Exit 1 = rien à commiter (aucune modification) : non bloquant.
        if (commit.exitCode != 0 &&
            commit.error?.contains("nothing to commit", ignoreCase = true) == false &&
            !commit.output.contains("nothing to commit", ignoreCase = true)
        ) {
            return@withContext commit
        }

        val branch = ShellExecutor.executeUbuntu(
            "git -C '$repoPath' branch --show-current",
            timeoutSeconds = 60,
            binds = listOf(reposDir.absolutePath)
        ).output.trim().ifBlank { "main" }

        return@withContext ShellExecutor.executeUbuntu(
            "git -C '$repoPath' push '${authUrl(owner, repoName)}' HEAD:'$branch'",
            timeoutSeconds = 600,
            binds = listOf(reposDir.absolutePath)
        )
    }

    private fun getOrigin(repo: File): String? {
        val result = ShellExecutor.executeUbuntu(
            "git -C '${repo.absolutePath}' remote get-url origin",
            timeoutSeconds = 60,
            binds = listOf(reposDir.absolutePath)
        )
        return result.output.trim().ifBlank { null }
    }
}