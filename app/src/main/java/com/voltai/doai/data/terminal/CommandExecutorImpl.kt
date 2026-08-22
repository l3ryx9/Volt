package com.voltai.doai.data.terminal

import com.voltai.doai.domain.interfaces.CommandExecutor
import com.voltai.doai.domain.models.CommandResult
import com.voltai.doai.data.tools.AutoFixer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CommandExecutorImpl : CommandExecutor {

    private val _executionLog = MutableStateFlow<List<CommandResult>>(emptyList())

    override fun getExecutionLog(): Flow<List<CommandResult>> = _executionLog.asStateFlow()

    private var ubuntuPrefixEnabled = true

    override fun executeCommand(command: String): CommandResult {
        return executeCommand(command, 0L)
    }

    override fun executeCommand(command: String, timeoutSeconds: Long): CommandResult {
        var result = run(command, timeoutSeconds)
        // Auto-réparation : si la commande échoue avec un outil/paquet
        // manquant, le script voltai-fix.sh installe les dépendances puis on
        // relance la commande une fois.
        // Garde : on ne tente la réparation que si Ubuntu est effectivement
        // installé — sinon voltai-fix.sh relancerait voltai-setup.sh qui
        // tenterait un téléchargement réseau complet, ce qui n'est pas le
        // comportement attendu d'un auto-fix silencieux.
        if (AutoFixer.shouldRepair(result) &&
            ShellExecutor.isUbuntuInstalled &&
            AutoFixer.requestRepair(appContext()).let { it.exitCode == 0 }
        ) {
            result = run(command, timeoutSeconds)
        }
        appendToLog(result)
        return result
    }

    private fun run(command: String, timeoutSeconds: Long = 0L): CommandResult =
        if (ubuntuPrefixEnabled && ShellExecutor.isUbuntuInstalled) {
            if (timeoutSeconds > 0) ShellExecutor.executeUbuntu(command, timeoutSeconds)
            else ShellExecutor.executeUbuntu(command)
        } else {
            if (timeoutSeconds > 0) ShellExecutor.execute(command, timeoutSeconds)
            else ShellExecutor.execute(command)
        }

    private fun appContext(): android.content.Context = com.voltai.doai.di.ServiceLocator.appContext()

    override fun analyzeRequest(request: String): String {
        val lower = request.lowercase()
        val text = lower.replace(Regex("[àâäéèêëîïôöùûüç]")) { c ->
            when (c.value) {
                "à", "â", "ä" -> "a"; "é", "è", "ê", "ë" -> "e"
                "î", "ï" -> "i"; "ô", "ö" -> "o"; "ù", "û", "ü" -> "u"; "ç" -> "c"
                else -> c.value
            }
        }

        return when {
            text.contains("install ubuntu") || (text.contains("install") && text.contains("ubuntu")) ->
                "proot-distro install ubuntu:24.04"

            text.contains("install proot") || (text.contains("proot") && text.contains("install")) ->
                "pkg update -y && pkg install -y proot-distro"

            containsAny(text, listOf("pkg update", "mets a jour", "mettre a jour", "update")) &&
                !text.contains("app") ->
                "pkg update -y && pkg upgrade -y"

            text.contains("install python") || (text.contains("python") && text.contains("install")) ->
                "pkg install -y python"

            text.contains("me faut") || text.contains("besoin de") || text.contains("besoin du") ||
                text.contains("besoin d'un") || text.contains("besoin d'une") ->
                "pkg install -y " + extractPackage(text)

            text.contains("pip install") ->
                "pip install " + extractTarget(text, "pip install")

            text.contains("git clone") ->
                "git clone " + extractTarget(text, "git clone")

            text.contains("install ") || text.contains("installe") ->
                "pkg install -y " + extractPackage(text)

            text.contains("uninstall") || text.contains("desinstalle") || text.contains("supprime le package") ->
                "pkg uninstall -y " + extractPackage(text)

            text.contains("search") || text.contains("recherche") ->
                "pkg search " + extractPackage(text)

            text.contains("list-installed") || (text.contains("liste") && text.contains("package")) ->
                "pkg list-installed"

            text.contains("liste les fichiers") || text.contains("list files") || text.contains("ls") ->
                "ls -la"

            text.contains("decompresse") || text.contains("extrait") || text.contains("unzip") ->
                "unzip -o " + extractArchive(text)

            text.contains("decomprimer") || text.contains("tar") ->
                "tar -xvf " + extractArchive(text)

            text.contains("compresse") || text.contains("zippe") || text.contains("zip ") ->
                "zip -r " + extractZipTarget(text)

            text.contains("telecharge") || text.contains("download") || text.contains("wget") ->
                "wget " + extractUrl(text)

            text.contains("curl") ->
                "curl " + extractUrl(text)

            text.contains("find ") || (text.contains("recherche") && text.contains("fichier")) ->
                "find . -name \"" + extractFilePattern(text) + "\""

            text.contains("greps") || text.contains("cherche") && text.contains("dans") ->
                "grep -r \"" + extractGrepPattern(text) + "\" ."

            text.contains("permissions") || text.contains("chmod") ->
                "chmod +x " + extractFilePattern(text)

            text.contains("dossier") || text.contains("mkdir") ->
                "mkdir -p " + extractTarget(text, "mkdir")

            text.contains("supprime le fichier") || text.contains("rm ") ->
                "rm " + extractTarget(text, "rm")

            text.contains("pwd") || text.contains("ou suis je") || text.contains("chemin actuel") ->
                "pwd"

            text.contains("whoami") || text.contains("qui suis je") ->
                "whoami"

            text.contains("uname") || text.contains("systeme") && text.contains("info") ->
                "uname -a"

            text.contains("df ") || text.contains("espace") ->
                "df -h"

            text.contains("free ") || text.contains("memoire") ->
                "free -h"

            text.contains("top ") || text.contains("processus") ->
                "ps aux"

            isLikelyCommand(lower) -> lower.trim()
            else -> ""
        }
    }

    override fun executeWithUbuntu(command: String): CommandResult {
        val result = ShellExecutor.executeUbuntu(command)
        appendToLog(result)
        return result
    }

    override fun setUbuntuPrefix(enabled: Boolean) {
        ubuntuPrefixEnabled = enabled
    }

    override fun isUbuntuPrefixEnabled(): Boolean = ubuntuPrefixEnabled

    private fun appendToLog(result: CommandResult) {
        _executionLog.value = (_executionLog.value + result).takeLast(100)
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }

    private fun extractPackage(text: String): String {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val stop = setOf(
            "pkg", "install", "-y", "installe", "uninstall", "desinstalle", "supprime",
            "le", "package", "search", "recherche", "the", "a", "pour", "moi", "apt",
            "update", "upgrade", "et", "tu", "peux", "s'il", "te", "plait", "plait"
        )
        val candidate = tokens.lastOrNull { it !in stop && !it.startsWith("--") }
            ?: tokens.firstOrNull { !it.startsWith("-") }
        return candidate?.trim('\'', '"', '.', '?', '!') ?: ""
    }

    private fun extractArchive(text: String): String {
        val match = Regex("[\\w./-]+\\.(zip|rar|7z|jar|tar\\.gz|tgz|tar\\.bz2|tar)").find(text)
        return match?.value ?: extractTarget(text, "extrait")
    }

    private fun extractZipTarget(text: String): String {
        val match = Regex("[\\w./-]+\\.zip").find(text)
        return match?.value ?: "archive.zip"
    }

    private fun extractUrl(text: String): String {
        val match = Regex("https?://\\S+").find(text)
        return match?.value?.trim('.', '?', '!', ',') ?: ""
    }

    private fun extractTarget(text: String, keyword: String): String {
        val idx = text.indexOf(keyword)
        if (idx == -1) return ""
        return text.substring(idx + keyword.length).trim().trim('\'', '"', '.', '?', '!')
    }

    private fun extractFilePattern(text: String): String {
        val match = Regex("[\"']([^\"']+)[\"']").find(text)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun extractGrepPattern(text: String): String {
        val match = Regex("[\"']([^\"']+)[\"']").find(text)
        return match?.groupValues?.get(1) ?: ".*"
    }

    private fun isLikelyCommand(lower: String): Boolean {
        val known = listOf(
            "apt", "pkg", "git", "python", "pip", "zip", "unzip", "tar", "curl",
            "wget", "grep", "sed", "awk", "find", "ls", "cd", "mkdir", "rm", "cp",
            "mv", "chmod", "chown", "cat", "echo", "touch", "head", "tail", "ps",
            "kill", "du", "df", "free", "uname", "whoami", "su", "nano", "vim",
            "python3", "java", "javac", "adb", "apktool", "jadx", "sqlite3"
        )
        val first = lower.trim().split(Regex("\\s+")).firstOrNull() ?: return false
        return known.any { first == it || first.startsWith("$it") }
    }
}
