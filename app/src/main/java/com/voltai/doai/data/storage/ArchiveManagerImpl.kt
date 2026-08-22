package com.voltai.doai.data.storage

import com.voltai.doai.data.terminal.ShellExecutor
import com.voltai.doai.domain.interfaces.ArchiveManager
import com.voltai.doai.domain.models.ArchiveProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ArchiveManagerImpl : ArchiveManager {

    private val supportedExtensions = setOf("apk", "aab", "zip", "jar", "rar", "7z", "tar", "gz", "tgz", "bz2")

    override fun isSupportedArchive(path: String): Boolean {
        return getArchiveType(path).isNotEmpty()
    }

    override fun getArchiveType(path: String): String {
        val name = path.substringAfterLast('/').lowercase()
        return when {
            name.endsWith(".tar.gz") || name.endsWith(".tgz") || name.endsWith(".tar.bz2") -> "tar"
            name.endsWith(".rar") -> "rar"
            name.endsWith(".7z") -> "7z"
            name.endsWith(".apk") || name.endsWith(".aab") || name.endsWith(".zip") || name.endsWith(".jar") -> "zip"
            else -> ""
        }
    }

    override fun archiveSize(path: String): Long {
        return File(path).length()
    }

    override fun extractArchive(archivePath: String, destinationDir: String, password: String?): Flow<ArchiveProgress> {
        return flow {
            val type = getArchiveType(archivePath)
            if (type.isEmpty()) {
                emit(ArchiveProgress("EXTRACT", archivePath, 0, 0, 0f, 0, 0, true, "Format non supporté"))
                return@flow
            }
            val dest = File(destinationDir)
            if (!dest.exists()) dest.mkdirs()

            when (type) {
                "zip" -> emitAll(extractZip(archivePath, dest))
                else -> emitAll(extractViaShell(type, archivePath, dest, password))
            }
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun extractZip(archivePath: String, dest: File): Flow<ArchiveProgress> = flow {
        val start = System.currentTimeMillis()
        try {
            val input = ZipInputStream(BufferedInputStream(FileInputStream(archivePath)))
            var total = 0
            var entry = input.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) total++
                entry = input.nextEntry
            }
            input.close()

            var processed = 0
            val zin = ZipInputStream(BufferedInputStream(FileInputStream(archivePath)))
            var current = zin.nextEntry
            while (current != null) {
                val zipEntry = current
                val outFile = File(dest, zipEntry.name)
                if (zipEntry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        zin.copyTo(out)
                    }
                    if (!zipEntry.name.endsWith("/")) processed++
                }
                current = zin.nextEntry
                emit(progress("EXTRACT", zipEntry.name, processed, total, start))
            }
            zin.close()
            emit(ArchiveProgress("EXTRACT", "Terminé", processed, total, 100f, System.currentTimeMillis() - start, 0, true, null))
        } catch (e: Exception) {
            emit(ArchiveProgress("EXTRACT", archivePath, 0, 0, 0f, System.currentTimeMillis() - start, 0, true, e.message ?: "Erreur d'extraction"))
        }
    }

    private suspend fun extractViaShell(type: String, archivePath: String, dest: File, password: String?): Flow<ArchiveProgress> = flow {
        val start = System.currentTimeMillis()
        emit(ArchiveProgress("EXTRACT", archivePath, 0, -1, 0f, 0, -1, false, null))
        val pwdArg = if (!password.isNullOrBlank()) "-P$password" else ""
        val command = when (type) {
            "rar" -> "unrar x -o+ $pwdArg \"$archivePath\" \"${dest.absolutePath}/\""
            "7z" -> "7z x -y $pwdArg -o\"${dest.absolutePath}\" \"$archivePath\""
            "tar" -> "tar -xf \"$archivePath\" -C \"${dest.absolutePath}\""
            else -> "unzip -o \"$archivePath\" -d \"${dest.absolutePath}\""
        }
        val result = ShellExecutor.execute(command, timeoutSeconds = 600L)
        val error = if (result.exitCode != 0) (result.error ?: "Échec de l'extraction") else null
        emit(ArchiveProgress("EXTRACT", archivePath, 0, 1, if (error == null) 100f else 0f, System.currentTimeMillis() - start, 0, true, error))
    }

    override fun createArchive(sourceDir: String, archivePath: String, compressionLevel: Int): Flow<ArchiveProgress> {
        return flow {
            val start = System.currentTimeMillis()
            val src = File(sourceDir)
            if (!src.exists() || !src.isDirectory) {
                emit(ArchiveProgress("CREATE", sourceDir, 0, 0, 0f, 0, 0, true, "Dossier source invalide"))
                return@flow
            }
            val outFile = File(archivePath)
            outFile.parentFile?.mkdirs()

            var total = 0
            src.walkTopDown().forEach { if (it.isFile) total++ }
            if (total == 0) total = 1

            var processed = 0
            val base = src.absolutePath
            try {
                val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile)))
                zos.setLevel(compressionLevel.coerceIn(0, 9))
                src.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relative = file.absolutePath.removePrefix(base).removePrefix(File.separator)
                        val entry = ZipEntry(relative)
                        entry.time = file.lastModified()
                        zos.putNextEntry(entry)
                        FileInputStream(file).use { input ->
                            val buffer = ByteArray(8192)
                            var count = input.read(buffer)
                            while (count != -1) {
                                zos.write(buffer, 0, count)
                                count = input.read(buffer)
                            }
                        }
                        zos.closeEntry()
                        processed++
                        emit(progress("CREATE", relative, processed, total, start))
                    }
                }
                zos.close()
                emit(ArchiveProgress("CREATE", "Terminé", processed, total, 100f, System.currentTimeMillis() - start, 0, true, null))
            } catch (e: Exception) {
                emit(ArchiveProgress("CREATE", sourceDir, processed, total, 0f, System.currentTimeMillis() - start, 0, true, e.message ?: "Erreur de création"))
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun progress(operation: String, currentFile: String, processed: Int, total: Int, start: Long): ArchiveProgress {
        val elapsed = System.currentTimeMillis() - start
        val percentage = if (total > 0) (processed.toFloat() / total * 100f).coerceIn(0f, 100f) else 0f
        val remaining = if (percentage > 0f) (elapsed / percentage * (100f - percentage)).toLong() else -1L
        return ArchiveProgress(operation, currentFile, processed, total, percentage, elapsed, remaining, false, null)
    }
}
