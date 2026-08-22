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

data class ChatSession(
    val id: Int,
    val messages: List<Message> = emptyList()
)

class ChatViewModel : ViewModel() {

    private val agentEngine = ServiceLocator.agentEngine
    private val githubManager = ServiceLocator.githubManager

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    val messages: StateFlow<List<Message>> = agentEngine.messages

    val busy: StateFlow<Boolean> = agentEngine.busy

    val currentPhase: StateFlow<PhaseState> = agentEngine.currentPhase

    val history: StateFlow<List<ExecutionReport>> = agentEngine.history

    val modelStatus: StateFlow<ModelStatus> = ServiceLocator.modelManager.status

    val executionStatus: StateFlow<ExecutionStatus> = agentEngine.executionStatus

    companion object {
        const val MAX_SESSIONS = 5
    }

    private val _sessions = MutableStateFlow(listOf(ChatSession(id = 1)))
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow(1)
    val activeSessionId: StateFlow<Int> = _activeSessionId.asStateFlow()

    private var nextSessionId = 2

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

    fun newSession() {
        if (_sessions.value.size >= MAX_SESSIONS) return
        saveCurrentSession()
        val id = nextSessionId++
        _sessions.value = _sessions.value + ChatSession(id = id)
        _activeSessionId.value = id
        agentEngine.clearConversation()
        agentEngine.clearHistory()
    }

    fun switchSession(sessionId: Int) {
        if (sessionId == _activeSessionId.value) return
        saveCurrentSession()
        _activeSessionId.value = sessionId
        val target = _sessions.value.find { it.id == sessionId } ?: return
        agentEngine.clearConversation()
        agentEngine.clearHistory()
    }

    fun deleteSession(sessionId: Int) {
        val current = _sessions.value
        if (current.size <= 1) return
        _sessions.value = current.filter { it.id != sessionId }
        if (_activeSessionId.value == sessionId) {
            val next = _sessions.value.first()
            _activeSessionId.value = next.id
            agentEngine.clearConversation()
            agentEngine.clearHistory()
        }
    }

    fun clearSession() {
        agentEngine.clearConversation()
    }

    fun insertIntoInput(text: String) {
        val current = _inputText.value
        _inputText.value = if (current.isBlank()) text else "$current $text"
    }

    private fun saveCurrentSession() {
        val activeId = _activeSessionId.value
        val currentMessages = agentEngine.messages.value
        _sessions.value = _sessions.value.map { session ->
            if (session.id == activeId) session.copy(messages = currentMessages)
            else session
        }
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
                result == null -> "Erreur lors du push."
                result.exitCode == 0 -> "Push reussi (VoltAI : modifications)"
                result.error?.contains("nothing to commit", ignoreCase = true) == true -> "Aucune modification a pousser"
                else -> "Push : ${result.error ?: result.output}"
            }
            _pushing.value = false
        }
    }
}
