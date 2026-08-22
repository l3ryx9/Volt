package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.LlamaMemory

/**
 * Construit les prompts du modèle local : système, historique, mémoire et
 * fenêtrage du contexte long, plus un cache des réponses déjà générées.
 */
interface PromptManager {

    /** Prompt système décrivant Qwen et son cadre Demande/Action/Résultat/Modification/Validation. */
    fun buildSystemPrompt(context: String? = null): String

    /**
     * Assemble le prompt complet : système + historique (contexte long) +
     * mémoire pertinente + demande.
     */
    fun buildPrompt(
        request: String,
        history: List<String> = emptyList(),
        memories: List<LlamaMemory> = emptyList(),
        maxTokens: Int = 4096
    ): String

    /** Réduit l'historique pour tenir dans maxTokens (contexte long). */
    fun truncateHistory(history: List<String>, maxTokens: Int): List<String>

    /** Estimation simple du nombre de tokens d'un texte (~4 caractères/token). */
    fun estimateTokens(text: String): Int

    /** Clé de cache pour une demande + prompt système donnés. */
    fun cacheKey(request: String, systemPrompt: String): String

    /** Réponse mise en cache, ou null. */
    suspend fun cached(key: String): String?

    /** Met en cache une réponse générée. */
    suspend fun cache(key: String, result: String)

    /** Nombre d'entrées actuellement en cache. */
    fun cacheSize(): Int
}
