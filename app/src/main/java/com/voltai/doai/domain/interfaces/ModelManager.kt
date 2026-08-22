package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.ModelStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Vérifie la disponibilité du serveur Qwen distant au démarrage de l'app.
 */
interface ModelManager {

    /** Flux d'état du serveur Qwen distant (connexion / prêt / erreur). */
    val status: StateFlow<ModelStatus>

    /**
     * Vérifie que le serveur Qwen distant (llama-server Colab) est disponible
     * via GET /v1/models et met à jour le statut.
     */
    suspend fun ensureModel(): ModelStatus

    /** Vérifie la disponibilité du serveur Qwen distant. */
    suspend fun verifyModel(): Boolean

    /** Aucun fichier modèle local : retourne toujours null. */
    fun modelPath(): String?

    /** Rien à supprimer localement. */
    suspend fun deleteModel()
}
