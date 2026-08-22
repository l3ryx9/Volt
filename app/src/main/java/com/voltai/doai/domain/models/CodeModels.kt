package com.voltai.doai.domain.models

enum class CodeLanguage(val displayName: String, val extensions: List<String>) {
    KOTLIN("Kotlin", listOf(".kt", ".kts")),
    JAVA("Java", listOf(".java")),
    PYTHON("Python", listOf(".py", ".pyw")),
    C("C", listOf(".c", ".h")),
    CPP("C++", listOf(".cpp", ".cc", ".cxx", ".hpp", ".hh", ".hxx")),
    RUST("Rust", listOf(".rs")),
    GO("Go", listOf(".go")),
    JAVASCRIPT("JavaScript", listOf(".js", ".mjs", ".cjs", ".jsx", ".ts", ".tsx")),
    SMALI("Smali", listOf(".smali")),
    XML("XML", listOf(".xml")),
    JSON("JSON", listOf(".json", ".jsonc"));

    companion object {
        fun fromPath(path: String): CodeLanguage? {
            val lower = path.lowercase()
            return entries.firstOrNull { lang -> lang.extensions.any { lower.endsWith(it) } }
        }

        fun fromContent(content: String): CodeLanguage? {
            val trimmed = content.trim()
            if (trimmed.startsWith("<?xml") || trimmed.startsWith("<") && !trimmed.startsWith("//")) return XML
            if (trimmed.startsWith("{")) return JSON
            if (trimmed.startsWith("#!/") || trimmed.contains(Regex("^\\s*(from|import)\\s+\\w+.*$", RegexOption.MULTILINE))) return PYTHON
            if (trimmed.contains(Regex("^\\s*package\\s+[a-z][\\w.]+\\s*;", RegexOption.MULTILINE)) ||
                trimmed.contains(Regex("^\\s*public\\s+(class|interface|enum)\\s+\\w+", RegexOption.MULTILINE))) return JAVA
            if (trimmed.contains(Regex("^\\s*(package|import)\\s+[a-zA-Z_][\\w.]*", RegexOption.MULTILINE)) &&
                trimmed.contains(Regex("\\bfun\\s+\\w+\\s*\\(", RegexOption.MULTILINE))) return KOTLIN
            if (trimmed.contains(Regex("^\\s*(use|mod|fn|struct|impl|trait)\\s+\\w+", RegexOption.MULTILINE)) &&
                trimmed.contains("->")) return RUST
            if (trimmed.contains(Regex("^\\s*package\\s+\\w+\\s*;", RegexOption.MULTILINE)) &&
                trimmed.contains(Regex("^\\s*func\\s+\\w+", RegexOption.MULTILINE))) return GO
            if (trimmed.contains(Regex("^\\s*\\.[a-z_\\-]+\\s", RegexOption.MULTILINE)) &&
                trimmed.contains(Regex("^\\.method\\b", RegexOption.MULTILINE))) return SMALI
            if (trimmed.contains(Regex("^\\s*(const|let|var)\\s+\\w+", RegexOption.MULTILINE)) ||
                trimmed.contains(Regex("^\\s*function\\s+\\w+\\s*\\(", RegexOption.MULTILINE))) return JAVASCRIPT
            if (trimmed.contains(Regex("^\\s*(#include|typedef|struct\\s+\\w+\\s*\\{)", RegexOption.MULTILINE)) &&
                trimmed.contains(";")) return C
            if (trimmed.contains(Regex("^\\s*(namespace|using\\s+namespace|class\\s+\\w+\\s*:)", RegexOption.MULTILINE))) return CPP
            return null
        }
    }
}

enum class SymbolType {
    CLASS, INTERFACE, ENUM, ANNOTATION, OBJECT, STRUCT, TRAIT, MODULE,
    FUNCTION, METHOD, CONSTRUCTOR, FIELD, PROPERTY, VARIABLE, CONSTANT,
    PARAMETER, IMPORT, PACKAGE, ELEMENT, NAMESPACE, RECORD, MACRO
}

data class CodeSymbol(
    val name: String,
    val type: SymbolType,
    val language: CodeLanguage,
    val file: String = "",
    val line: Int = 0,
    val modifiers: List<String> = emptyList(),
    val parentName: String? = null,
    val signature: String? = null,
    val obfuscated: Boolean = false
)

data class CodeIndex(
    val root: String,
    val language: CodeLanguage,
    val fileCount: Int,
    val symbolCount: Int,
    val symbols: List<CodeSymbol>,
    val classes: List<CodeSymbol>,
    val functions: List<CodeSymbol>,
    val imports: List<String>
)

enum class DependencyType { IMPORT, INHERITANCE, IMPLEMENTATION, CALL, USE }

data class CodeDependency(
    val from: String,
    val to: String,
    val type: DependencyType,
    val file: String = "",
    val line: Int = 0
)

data class ArchitectureInfo(
    val entryPoints: List<String>,
    val modules: List<String>,
    val dependencies: List<CodeDependency>,
    val cycles: List<List<String>>,
    val description: String
)

data class ObfuscationTechnique(
    val name: String,
    val description: String,
    val indicators: List<String>
)

data class ErrorAnalysis(
    val category: String,
    val severity: String,
    val probableCause: String,
    val suggestedFix: String,
    val hints: List<String>
)
