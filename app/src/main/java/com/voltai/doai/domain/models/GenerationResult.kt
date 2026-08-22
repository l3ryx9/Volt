package com.voltai.doai.domain.models

/**
 * Résultat d'une génération du moteur d'inférence distant (llama-server Colab).
 */
data class GenerationResult(
    val text: String
)