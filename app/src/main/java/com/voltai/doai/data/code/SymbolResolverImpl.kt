package com.voltai.doai.data.code

import com.voltai.doai.domain.interfaces.SymbolResolver
import com.voltai.doai.domain.models.CodeLanguage
import com.voltai.doai.domain.models.CodeSymbol
import com.voltai.doai.domain.models.SymbolType

class SymbolResolverImpl(
    private val astExtractor: AstSymbolExtractor = AstSymbolExtractor(),
    private val treeSitterExtractor: TreeSitterSymbolExtractor = TreeSitterSymbolExtractor()
) : SymbolResolver {

    override fun extractSymbols(source: String, language: CodeLanguage, file: String): List<CodeSymbol> {
        val tsSymbols = treeSitterExtractor.extract(source, language, file)
        if (tsSymbols != null && tsSymbols.isNotEmpty()) return tsSymbols
        return astExtractor.extractWith(source, language, file)
    }

    override fun findFunction(source: String, language: CodeLanguage, name: String, file: String): CodeSymbol? {
        val wanted = name.lowercase()
        return extractSymbols(source, language, file).firstOrNull {
            (it.type == SymbolType.FUNCTION || it.type == SymbolType.METHOD) && it.name.equals(wanted, ignoreCase = true)
        }
    }

    override fun findClass(source: String, language: CodeLanguage, name: String, file: String): CodeSymbol? {
        val wanted = name.lowercase()
        return extractSymbols(source, language, file).firstOrNull {
            it.type in CodeIndexerImpl.CLASS_LIKE && (it.name.equals(wanted, ignoreCase = true) ||
                it.name.contains(wanted, ignoreCase = true) || wanted.contains(it.name.lowercase()))
        }
    }

    override fun explainClass(source: String, language: CodeLanguage, className: String, file: String): String {
        val symbols = extractSymbols(source, language, file)
        val cls = findClass(source, language, className, file)
            ?: return "Classe « $className » introuvable dans ce fichier.\n" +
                "Classes détectées : ${symbols.filter { it.type in CodeIndexerImpl.CLASS_LIKE }.map { it.name }.joinToString(", ").ifEmpty { "aucune" }}"

        val sb = StringBuilder()
        val mods = if (cls.modifiers.isNotEmpty()) " [${cls.modifiers.joinToString(" ")}]" else ""
        sb.appendLine("Classe : ${cls.name} (${language.displayName})$mods")
        val sig = cls.signature
        if (!sig.isNullOrBlank()) sb.appendLine("Signature : ${sig.take(160)}")
        if (cls.parentName != null) sb.appendLine("Conteneur : ${cls.parentName}")
        if (file.isNotBlank()) sb.appendLine("Fichier : $file (ligne ${cls.line})")

        val lineage = extractLineage(source, language, cls.name)
        if (lineage.isNotEmpty()) sb.appendLine("Héritage : ${lineage.joinToString(" -> ")}")

        val fields = symbols.filter { it.parentName == cls.name && it.type in FIELD_LIKE }
        if (fields.isNotEmpty()) {
            sb.appendLine("Champs (${fields.size}) :")
            fields.forEach { sb.appendLine("  - ${it.name}${obfuscationMark(it)}${modsSuffix(it)}") }
        }

        val methods = symbols.filter {
            it.parentName == cls.name &&
                (it.type == SymbolType.METHOD || it.type == SymbolType.CONSTRUCTOR || it.type == SymbolType.FUNCTION)
        }
        if (methods.isNotEmpty()) {
            sb.appendLine("Méthodes (${methods.size}) :")
            methods.forEach { sb.appendLine("  - ${it.name}()${obfuscationMark(it)} (ligne ${it.line})") }
        }

        val nested = symbols.filter { it.parentName == cls.name && it.type in CodeIndexerImpl.CLASS_LIKE }
        if (nested.isNotEmpty()) sb.appendLine("Classes imbriquées : ${nested.map { it.name }.joinToString(", ")}")

        val lineCount = source.lines().size
        sb.appendLine("Complexité estimée : $lineCount lignes, ${symbols.size} symboles dans le fichier")

        val obfuscated = symbols.filter { it.parentName == cls.name && it.obfuscated }
        if (obfuscated.isNotEmpty()) {
            sb.appendLine("⚠ Signaux d'obfuscation : ${obfuscated.map { it.name }.joinToString(", ")}")
        }
        return sb.toString()
    }

    override fun explainFile(source: String, language: CodeLanguage, path: String): String {
        val symbols = extractSymbols(source, language, path)
        val sb = StringBuilder()
        sb.appendLine("Fichier : ${path.ifBlank { "(source en mémoire)" }}")
        sb.appendLine("Langage : ${language.displayName} (${symbols.size} symboles, ${source.lines().size} lignes)")

        val pkg = symbols.firstOrNull { it.type == SymbolType.PACKAGE }
        if (pkg != null) sb.appendLine("Paquetage : ${pkg.name.removePrefix("package ")}")

        val imports = symbols.filter { it.type == SymbolType.IMPORT }
        if (imports.isNotEmpty()) {
            sb.appendLine("Imports (${imports.size}) :")
            imports.take(15).forEach { sb.appendLine("  - ${it.signature ?: it.name}") }
            if (imports.size > 15) sb.appendLine("  … et ${imports.size - 15} de plus")
        }

        val classes = symbols.filter { it.type in CodeIndexerImpl.CLASS_LIKE }
        if (classes.isNotEmpty()) {
            sb.appendLine("Classes/Types (${classes.size}) : ${classes.map { it.name }.joinToString(", ")}")
        }

        val functions = symbols.filter { it.type == SymbolType.FUNCTION }
        if (functions.isNotEmpty()) {
            sb.appendLine("Fonctions (${functions.size}) : ${functions.map { it.name }.joinToString(", ")}")
        }

        val obfuscated = symbols.count { it.obfuscated }
        if (obfuscated > 0) sb.appendLine("⚠ ${obfuscated} symboles potentiellement obfusqués")

        val dependencies = DependencyAnalyzerImpl().analyzeDependencies(source, language, path)
        if (dependencies.isNotEmpty()) {
            val targets = dependencies.map { it.to }.distinct()
            sb.appendLine("Dépendances (${targets.size}) : ${targets.take(20).joinToString(", ")}")
        }
        return sb.toString()
    }

    private fun extractLineage(source: String, language: CodeLanguage, className: String): List<String> {
        val chain = mutableListOf(className)
        var current = className
        var remaining = source
        repeat(8) {
            val pattern = when (language) {
                CodeLanguage.KOTLIN -> Regex("\\b(class|interface|enum class|object)\\s+${Regex.escape(current)}(?:\\s*\\([^)]*\\))?\\s*:\\s*([A-Za-z_][\\w.]*)")
                CodeLanguage.JAVA -> Regex("\\b(class|interface|enum)\\s+${Regex.escape(current)}\\s+(?:extends|implements)\\s+([A-Za-z_][\\w.,\\s]*)")
                CodeLanguage.CPP -> Regex("\\bclass\\s+${Regex.escape(current)}\\s*:\\s*(?:public|private|protected)?\\s*([A-Za-z_][\\w:]*)")
                CodeLanguage.PYTHON -> Regex("\\bclass\\s+${Regex.escape(current)}\\s*\\(([^)]+)\\)")
                else -> null
            } ?: return chain
            val m = pattern.find(remaining) ?: return chain
            val parent = m.groupValues[2].trim().substringBefore(',').substringBefore(' ').substringAfterLast('.')
            if (parent.isEmpty() || parent == current || parent in chain) return chain
            chain.add(parent)
            current = parent
            remaining = remaining.substring(m.range.last)
        }
        return chain
    }

    private fun modsSuffix(symbol: CodeSymbol): String =
        if (symbol.modifiers.isNotEmpty()) " [${symbol.modifiers.joinToString(" ")}]" else ""

    private fun obfuscationMark(symbol: CodeSymbol): String =
        if (symbol.obfuscated) " ⚠ (obfusqué)" else ""

    companion object {
        private val FIELD_LIKE = setOf(
            SymbolType.FIELD, SymbolType.PROPERTY, SymbolType.VARIABLE, SymbolType.CONSTANT
        )
    }
}
