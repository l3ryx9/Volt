package com.voltai.doai.data.qwen

import com.voltai.doai.domain.interfaces.ModelManager
import com.voltai.doai.domain.models.ModelPhase
import com.voltai.doai.domain.models.ModelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestionnaire du modèle Qwen distant : le « modèle » n'est pas un fichier
 * local mais le serveur llama-server de Colab. Le statut reflète la
 * disponibilité de Qwen via /v1/models. Aucun GGUF n'est stocké sur le
 * téléphone.
 */
class QwenModelManagerImpl(
    private val qwenClient: QwenClient
) : ModelManager {

    private val _status = MutableStateFlow(
        ModelStatus(ModelPhase.NOT_DOWNLOADED, message = "URL Colab non configurée")
    )
    override val status: StateFlow<ModelStatus> = _status.asStateFlow()

    override suspend fun ensureModel(): ModelStatus {
        val ok = qwenClient.checkModel()
        _status.value = if (ok) {
            ModelStatus(ModelPhase.READY, message = "Qwen distant disponible")
        } else {
            ModelStatus(
                ModelPhase.ERROR,
                message = "Qwen distant indisponible (vérifiez l'URL Colab)"
            )
        }
        return _status.value
    }

    override suspend fun verifyModel(): Boolean = qwenClient.checkModel()

    /** Aucun fichier modèle local : toujours null. */
    override fun modelPath(): String? = null

    /** Rien à supprimer localement. */
    override suspend fun deleteModel() {
        _status.value = ModelStatus(ModelPhase.NOT_DOWNLOADED, message = "URL Colab non configurée")
    }
}