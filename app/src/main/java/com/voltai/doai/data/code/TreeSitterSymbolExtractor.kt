package com.voltai.doai.data.code

import com.itsaky.androidide.treesitter.TSNode
import com.itsaky.androidide.treesitter.TSParser
import com.itsaky.androidide.treesitter.TSLanguage
import com.itsaky.androidide.treesitter.TreeSitter
import com.itsaky.androidide.treesitter.c.TSLanguageC
import com.itsaky.androidide.treesitter.cpp.TSLanguageCpp
import com.itsaky.androidide.treesitter.java.TSLanguageJava
import com.itsaky.androidide.treesitter.json.TSLanguageJson
import com.itsaky.androidide.treesitter.kotlin.TSLanguageKotlin
import com.itsaky.androidide.treesitter.python.TSLanguagePython
import com.itsaky.androidide.treesitter.xml.TSLanguageXml
import com.voltai.doai.domain.models.CodeLanguage
import com.voltai.doai.domain.models.CodeSymbol
import com.voltai.doai.domain.models.SymbolType
import java.util.Stack

/**
 * Extraction de symboles via la bibliothèque native Tree-sitter
 * (vraies grammaires compilées, embarquées pour l'appareil Android).
 * Retourne null si la bibliothèque native ou la grammaire n'est pas
 * disponible : l'appelant bascule alors sur l'analyseur AST intégré.
 */
class TreeSitterSymbolExtractor {

    private var nativeAvailable: Boolean? = null

    fun isNativeAvailable(): Boolean {
        if (nativeAvailable == null) {
            nativeAvailable = try {
                TreeSitter.loadLibrary()
                true
            } catch (_: Throwable) {
                false
            }
        }
        return nativeAvailable!!
    }

    fun extract(source: String, language: CodeLanguage, file: String = ""): List<CodeSymbol>? {
        if (!isNativeAvailable()) return null
        return try {
            val tsLanguage = grammarFor(language) ?: return null
            val parser = TSParser.create()
            try {
                parser.setLanguage(tsLanguage)
                val tree = parser.parseString(source) ?: return null
                try {
                    val symbols = mutableListOf<CodeSymbol>()
                    val bytes = source.toByteArray(Charsets.UTF_8)
                    walk(tree.getRootNode(), language, file, bytes, Stack(), symbols)
                    symbols
                } finally {
                    tree.close()
                }
            } finally {
                parser.close()
                tsLanguage.close()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun grammarFor(language: CodeLanguage): TSLanguage? = when (language) {
        CodeLanguage.KOTLIN -> TSLanguageKotlin.getInstance()
        CodeLanguage.JAVA -> TSLanguageJava.getInstance()
        CodeLanguage.PYTHON -> TSLanguagePython.getInstance()
        CodeLanguage.C -> TSLanguageC.getInstance()
        CodeLanguage.CPP -> TSLanguageCpp.getInstance()
        CodeLanguage.JSON -> TSLanguageJson.getInstance()
        CodeLanguage.XML -> TSLanguageXml.getInstance()
        else -> null
    }

    private fun walk(
        node: TSNode,
        language: CodeLanguage,
        file: String,
        bytes: ByteArray,
        stack: Stack<String>,
        out: MutableList<CodeSymbol>
    ) {
        val type = node.getType()
        val symbolType = mapType(type, language)
        val name = if (symbolType != null) nodeName(node, bytes) else null
        val enclosing = stack.lastOrNull()

        if (symbolType != null && name != null && name.isNotBlank()) {
            out.add(
                CodeSymbol(
                    name = name,
                    type = symbolType,
                    language = language,
                    file = file,
                    line = node.getStartPoint().getRow() + 1,
                    parentName = enclosing,
                    obfuscated = isObfuscatedName(name)
                )
            )
        }

        val isContainer = symbolType != null && type in CONTAINER_NODE_TYPES && name != null
        if (isContainer) stack.push(name)
        for (i in 0 until node.getNamedChildCount()) {
            walk(node.getNamedChild(i), language, file, bytes, stack, out)
        }
        if (isContainer) stack.pop()
    }

    private fun nodeName(node: TSNode, bytes: ByteArray): String? {
        node.getChildByFieldName("name")?.let { child ->
            if (child.getType() in IDENTIFIER_TYPES) return text(child, bytes)
        }
        // Repli : parcours limité pour trouver un identifiant
        var candidate: TSNode? = null
        val stack = Stack<TSNode>()
        stack.push(node)
        var visited = 0
        while (stack.isNotEmpty() && visited < 64) {
            visited++
            val current = stack.pop()
            if (current.getType() in IDENTIFIER_TYPES) {
                candidate = current
            }
            for (i in 0 until current.getNamedChildCount()) {
                stack.push(current.getNamedChild(i))
            }
        }
        return candidate?.let { text(it, bytes) }
    }

    private fun text(node: TSNode, bytes: ByteArray): String {
        val start = node.getStartByte()
        val end = node.getEndByte()
        if (start < 0 || end > bytes.size || end < start) return ""
        return String(bytes, start, end - start, Charsets.UTF_8).trim()
    }

    private fun mapType(type: String, language: CodeLanguage): SymbolType? = when (type) {
        "class_declaration", "class_specifier", "declaration" -> if (type == "declaration" && language == CodeLanguage.CPP) SymbolType.CLASS else SymbolType.CLASS
        "interface_declaration" -> SymbolType.INTERFACE
        "enum_declaration", "enum_specifier", "enum_item" -> SymbolType.ENUM
        "annotation_type_declaration" -> SymbolType.ANNOTATION
        "record_declaration" -> SymbolType.RECORD
        "method_declaration", "method_definition" -> SymbolType.METHOD
        "constructor_declaration" -> SymbolType.CONSTRUCTOR
        "function_definition", "function_declaration", "function_item" -> SymbolType.FUNCTION
        "field_declaration", "field" -> SymbolType.FIELD
        "struct_specifier", "struct_item", "struct_declaration", "union_specifier" -> SymbolType.STRUCT
        "trait_item", "trait_declaration" -> SymbolType.TRAIT
        "impl_item", "impl_definition" -> SymbolType.MODULE
        "mod_item", "module_declaration" -> SymbolType.MODULE
        "use_declaration", "import_statement", "import_declaration", "import_from_statement" -> SymbolType.IMPORT
        "package_declaration", "package_clause" -> SymbolType.PACKAGE
        "namespace_definition" -> SymbolType.NAMESPACE
        "preproc_include", "preproc_include_declaration" -> SymbolType.IMPORT
        "preproc_def" -> SymbolType.MACRO
        "const_item", "const_declaration", "constant" -> SymbolType.CONSTANT
        "var_declaration", "variable_declaration", "lexical_declaration", "let_declaration" -> SymbolType.VARIABLE
        "type_item", "type_declaration", "type_spec" -> SymbolType.CLASS
        "object_creation_expression" -> null
        "element" -> if (language == CodeLanguage.XML) SymbolType.ELEMENT else null
        "attribute" -> if (language == CodeLanguage.XML) SymbolType.PROPERTY else null
        "pair" -> if (language == CodeLanguage.JSON) SymbolType.PROPERTY else null
        else -> null
    }

    private fun isObfuscatedName(name: String): Boolean {
        if (name.length >= 4) return false
        return name.length == 1 || (name.length == 2 && (name[0].isDigit() || name.all { it in 'a'..'z' || it.isDigit() }))
    }

    companion object {
        private val CONTAINER_NODE_TYPES = setOf(
            "class_declaration", "class_specifier", "interface_declaration", "enum_declaration",
            "enum_specifier", "struct_specifier", "struct_item", "union_specifier", "trait_item",
            "trait_declaration", "impl_item", "impl_definition", "mod_item", "module_declaration",
            "namespace_definition", "record_declaration", "annotation_type_declaration", "type_declaration"
        )

        private val IDENTIFIER_TYPES = setOf(
            "identifier", "type_identifier", "property_identifier", "function", "field_identifier"
        )
    }
}
