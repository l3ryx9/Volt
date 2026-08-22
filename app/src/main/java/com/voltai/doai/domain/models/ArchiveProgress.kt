package com.voltai.doai.domain.models

data class ArchiveProgress(
    val operation: String,
    val currentFile: String,
    val processedFiles: Int,
    val totalFiles: Int,
    val percentage: Float,
    val elapsedMs: Long,
    val remainingMs: Long,
    val isFinished: Boolean,
    val error: String?
) {
    val isIndeterminate: Boolean
        get() = totalFiles <= 0
}
