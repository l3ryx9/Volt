package com.voltai.doai.domain.models

/**
 * Types d'entrées mémorisées par Qwen.
 */
enum class MemoryKind {
    SUCCESS,
    ERROR,
    SOLUTION,
    EFFECTIVE_COMMAND
}

/**
 * Entrée de mémoire persistante de Qwen, au format
 * Demande → Action → Résultat → Modification → Validation.
 */
data class LlamaMemory(
    val kind: MemoryKind,
    val demande: String,
    val action: String,
    val resultat: String,
    val modification: String = "",
    val validation: String = "",
    val timestamp: Long = System.currentTimeMillis()
)