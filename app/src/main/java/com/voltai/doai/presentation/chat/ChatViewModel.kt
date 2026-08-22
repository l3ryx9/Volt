package com.voltai.doai.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voltai.doai.di.ServiceLocator
import com.voltai.doai.domain.models.ExecutionReport
import com.voltai.doai.domain.models.ExecutionStatus
import com.voltai.doai.domain.models.Message
import com.voltai.doai.domain.models.ModelStatus
import com.voltai.doai.domain.models.PhaseState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class ChatViewModel : ViewModel() {

    private val agentEngine = ServiceLocator.agentEngine
    private val githubManager = ServiceLocator.githubManager

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    /** Messages gérés par l'agent Qwen (compréhension, plan, exécution, validation). */
    val messages: StateFlow<List<Message>> = agentEngine.messages

    val busy: StateFlow<Boolean> = agentEngine.busy

    val currentPhase: StateFlow<PhaseState> = agentEngine.currentPhase

    /** Historique des exécutions de l'orchestrateur. */
    val history: StateFlow<List<ExecutionReport>> = agentEngine.history

    /** État du modèle Qwen distant (connexion / prêt / erreur). */
    val modelStatus: StateFlow<ModelStatus> = ServiceLocator.modelManager.status

    /** Progression de l'exécution (étapes faites/total + étape courante). */
    val executionStatus: StateFlow<ExecutionStatus> = agentEngine.executionStatus

    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || agentEngine.busy.value) return
        _inputText.value = ""
        viewModelScope.launch {
            agentEngine.send(text)
        }
    }

    fun stop() = agentEngine.stop()
    fun pause() = agentEngine.pause()
    fun resume() = agentEngine.resume()
    fun confirmAction() = agentEngine.confirmAction()
    fun denyAction() = agentEngine.denyAction()

    fun setConfirmationMode(enabled: Boolean) = agentEngine.setConfirmationMode(enabled)
    fun isConfirmationMode(): Boolean = agentEngine.isConfirmationMode()

    fun clearHistory() {
        agentEngine.clearConversation()
        agentEngine.clearHistory()
    }

    /** Démarre une nouvelle session (conversation et historique remis à zéro). */
    fun newSession() {
        agentEngine.clearConversation()
        agentEngine.clearHistory()
    }

    /** Vide la session active. */
    fun clearSession() {
        agentEngine.clearConversation()
    }

    /** Insère une commande/chemin dans le champ de saisie (pièce jointe, outil). */
    fun insertIntoInput(text: String) {
        val current = _inputText.value
        _inputText.value = if (current.isBlank()) text else "$current $text"
    }

    private val _repos = MutableStateFlow<List<File>>(emptyList())
    val repos: StateFlow<List<File>> = _repos.asStateFlow()

    private val _selectedRepo = MutableStateFlow<String?>(null)
    val selectedRepo: StateFlow<String?> = _selectedRepo.asStateFlow()

    private val _pushing = MutableStateFlow(false)
    val pushing: StateFlow<Boolean> = _pushing.asStateFlow()

    private val _pushResult = MutableStateFlow<String?>(null)
    val pushResult: StateFlow<String?> = _pushResult.asStateFlow()

    init {
        refreshRepos()
    }

    fun refreshRepos() {
        _repos.value = runCatching { githubManager.listLocalRepos() }.getOrDefault(emptyList())
    }

    fun selectRepo(path: String) {
        _selectedRepo.value = path
        insertIntoInput(path)
    }

    fun clearSelectedRepo() {
        _selectedRepo.value = null
    }

    fun pushSelectedRepo() {
        val repo = _selectedRepo.value ?: return
        _pushing.value = true
        _pushResult.value = null
        viewModelScope.launch {
            val result = runCatching { githubManager.pushRepo(repo) }.getOrNull()
            _pushResult.value = when {
                result == null -> "✗ Erreur lors du push."
                result.exitCode == 0 -> "✓ Push réussi (VoltAI : modifications)"
                result.error?.contains("nothing to commit", ignoreCase = true) == true -> "✓ Aucune modification à pousser"
                else -> "✗ Push : ${result.error ?: result.output}"
            }
            _pushing.value = false
        }
    }
}
