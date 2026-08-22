package com.voltai.doai.domain.models

data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val extension: String,
    val lastModified: Long
) {
    val displaySize: String
        get() = if (isDirectory) "" else formatSize(size)

    companion object {
        fun formatSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var value = bytes.toDouble()
            var index = 0
            while (value >= 1024 && index < units.size - 1) {
                value /= 1024
                index++
            }
            return if (index == 0) "${bytes} B" else String.format("%.1f %s", value, units[index])
        }
    }
}
