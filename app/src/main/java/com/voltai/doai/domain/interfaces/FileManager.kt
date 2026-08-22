package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.FileEntry

interface FileManager {
    fun listDirectory(path: String): List<FileEntry>
    fun getRootPaths(): List<FileEntry>
    fun readTextFile(path: String): String
    fun openFile(path: String): Boolean
    fun getFileInfo(path: String): FileEntry?
    fun searchFiles(query: String, root: String, maxResults: Int = 100): List<FileEntry>
    fun deleteFile(path: String): Boolean
    fun isTextFile(path: String): Boolean
}
