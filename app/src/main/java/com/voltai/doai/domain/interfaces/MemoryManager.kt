package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.LlamaMemory

/**
 * Mémoire de Qwen : sauvegarde réussites, erreurs, solutions et commandes
 * efficaces, et les rappelle en fonction de la demande.
 */
interface MemoryManager {

    /** Sauvegarde une entrée mémoire (Demande → Action → Résultat → Modification → Validation). */
    suspend fun record(entry: LlamaMemory)

    /** Demandes menées à bien. */
    fun successes(): List<LlamaMemory>

    /** Erreurs rencontrées. */
    fun errors(): List<LlamaMemory>

    /** Solutions efficaces. */
    fun solutions(): List<LlamaMemory>

    /** Commandes avérées efficaces. */
    fun effectiveCommands(): List<LlamaMemory>

    /** Entrées les plus pertinentes pour une demande donnée. */
    fun recall(query: String, limit: Int = 5): List<LlamaMemory>

    /** Toutes les entrées, de la plus récente à la plus ancienne. */
    fun all(): List<LlamaMemory>

    /** Vide la mémoire. */
    suspend fun clear()
}
