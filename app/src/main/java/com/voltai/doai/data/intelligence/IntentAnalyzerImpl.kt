package com.voltai.doai.data.intelligence

import com.voltai.doai.domain.interfaces.Complexity
import com.voltai.doai.domain.interfaces.IntentAnalyzer
import com.voltai.doai.domain.models.Intent

class IntentAnalyzerImpl : IntentAnalyzer {

    override fun analyzeRequest(request: String): Intent {
        val action = detectAction(request)
        val target = detectTarget(request)
        val files = detectFiles(request)
        val tools = detectTools(request)
        val complexity = estimateComplexity(request)
        val confidence = estimateConfidence(action, target, request)

        return Intent(
            action = action,
            target = target,
            tools = tools,
            files = files,
            complexity = complexity,
            confidence = confidence
        )
    }

    override fun detectAction(request: String): String {
        val text = normalize(request)
        return when {
            containsAny(text, listOf("analyse", "analyser", "analyze", "explique", "expliquer", "comprends", "comprendre")) &&
                containsAny(text, listOf("erreur", "error", "bug", "crash", "exception", "plant")) ->
                ACTION_ANALYZE_ERROR

            containsAny(text, listOf("analyse", "analyser", "analyze")) &&
                containsAny(text, listOf("apk", "aab")) -> ACTION_ANALYZE_APK

            containsAny(text, listOf("analyse", "analyser", "analyze", "etudie", "inspecte", "reverse")) ->
                ACTION_ANALYZE_APK

            containsAny(text, listOf("decompil", "smali", "dex", "desassemble", "disassemble")) ->
                ACTION_DECOMPILE

            containsAny(text, listOf("dechiffr", "decrypt", "desobfus", "obfuscation", "crypte")) ->
                ACTION_DECRYPT

            containsAny(text, listOf("reverse engineering", "reverse-engineer", "reverse_engineer", "analyse complete", "analyse approfondie", "cartographie", "reconstruction des flux", "dechiffrement")) ->
                ACTION_REVERSE_ENGINEER

            containsAny(text, listOf("decompresse", "decomprime", "extrait", "extraire", "unzip", "unrar", "7z")) ->
                ACTION_EXTRACT_ARCHIVE

            containsAny(text, listOf("compresse", "comprime", "zippe", "creer une archive", "creer l'archive")) ->
                ACTION_CREATE_ARCHIVE

            containsAny(text, listOf("modifie", "modifier", "modif le code", "changer", "edite", "edit")) ->
                ACTION_MODIFY_CODE

            containsAny(text, listOf("explique", "expliquer", "comprends", "comprendre")) ->
                ACTION_EXPLAIN_CODE

            containsAny(text, listOf("recherche", "cherche", "trouve", "grep")) ->
                ACTION_SEARCH_CODE

            containsAny(text, listOf("installe", "install", "installation")) ->
                ACTION_INSTALL_PACKAGE

            containsAny(text, listOf("mets a jour", "mettre a jour", "update", "maj")) ->
                ACTION_UPDATE_PACKAGES

            containsAny(text, listOf("liste", "lister", "list", "affiche les fichiers")) ->
                ACTION_LIST_FILES

            containsAny(text, listOf("telecharge", "download", "wget", "curl")) ->
                ACTION_DOWNLOAD

            containsAny(text, listOf("clone", "git")) ->
                ACTION_GIT_CLONE

            else -> ACTION_GENERIC_COMMAND
        }
    }

    override fun detectTarget(request: String): String {
        val action = detectAction(request)
        if (action == ACTION_DOWNLOAD || action == ACTION_GIT_CLONE) {
            val url = Regex("https?://\\S+").find(request)?.value?.trim('.', '?', '!', ',')
            if (!url.isNullOrBlank()) return url
        }

        val file = detectFile(request)
        if (file.isNotEmpty()) return file

        val url = Regex("https?://\\S+").find(request)?.value?.trim('.', '?', '!', ',')
        if (!url.isNullOrBlank()) return url

        val text = normalize(request)
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val stop = when (action) {
            ACTION_INSTALL_PACKAGE -> setOf(
                "installe", "install", "peux-tu", "moi", "le", "la", "les", "package", "un", "une",
                "s'il", "te", "plait", "stp", "tu", "pour", "merci", "et", "de"
            )
            ACTION_GIT_CLONE -> setOf("git", "clone", "le", "la", "les", "de", "du", "repertoire", "repo")
            ACTION_EXTRACT_ARCHIVE -> setOf(
                "decompresse", "decomprime", "extrait", "extraire", "le", "la", "les", "archive",
                "fichier", "ce", "cette", "cet", "mon", "ma", "mes", "du", "de", "l'"
            )
            else -> setOf(
                "analyse", "analyser", "apk", "le", "la", "les", "ce", "cette", "cet", "mon", "ma",
                "mes", "fichier", "fichiers", "de", "du", "des", "code", "pour", "moi", "s'il",
                "plait", "stp", "tu", "peux", "et", "un", "une", "decompresse", "extrait"
            )
        }
        val candidate = tokens.firstOrNull { it !in stop && !it.startsWith("--") }
        return candidate?.trim('\'', '"', '.', '?', '!', ',') ?: ""
    }

    override fun detectTools(request: String): List<String> {
        val action = detectAction(request)
        return when (action) {
            ACTION_ANALYZE_APK -> listOf("apktool", "jadx", "androguard", "ai")
            ACTION_DECOMPILE -> listOf("jadx", "smali", "baksmali")
            ACTION_DECRYPT -> listOf("frida-gadget", "jadx-string-decrypt", "paranoid-deobfuscator", "proguard-deobfuscator")
            ACTION_REVERSE_ENGINEER -> listOf("apktool", "jadx", "androguard", "7z", "baksmali", "python", "ai")
            ACTION_EXTRACT_ARCHIVE -> detectArchiveTool(request)
            ACTION_CREATE_ARCHIVE -> listOf(detectArchiveTool(request).firstOrNull() ?: "zip")
            ACTION_MODIFY_CODE -> listOf("sed", "python")
            ACTION_EXPLAIN_CODE -> listOf("ai", "tree-sitter")
            ACTION_SEARCH_CODE -> listOf("grep", "ripgrep")
            ACTION_ANALYZE_ERROR -> listOf("tree-sitter", "ai")
            ACTION_INSTALL_PACKAGE -> listOf("pkg", "apt")
            ACTION_UPDATE_PACKAGES -> listOf("pkg", "apt")
            ACTION_LIST_FILES -> listOf("find", "ls")
            ACTION_DOWNLOAD -> listOf("wget", "curl")
            ACTION_GIT_CLONE -> listOf("git")
            else -> emptyList()
        }
    }

    override fun detectFiles(request: String): List<String> {
        val matches = FILE_PATTERN.findAll(request).map { it.value }.toList()
        return matches.distinct()
    }

    override fun estimateComplexity(request: String): Complexity {
        val action = detectAction(request)
        return when (action) {
            ACTION_ANALYZE_APK, ACTION_DECRYPT, ACTION_ANALYZE_ERROR, ACTION_REVERSE_ENGINEER -> Complexity.EXPERT
            ACTION_DECOMPILE, ACTION_MODIFY_CODE, ACTION_EXPLAIN_CODE -> Complexity.COMPLEX
            ACTION_SEARCH_CODE, ACTION_CREATE_ARCHIVE, ACTION_GIT_CLONE -> Complexity.MEDIUM
            else -> Complexity.SIMPLE
        }
    }

    private fun detectFile(request: String): String {
        return FILE_PATTERN.find(request)?.value ?: ""
    }

    private fun detectArchiveTool(request: String): List<String> {
        return when {
            containsAny(normalize(request), listOf(".rar", "unrar")) -> listOf("unrar")
            containsAny(normalize(request), listOf(".7z", "7z")) -> listOf("7z", "p7zip")
            containsAny(normalize(request), listOf(".tar", ".tgz", ".tar.gz", "tar")) -> listOf("tar")
            containsAny(normalize(request), listOf(".jar", ".apk", ".aab")) -> listOf("unzip", "zip")
            else -> listOf("unzip", "zip")
        }
    }

    private fun estimateConfidence(action: String, target: String, request: String): Float {
        var confidence = 0.5f
        if (action != ACTION_GENERIC_COMMAND) confidence += 0.25f
        if (target.isNotEmpty()) confidence += 0.2f
        if (normalize(request).length > 20) confidence += 0.05f
        return confidence.coerceAtMost(0.95f)
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }

    private fun normalize(request: String): String {
        return request.lowercase().replace(Regex("[àâäéèêëîïôöùûüç]")) { c ->
            when (c.value) {
                "à", "â", "ä" -> "a"; "é", "è", "ê", "ë" -> "e"
                "î", "ï" -> "i"; "ô", "ö" -> "o"; "ù", "û", "ü" -> "u"; "ç" -> "c"
                else -> c.value
            }
        }
    }

    companion object {
        const val ACTION_ANALYZE_APK = "ANALYZE_APK"
        const val ACTION_DECOMPILE = "DECOMPILE"
        const val ACTION_DECRYPT = "DECRYPT"
        const val ACTION_ANALYZE_ERROR = "ANALYZE_ERROR"
        const val ACTION_REVERSE_ENGINEER = "REVERSE_ENGINEER"
        const val ACTION_EXTRACT_ARCHIVE = "EXTRACT_ARCHIVE"
        const val ACTION_CREATE_ARCHIVE = "CREATE_ARCHIVE"
        const val ACTION_MODIFY_CODE = "MODIFY_CODE"
        const val ACTION_EXPLAIN_CODE = "EXPLAIN_CODE"
        const val ACTION_SEARCH_CODE = "SEARCH_CODE"
        const val ACTION_INSTALL_PACKAGE = "INSTALL_PACKAGE"
        const val ACTION_UPDATE_PACKAGES = "UPDATE_PACKAGES"
        const val ACTION_LIST_FILES = "LIST_FILES"
        const val ACTION_DOWNLOAD = "DOWNLOAD"
        const val ACTION_GIT_CLONE = "GIT_CLONE"
        const val ACTION_GENERIC_COMMAND = "GENERIC_COMMAND"

        val FILE_PATTERN = Regex(
            "[\\w./\\-]+?\\.(apk|aab|zip|rar|7z|jar|dex|smali|java|kt|xml|json|txt|py|go|rs|c|h|cpp|js|ts|so|enc|dat|bin|tar|gz|tgz)"
        )
    }
}
