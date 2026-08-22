package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.ExecutionReport
import com.voltai.doai.domain.models.TaskStep
import kotlinx.coroutines.flow.Flow

/**
 * Gestionnaire d'exécution de l'orchestrateur : observe chaque étape
 * (répertoire, dépendances, cohérence), contrôle l'exécution (pause, arrêt)
 * et conserve l'historique des rapports.
 *
 * Ne bloque jamais une étape : les observations sont purement informatives
 * et le contrôle (pause/arrêt) est déclenché par l'utilisateur.
 */
interface ExecutionManager {
    /** Observe une étape et retourne les observations (répertoire, dépendances, cohérence). */
    fun observe(step: TaskStep): List<String>

    fun requestStop()
    fun isStopRequested(): Boolean
    fun reset()

    fun pause()
    fun resume()
    fun isPaused(): Boolean
    /** Bloque le thread d'exécution tant que la pause est active. */
    fun awaitResume()

    fun addToHistory(report: ExecutionReport)
    fun clearHistory()
    fun getHistory(): List<ExecutionReport>
    fun historyFlow(): Flow<List<ExecutionReport>>
}
