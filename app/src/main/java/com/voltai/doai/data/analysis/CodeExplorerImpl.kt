package com.voltai.doai.data.analysis

import com.voltai.doai.domain.interfaces.CodeExplorer
import java.io.File

class CodeExplorerImpl : CodeExplorer {

    private val codeExtensions = setOf("java", "kt", "smali", "py", "c", "cpp", "rs", "go", "js", "ts")

    override fun findClasses(root: String, query: String?, maxResults: Int): List<String> {
        if (!File(root).exists()) return emptyList()
        val result = mutableListOf<String>()
        val q = query?.lowercase()?.trim()
        File(root).walkTopDown().forEach { file ->
            if (result.size >= maxResults) return@forEach
            if (file.isFile && file.extension in codeExtensions) {
                val relative = file.absolutePath.removePrefix(root).trimStart('/')
                if (q == null || relative.lowercase().contains(q)) {
                    result.add(relative)
                }
            }
        }
        return result
    }

    override fun findMethods(root: String, query: String?, maxResults: Int): List<String> {
        if (!File(root).exists()) return emptyList()
        val result = mutableListOf<String>()
        val q = query?.lowercase()?.trim()
        val methodPattern = Regex("(public|private|protected|static|final|void|synchronized).*\\(.*\\)")
        File(root).walkTopDown().forEach { file ->
            if (result.size >= maxResults) return@forEach
            if (file.isFile && (file.extension == "java" || file.extension == "kt")) {
                try {
                    file.useLines { lines ->
                        lines.take(2000).forEach line@ { line ->
                            if (result.size >= maxResults) return@line
                            val trimmed = line.trim()
                            if (methodPattern.containsMatchIn(trimmed) && trimmed.contains("(")) {
                                if (q == null || trimmed.lowercase().contains(q)) {
                                    result.add("${file.name}: ${trimmed.take(120)}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // fichier illisible, on continue
                }
            }
        }
        return result
    }

    override fun findStrings(root: String, query: String, maxResults: Int): List<String> {
        if (!File(root).exists()) return emptyList()
        val result = mutableListOf<String>()
        val q = query.lowercase()
        File(root).walkTopDown().forEach { file ->
            if (result.size >= maxResults) return@forEach
            if (file.isFile && file.extension in codeExtensions) {
                try {
                    file.useLines { lines ->
                        lines.take(3000).forEach line@ { line ->
                            if (result.size >= maxResults) return@line
                            val match = Regex("\"([^\"]*$q[^\"]*)\"", RegexOption.IGNORE_CASE)
                                .find(line)?.groupValues?.get(1)
                            if (match != null) {
                                result.add("${file.name}: \"${match.take(120)}\"")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // fichier illisible, on continue
                }
            }
        }
        return result
    }
}
