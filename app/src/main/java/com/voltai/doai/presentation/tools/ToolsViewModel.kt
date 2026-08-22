package com.voltai.doai.presentation.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voltai.doai.di.ServiceLocator
import com.voltai.doai.domain.interfaces.EnvironmentStatus
import com.voltai.doai.domain.models.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ToolsViewModel : ViewModel() {

    private val environmentManager = ServiceLocator.environmentManager
    private val commandExecutor = ServiceLocator.commandExecutor

    private val _status = MutableStateFlow("Environnement prêt")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _envStatus = MutableStateFlow<EnvironmentStatus>(environmentManager.getEnvironmentStatus())
    val envStatus: StateFlow<EnvironmentStatus> = _envStatus.asStateFlow()

    private val _results = MutableStateFlow<List<CommandResult>>(emptyList())
    val results: StateFlow<List<CommandResult>> = _results.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _testCommand = MutableStateFlow("")
    val testCommand: StateFlow<String> = _testCommand.asStateFlow()

    init {
        refreshStatus()
    }

    fun onTestCommandChange(text: String) {
        _testCommand.value = text
    }

    fun refreshStatus() {
        _envStatus.value = environmentManager.getEnvironmentStatus()
    }

    fun initializeEnvironment() {
        runTask("Initialisation de l'environnement...") { environmentManager.initializeTermuxEnvironment() }
    }

    fun installBasicPackages() {
        runTask("Installation des packages de base...") { environmentManager.installBasicPackages() }
    }

    fun installProotDistro() {
        runTask("Installation de proot-distro...") { environmentManager.installProotDistro() }
    }

    fun installUbuntu() {
        runTask("Installation d'Ubuntu 24.04...") { environmentManager.installUbuntu() }
    }

    fun setupUbuntu() {
        runTask("Configuration d'Ubuntu...") { environmentManager.setupUbuntuEnvironment() }
    }

    fun testRuntime(context: android.content.Context) {
        runTask("Test du runtime Termux embarqué...") {
            com.voltai.doai.data.terminal.TermuxRuntimeManager.init(context.applicationContext)
            val installed = com.voltai.doai.data.terminal.TermuxRuntimeManager.isRuntimeInstalled
            if (!installed) {
                return@runTask CommandResult(
                    "proot login test",
                    "",
                    "Runtime non décompressé (bootstrap échoué) : ${com.voltai.doai.data.terminal.TermuxRuntimeManager.lastError ?: "erreur inconnue"}",
                    -1,
                    0L
                )
            }
            // Vérifie proot + shell réellement exécutables sur l'appareil.
            commandExecutor.executeCommand(
                "echo RUNTIME_OK && command -v proot && proot --version"
            )
        }
    }

    fun repairEnvironment(context: android.content.Context) {
        runTask("Réparation de l'environnement...") {
            com.voltai.doai.data.tools.AutoFixer.repairNow(context.applicationContext)
        }
    }

    fun executeTestCommand() {
        val command = _testCommand.value.trim()
        if (command.isEmpty()) return
        runTask("Exécution de : $command") { commandExecutor.executeCommand(command) }
        _testCommand.value = ""
    }

    fun analyzeAndExecute(request: String) {
        val trimmed = request.trim()
        if (trimmed.isEmpty()) return
        runTask("Analyse : $trimmed") {
            val command = commandExecutor.analyzeRequest(trimmed)
            if (command.isBlank()) {
                CommandResult("", "", "Demande non reconnue comme une commande exécutable", -1, 0L)
            } else {
                commandExecutor.executeCommand(command)
            }
        }
    }

    private fun runTask(message: String, task: suspend () -> CommandResult) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            _status.value = message
            try {
                val result = task()
                _results.value = _results.value + result
                _status.value = if (result.exitCode == 0) {
                    "Terminé (${result.duration / 1000.0}s)"
                } else {
                    "Échec (code ${result.exitCode})"
                }
            } finally {
                _busy.value = false
                refreshStatus()
            }
        }
    }
}
