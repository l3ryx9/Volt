package com.voltai.doai.data.llama

import com.voltai.doai.domain.interfaces.PromptManager
import com.voltai.doai.domain.models.LlamaMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Construit les prompts Qwen : prompt système, historique fenêtré (contexte
 * long), mémoire pertinente et cache de réponses persistant.
 */
class PromptManagerImpl(
    private val cacheDir: File
) : PromptManager {

    private val cacheFile: File = File(cacheDir, "prompt_cache.txt")
    private val lru: LinkedHashMap<String, String> = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_CACHE
        }
    }

    init {
        loadCache()
    }

    override fun buildSystemPrompt(context: String?): String {
        val sb = StringBuilder()
        sb.appendLine(
            "Tu es Qwen, assistant IA intégré à VoltAI. Tu analyses, exécutes " +
                "et expliques les demandes de l'utilisateur sur son appareil Android."
        )
        sb.appendLine()
        sb.appendLine("Cadre de travail (structure) :")
        sb.appendLine("- Demande : ce que l'utilisateur veut faire")
        sb.appendLine("- Action : les commandes ou étapes que tu exécutes")
        sb.appendLine("- Résultat : ce que l'exécution a produit")
        sb.appendLine("- Modification : les changements appliqués")
        sb.appendLine("- Validation : la confirmation que le succès est atteint")
        sb.appendLine()
        sb.appendLine(
            "Tu disposes d'une mémoire des réussites, erreurs, solutions et commandes " +
                "efficaces : réutilise-les plutôt que de réinventer."
        )
        sb.appendLine("Réponds en français, de façon concise et directe.")
        if (!context.isNullOrBlank()) {
            sb.appendLine()
            sb.appendLine("Contexte : $context")
        }
        return sb.toString().trim()
    }

    override fun buildPrompt(
        request: String,
        history: List<String>,
        memories: List<LlamaMemory>,
        maxTokens: Int
    ): String {
        val system = buildSystemPrompt()
        val parts = mutableListOf(system)

        val historyTokens = (maxTokens * 0.4).toInt().coerceAtLeast(256)
        val kept = truncateHistory(history, historyTokens)
        if (kept.isNotEmpty()) {
            parts.add("Historique récent :")
            kept.forEach { parts.add("- $it") }
        }

        if (memories.isNotEmpty()) {
            parts.add("Mémoire pertinente :")
            memories.forEach { memory ->
                parts.add(
                    "- [${memory.kind}] ${memory.demande} → ${memory.action} → ${memory.resultat}" +
                        memory.validation.let { if (it.isNotBlank()) " (validation : $it)" else "" }
                )
            }
        }

        parts.add("Demande :")
        parts.add(request)
        return parts.joinToString("\n")
    }

    override fun truncateHistory(history: List<String>, maxTokens: Int): List<String> {
        if (history.isEmpty() || maxTokens <= 0) return emptyList()
        val budget = maxTokens
        var used = 0
        val kept = mutableListOf<String>()
        for (entry in history.asReversed()) {
            val tokens = estimateTokens(entry) + 1
            if (used + tokens > budget) break
            used += tokens
            kept.add(entry)
        }
        return kept.asReversed()
    }

    override fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        // Estimation ~4 caractères par token.
        return (text.length / 4.0).toInt().coerceAtLeast(1)
    }

    override fun cacheKey(request: String, systemPrompt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$request|$systemPrompt".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    override suspend fun cached(key: String): String? = withContext(Dispatchers.IO) {
        synchronized(lru) { lru[key] }
    }

    override suspend fun cache(key: String, result: String) = withContext(Dispatchers.IO) {
        synchronized(lru) {
            lru[key] = result
        }
        persist()
    }

    override fun cacheSize(): Int = synchronized(lru) { lru.size }

    private fun loadCache() {
        if (!cacheFile.isFile) return
        runCatching {
            cacheFile.readLines().forEach { line ->
                val sep = line.indexOf('\t')
                if (sep > 0) {
                    val key = line.substring(0, sep)
                    val value = unescape(line.substring(sep + 1))
                    synchronized(lru) { lru[key] = value }
                }
            }
        }
    }

    private fun persist() {
        runCatching {
            cacheDir.mkdirs()
            val tmp = File(cacheDir, "prompt_cache.txt.tmp")
            tmp.writeText(buildString {
                synchronized(lru) {
                    lru.forEach { (key, value) ->
                        append(key).append('\t').append(escape(value)).append('\n')
                    }
                }
            })
            tmp.renameTo(cacheFile)
        }
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }

    private fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    companion object {
        private const val MAX_CACHE = 64
    }
}
