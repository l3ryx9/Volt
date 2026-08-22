package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.ArchiveProgress
import kotlinx.coroutines.flow.Flow

interface ArchiveManager {
    fun extractArchive(archivePath: String, destinationDir: String, password: String? = null): Flow<ArchiveProgress>
    fun createArchive(sourceDir: String, archivePath: String, compressionLevel: Int = 5): Flow<ArchiveProgress>
    fun isSupportedArchive(path: String): Boolean
    fun getArchiveType(path: String): String
    fun archiveSize(path: String): Long
}
