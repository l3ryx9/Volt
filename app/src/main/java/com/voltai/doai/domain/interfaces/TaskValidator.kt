package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.CommandResult
import com.voltai.doai.domain.models.ExecutionReport
import com.voltai.doai.domain.models.TaskStep

/**
 * Validation des résultats réels d'une exécution.
 * Une commande n'est jamais considérée réussie uniquement parce qu'elle
 * n'a pas affiché d'erreur : le code retour et le contenu sont vérifiés.
 */
interface TaskValidator {
    /** Valide le résultat réel d'une étape (code retour, sortie attendue). */
    fun validateStepResult(result: CommandResult, step: TaskStep): Boolean

    /** Valide une exécution complète (aucune étape en échec). */
    fun validateExecution(report: ExecutionReport): Boolean

    /** Détecte les fichiers modifiés/créés à partir de la sortie et de la commande. */
    fun detectFilesChanged(output: String, command: String): List<String>

    /** Analyse une sortie de build (BUILD SUCCESSFUL / BUILD FAILED / null). */
    fun analyzeBuildResult(output: String): String?

    /** Analyse une sortie de tests (TESTS OK / TESTS FAILED / null). */
    fun analyzeTestResult(output: String): String?
}
