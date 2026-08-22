package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.FileEntry
import java.io.File

interface WorkspaceManager {
    fun getWorkspaceDir(): File
    fun getDownloadsDir(): File
    fun getExtractDir(): File
    fun getAnalysisDir(): File
    fun getReposDir(): File
    fun resetWorkspace(): Boolean
    fun getWorkspaceSize(): Long
    fun getFilesCount(): Int
    fun listWorkspace(): List<FileEntry>
}
