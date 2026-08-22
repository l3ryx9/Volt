package com.voltai.doai.data.code

import com.voltai.doai.domain.interfaces.CodeIndexer
import com.voltai.doai.domain.interfaces.SymbolResolver
import com.voltai.doai.domain.models.CodeIndex
import com.voltai.doai.domain.models.CodeLanguage
import com.voltai.doai.domain.models.CodeSymbol
import com.voltai.doai.domain.models.SymbolType
import java.io.File

class CodeIndexerImpl(
    private val symbolResolver: SymbolResolver = SymbolResolverImpl()
) : CodeIndexer {

    override fun indexFile(file: File): CodeIndex? {
        if (!file.isFile) return null
        val source = try {
            file.readText()
        } catch (e: Exception) {
            return null
        }
        val language = CodeLanguage.fromPath(file.name)
            ?: CodeLanguage.fromContent(source)
            ?: return null
        val symbols = symbolResolver.extractSymbols(source, language, file.absolutePath)
        return buildIndex(file.absolutePath, language, listOf(file to symbols))
    }

    override fun indexDirectory(dir: File): Map<CodeLanguage, CodeIndex> {
        if (!dir.isDirectory) return emptyMap()
        return indexFiles(dir.walkTopDown().filter { it.isFile }.toList())
    }

    override fun indexFiles(files: List<File>): Map<CodeLanguage, CodeIndex> {
        val grouped = linkedMapOf<CodeLanguage, MutableList<Pair<File, List<CodeSymbol>>>>()
        for (file in files) {
            if (!file.isFile) continue
            val language = CodeLanguage.fromPath(file.name) ?: continue
            val source = try {
                file.readText()
            } catch (e: Exception) {
                continue
            }
            val symbols = symbolResolver.extractSymbols(source, language, file.absolutePath)
            grouped.getOrPut(language) { mutableListOf() }.add(file to symbols)
        }
        return grouped.mapValues { (language, entries) ->
            val root = entries.firstOrNull()?.first?.parent ?: ""
            buildIndex(root, language, entries)
        }
    }

    private fun buildIndex(
        root: String,
        language: CodeLanguage,
        entries: List<Pair<File, List<CodeSymbol>>>
    ): CodeIndex {
        val symbols = entries.flatMap { it.second }
        val classes = symbols.filter { it.type in CLASS_LIKE }
        val functions = symbols.filter { it.type == SymbolType.FUNCTION || it.type == SymbolType.METHOD }
        val imports = symbols.filter { it.type == SymbolType.IMPORT }.mapNotNull { it.signature ?: it.name }
        return CodeIndex(
            root = root,
            language = language,
            fileCount = entries.size,
            symbolCount = symbols.size,
            symbols = symbols,
            classes = classes,
            functions = functions,
            imports = imports
        )
    }

    companion object {
        val CLASS_LIKE = setOf(
            SymbolType.CLASS, SymbolType.INTERFACE, SymbolType.ENUM, SymbolType.OBJECT,
            SymbolType.STRUCT, SymbolType.TRAIT, SymbolType.ANNOTATION, SymbolType.RECORD
        )
    }
}
