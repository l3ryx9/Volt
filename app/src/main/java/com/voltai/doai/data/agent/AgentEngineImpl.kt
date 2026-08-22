package com.voltai.doai.data.agent

import com.voltai.doai.di.ServiceLocator
import com.voltai.doai.domain.interfaces.AgentEngine
import com.voltai.doai.domain.interfaces.CommandExecutor
import com.voltai.doai.domain.interfaces.Complexity
import com.voltai.doai.domain.interfaces.ContextManager
import com.voltai.doai.domain.interfaces.ContextUpdate
import com.voltai.doai.domain.interfaces.ExecutionManager
import com.voltai.doai.domain.interfaces.IntentAnalyzer
import com.voltai.doai.domain.interfaces.MemoryManager
import com.voltai.doai.domain.interfaces.Orchestrator
import com.voltai.doai.domain.interfaces.TaskPlanner
import com.voltai.doai.domain.interfaces.TaskValidator
import com.voltai.doai.domain.models.CodeLanguage
import com.voltai.doai.domain.models.ExecutionReport
import com.voltai.doai.domain.models.ExecutionStatus
import com.voltai.doai.domain.models.Intent
import com.voltai.doai.domain.models.LlamaMemory
import com.voltai.doai.domain.models.MemoryKind
import com.voltai.doai.domain.models.Message
import com.voltai.doai.domain.models.PhaseState
import com.voltai.doai.domain.models.StepReport
import com.voltai.doai.domain.models.Task
import com.voltai.doai.domain.models.TaskStep
import com.voltai.doai.data.tools.AutoFixer
import com.voltai.doai.data.tools.ToolchainManager
import com.voltai.doai.data.tools.ToolchainPhase
import com.voltai.doai.data.terminal.ShellExecutor
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Agent autonome Qwen : cerveau décisionnel.
 * Comprend → planifie → délègue l'exécution à l'Orchestrateur → analyse les
 * résultats → valide → mémorise → répond.
 *
 * Qwen décide ; l'Orchestrateur exécute sans restriction.
 */
class AgentEngineImpl(
    private val intentAnalyzer: IntentAnalyzer = ServiceLocator.intentAnalyzer,
    private val taskPlanner: TaskPlanner = ServiceLocator.taskPlanner,
    private val contextManager: ContextManager = ServiceLocator.contextManager,
    private val commandExecutor: CommandExecutor = ServiceLocator.commandExecutor,
    private val orchestrator: Orchestrator = ServiceLocator.orchestrator,
    private val executionManager: ExecutionManager = ServiceLocator.executionManager,
    private val taskValidator: TaskValidator = ServiceLocator.taskValidator,
    private val memoryManager: MemoryManager = ServiceLocator.memoryManager
) : AgentEngine {

    private val sessionId = UUID.randomUUID().toString()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    override val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    override val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _currentPhase = MutableStateFlow(PhaseState.PENDING)
    override val currentPhase: StateFlow<PhaseState> = _currentPhase.asStateFlow()

    private val _executionStatus = MutableStateFlow(
        ExecutionStatus(PhaseState.PENDING, null, 0, 0, "En attente")
    )
    override val executionStatus: StateFlow<ExecutionStatus> = _executionStatus.asStateFlow()

    private val _history = MutableStateFlow<List<ExecutionReport>>(emptyList())
    override val history: StateFlow<List<ExecutionReport>> = _history.asStateFlow()

    @Volatile
    private var confirmationMode = false

    @Volatile
    private var pendingConfirmation: Boolean? = null
    private val confirmationMonitor = Object()

    private val stepCounter = java.util.concurrent.atomic.AtomicInteger()

    init {
        contextManager.createContext(sessionId)
    }

    override suspend fun send(text: String) {
        val request = text.trim()
        if (request.isEmpty() || _busy.value) return

        _busy.value = true
        _messages.value = _messages.value + Message(
            id = UUID.randomUUID().toString(),
            content = request,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        try {
            withContext(Dispatchers.IO) {
                // Garde : vérifier que le runtime + les outils sont prêts avant
                // toute exécution. Si l'installation est encore en cours (lancée
                // par l'utilisateur depuis la fenêtre « Installer les dépendances
                // nécessaires »), on attend qu'elle finisse. Si elle a échoué,
                // on informe l'utilisateur au lieu de crasher.
                val toolchainStatus = ToolchainManager.status.value
                when (toolchainStatus.phase) {
                    ToolchainPhase.RUNNING -> {
                        publishPhase(PhaseState.ACTIVE, "Installation des outils en cours, patientez…")
                        // Attendre que l'installation se termine (max 15 min)
                        var waited = 0L
                        while (ToolchainManager.status.value.phase == ToolchainPhase.RUNNING && waited < 900_000L) {
                            kotlinx.coroutines.delay(2_000L)
                            waited += 2_000L
                            val current = ToolchainManager.status.value
                            publishPhase(PhaseState.ACTIVE, current.message ?: "Installation en cours…")
                        }
                        if (ToolchainManager.status.value.phase != ToolchainPhase.DONE) {
                            publishPhase(PhaseState.FAILED, "L'installation des outils n'a pas abouti. Relancez l'application.")
                            appendAssistant("L'environnement d'exécution n'est pas encore prêt (installation en cours ou échouée). Relancez l'application et attendez que l'installation se termine avant de réessayer.")
                            return@withContext
                        }
                    }
                    ToolchainPhase.FAILED, ToolchainPhase.IDLE -> {
                        publishPhase(PhaseState.FAILED, "Outils non installés.")
                        appendAssistant("L'environnement d'exécution n'est pas installé. Appuyez sur « Installer » dans la fenêtre de dépendances pour lancer l'installation.")
                        return@withContext
                    }
                    ToolchainPhase.DONE -> { /* OK, on continue */ }
                }

                val intent = intentAnalyzer.analyzeRequest(request)
                contextManager.updateContext(sessionId, ContextUpdate(lastAction = intent.action))
                publishPhase(PhaseState.ACTIVE, "Qwen analyse la demande...")

                val conversational = intent.action == "GENERIC_COMMAND" && intent.target.isBlank() && intent.confidence < 0.6f
                if (conversational) {
                    handleConversational(request)
                    return@withContext
                }

                if (isModification(intent) && confirmationMode) {
                    publishPhase(PhaseState.AWAITING_CONFIRMATION, "Qwen attend votre confirmation pour modifier.")
                    if (awaitConfirmation() != true) {
                        publishPhase(PhaseState.CANCELLED, "Modification annulée par l'utilisateur.")
                        appendAssistant(
                            "Modification annulée. La demande « $request » n'a pas été exécutée."
                        )
                        return@withContext
                    }
                }

                val task = taskPlanner.createPlan(intent)
                if (!taskPlanner.validatePlan(task)) {
                    publishPhase(PhaseState.FAILED, "Plan invalide — impossible d'exécuter.")
                    appendAssistant(buildInvalidPlanText(intent, request))
                    return@withContext
                }

                appendAssistant(buildPlanText(intent, task))
                publishStatus(PhaseState.ACTIVE, "Qwen exécute le plan...", 0, task.steps.size)

                stepCounter.set(0)
                val report = orchestrator.execute(task, onStep = { stepReport ->
                    appendStepToLastMessage(stepReport)
                    publishStatus(
                        PhaseState.ACTIVE,
                        "Exécution de ${stepReport.command}",
                        stepCounter.incrementAndGet(),
                        task.steps.size
                    )
                })

                // Après toute décompilation/analyse : voltai-fix.sh vérifie
                // les dépendances de chaque programme utilisé et installe
                // automatiquement les packages manquants dans Ubuntu proot.
                // Ceci est systématique (pas seulement quand une commande échoue)
                // car certaines dépendances ne sont nécessaires qu'à l'étape
                // d'analyse post-décompilation (jadx, androguard, frida…).
                if (isDecompilationAction(intent.action) && ShellExecutor.isUbuntuInstalled) {
                    publishPhase(PhaseState.ACTIVE, "Vérification des dépendances…")
                    AutoFixer.repairNow(ServiceLocator.appContext()) { progress, message ->
                        publishPhase(PhaseState.ACTIVE, message)
                    }
                }

                val analysis = analyzeResults(intent, request, report)

                publishPhase(PhaseState.VALIDATING, "Qwen valide les résultats...")
                val finalPhase = when {
                    report.phase == PhaseState.CANCELLED -> PhaseState.CANCELLED
                    taskValidator.validateExecution(report) -> PhaseState.COMPLETED
                    else -> PhaseState.FAILED
                }
                publishPhase(finalPhase, report.message)

                rememberExecution(intent, request, report)
                executionManager.addToHistory(report)
                _history.value = executionManager.getHistory()

                appendToLastMessage(buildFinalText(intent, report, analysis))
            }
        } finally {
            _busy.value = false
        }
    }

    override fun stop() {
        orchestrator.stop()
        executionManager.requestStop()
    }

    override fun pause() {
        executionManager.pause()
        publishStatus(PhaseState.ACTIVE, "Qwen en pause (étape en attente)...", 0, 0)
    }

    override fun resume() {
        executionManager.resume()
    }

    override fun confirmAction() {
        synchronized(confirmationMonitor) {
            pendingConfirmation = true
            confirmationMonitor.notifyAll()
        }
    }

    override fun denyAction() {
        synchronized(confirmationMonitor) {
            pendingConfirmation = false
            confirmationMonitor.notifyAll()
        }
    }

    override fun setConfirmationMode(enabled: Boolean) {
        confirmationMode = enabled
    }

    override fun isConfirmationMode(): Boolean = confirmationMode

    override fun clearConversation() {
        _messages.value = emptyList()
        contextManager.clearContext(sessionId)
    }

    override fun clearHistory() {
        executionManager.clearHistory()
        _history.value = emptyList()
    }

    // ------------------------------------------------------------------
    // Conversations
    // ------------------------------------------------------------------

    private suspend fun handleConversational(request: String) {
        if (qwenAvailable()) {
            streamQwenReply(request)
            return
        }
        val command = commandExecutor.analyzeRequest(request)
        val content = if (command.isBlank()) {
            "Je comprends votre demande. Donnez-moi un fichier, un package ou une commande précise à exécuter."
        } else {
            "Demande détectée comme commande : `$command`.\nExécution..."
        }
        appendAssistant(content)
        publishPhase(PhaseState.COMPLETED, "Réponse fournie.")
    }

    private suspend fun qwenAvailable(): Boolean {
        return runCatching { ServiceLocator.qwenClient.checkModel() }.getOrDefault(false)
    }

    /** Réponse générée par Qwen distant (llama-server Colab) affichée en streaming. */
    private suspend fun streamQwenReply(request: String) {
        val promptManager = ServiceLocator.promptManager
        val engine = ServiceLocator.llamaEngine

        val system = promptManager.buildSystemPrompt()
        val key = promptManager.cacheKey(request, system)
        val assistantId = UUID.randomUUID().toString()

        val history = _messages.value
            .asReversed()
            .take(6)
            .asReversed()
            .map { msg -> "${if (msg.isUser) "Utilisateur" else "Qwen"} : ${msg.content.take(400)}" }
        val relevant = memoryManager.recall(request)
        val prompt = promptManager.buildPrompt(request, history, relevant)

        _messages.value = _messages.value + Message(
            id = assistantId,
            content = "",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        publishPhase(PhaseState.ACTIVE, "Qwen génère la réponse...")

        var full = ""
        val cached = promptManager.cached(key)
        if (cached != null) {
            full = cached
            updateMessageContent(assistantId, full)
        } else {
            engine.streamGenerate(prompt = prompt, systemPrompt = system, maxTokens = 512, contextSize = 4096).collect { chunk ->
                full += chunk
                updateMessageContent(assistantId, full)
            }
            if (full.isNotBlank()) promptManager.cache(key, full)
        }

        memoryManager.record(
            LlamaMemory(
                kind = MemoryKind.SUCCESS,
                demande = request,
                action = "Génération Qwen (llama-server Colab)",
                resultat = full.take(200),
                modification = "Réponse affichée en streaming",
                validation = "Réponse générée (${full.length} caractères)"
            )
        )
        publishPhase(PhaseState.COMPLETED, "Réponse générée.")
    }

    // ------------------------------------------------------------------
    // Analyse post-exécution (Qwen)
    // ------------------------------------------------------------------

    private suspend fun analyzeResults(intent: Intent, request: String, report: ExecutionReport): String {
        val sb = StringBuilder()
        if (intent.action == "ANALYZE_APK" && intent.target.isNotBlank()) {
            val apkPath = intent.files.firstOrNull() ?: intent.target
            sb.appendLine()
            sb.appendLine("Qwen analyse l'APK...")
            try {
                val apkReport = ServiceLocator.apkAnalyzer.analyzeApk(apkPath)
                sb.appendLine(ServiceLocator.reportGenerator.generateReport(apkReport))
            } catch (e: Exception) {
                sb.appendLine("Erreur d'analyse APK : ${e.message}")
            }
        }

        if (intent.action == "EXPLAIN_CODE" || intent.action == "SEARCH_CODE" ||
            intent.action == "DECRYPT" || intent.action == "ANALYZE_ERROR"
        ) {
            sb.appendLine()
            try {
                sb.appendLine(handleCodeComprehension(intent, request))
            } catch (e: Exception) {
                sb.appendLine("Erreur de compréhension du code : ${e.message}")
            }
        }
        return sb.toString()
    }

    private fun handleCodeComprehension(intent: Intent, request: String): String {
        val sb = StringBuilder()
        val path = intent.files.firstOrNull() ?: intent.target
        val file = java.io.File(path)
        val language = ServiceLocator.languageDetector.detectLanguage(file.name, file.takeIf { it.isFile }?.readText().orEmpty())

        if (intent.action == "ANALYZE_ERROR") {
            val source = if (file.isFile) file.readText() else ""
            val lang = language ?: ServiceLocator.languageDetector.detectLanguage(source)
                ?: CodeLanguage.KOTLIN
            val analysis = ServiceLocator.dependencyAnalyzer.analyzeError(source, lang, request, path)
            sb.appendLine("━━ Analyse d'erreur ━━")
            sb.appendLine("Catégorie : ${analysis.category}")
            sb.appendLine("Sévérité : ${analysis.severity}")
            sb.appendLine("Cause probable : ${analysis.probableCause}")
            sb.appendLine("Correction suggérée : ${analysis.suggestedFix}")
            if (analysis.hints.isNotEmpty()) {
                sb.appendLine("Indices :")
                analysis.hints.forEach { sb.appendLine("  - $it") }
            }
            return sb.toString()
        }

        if (!file.isFile) {
            if (file.isDirectory) {
                sb.appendLine("━━ Architecture du projet ━━")
                val index = ServiceLocator.codeIndexer.indexDirectory(file)
                if (index.isEmpty()) return sb.appendLine("Aucun fichier de code indexé dans $path").toString()
                index.forEach { (lang, codeIndex) ->
                    sb.appendLine("• ${lang.displayName} : ${codeIndex.fileCount} fichiers, ${codeIndex.symbolCount} symboles, " +
                        "${codeIndex.classes.size} classes, ${codeIndex.functions.size} fonctions")
                }
                val filesByLanguage = mutableMapOf<String, String>()
                file.walkTopDown().filter { it.isFile && CodeLanguage.fromPath(it.name) != null }.take(200).forEach { f ->
                    try {
                        filesByLanguage[f.absolutePath] = f.readText()
                    } catch (e: Exception) { }
                }
                val arch = ServiceLocator.dependencyAnalyzer.analyzeArchitecture(filesByLanguage)
                sb.appendLine(arch.description)
                if (arch.cycles.isNotEmpty()) sb.appendLine("⚠ Corriger les cycles pour améliorer la cohésion.")
                return sb.toString()
            }
            return sb.appendLine("Fichier introuvable : $path").toString()
        }

        val source = file.readText()
        val lang = language ?: return sb.appendLine("Langage non reconnu pour $path").toString()

        when (intent.action) {
            "EXPLAIN_CODE" -> {
                val symbolName = intent.target
                    .takeIf { it != file.name && !it.contains('/') && !it.contains('\\') && !it.endsWith("code") }
                val className = symbolName?.let { ServiceLocator.symbolResolver.findClass(source, lang, it, path) }
                val functionName = symbolName?.let { ServiceLocator.symbolResolver.findFunction(source, lang, it, path) }
                when {
                    className != null -> sb.appendLine(ServiceLocator.symbolResolver.explainClass(source, lang, className.name, path))
                    functionName != null -> {
                        sb.appendLine("━━ Fonction : ${functionName.name} ━━")
                        functionName.signature?.let { sb.appendLine("Signature : ${it.take(180)}") }
                        sb.appendLine("Ligne : ${functionName.line}")
                        if (functionName.parentName != null) sb.appendLine("Appartient à : ${functionName.parentName}")
                    }
                    else -> sb.appendLine(ServiceLocator.symbolResolver.explainFile(source, lang, path))
                }
            }
            "SEARCH_CODE" -> {
                val name = intent.target.ifBlank { request.replace(Regex("(?i)(trouve|cherche|recherche|la|le|fonction|classe|code|dans|dans|stp|s'il|te|plait|tu|peux|moi)"), "").trim() }
                val fn = ServiceLocator.symbolResolver.findFunction(source, lang, name, path)
                val cls = ServiceLocator.symbolResolver.findClass(source, lang, name, path)
                sb.appendLine("━━ Recherche « $name » dans $path ━━")
                if (cls != null) sb.appendLine("Classe trouvée : ${cls.name} (ligne ${cls.line})${if (cls.obfuscated) " ⚠ obfusquée" else ""}")
                if (fn != null) sb.appendLine("Fonction trouvée : ${fn.name}() (ligne ${fn.line})${if (fn.obfuscated) " ⚠ obfusquée" else ""}")
                if (cls == null && fn == null) {
                    val matches = ServiceLocator.symbolResolver.extractSymbols(source, lang, path)
                        .filter { it.name.contains(name, ignoreCase = true) }
                    if (matches.isEmpty()) sb.appendLine("Aucun symbole « $name » trouvé.")
                    else matches.take(10).forEach { sb.appendLine("  - ${it.type} ${it.name} (ligne ${it.line})") }
                }
            }
            "DECRYPT" -> {
                val techniques = ServiceLocator.dependencyAnalyzer.detectObfuscation(source, lang)
                if (techniques.isEmpty()) {
                    sb.appendLine("Aucune obfuscation évidente détectée dans $path.")
                } else {
                    sb.appendLine("━━ Techniques d'obfuscation détectées ━━")
                    techniques.forEach { sb.appendLine("• ${it.name} : ${it.description}") }
                }
                sb.appendLine()
                sb.appendLine(ServiceLocator.dependencyAnalyzer.deobfuscate(source, lang))
            }
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // Construction de la réponse
    // ------------------------------------------------------------------

    private fun buildInvalidPlanText(intent: Intent, request: String): String {
        val sb = StringBuilder()
        sb.appendLine("Analyse : ${intent.action} (confiance ${(intent.confidence * 100).toInt()}%)")
        if (intent.target.isNotBlank()) sb.appendLine("Cible : ${intent.target}")
        if (intent.files.isNotEmpty()) sb.appendLine("Fichiers : ${intent.files.joinToString(", ")}")
        if (intent.tools.isNotEmpty()) sb.appendLine("Outils : ${intent.tools.joinToString(", ")}")
        sb.appendLine("Complexité : ${complexityLabel(intent.complexity)}")
        sb.appendLine()
        sb.appendLine("Plan invalide — impossible d'exécuter.")
        return sb.toString()
    }

    private fun buildPlanText(intent: Intent, task: Task): String {
        val sb = StringBuilder()
        sb.appendLine("Analyse : ${intent.action} (confiance ${(intent.confidence * 100).toInt()}%)")
        if (intent.target.isNotBlank()) sb.appendLine("Cible : ${intent.target}")
        if (intent.files.isNotEmpty()) sb.appendLine("Fichiers : ${intent.files.joinToString(", ")}")
        if (intent.tools.isNotEmpty()) sb.appendLine("Outils : ${intent.tools.joinToString(", ")}")
        sb.appendLine("Complexité : ${complexityLabel(intent.complexity)}")
        sb.appendLine()
        sb.appendLine("Plan d'action (${task.steps.size} étapes) :")
        task.steps.forEachIndexed { index, step ->
            sb.appendLine("  ${index + 1}. [${step.tool}] ${step.command}")
        }
        sb.appendLine()
        sb.appendLine("Qwen exécute...")
        return sb.toString()
    }

    private fun buildFinalText(intent: Intent, report: ExecutionReport, analysis: String): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("━━ Validation ━━")
        val valid = taskValidator.validateExecution(report)
        if (valid) {
            sb.appendLine("Résultats validés (${report.steps.size} étapes réussies).")
        } else {
            sb.appendLine("Exécution terminée avec échecs (${report.steps.count { it.exitCode != 0 }} étapes en erreur).")
        }
        if (report.filesChanged.isNotEmpty()) {
            sb.appendLine("Fichiers modifiés/créés : ${report.filesChanged.joinToString(", ")}")
        }
        report.buildResult?.let { sb.appendLine("Build : $it") }
        report.testResult?.let { sb.appendLine("Tests : $it") }
        sb.append(analysis)
        return sb.toString()
    }

    private fun appendStepToLastMessage(stepReport: StepReport) {
        val content = buildStepText(stepReport)
        _messages.value = _messages.value.mapIndexed { index, msg ->
            if (index == _messages.value.lastIndex && !msg.isUser) {
                msg.copy(content = msg.content + content)
            } else {
                msg
            }
        }
    }

    private fun buildStepText(stepReport: StepReport): String {
        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("Étape : ${stepReport.command}")
        val output = if (stepReport.output.isNotBlank()) "    ${stepReport.output.take(600)}\n" else ""
        val error = stepReport.error?.let { "    ERREUR: ${it.take(300)}\n" } ?: ""
        sb.appendLine("    → exit ${stepReport.exitCode} (${stepReport.duration} ms)")
        if (output.isNotBlank()) sb.append(output)
        if (error.isNotBlank()) sb.append(error)
        if (stepReport.observations.isNotEmpty()) {
            sb.appendLine("    Observation : ${stepReport.observations.firstOrNull()}")
        }
        return sb.toString()
    }

    private fun appendAssistant(content: String) {
        _messages.value = _messages.value + Message(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun appendToLastMessage(content: String) {
        _messages.value = _messages.value.mapIndexed { index, msg ->
            if (index == _messages.value.lastIndex && !msg.isUser) {
                msg.copy(content = msg.content + content)
            } else {
                msg
            }
        }
    }

    private fun updateMessageContent(id: String, content: String) {
        _messages.value = _messages.value.map { if (it.id == id) it.copy(content = content) else it }
    }

    // ------------------------------------------------------------------
    // Mémoire Qwen (structure Demande/Action/Résultat/Modification/Validation)
    // ------------------------------------------------------------------

    private suspend fun rememberExecution(intent: Intent, request: String, report: ExecutionReport) {
        val demande = request.ifBlank { intent.target }
        report.steps.forEach { stepReport ->
            val success = stepReport.exitCode == 0
            memoryManager.record(
                LlamaMemory(
                    kind = if (success) MemoryKind.EFFECTIVE_COMMAND else MemoryKind.ERROR,
                    demande = demande,
                    action = stepReport.command,
                    resultat = stepReport.output.take(200),
                    modification = if (success) "Exécutée" else "Aucune",
                    validation = "exit ${stepReport.exitCode} (${stepReport.duration} ms)"
                )
            )
        }
        if (report.steps.isNotEmpty() && report.steps.all { it.exitCode == 0 }) {
            memoryManager.record(
                LlamaMemory(
                    kind = MemoryKind.SUCCESS,
                    demande = demande,
                    action = "Plan Qwen exécuté",
                    resultat = "${report.steps.size} étapes validées",
                    validation = "OK"
                )
            )
        }
        if (intent.action == "EXPLAIN_CODE" || intent.action == "SEARCH_CODE" ||
            intent.action == "DECRYPT" || intent.action == "ANALYZE_ERROR"
        ) {
            memoryManager.record(
                LlamaMemory(
                    kind = MemoryKind.SOLUTION,
                    demande = demande,
                    action = "Analyse ${intent.action}",
                    resultat = intent.target.ifBlank { "Code analysé" },
                    validation = "Analyse fournie"
                )
            )
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private fun isModification(intent: Intent): Boolean {
        return intent.action == "MODIFY_CODE" ||
            intent.action == "DECRYPT" ||
            intent.action == "DECOMPILE"
    }

    /** Actions qui impliquent une décompilation et nécessitent une vérification
     *  systématique des dépendances après exécution. */
    private fun isDecompilationAction(action: String): Boolean {
        return action == "ANALYZE_APK" ||
            action == "DECOMPILE" ||
            action == "DECRYPT" ||
            action == "REVERSE_ENGINEER" ||
            action == "EXTRACT_ARCHIVE"
    }

    private fun awaitConfirmation(): Boolean {
        synchronized(confirmationMonitor) {
            while (pendingConfirmation == null) {
                try {
                    confirmationMonitor.wait(120_000)
                } catch (_: InterruptedException) {
                    break
                }
            }
            val answer = pendingConfirmation ?: true
            pendingConfirmation = null
            return answer
        }
    }

    private fun publishPhase(phase: PhaseState, message: String) {
        _currentPhase.value = phase
        publishStatus(phase, message, _executionStatus.value.stepsDone, _executionStatus.value.stepsTotal)
    }

    private fun publishStatus(phase: PhaseState, message: String, stepsDone: Int, stepsTotal: Int) {
        _executionStatus.value = ExecutionStatus(
            phase = phase,
            currentStep = message,
            stepsDone = stepsDone,
            stepsTotal = stepsTotal,
            message = message
        )
    }

    private fun complexityLabel(complexity: Complexity): String {
        return when (complexity) {
            Complexity.SIMPLE -> "Simple"
            Complexity.MEDIUM -> "Moyenne"
            Complexity.COMPLEX -> "Complexe"
            Complexity.EXPERT -> "Expert"
        }
    }
}
