package com.voltai.doai.data.qwen

import com.voltai.doai.domain.interfaces.LlamaEngine
import com.voltai.doai.domain.models.GenerationResult
import kotlinx.coroutines.flow.Flow

/**
 * Moteur d'inférence Qwen distant : communique avec llama-server exécuté sur
 * Google Colab via QwenClient. Implémente l'interface LlamaEngine existante
 * pour ne rien changer au reste de l'architecture (AgentEngine, ViewModel).
 *
 * Le téléphone ne contient ni llama.cpp, ni llama-server, ni modèle GGUF :
 * seule l'URL publique du tunnel est utilisée.
 */
class QwenEngineImpl(
    private val qwenClient: QwenClient
) : LlamaEngine {

    @Volatile
    private var available = false

    override suspend fun loadModel(): Boolean {
        available = qwenClient.checkModel()
        return available
    }

    override fun isLoaded(): Boolean = available

    override fun isNativeAvailable(): Boolean = true

    override suspend fun complete(
        prompt: String,
        systemPrompt: String,
        maxTokens: Int,
        contextSize: Int
    ): GenerationResult = qwenClient.complete(prompt, systemPrompt, maxTokens)

    override fun streamGenerate(
        prompt: String,
        systemPrompt: String,
        maxTokens: Int,
        contextSize: Int
    ): Flow<String> = qwenClient.streamGenerate(prompt, systemPrompt, maxTokens)

    override suspend fun release() {
        available = false
    }
}