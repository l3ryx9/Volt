package com.voltai.doai.presentation.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voltai.doai.di.ServiceLocator
import com.voltai.doai.domain.models.ArchiveProgress
import com.voltai.doai.domain.models.FileEntry
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class FilesViewModel : ViewModel() {

    private val fileManager = ServiceLocator.fileManager
    private val archiveManager = ServiceLocator.archiveManager
    private val workspaceManager = ServiceLocator.workspaceManager

    var state by mutableStateOf(FilesUiState())
        private set

    init {
        loadWorkspace()
    }

    fun loadWorkspace() {
        state = state.copy(
            currentPath = workspaceManager.getWorkspaceDir().absolutePath,
            entries = workspaceManager.listWorkspace(),
            searchResults = emptyList(),
            searchQuery = "",
            fileContent = null,
            message = null
        )
    }

    fun navigateTo(path: String) {
        val entries = fileManager.listDirectory(path)
        if (entries.isNotEmpty() || File(path).exists()) {
            state = state.copy(
                currentPath = path,
                entries = entries,
                searchResults = emptyList(),
                searchQuery = "",
                fileContent = null,
                message = null
            )
        } else {
            state = state.copy(message = "Dossier inaccessible")
        }
    }

    fun navigateUp() {
        val parent = File(state.currentPath).parent
        if (parent != null) navigateTo(parent) else loadWorkspace()
    }

    fun goToRoot() {
        navigateTo("/storage/emulated/0")
    }

    fun goToDownloads() {
        navigateTo("/storage/emulated/0/Download")
    }

    fun onSearchQueryChange(query: String) {
        state = state.copy(searchQuery = query)
    }

    fun search() {
        val query = state.searchQuery
        if (query.isBlank()) {
            state = state.copy(searchResults = emptyList(), message = "Entrez un nom de fichier")
            return
        }
        val results = fileManager.searchFiles(query, state.currentPath)
        state = state.copy(
            searchResults = results,
            message = if (results.isEmpty()) "Aucun résultat pour \"$query\"" else null
        )
    }

    fun openFile(path: String) {
        val info = fileManager.getFileInfo(path)
        if (info == null) {
            state = state.copy(message = "Fichier introuvable")
            return
        }
        if (info.isDirectory) {
            navigateTo(path)
            return
        }
        if (fileManager.isTextFile(path)) {
            state = state.copy(fileContent = fileManager.readTextFile(path))
        } else {
            state = state.copy(message = "Fichier binaire (${info.extension.uppercase()})")
        }
    }

    fun closeFile() {
        state = state.copy(fileContent = null)
    }

    fun extractArchive(path: String) {
        val type = archiveManager.getArchiveType(path)
        if (type.isEmpty()) {
            state = state.copy(message = "Archive non supportée")
            return
        }
        viewModelScope.launch {
            val dest = File(workspaceManager.getExtractDir(), File(path).nameWithoutExtension)
            dest.mkdirs()
            archiveManager.extractArchive(path, dest.absolutePath)
                .collectLatest { progress ->
                    state = state.copy(progress = progress)
                    if (progress.isFinished) {
                        state = state.copy(
                            message = progress.error ?: "Extraction terminée (${progress.processedFiles} fichiers)",
                            progress = null
                        )
                        if (progress.error == null) navigateTo(dest.absolutePath)
                    }
                }
        }
    }

    fun createArchive(sourcePath: String) {
        viewModelScope.launch {
            val src = File(sourcePath)
            val dest = File(workspaceManager.getDownloadsDir(), "${src.name}.zip")
            archiveManager.createArchive(src.absolutePath, dest.absolutePath)
                .collectLatest { progress ->
                    state = state.copy(progress = progress)
                    if (progress.isFinished) {
                        state = state.copy(
                            message = progress.error ?: "Archive créée: ${dest.absolutePath}",
                            progress = null
                        )
                    }
                }
        }
    }

    fun deleteFile(path: String) {
        val success = fileManager.deleteFile(path)
        state = state.copy(
            message = if (success) "Supprimé" else "Échec de la suppression"
        )
        navigateTo(state.currentPath)
    }

    fun resetWorkspace() {
        workspaceManager.resetWorkspace()
        loadWorkspace()
        state = state.copy(message = "Workspace réinitialisé")
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }
}

data class FilesUiState(
    val currentPath: String = "",
    val entries: List<FileEntry> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<FileEntry> = emptyList(),
    val progress: ArchiveProgress? = null,
    val fileContent: String? = null,
    val message: String? = null
)
