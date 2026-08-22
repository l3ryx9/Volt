package com.voltai.doai.data.storage

import android.content.Context
import com.voltai.doai.domain.interfaces.WorkspaceManager
import com.voltai.doai.domain.models.FileEntry
import java.io.File

class WorkspaceManagerImpl(private val context: Context) : WorkspaceManager {

    override fun getWorkspaceDir(): File {
        val dir = File(context.filesDir, "workspace")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getDownloadsDir(): File {
        val dir = File(getWorkspaceDir(), "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getExtractDir(): File {
        val dir = File(getWorkspaceDir(), "extracted")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getAnalysisDir(): File {
        val dir = File(getWorkspaceDir(), "analysis")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun getReposDir(): File {
        val dir = File(getWorkspaceDir(), "repos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    override fun resetWorkspace(): Boolean {
        val dir = getWorkspaceDir()
        return try {
            dir.listFiles()?.forEach { it.deleteRecursively() }
            getWorkspaceDir().mkdirs()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getWorkspaceSize(): Long {
        return getWorkspaceDir().walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    override fun getFilesCount(): Int {
        return getWorkspaceDir().walkTopDown().count { it.isFile }
    }

    override fun listWorkspace(): List<FileEntry> {
        return getWorkspaceDir().listFiles()
            ?.map {
                FileEntry(
                    name = it.name,
                    path = it.absolutePath,
                    isDirectory = it.isDirectory,
                    size = if (it.isDirectory) 0 else it.length(),
                    extension = if (it.isDirectory) "" else it.extension.lowercase(),
                    lastModified = it.lastModified()
                )
            }
            ?.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }
}
