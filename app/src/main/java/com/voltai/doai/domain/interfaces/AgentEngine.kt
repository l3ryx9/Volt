package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.ExecutionReport
import com.voltai.doai.domain.models.ExecutionStatus
import com.voltai.doai.domain.models.Message
import com.voltai.doai.domain.models.PhaseState
import kotlinx.coroutines.flow.StateFlow

/**
 * Agent autonome Qwen : cerveau décisionnel qui comprend la demande,
 * planifie, délègue l'exécution à l'Orchestrateur, analyse les résultats,
 * valide, mémorise et répond.
 *
 * L'orchestrateur n'exécute jamais de demande que Qwen n'a pas décidée,
 * et Qwen ne force aucune restriction sur l'orchestrateur.
 */
interface AgentEngine {
    val messages: StateFlow<List<Message>>
    val busy: StateFlow<Boolean>
    val currentPhase: StateFlow<PhaseState>
    val executionStatus: StateFlow<ExecutionStatus>
    val history: StateFlow<List<ExecutionReport>>

    /** Traite une demande utilisateur : comprendre → planifier → exécuter → analyser → valider. */
    suspend fun send(text: String)

    fun stop()
    fun pause()
    fun resume()

    /** Répond à une demande de confirmation de modification. */
    fun confirmAction()
    fun denyAction()

    fun setConfirmationMode(enabled: Boolean)
    fun isConfirmationMode(): Boolean

    fun clearConversation()
    fun clearHistory()
}
