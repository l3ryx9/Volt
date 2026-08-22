package com.voltai.doai.domain.interfaces

import com.voltai.doai.domain.models.ArchitectureInfo
import com.voltai.doai.domain.models.CodeDependency
import com.voltai.doai.domain.models.CodeIndex
import com.voltai.doai.domain.models.CodeLanguage
import com.voltai.doai.domain.models.CodeSymbol
import com.voltai.doai.domain.models.ErrorAnalysis
import com.voltai.doai.domain.models.ObfuscationTechnique
import java.io.File

interface LanguageDetector {
    fun detectLanguage(path: String): CodeLanguage?
    fun detectLanguage(path: String?, content: String): CodeLanguage?
}

interface CodeIndexer {
    fun indexFile(file: File): CodeIndex?
    fun indexDirectory(dir: File): Map<CodeLanguage, CodeIndex>
    fun indexFiles(files: List<File>): Map<CodeLanguage, CodeIndex>
}

interface SymbolResolver {
    fun extractSymbols(source: String, language: CodeLanguage, file: String = ""): List<CodeSymbol>
    fun findFunction(source: String, language: CodeLanguage, name: String, file: String = ""): CodeSymbol?
    fun findClass(source: String, language: CodeLanguage, name: String, file: String = ""): CodeSymbol?
    fun explainClass(source: String, language: CodeLanguage, className: String, file: String = ""): String
    fun explainFile(source: String, language: CodeLanguage, path: String): String
}

interface DependencyAnalyzer {
    fun analyzeDependencies(source: String, language: CodeLanguage, file: String = ""): List<CodeDependency>
    fun analyzeArchitecture(files: Map<String, String>): ArchitectureInfo
    fun analyzeError(source: String, language: CodeLanguage, errorMessage: String, file: String = ""): ErrorAnalysis
    fun obfuscationTechniques(): List<ObfuscationTechnique>
    fun detectObfuscation(source: String, language: CodeLanguage): List<ObfuscationTechnique>
    fun deobfuscate(source: String, language: CodeLanguage): String
}
