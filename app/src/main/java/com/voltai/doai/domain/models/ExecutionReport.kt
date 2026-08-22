package com.voltai.doai.domain.models

/**
 * États d'une phase d'exécution de l'orchestrateur.
 * Une seule phase peut être active à la fois.
 */
enum class PhaseState {
    PENDING,
    ACTIVE,
    AWAITING_CONFIRMATION,
    BUILDING,
    TESTING,
    VALIDATING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Résultat d'une étape exécutée par l'orchestrateur.
 * COMMAND / OUTPUT / ERROR / EXIT_CODE / DURATION / VALIDATION.
 */
data class StepReport(
    val step: TaskStep,
    val command: String,
    val output: String,
    val error: String?,
    val exitCode: Int,
    val duration: Long,
    val validated: Boolean,
    val observations: List<String> = emptyList()
)

/**
 * Rapport d'exécution complet retourné par l'orchestrateur pour chaque demande.
 * Contient : COMMAND, OUTPUT, ERROR, EXIT_CODE, DURATION, FILES_CHANGED,
 * BUILD_RESULT, TEST_RESULT.
 */
data class ExecutionReport(
    val id: String,
    val request: String,
    val intent: Intent,
    val task: Task?,
    val phase: PhaseState,
    val steps: List<StepReport>,
    val filesChanged: List<String>,
    val buildResult: String?,
    val testResult: String?,
    val message: String,
    val timestamp: Long
)

/**
 * État d'exécution courant publié par l'agent (progression, étape en cours).
 */
data class ExecutionStatus(
    val phase: PhaseState,
    val currentStep: String?,
    val stepsDone: Int,
    val stepsTotal: Int,
    val message: String
)
