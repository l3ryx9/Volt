package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.ExecutionReport
import com.voltai.doai.domain.models.StepReport
import com.voltai.doai.domain.models.Task
import com.voltai.doai.domain.models.TaskStep

/**
 * Orchestrateur = moteur d'exécution de VoltAI.
 *
 * Il reçoit les décisions de Qwen (un plan [Task] validé) et les exécute.
 * Il n'invente jamais une intention et n'ajoute aucune restriction : chaque
 * étape du plan est exécutée et rapportée fidèlement.
 *
 * Pour chaque exécution il retourne : COMMAND, OUTPUT, ERROR, EXIT_CODE,
 * DURATION, FILES_CHANGED, BUILD_RESULT, TEST_RESULT.
 */
interface Orchestrator {
    /**
     * Exécute le plan complet.
     *
     * @param confirm callback optionnel de confirmation par étape (fourni par
     *   Qwen selon la demande) ; si null, aucune confirmation demandée.
     * @param onStep callback de progression appelé après chaque étape.
     */
    fun execute(
        task: Task,
        confirm: ((TaskStep) -> Boolean)? = null,
        onStep: ((StepReport) -> Unit)? = null
    ): ExecutionReport

    /** Demande l'arrêt de l'exécution en cours. */
    fun stop()
}
