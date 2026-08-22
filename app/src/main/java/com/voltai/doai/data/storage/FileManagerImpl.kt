package com.voltai.doai.data.storage

import com.voltai.doai.domain.interfaces.FileManager
import com.voltai.doai.domain.models.FileEntry
import java.io.File

class FileManagerImpl : FileManager {

    private val textExtensions = setOf(
        "apk", "aab", "zip", "jar", "dex", "smali", "java", "kt", "xml", "json", "txt",
        "py", "go", "rs", "c", "cpp", "js", "ts", "sh", "md", "log", "yml", "yaml",
        "properties", "cfg", "conf", "ini", "html", "css", "gradle", "toml", "csv"
    )

    override fun listDirectory(path: String): List<FileEntry> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.map { it.toEntry() }
            ?.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    override fun getRootPaths(): List<FileEntry> {
        return listOf(
            FileEntry("/storage/emulated/0", "/storage/emulated/0", true, 0, "", 0L),
            FileEntry("/storage/emulated/0/Download", "/storage/emulated/0/Download", true, 0, "", 0L),
            FileEntry("/sdcard", "/sdcard", true, 0, "", 0L)
        )
    }

    override fun readTextFile(path: String): String {
        val file = File(path)
        if (!file.exists() || file.length() > 2_000_000) return "Fichier illisible ou trop volumineux"
        return try {
            file.readText()
        } catch (e: Exception) {
            "Erreur de lecture: ${e.message}"
        }
    }

    override fun openFile(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.isFile
    }

    override fun getFileInfo(path: String): FileEntry? {
        val file = File(path)
        if (!file.exists()) return null
        return file.toEntry()
    }

    override fun searchFiles(query: String, root: String, maxResults: Int): List<FileEntry> {
        val result = mutableListOf<FileEntry>()
        val dir = File(root)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val q = query.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty() && result.size < maxResults) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (result.size >= maxResults) break
                if (child.isDirectory) {
                    if (child.name.lowercase().contains(q)) result.add(child.toEntry())
                    stack.add(child)
                } else {
                    if (child.name.lowercase().contains(q)) result.add(child.toEntry())
                }
            }
        }
        return result.sortedBy { it.name.lowercase() }
    }

    override fun deleteFile(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        return try {
            file.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    override fun isTextFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in textExtensions
    }

    private fun File.toEntry(): FileEntry {
        return FileEntry(
            name = name.ifEmpty { absolutePath },
            path = absolutePath,
            isDirectory = isDirectory,
            size = if (isDirectory) 0 else length(),
            extension = if (isDirectory) "" else extension.lowercase(),
            lastModified = lastModified()
        )
    }
}
