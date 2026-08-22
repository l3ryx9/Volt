package com.voltai.doai.domain.models

/**
 * Phases du cycle de vie du modèle Qwen distant.
 */
enum class ModelPhase {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    LOADING,
    READY,
    ERROR,
    UNAVAILABLE
}

/**
 * État du modèle Qwen distant (serveur llama-server sur Colab).
 */
data class ModelStatus(
    val phase: ModelPhase,
    val message: String? = null,
    val percent: Int = 0
)