package com.voltai.doai.data.qwen

import com.voltai.doai.data.llama.LlamaJsonParser

/**
 * Parse les réponses JSON et SSE renvoyées par llama-server sur Colab :
 * liste de modèles (/v1/models), complétion (/v1/chat/completions) et flux
 * streaming (`data: {…}` puis `data: [DONE]`).
 */
internal object QwenJsonParser {

    private val ERROR_RE = Regex("\"error\"\\s*:\\s*\\{([^}]*)\\}")

    // ------------------------------------------------------------------
    // /v1/models
    // ------------------------------------------------------------------

    /** Extrait les identifiants de modèles de la liste /v1/models. */
    fun parseModels(json: String): List<String> {
        val ids = mutableListOf<String>()
        val dataRe = Regex("\"data\"\\s*:\\s*\\[")
        val match = dataRe.find(json) ?: return ids
        val rest = json.substring(match.range.last + 1)
        val idRe = Regex("\"id\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        for (m in idRe.findAll(rest)) {
            val id = LlamaJsonParser.unescape(m.groupValues[1])
            if (id.isNotBlank()) ids.add(id)
        }
        return ids
    }

    /** Vrai si la liste contient un modèle Qwen (insensible à la casse). */
    fun hasQwen(json: String): Boolean =
        parseModels(json).any { it.contains("qwen", ignoreCase = true) }

    // ------------------------------------------------------------------
    // /v1/chat/completions (réponse complète)
    // ------------------------------------------------------------------

    /** Contenu assistant de la réponse. */
    fun parseContent(json: String): String {
        val m = Regex("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: return ""
        return LlamaJsonParser.unescape(m.groupValues[1]).trim()
    }

    /** Message d'erreur JSON (champ error), ou null si absent. */
    fun error(json: String): String? {
        val m = ERROR_RE.find(json)
        if (m == null) {
            val simple = Regex("\"error\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)
                ?: return null
            return LlamaJsonParser.unescape(simple.groupValues[1])
        }
        val msg = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(m.value)
            ?: return null
        return LlamaJsonParser.unescape(msg.groupValues[1])
    }

    // ------------------------------------------------------------------
    // Streaming SSE
    // ------------------------------------------------------------------

    /** Vrai si la ligne SSE marque la fin du flux. */
    fun isDone(line: String): Boolean = line.trim() == "data: [DONE]"

    /**
     * Extrait le fragment de contenu (delta.content) d'une ligne SSE.
     * Retourne null pour une ligne vide, un commentaire, [DONE] ou un delta
     * sans contenu.
     */
    fun parseSseLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.startsWith(":")) return null
        if (!trimmed.startsWith("data:")) return null
        if (trimmed == "data: [DONE]") return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload.isBlank()) return null
        val m = Regex("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(payload) ?: return null
        val text = LlamaJsonParser.unescape(m.groupValues[1])
        return text.ifBlank { null }
    }

    /** Convertit la réponse complète en résultat de génération. */
    fun parse(json: String): com.voltai.doai.domain.models.GenerationResult {
        val err = error(json)
        if (err != null) return com.voltai.doai.domain.models.GenerationResult("(Erreur Qwen : $err)")
        return com.voltai.doai.domain.models.GenerationResult(parseContent(json))
    }
}