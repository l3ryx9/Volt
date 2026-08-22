package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.GenerationResult
import kotlinx.coroutines.flow.Flow

/**
 * Moteur d'inférence du modèle Qwen. L'implémentation actuelle communique
 * avec llama-server exécuté sur Google Colab (aucun modèle embarqué).
 */
interface LlamaEngine {

    /** Charge/vérifie le modèle (distant) : true si disponible. */
    suspend fun loadModel(): Boolean

    /** Vrai si le modèle est chargé et prêt. */
    fun isLoaded(): Boolean

    /** Vrai si l'inférence native est disponible. */
    fun isNativeAvailable(): Boolean

    /** Génération complète (non streaming). */
    suspend fun complete(
        prompt: String,
        systemPrompt: String,
        maxTokens: Int,
        contextSize: Int
    ): GenerationResult

    /** Génération en streaming, token par token. */
    fun streamGenerate(
        prompt: String,
        systemPrompt: String,
        maxTokens: Int,
        contextSize: Int
    ): Flow<String>

    /** Libère le modèle. */
    suspend fun release()
}