package com.voltai.doai.data.code

import com.voltai.doai.domain.models.CodeLanguage
import com.voltai.doai.domain.models.CodeSymbol
import com.voltai.doai.domain.models.SymbolType
import java.util.Stack

/**
 * Analyseur AST léger, sans dépendance native.
 * Produit un arbre de blocs puis extrait les symboles par langage.
 * Utilisé comme repli lorsque la bibliothèque native Tree-sitter
 * n'est pas disponible (appareil Android notamment).
 */
class AstSymbolExtractor {

    fun extract(source: String, language: CodeLanguage, file: String = ""): List<CodeSymbol> {
        if (source.isBlank()) return emptyList()
        return when (language) {
            CodeLanguage.PYTHON -> extractPython(source, file)
            CodeLanguage.SMALI -> extractSmali(source, file)
            CodeLanguage.XML -> extractXml(source, file)
            CodeLanguage.JSON -> extractJson(source, file)
            else -> extractBrace(source, language, file)
        }
    }

    // ------------------------------------------------------------------
    // Langages à accolades : Kotlin, Java, C, C++, Rust, Go, JavaScript
    // ------------------------------------------------------------------

    private data class Unit(
        val text: String,
        val line: Int,
        val depth: Int,
        val block: Boolean,
        val children: MutableList<Unit> = mutableListOf()
    )

    private fun extractBrace(source: String, language: CodeLanguage, file: String): List<CodeSymbol> {
        val masked = mask(source)
        val units = scanBlocks(masked)
        val symbols = mutableListOf<CodeSymbol>()
        val parentStack = Stack<CodeSymbol?>()
        parentStack.push(null)

        for (unit in units) {
            while (parentStack.size - 1 > unit.depth) parentStack.pop()
            val parent = parentStack.peek()
            val symbol = classify(unit.text, language, file, unit.line, unit.block, parent?.name)
            if (symbol != null) {
                symbols.add(symbol)
                if (unit.block) parentStack.push(symbol)
            } else if (unit.block) {
                parentStack.push(null)
            }
        }
        return symbols
    }

    private fun mask(source: String): String {
        val chars = source.toCharArray()
        var i = 0
        var inBlockComment = false
        while (i < chars.size - 1) {
            val c = chars[i]
            val next = chars[i + 1]
            if (inBlockComment) {
                chars[i] = ' '
                if (c == '*' && next == '/') {
                    chars[i + 1] = ' '
                    inBlockComment = false
                    i += 2
                } else {
                    i++
                }
                continue
            }
            when {
                c == '"' -> {
                    chars[i] = ' '
                    i++
                    while (i < chars.size && chars[i] != '"' && chars[i] != '\n') {
                        if (chars[i] == '\\' && i + 1 < chars.size) {
                            chars[i] = '0'
                            chars[i + 1] = '0'
                            i += 2
                        } else {
                            chars[i] = '0'
                            i++
                        }
                    }
                    if (i < chars.size && chars[i] == '"') chars[i] = ' '
                }
                c == '\'' && next != '\'' -> {
                    chars[i] = ' '
                    i++
                    while (i < chars.size && chars[i] != '\'' && chars[i] != '\n') {
                        if (chars[i] == '\\' && i + 1 < chars.size) {
                            chars[i] = '0'
                            chars[i + 1] = '0'
                            i += 2
                        } else {
                            chars[i] = '0'
                            i++
                        }
                    }
                    if (i < chars.size && chars[i] == '\'') chars[i] = ' '
                }
                c == '/' && next == '/' -> {
                    chars[i] = ' '
                    chars[i + 1] = ' '
                    i += 2
                    while (i < chars.size && chars[i] != '\n') {
                        chars[i] = ' '
                        i++
                    }
                }
                c == '/' && next == '*' -> {
                    chars[i] = ' '
                    chars[i + 1] = ' '
                    i += 2
                    while (i < chars.size && !(chars[i] == '*' && i + 1 < chars.size && chars[i + 1] == '/')) {
                        if (chars[i] != '\n') chars[i] = ' '
                        i++
                    }
                    if (i < chars.size) {
                        if (chars[i] == '*') chars[i] = ' '
                        if (i + 1 < chars.size && chars[i + 1] == '/') chars[i + 1] = ' '
                        i++
                    }
                }
                else -> i++
            }
        }
        return String(chars)
    }

    private fun scanBlocks(masked: String): List<Unit> {
        val units = mutableListOf<Unit>()
        val stack = Stack<Int>()
        var line = 1
        var pending = StringBuilder()
        var pendingLine = 1
        var depth = 0

        fun flushBlock(text: String, l: Int, d: Int) {
            val t = text.trim().replace(Regex("\\s+"), " ")
            if (t.isNotEmpty()) {
                val unit = Unit(t, l, d, true)
                if (stack.isNotEmpty()) units.lastOrNull()?.children?.add(unit)
                units.add(unit)
            }
        }

        fun flushStatement(text: String, l: Int, d: Int) {
            val t = text.trim().replace(Regex("\\s+"), " ")
            if (t.isNotEmpty()) {
                val unit = Unit(t, l, d, false)
                if (stack.isNotEmpty() && units.isNotEmpty()) units.last().children.add(unit)
                units.add(unit)
            }
        }

        var i = 0
        while (i < masked.length) {
            val c = masked[i]
            when {
                c == '\n' -> {
                    line++
                    val p = pending.toString()
                    if (p.isNotBlank() && isBalanced(p) && !continuesStatement(p)) {
                        flushStatement(p, pendingLine, depth)
                        pending = StringBuilder()
                        pendingLine = line
                    }
                }
                c == '{' -> {
                    flushBlock(pending.toString(), pendingLine, depth)
                    pending = StringBuilder()
                    pendingLine = line
                    stack.push(depth)
                    depth++
                }
                c == '}' -> {
                    val p = pending.toString()
                    if (p.isNotBlank() && isBalanced(p) && !continuesStatement(p)) {
                        flushStatement(p, pendingLine, depth)
                    }
                    if (depth > 0) {
                        depth--
                        if (stack.isNotEmpty()) stack.pop()
                    }
                    pending = StringBuilder()
                    pendingLine = line
                }
                c == ';' -> {
                    if (depth == 0 || !isUnbalancedPending(pending.toString())) {
                        flushStatement(pending.toString(), pendingLine, depth)
                    }
                    pending = StringBuilder()
                    pendingLine = line
                }
                else -> {
                    if (pending.isEmpty() && !c.isWhitespace()) pendingLine = line
                    pending.append(if (c == '\n') ' ' else c)
                }
            }
            i++
        }
        if (pending.isNotBlank()) {
            flushStatement(pending.toString(), pendingLine, depth)
        }
        return units
    }

    private fun continuesStatement(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.endsWith("->") || t.endsWith("=>") || t.endsWith("&&") || t.endsWith("||")) return true
        return t.last() in "=([{,<.:@+-*!?%&|/".toCharArray()
    }

    private fun isBalanced(text: String): Boolean {
        var p = 0
        var b = 0
        var a = 0
        for (c in text) {
            when (c) {
                '(' -> p++
                ')' -> p--
                '[' -> b++
                ']' -> b--
                '<' -> a++
                '>' -> a--
            }
        }
        return p <= 0 && b <= 0 && a <= 0
    }

    private fun isUnbalancedPending(text: String): Boolean {
        var p = 0
        for (c in text) {
            if (c == '(') p++
            if (c == ')') p--
        }
        return p > 0
    }

    private fun classify(
        text: String,
        language: CodeLanguage,
        file: String,
        line: Int,
        block: Boolean,
        parent: String?
    ): CodeSymbol? {
        return when (language) {
            CodeLanguage.KOTLIN -> classifyKotlin(text, file, line, parent)
            CodeLanguage.JAVA -> classifyJava(text, file, line, block, parent)
            CodeLanguage.C -> classifyC(text, file, line, block, parent, cpp = false)
            CodeLanguage.CPP -> classifyC(text, file, line, block, parent, cpp = true)
            CodeLanguage.RUST -> classifyRust(text, file, line, parent)
            CodeLanguage.GO -> classifyGo(text, file, line, parent)
            CodeLanguage.JAVASCRIPT -> classifyJs(text, file, line, block, parent)
            else -> null
        }
    }

    private fun isObfuscatedName(name: String): Boolean {
        if (name.length >= 4) return false
        if (name.length == 1) return true
        return name.length == 2 && (name.isDigitsOnly() || (name[0].isDigit()) || name.all { it in 'a'..'z' || it.isDigit() })
    }

    private fun String.isDigitsOnly() = all { it.isDigit() }

    // --- Kotlin ---------------------------------------------------------

    private fun classifyKotlin(text: String, file: String, line: Int, parent: String?): CodeSymbol? {
        val pkg = Regex("^package\\s+([\\w.]+)").find(text)
        if (pkg != null) return symbol("package ${pkg.groupValues[1]}", SymbolType.PACKAGE, file, line, parent)

        val imp = Regex("^import\\s+([\\w.\\*]+)").find(text)
        if (imp != null) return symbol(imp.groupValues[1].substringAfterLast('.'), SymbolType.IMPORT, file, line, parent, signature = imp.groupValues[1])

        val funMatch = Regex("^(?:[\\w\\s`]*(?:fun|constructor)\\s+)?([`\\w]+\\s*[\\.\\w`]*\\s*)?(?:override\\s+)?fun\\s+([`\\w]+)\\s*\\(").find(text)
        if (funMatch != null) {
            val name = funMatch.groupValues[2].trim()
            return symbol(name, SymbolType.FUNCTION, file, line, parent, modifiers = modifiersOf(text, KOTLIN_MODIFIERS), signature = text)
        }

        val valVar = Regex("^(?:const\\s+|lateinit\\s+var\\s+|[\\w\\s]*?)?(val|var)\\s+([`\\w]+)\\s*[:=]").find(text)
        if (valVar != null) {
            val name = valVar.groupValues[2]
            val isConst = text.startsWith("const ")
            return symbol(name, if (isConst) SymbolType.CONSTANT else SymbolType.PROPERTY, file, line, parent, modifiers = modifiersOf(text, KOTLIN_MODIFIERS), signature = text)
        }

        val cls = Regex("^([\\w\\s]*)(data\\s+|sealed\\s+|abstract\\s+|open\\s+|final\\s+|inner\\s+|internal\\s+|public\\s+|private\\s+|protected\\s+|annotation\\s+|enum\\s+|value\\s+|expect\\s+|actual\\s+|external\\s+)*(class|interface|enum\\s+class|object|record|annotation\\s+class)\\s+([`\\w]+)").find(text)
        if (cls != null) {
            val kind = when {
                cls.groupValues[3].contains("interface") -> SymbolType.INTERFACE
                cls.groupValues[3].contains("enum") -> SymbolType.ENUM
                cls.groupValues[3].contains("annotation") -> SymbolType.ANNOTATION
                cls.groupValues[3].contains("object") -> SymbolType.OBJECT
                cls.groupValues[3].contains("record") -> SymbolType.RECORD
                else -> SymbolType.CLASS
            }
            return symbol(cls.groupValues[4].trim(), kind, file, line, parent, modifiers = modifiersOf(text, KOTLIN_MODIFIERS), signature = text)
        }
        return null
    }

    // --- Java ------------------------------------------------------------

    private fun classifyJava(text: String, file: String, line: Int, block: Boolean, parent: String?): CodeSymbol? {
        val pkg = Regex("^package\\s+([\\w.]+)\\s*;?").find(text)
        if (pkg != null) return symbol("package ${pkg.groupValues[1]}", SymbolType.PACKAGE, file, line, parent)

        val imp = Regex("^import\\s+(?:static\\s+)?([\\w.\\*]+)\\s*;").find(text)
        if (imp != null) return symbol(imp.groupValues[1].substringAfterLast('.'), SymbolType.IMPORT, file, line, parent, signature = imp.groupValues[1])

        val cls = Regex("^([\\w\\s@]*)class\\s+(\\w+)").find(text)
        if (cls != null && !text.startsWith(":") && !text.trimStart().startsWith("(")) {
            val kind = when {
                text.contains("interface") -> SymbolType.INTERFACE
                text.contains("enum") -> SymbolType.ENUM
                text.contains("@interface") -> SymbolType.ANNOTATION
                text.contains("record") -> SymbolType.RECORD
                else -> SymbolType.CLASS
            }
            return symbol(cls.groupValues[2], kind, file, line, parent, modifiers = modifiersOf(text, JAVA_MODIFIERS), signature = text)
        }
        if (text.contains("interface")) {
            val itf = Regex("^([\\w\\s@]*)interface\\s+(\\w+)").find(text)
            if (itf != null) return symbol(itf.groupValues[2], SymbolType.INTERFACE, file, line, parent, modifiers = modifiersOf(text, JAVA_MODIFIERS), signature = text)
        }
        if (text.contains("enum")) {
            val en = Regex("^([\\w\\s@]*)enum\\s+(\\w+)").find(text)
            if (en != null) return symbol(en.groupValues[2], SymbolType.ENUM, file, line, parent, modifiers = modifiersOf(text, JAVA_MODIFIERS), signature = text)
        }

        val ctor = Regex("^([\\w\\s@]*)(${Regex.escape(parent ?: "XXX")})\\s*\\(").find(text)
        if (ctor != null && parent != null && !text.contains("class ")) {
            return symbol(ctor.groupValues[2], SymbolType.CONSTRUCTOR, file, line, parent, modifiers = modifiersOf(text, JAVA_MODIFIERS), signature = text)
        }

        val method = Regex("^([\\w\\s@<>,.\\[\\]]*?)(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w.]+(?:,\\s*[\\w.]+)*)?\\s*\\{?").find(text)
        if (method != null && block) {
            val name = method.groupValues[2]
            if (name !in CONTROL_KEYWORDS && text.firstOrNull()?.isDigit() != true) {
                val mods = modifiersOf(text, JAVA_MODIFIERS)
                return symbol(name, SymbolType.METHOD, file, line, parent, modifiers = mods, signature = text)
            }
        }

        val field = Regex("^([\\w\\s@<>,.\\[\\]]*?)(\\w+)\\s*(=|;)").find(text)
        if (field != null && field.groupValues[2] !in CONTROL_KEYWORDS) {
            return symbol(field.groupValues[2], SymbolType.FIELD, file, line, parent, modifiers = modifiersOf(text, JAVA_MODIFIERS), signature = text)
        }
        return null
    }

    // --- C / C++ ---------------------------------------------------------

    private fun classifyC(text: String, file: String, line: Int, block: Boolean, parent: String?, cpp: Boolean): CodeSymbol? {
        val inc = Regex("^#include\\s*[<\"]([\\w./]+)[>\"]").find(text)
        if (inc != null) return symbol(inc.groupValues[1].substringAfterLast('/'), SymbolType.IMPORT, file, line, parent, signature = inc.groupValues[1])

        val define = Regex("^#define\\s+(\\w+)").find(text)
        if (define != null) return symbol(define.groupValues[1], SymbolType.MACRO, file, line, parent, signature = text)

        if (cpp) {
            val ns = Regex("^namespace\\s+(\\w+)").find(text)
            if (ns != null) return symbol(ns.groupValues[1], SymbolType.NAMESPACE, file, line, parent, signature = text)
            val usingNs = Regex("^using\\s+namespace\\s+(\\w+)").find(text)
            if (usingNs != null) return symbol(usingNs.groupValues[1], SymbolType.IMPORT, file, line, parent)
            val cls = Regex("^class\\s+(\\w+)").find(text)
            if (cls != null && !text.startsWith("typedef")) {
                return symbol(cls.groupValues[1], SymbolType.CLASS, file, line, parent, modifiers = modifiersOf(text, JAVA_MODIFIERS), signature = text)
            }
        }

        val st = Regex("^\\s*(typedef\\s+)?(struct|union)\\s*(\\w*)\\s*(\\{?|;?)$|^\\s*struct\\s+(\\w+)").find(text.trim())
        if (st != null) {
            val name = st.groupValues[3].ifBlank { st.groupValues[5] }
            if (name.isNotEmpty()) return symbol(name, SymbolType.STRUCT, file, line, parent, signature = text)
        }
        val en = Regex("^\\s*(typedef\\s+)?enum\\s*(\\w*)").find(text.trim())
        if (en != null) {
            val name = en.groupValues[2]
            if (name.isNotEmpty()) return symbol(name, SymbolType.ENUM, file, line, parent, signature = text)
        }

        val fn = Regex("^([\\w\\s\\*]+?)\\b(\\w+)\\s*\\(").find(text)
        if (fn != null && block) {
            val name = fn.groupValues[2]
            if (name !in CONTROL_KEYWORDS && !name.isDigitsOnly() && !name[0].isDigit()) {
                return symbol(name, SymbolType.FUNCTION, file, line, parent, modifiers = listOf("extern") + (if (text.startsWith("static")) listOf("static") else emptyList()), signature = text)
            }
        }
        return null
    }

    // --- Rust ------------------------------------------------------------

    private fun classifyRust(text: String, file: String, line: Int, parent: String?): CodeSymbol? {
        val useMod = Regex("^(?:pub(?:\\s*\\([^)]*\\))?\\s+)?(use|mod)\\s+([\\w:]+)").find(text)
        if (useMod != null) {
            val isUse = useMod.groupValues[1] == "use"
            return symbol(useMod.groupValues[2].substringAfterLast(':'), if (isUse) SymbolType.IMPORT else SymbolType.MODULE, file, line, parent, signature = text)
        }
        val item = Regex("^(?:pub(?:\\s*\\([^)]*\\))?\\s+)?(struct|enum|union|trait|fn|const|static)\\s+([\\w]+)").find(text)
        if (item != null) {
            val kind = when (item.groupValues[1]) {
                "struct" -> SymbolType.STRUCT
                "enum" -> SymbolType.ENUM
                "union" -> SymbolType.STRUCT
                "trait" -> SymbolType.TRAIT
                "fn" -> SymbolType.FUNCTION
                "const", "static" -> SymbolType.CONSTANT
                else -> SymbolType.CLASS
            }
            return symbol(item.groupValues[2], kind, file, line, parent, modifiers = if (text.startsWith("pub")) listOf("pub") else emptyList(), signature = text)
        }
        val impl = Regex("^\\s*impl(?:<[^>]*>)?\\s+(?:\\w+::)?(\\w+)").find(text)
        if (impl != null) {
            return symbol(impl.groupValues[1], SymbolType.MODULE, file, line, parent, modifiers = listOf("impl"), signature = text)
        }
        val let = Regex("^\\s*(let|const|static)\\s+mut?\\s+([\\w_]+)").find(text)
        if (let != null) {
            return symbol(let.groupValues[2], if (let.groupValues[1] == "let") SymbolType.VARIABLE else SymbolType.CONSTANT, file, line, parent, signature = text)
        }
        return null
    }

    // --- Go --------------------------------------------------------------

    private fun classifyGo(text: String, file: String, line: Int, parent: String?): CodeSymbol? {
        val pkg = Regex("^package\\s+(\\w+)").find(text)
        if (pkg != null) return symbol("package ${pkg.groupValues[1]}", SymbolType.PACKAGE, file, line, parent)

        val imp = Regex("^import\\s+(?:\\(?)?\\s*\"?([\\w./-]+)\"?").find(text)
        if (imp != null && !text.contains("(")) return symbol(imp.groupValues[1].substringAfterLast('/'), SymbolType.IMPORT, file, line, parent, signature = imp.groupValues[1])

        val fn = Regex("^func\\s+\\((\\w+\\s+\\*?\\w+)\\)\\s+(\\w+)\\s*\\(").find(text)
        if (fn != null) {
            return symbol(fn.groupValues[2], SymbolType.METHOD, file, line, parent, signature = text)
        }
        val fn2 = Regex("^func\\s+(\\w+)\\s*\\(").find(text)
        if (fn2 != null) {
            return symbol(fn2.groupValues[1], SymbolType.FUNCTION, file, line, parent, signature = text)
        }
        val typ = Regex("^type\\s+(\\w+)\\s+(struct|interface)\\s*\\{?").find(text)
        if (typ != null) {
            return symbol(typ.groupValues[1], if (typ.groupValues[2] == "struct") SymbolType.STRUCT else SymbolType.INTERFACE, file, line, parent, signature = text)
        }
        val cst = Regex("^const\\s+(?:\\(?\\s*)?(\\w+)").find(text)
        if (cst != null) return symbol(cst.groupValues[1], SymbolType.CONSTANT, file, line, parent, signature = text)
        val vr = Regex("^var\\s+(?:\\(?\\s*)?(\\w+)").find(text)
        if (vr != null) return symbol(vr.groupValues[1], SymbolType.VARIABLE, file, line, parent, signature = text)
        return null
    }

    // --- JavaScript / TypeScript ------------------------------------------

    private fun classifyJs(text: String, file: String, line: Int, block: Boolean, parent: String?): CodeSymbol? {
        val imp = Regex("^import\\s+(?:.*?from\\s+)?['\"]([^'\"]+)['\"]").find(text)
        if (imp != null) return symbol(imp.groupValues[1].substringAfterLast('/'), SymbolType.IMPORT, file, line, parent, signature = imp.groupValues[1])

        val cls = Regex("^(?:export\\s+(?:default\\s+)?)?class\\s+(\\w+)").find(text)
        if (cls != null) return symbol(cls.groupValues[1], SymbolType.CLASS, file, line, parent, signature = text)

        val fn = Regex("^function\\s+(\\w+)\\s*\\(").find(text)
        if (fn != null) return symbol(fn.groupValues[1], SymbolType.FUNCTION, file, line, parent, signature = text)

        val arrow = Regex("^(?:export\\s+)?(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:(?:async\\s+)?\\(?[^)]*\\)?\\s*=>|function)").find(text)
        if (arrow != null) return symbol(arrow.groupValues[1], SymbolType.FUNCTION, file, line, parent, signature = text)

        if (parent != null && text.contains("(") && text.contains(")") && block) {
            val m = Regex("^([\\w]+)\\s*\\([^)]*\\)\\s*\\{?$").find(text)
            if (m != null) return symbol(m.groupValues[1], SymbolType.METHOD, file, line, parent, signature = text)
        }

        val decl = Regex("^(?:export\\s+)?(?:const|let|var)\\s+(\\w+)\\s*=").find(text)
        if (decl != null) return symbol(decl.groupValues[1], SymbolType.VARIABLE, file, line, parent, signature = text)
        return null
    }

    // --- Python -----------------------------------------------------------

    private fun extractPython(source: String, file: String): List<CodeSymbol> {
        val symbols = mutableListOf<CodeSymbol>()
        val parentStack = Stack<CodeSymbol?>()
        parentStack.push(null)
        var prevIndent = -1

        source.lines().forEachIndexed { index, raw ->
            val line = index + 1
            val stripped = raw.trimStart()
            if (stripped.isEmpty() || stripped.startsWith("#")) return@forEachIndexed
            val indent = raw.length - stripped.length

            val imp = Regex("^(?:from\\s+([\\w.]+)\\s+)?import\\s+(.+)$").find(stripped)
            if (imp != null) {
                val target = imp.groupValues[1].ifEmpty { imp.groupValues[2].substringBefore(",").trim() }
                symbols.add(symbol(target.substringAfterLast('.'), SymbolType.IMPORT, file, line, parentStack.peek()?.name, signature = stripped))
                return@forEachIndexed
            }

            val cls = Regex("^class\\s+(\\w+)").find(stripped)
            if (cls != null) {
                val s = symbol(cls.groupValues[1], SymbolType.CLASS, file, line, parentStack.peek()?.name, signature = stripped)
                symbols.add(s)
                while (parentStack.size > 1 && prevIndent >= indent) parentStack.pop()
                parentStack.push(s)
                prevIndent = indent
                return@forEachIndexed
            }

            val fn = Regex("^(?:async\\s+)?def\\s+(\\w+)\\s*\\(").find(stripped)
            if (fn != null) {
                val s = symbol(fn.groupValues[1], SymbolType.FUNCTION, file, line, parentStack.peek()?.name, signature = stripped)
                symbols.add(s)
                while (parentStack.size > 1 && prevIndent >= indent) parentStack.pop()
                parentStack.push(s)
                prevIndent = indent
                return@forEachIndexed
            }

            val assign = Regex("^([A-Za-z_][\\w]*)\\s*=").find(stripped)
            if (assign != null && parentStack.size == 1) {
                symbols.add(symbol(assign.groupValues[1], SymbolType.VARIABLE, file, line, null, signature = stripped))
            }
        }
        return symbols
    }

    // --- Smali ------------------------------------------------------------

    private fun extractSmali(source: String, file: String): List<CodeSymbol> {
        val symbols = mutableListOf<CodeSymbol>()
        var className: String? = null
        source.lines().forEachIndexed { index, raw ->
            val line = index + 1
            val t = raw.trim()
            when {
                t.startsWith(".class") -> {
                    val m = Regex("^\\.class\\s+([\\w\\s]*?)(?:L[\\w/$]+;)").find(t)
                    val type = if (t.contains("interface")) SymbolType.INTERFACE else if (t.contains("enum")) SymbolType.ENUM else if (t.contains("annotation")) SymbolType.ANNOTATION else SymbolType.CLASS
                    val desc = Regex("L([\\w/$]+);").find(t)?.groupValues?.get(1)
                    if (desc != null) {
                        className = desc
                        symbols.add(symbol(desc.substringAfterLast('/'), type, file, line, null, modifiers = (m?.groupValues?.get(1)?.trim()?.split(" ") ?: emptyList()).filter { it.isNotBlank() }, signature = t))
                    }
                }
                t.startsWith(".method") -> {
                    val m = Regex("^\\.method\\s+([\\w\\s]*)").find(t)
                    val sig = Regex("^\\.method\\s+[\\w\\s]*?([\\w<>]+)\\s*\\(").find(t)
                    val name = sig?.groupValues?.get(1) ?: t.substringAfterLast(' ').substringBefore('(')
                    val isCtor = name == "<init>" || name == "<clinit>"
                    symbols.add(symbol(name, if (isCtor) SymbolType.CONSTRUCTOR else SymbolType.METHOD, file, line, className, modifiers = (m?.groupValues?.get(1)?.trim()?.split(" ") ?: emptyList()).filter { it.isNotBlank() }, signature = t))
                }
                t.startsWith(".field") -> {
                    val m = Regex("^\\.field\\s+([\\w\\s]*)").find(t)
                    val n = Regex("^\\.field\\s+[\\w\\s]*?([\\w]+):").find(t)?.groupValues?.get(1)
                    if (n != null) symbols.add(symbol(n, SymbolType.FIELD, file, line, className, modifiers = (m?.groupValues?.get(1)?.trim()?.split(" ") ?: emptyList()).filter { it.isNotBlank() }, signature = t))
                }
            }
        }
        return symbols
    }

    // --- XML --------------------------------------------------------------

    private fun extractXml(source: String, file: String): List<CodeSymbol> {
        val symbols = mutableListOf<CodeSymbol>()
        val tagRegex = Regex("<\\s*/?\\s*([\\w:]+)([^>]*?)(/?)>")
        val attrRegex = Regex("([\\w:.-]+)\\s*=\\s*['\"][^'\"]*['\"]")
        val seen = mutableSetOf<String>()
        for (m in tagRegex.findAll(source)) {
            val name = m.groupValues[1]
            val closing = m.value.trimStart().startsWith("</")
            val selfClosing = m.groupValues[3] == "/"
            val line = source.substring(0, m.range.first).count { it == '\n' } + 1
            if (!closing) {
                if (seen.add(name)) {
                    symbols.add(symbol(name, SymbolType.ELEMENT, file, line, null, signature = "<$name>"))
                }
                val attrs = attrRegex.findAll(m.groupValues[2]).map { it.groupValues[1] }.toList()
                attrs.forEach { a ->
                    symbols.add(symbol(a, SymbolType.PROPERTY, file, line, name, signature = "$name/$a"))
                }
                if (!selfClosing) Unit
            }
        }
        return symbols
    }

    // --- JSON -------------------------------------------------------------

    private fun extractJson(source: String, file: String): List<CodeSymbol> {
        val symbols = mutableListOf<CodeSymbol>()
        val lineRegex = Regex("^\\s*\"([^\"]+)\"\\s*:([\\s\\[{]?)", RegexOption.MULTILINE)
        for (m in lineRegex.findAll(source)) {
            val key = m.groupValues[1]
            val line = source.substring(0, m.range.first).count { it == '\n' } + 1
            val opensBlock = m.groupValues[2].contains("[") || m.groupValues[2].contains("{")
            symbols.add(symbol(key, SymbolType.PROPERTY, file, line, null, signature = if (opensBlock) "$key:{}" else "$key:value"))
        }
        return symbols.distinctBy { "${it.name}:${it.line}" }
    }

    // ----------------------------------------------------------------------

    private val KOTLIN_MODIFIERS = listOf(
        "public", "private", "protected", "internal", "abstract", "open", "final",
        "override", "data", "sealed", "inline", "suspend", "external", "const", "lateinit",
        "operator", "infix", "tailrec", "value", "inner"
    )

    private val JAVA_MODIFIERS = listOf(
        "public", "private", "protected", "static", "final", "abstract", "synchronized",
        "native", "strictfp", "default", "transient", "volatile", "sealed", "non-sealed"
    )

    private val CONTROL_KEYWORDS = setOf(
        "if", "for", "while", "switch", "catch", "do", "return", "else", "new",
        "sizeof", "main", "try", "super", "this", "with", "require", "when"
    )

    private fun modifiersOf(text: String, all: List<String>): List<String> {
        return all.filter { Regex("\\b$it\\b").containsMatchIn(text) }
    }

    private fun symbol(
        name: String,
        type: SymbolType,
        file: String,
        line: Int,
        parent: String?,
        modifiers: List<String> = emptyList(),
        signature: String? = null
    ): CodeSymbol {
        return CodeSymbol(
            name = name,
            type = type,
            language = CodeLanguage.KOTLIN, // remplacé par l'appelant via copy()
            file = file,
            line = line,
            modifiers = modifiers,
            parentName = parent,
            signature = signature,
            obfuscated = isObfuscatedName(name)
        )
    }
}

fun CodeSymbol.withLanguage(language: CodeLanguage): CodeSymbol =
    copy(language = language)

internal fun AstSymbolExtractor.extractWith(source: String, language: CodeLanguage, file: String = ""): List<CodeSymbol> =
    extract(source, language, file).map { it.withLanguage(language) }
