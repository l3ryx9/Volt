package com.voltai.doai.data.code

import com.voltai.doai.domain.interfaces.DependencyAnalyzer
import com.voltai.doai.domain.models.ArchitectureInfo
import com.voltai.doai.domain.models.CodeDependency
import com.voltai.doai.domain.models.CodeLanguage
import com.voltai.doai.domain.models.DependencyType
import com.voltai.doai.domain.models.ErrorAnalysis
import com.voltai.doai.domain.models.ObfuscationTechnique
import java.util.Base64

class DependencyAnalyzerImpl : DependencyAnalyzer {

    private data class ErrorAnswer(val category: String, val severity: String, val cause: String, val fix: String)

    override fun analyzeDependencies(source: String, language: CodeLanguage, file: String): List<CodeDependency> {
        val deps = mutableListOf<CodeDependency>()
        val classNames = mutableSetOf<String>()
        SymbolResolverImpl().extractSymbols(source, language, file)
            .filter { it.type in com.voltai.doai.domain.models.SymbolType.entries }
            .forEach { if (it.type.name in setOf("CLASS", "INTERFACE", "STRUCT", "TRAIT", "ENUM", "OBJECT", "RECORD")) classNames.add(it.name) }

        val importPatterns = when (language) {
            CodeLanguage.KOTLIN -> Regex("^\\s*import\\s+([\\w.\\*]+)")
            CodeLanguage.JAVA -> Regex("^\\s*import\\s+(?:static\\s+)?([\\w.\\*]+)\\s*;")
            CodeLanguage.PYTHON -> Regex("^\\s*(?:from\\s+([\\w.]+)\\s+import\\s+.+|import\\s+([\\w.,\\s]+))")
            CodeLanguage.C, CodeLanguage.CPP -> Regex("^\\s*#include\\s*[<\"]([\\w./]+)[>\"]")
            CodeLanguage.RUST -> Regex("^\\s*(?:pub\\s+)?use\\s+([\\w:]+)")
            CodeLanguage.GO -> Regex("^\\s*import\\s+(?:\\()?\\s*\"([\\w./-]+)\"")
            CodeLanguage.JAVASCRIPT -> Regex("^\\s*import\\s+(?:.*?from\\s+)?['\"]([^'\"]+)['\"]")
            CodeLanguage.SMALI -> Regex("^\\.super\\s+L([\\w/$]+);")
            else -> null
        }
        importPatterns?.let { pattern ->
            var line = 0
            source.lines().forEach { raw ->
                line++
                val m = pattern.find(raw)
                if (m != null) {
                    val target = if (language == CodeLanguage.PYTHON) {
                        m.groupValues[1].ifBlank { m.groupValues[2] }.split(",").firstOrNull()?.trim() ?: ""
                    } else {
                        m.groupValues[1].trim()
                    }
                    if (target.isNotBlank()) {
                        deps.add(CodeDependency("file", target, DependencyType.IMPORT, file, line))
                    }
                }
            }
        }

        val lineagePatterns = when (language) {
            CodeLanguage.KOTLIN -> Regex("\\b(class|interface)\\s+[\\w`]+\\s*:\\s*([A-Za-z_][\\w.\\s,<>\\(]*)")
            CodeLanguage.JAVA -> Regex("\\b(class|interface)\\s+\\w+\\s+(?:extends|implements)\\s+([A-Za-z_][\\w.,\\s]*)")
            CodeLanguage.CPP -> Regex("\\bclass\\s+\\w+\\s*:\\s*([A-Za-z_][\\w:,\\s]*)\\s*\\{")
            CodeLanguage.PYTHON -> Regex("\\bclass\\s+\\w+\\s*\\(([^)]+)\\)")
            CodeLanguage.SMALI -> Regex("^\\.super\\s+L([\\w/$]+);")
            else -> null
        }
        lineagePatterns?.let { pattern ->
            var line = 0
            source.lines().forEach { raw ->
                line++
                val m = pattern.find(raw)
                if (m != null) {
                    val from = when (language) {
                        CodeLanguage.SMALI -> "class"
                        else -> m.groupValues[0].substringAfter("class").trim().substringBefore(' ').substringBefore('(')
                    }
                    val targets = m.groupValues[if (language == CodeLanguage.KOTLIN || language == CodeLanguage.JAVA) 2 else 1]
                        .split(',')
                        .map { it.trim().substringAfterLast('.').substringBefore('(') }
                        .filter { it.isNotEmpty() && it != from }
                    targets.forEach { target ->
                        deps.add(
                            CodeDependency(
                                from.ifBlank { "class" },
                                target,
                                if (language == CodeLanguage.SMALI) DependencyType.INHERITANCE else DependencyType.INHERITANCE,
                                file,
                                line
                            )
                        )
                    }
                }
            }
        }

        if (language != CodeLanguage.SMALI && language != CodeLanguage.XML && language != CodeLanguage.JSON) {
            val callPattern = Regex("\\b([A-Za-z_][\\w]*)\\s*\\(")
            val control = setOf(
                "if", "for", "while", "switch", "catch", "return", "new", "print", "println",
                "assert", "throw", "super", "this", "when", "require", "check", "synchronized",
                "sizeof", "typeof", "instanceof", "in", "is", "as", "try", "fun", "func", "fn",
                "function", "var", "val", "let", "const", "def", "public", "private", "protected"
            )
            var line = 0
            source.lines().forEach { raw ->
                line++
                if (raw.trimStart().startsWith("//") || raw.trimStart().startsWith("#") ||
                    raw.trimStart().startsWith("import") || raw.trimStart().startsWith("package") ||
                    raw.trimStart().startsWith("from ") || raw.trimStart().startsWith("use ") ||
                    raw.trimStart().startsWith("#include")
                ) return@forEach
                for (m in callPattern.findAll(raw)) {
                    val name = m.groupValues[1]
                    if (name in control || name in classNames || name == "main") continue
                    if (name.length > 1) {
                        deps.add(CodeDependency("file", name, DependencyType.CALL, file, line))
                    }
                }
            }
        }

        return deps.distinctBy { "${it.type}:${it.from}:${it.to}:${it.line}" }
    }

    override fun analyzeArchitecture(files: Map<String, String>): ArchitectureInfo {
        val modules = linkedMapOf<String, MutableList<CodeDependency>>()
        val classModules = mutableMapOf<String, String>()

        files.forEach { (path, content) ->
            val lang = CodeLanguage.fromPath(path) ?: return@forEach
            val module = moduleOf(path, lang)
            modules.getOrPut(module) { mutableListOf() }
            SymbolResolverImpl().extractSymbols(content, lang, path)
                .filter { it.type in CodeIndexerImpl.CLASS_LIKE }
                .forEach { classModules[it.name] = module }
        }

        files.forEach { (path, content) ->
            val lang = CodeLanguage.fromPath(path) ?: return@forEach
            val module = moduleOf(path, lang)
            modules.getOrPut(module) { mutableListOf() }
            analyzeDependencies(content, lang, path).forEach { dep ->
                val targetModule = when (dep.type) {
                    DependencyType.IMPORT -> moduleOfImport(dep.to, lang)
                    DependencyType.CALL, DependencyType.INHERITANCE -> classModules[dep.to]
                    else -> null
                }
                if (targetModule != null && targetModule != module) {
                    modules.getOrPut(module) { mutableListOf() }.add(
                        CodeDependency(module, targetModule, dep.type, path, dep.line)
                    )
                }
            }
        }
        val graph = modules.mapValues { (_, deps) -> deps.map { it.to }.toSet() }

        val cycles = detectCycles(graph)
        val entryPoints = files.keys.filter { path ->
            val content = files[path] ?: ""
            content.contains(Regex("\\bmain\\s*\\(")) || content.contains("onCreate(") ||
                content.contains("fun onCreate")
        }

        val description = buildString {
            appendLine("Modules détectés (${graph.size}) : ${graph.keys.joinToString(", ")}")
            if (entryPoints.isNotEmpty()) appendLine("Points d'entrée : ${entryPoints.joinToString(", ")}")
            if (cycles.isEmpty()) {
                appendLine("Architecture : aucun cycle de dépendances détecté.")
            } else {
                appendLine("⚠ ${cycles.size} cycle(s) de dépendances détecté(s) :")
                cycles.forEach { appendLine("  - ${it.joinToString(" -> ")}") }
            }
        }

        return ArchitectureInfo(
            entryPoints = entryPoints,
            modules = graph.keys.toList(),
            dependencies = modules.flatMap { it.value },
            cycles = cycles,
            description = description
        )
    }

    override fun analyzeError(source: String, language: CodeLanguage, errorMessage: String, file: String): ErrorAnalysis {
        val msg = errorMessage.lowercase()
        val refs = Regex("(?:class|function|method|symbol|identifier|reference|exception)\\s*[: ]+\\s*([\\w.]+)")
            .findAll(errorMessage).map { it.groupValues[1] }.toList()
        val sourceSymbols = SymbolResolverImpl().extractSymbols(source, language, file)
        val matchesInSource = refs.filter { r ->
            sourceSymbols.any { it.name.equals(r.substringAfterLast('.'), ignoreCase = true) }
        }

        val answer = when {
            msg.contains("unresolved reference") || msg.contains("cannot find symbol") ||
                msg.contains("symbol not found") || msg.contains("undefined name") ||
                msg.contains("undeclared identifier") || msg.contains("no member") ||
                msg.contains("unknown class") || msg.contains("unresolved symbol") ->
                ErrorAnswer(
                    "Compilation — symbole introuvable",
                    "Erreur",
                    "Un symbole (classe/fonction/variable) est référencé mais n'est ni importé, ni déclaré.",
                    "Ajouter l'import manquant ou vérifier le nom/chemin du symbole.${if (matchesInSource.isNotEmpty()) " Le symbole existe pourtant dans le code : ${matchesInSource.joinToString(", ")}." else ""}"
                )
            msg.contains("null pointer") || msg.contains("nullreference") || msg.contains("npe") ||
                msg.contains("nullreferenceexception") || msg.contains("nullpointer") ->
                ErrorAnswer(
                    "Exécution — pointeur nul",
                    "Critique",
                    "Une valeur null a été déréférencée (appel de méthode sur null).",
                    "Initialiser la valeur ou ajouter une vérification de nullité (?. / null-check / try-except)."
                )
            msg.contains("syntax") || msg.contains("parse error") || msg.contains("expected") ||
                msg.contains("unexpected token") || msg.contains("invalid syntax") ->
                ErrorAnswer(
                    "Syntaxe",
                    "Erreur",
                    "Le code ne respecte pas la grammaire du langage (parenthèse, accolade, point-virgule, indentation...).",
                    "Vérifier la ligne signalée et la structure : parenthèses/accolades équilibrées, indentation correcte."
                )
            msg.contains("outofmemory") || msg.contains("out of memory") || msg.contains("heap") ->
                ErrorAnswer(
                    "Exécution — mémoire",
                    "Critique",
                    "Trop de mémoire consommée (boucle infinie, liste géante, fuite).",
                    "Réduire la consommation (batch), libérer les ressources ou supprimer les références inutiles."
                )
            msg.contains("filenotfound") || msg.contains("no such file") || msg.contains("nosuchfile") ->
                ErrorAnswer(
                    "Entrées-sorties",
                    "Erreur",
                    "Un fichier ou un chemin demandé n'existe pas.",
                    "Vérifier le chemin absolu/relatif et que le fichier est présent."
                )
            msg.contains("permission denied") || msg.contains("permissiondenied") ->
                ErrorAnswer(
                    "Permissions",
                    "Erreur",
                    "Droits d'accès insuffisants sur le fichier/ressource.",
                    "Vérifier les permissions (chmod / manifest Android) ou exécuter avec les droits requis."
                )
            msg.contains("network") || msg.contains("connection refused") || msg.contains("unknownhost") ||
                msg.contains("connectexception") || msg.contains("socket") || msg.contains("timeout") ->
                ErrorAnswer(
                    "Réseau",
                    "Erreur",
                    "La connexion réseau a échoué (hôte inconnu, refus, délai).",
                    "Vérifier la connectivité, l'URL/le port, et le pare-feu."
                )
            msg.contains("classcast") || msg.contains("type mismatch") || msg.contains("cannot convert") ||
                msg.contains("incompatible types") || msg.contains("typeerror") ->
                ErrorAnswer(
                    "Typage",
                    "Erreur",
                    "Un type ne correspond pas à ce qui est attendu.",
                    "Convertir explicitement ou aligner les types des variables et des retours."
                )
            msg.contains("indexoutofbounds") || msg.contains("index out of range") ||
                msg.contains("indexoutofrange") || msg.contains("arrayindex") ->
                ErrorAnswer(
                    "Bornes",
                    "Erreur",
                    "Accès à un index hors limites (tableau/liste).",
                    "Vérifier les bornes avant l'accès (0..size-1) et la logique d'incrémentation."
                )
            msg.contains("no matching function") || msg.contains("no overload") ||
                msg.contains("wrong number of arguments") || msg.contains("arity") ->
                ErrorAnswer(
                    "Signature",
                    "Erreur",
                    "Appel d'une fonction avec des arguments incompatibles.",
                    "Vérifier le nombre et le type des arguments par rapport à la définition."
                )
            msg.contains("segmentation") || msg.contains("segfault") || msg.contains("core dumped") ->
                ErrorAnswer(
                    "Mémoire bas niveau",
                    "Critique",
                    "Accès mémoire invalide (pointeur nul, dépassement de tampon).",
                    "Vérifier les pointeurs, les tailles de tampon et les limites de tableaux (C/C++/Rust unsafe)."
                )
            msg.contains("runtimeexception") || msg.contains("exception") ->
                ErrorAnswer(
                    "Exécution",
                    "Erreur",
                    "Exception non gérée pendant l'exécution.",
                    "Capturer et traiter l'exception ou corriger la cause signalée par le message."
                )
            else ->
                ErrorAnswer(
                    "Non classifiée",
                    "Information",
                    "Message d'erreur non reconnu.",
                    "Lire le message complet et le contexte (fichier/ligne) pour identifier la cause."
                )
        }

        val hints = mutableListOf<String>()
        if (matchesInSource.isNotEmpty()) {
            hints.add("Référence trouvée dans le code source : ${matchesInSource.joinToString(", ")}")
        }
        if (refs.isNotEmpty()) hints.add("Symboles mentionnés par l'erreur : ${refs.joinToString(", ")}")
        if (file.isNotBlank()) hints.add("Fichier concerné : $file")
        hints.add("Langage : ${language.displayName}")

        return ErrorAnalysis(answer.category, answer.severity, answer.cause, answer.fix, hints)
    }

    override fun obfuscationTechniques(): List<ObfuscationTechnique> = OBFUSCATION_CATALOG

    override fun detectObfuscation(source: String, language: CodeLanguage): List<ObfuscationTechnique> {
        return OBFUSCATION_CATALOG.filter { technique ->
            technique.indicators.any { indicator ->
                when {
                    indicator.startsWith("re:") -> Regex(indicator.removePrefix("re:"), RegexOption.IGNORE_CASE)
                        .containsMatchIn(source)
                    else -> source.contains(indicator, ignoreCase = true)
                }
            }
        }
    }

    override fun deobfuscate(source: String, language: CodeLanguage): String {
        val techniques = detectObfuscation(source, language)
        val sb = StringBuilder()
        if (techniques.isEmpty()) {
            sb.appendLine("Aucune technique d'obfuscation évidente détectée.")
            sb.appendLine()
            sb.append(source)
            return sb.toString()
        }

        sb.appendLine("Techniques d'obfuscation détectées :")
        techniques.forEach { sb.appendLine("  - ${it.name} : ${it.description}") }
        sb.appendLine()
        sb.appendLine("Tentative de décodage des chaînes encodées :")

        var decoded = source
        val base64Regex = Regex("\"[A-Za-z0-9+/]{16,}={0,2}\"")
        var found = false
        decoded = base64Regex.replace(decoded) { m ->
            val payload = m.value.trim('"')
            try {
                val bytes = Base64.getDecoder().decode(payload)
                val text = String(bytes, Charsets.UTF_8)
                if (text.isPrintable() && text.length > 2) {
                    found = true
                    sb.appendLine("  Décodage base64 : « $payload » -> « ${text.take(120)} »")
                    "\"$text\""
                } else {
                    m.value
                }
            } catch (_: Exception) {
                m.value
            }
        }

        val unicodeRegex = Regex("\\\\u[0-9a-fA-F]{4}")
        if (unicodeRegex.containsMatchIn(decoded)) {
            found = true
            val cleaned = unicodeRegex.replace(decoded) { m ->
                m.value.replace("\\u", "u+").let { "\\u${m.value.drop(2)}" }
            }
            sb.appendLine("  Échappements unicode présents : $cleaned")
        }

        val hexRegex = Regex("0x[0-9a-fA-F]{2}(?:\\s*,\\s*0x[0-9a-fA-F]{2}){3,}")
        hexRegex.find(decoded)?.let { m ->
            found = true
            val bytes = Regex("0x([0-9a-fA-F]{2})").findAll(m.value).map { it.groupValues[1].toInt(16).toByte() }.toList().toByteArray()
            val text = String(bytes, Charsets.UTF_8)
            sb.appendLine("  Suite d'octets hexadécimaux : « ${m.value.take(80)} » -> « ${text.take(120)} »")
        }

        if (!found) {
            sb.appendLine("  (aucune chaîne encodée décodable trouvée)")
        }
        sb.appendLine()
        sb.appendLine("Recommandations de dés-obfuscation :")
        sb.appendLine("  - Décompiler puis formater le code (apktool/jadx, prettier/ktlint...).")
        sb.appendLine("  - Remplacer les noms courts par des noms explicites à partir du contexte.")
        sb.appendLine("  - Utiliser frida/string-decrypt ou un dé-obfuscateur (paranoid-deobfuscator).")
        sb.appendLine("  - Vérifier les appels réflexifs (getDeclaredMethod, Class.forName).")
        return sb.toString()
    }

    private fun String.isPrintable(): Boolean {
        return all { it.isLetterOrDigit() || it.isWhitespace() || it in ".,;:!?-_()[]{}<>@#%&*+/=|\"'\\" }
    }

    private fun moduleOf(path: String, language: CodeLanguage): String {
        val segments = path.split('/', '\\').filter { it.isNotBlank() }
        return when (language) {
            CodeLanguage.JAVA, CodeLanguage.KOTLIN -> segments.dropLast(1).lastOrNull()?.substringAfter(".")
                ?: segments.lastOrNull()?.substringBefore('.') ?: "root"
            CodeLanguage.PYTHON -> segments.dropLast(1).lastOrNull() ?: "root"
            CodeLanguage.C, CodeLanguage.CPP -> segments.dropLast(1).lastOrNull() ?: "root"
            CodeLanguage.RUST -> "crate"
            CodeLanguage.GO -> "package ${segments.lastOrNull()?.substringBefore('_') ?: "root"}"
            else -> segments.dropLast(1).lastOrNull() ?: "root"
        }
    }

    private fun moduleOfImport(importPath: String, language: CodeLanguage): String? {
        if (language == CodeLanguage.GO) return importPath.substringAfterLast('/')
        val top = importPath.substringAfterLast('.').ifEmpty { return null }
        return if (top.contains('*')) null else top
    }

    private fun detectCycles(graph: Map<String, Set<String>>): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val stack = mutableListOf<String>()
        val inStack = mutableSetOf<String>()

        fun dfs(node: String) {
            visited.add(node)
            stack.add(node)
            inStack.add(node)
            for (neighbor in graph[node] ?: emptySet()) {
                if (neighbor !in graph) continue
                if (neighbor in inStack) {
                    val idx = stack.indexOf(neighbor)
                    val cycle = stack.subList(idx, stack.size) + neighbor
                    if (cycles.none { it.toSet() == cycle.toSet() }) cycles.add(cycle)
                } else if (neighbor !in visited) {
                    dfs(neighbor)
                }
            }
            stack.removeAt(stack.size - 1)
            inStack.remove(node)
        }

        graph.keys.forEach { if (it !in visited) dfs(it) }
        return cycles
    }

    companion object {
        val OBFUSCATION_CATALOG = listOf(
            ObfuscationTechnique(
                "Renommage (ProGuard/R8/obfuscator)",
                "Les noms de classes/méthodes sont remplacés par des identifiants courts et non significatifs (a, b, aa, a1...).",
                listOf("re:\\b[a-z]{1,2}\\s*\\(", "re:\\b(class|fun|func|fn|def)\\s+[a-z]\\b", "re:\\bL[a-z]/[a-z]/[a-z][a-z0-9]+;")
            ),
            ObfuscationTechnique(
                "Flattening de classes",
                "Toutes les classes sont regroupées dans un seul paquetage pour perdre la structure.",
                listOf("re:\\bL[a-z][a-z0-9]+/[a-z][a-z0-9]*/[a-z][a-z0-9]+;", "re:\\bpackage\\s+[a-z0-9]{1,3}\\b")
            ),
            ObfuscationTechnique(
                "Encodage des chaînes",
                "Les chaînes de caractères sont stockées encodées (base64, hex, XOR) et décodées à l'exécution.",
                listOf("re:\"[A-Za-z0-9+/]{20,}={0,2}\"", "re:0x[0-9a-fA-F]{2},\\s*0x[0-9a-fA-F]{2}", "Base64", "decode", "encrypt", "decrypt")
            ),
            ObfuscationTechnique(
                "Chiffrement de code",
                "Le code est chiffré (AES/DES/XOR) et déchiffré en mémoire avant exécution.",
                listOf("AES", "DES", "XOR", "Cipher", "PBKDF2", "getInstance", "SecretKey", "doFinal")
            ),
            ObfuscationTechnique(
                "Chargement réflexif dynamique",
                "Le code charge des classes/méthodes par nom de façon dynamique (Class.forName, getDeclaredMethod).",
                listOf("Class.forName", "forName(", "getDeclaredMethod", "getMethod", "getDeclaredField")
            ),
            ObfuscationTechnique(
                "Contrôle de flux opaque",
                "Ajout de conditions et de boucles impossibles ou trompeuses pour compliquer l'analyse.",
                listOf("re:\\bif\\s*\\(\\s*[0-9]+\\s*[=!]{1,2}\\s*[0-9]+\\s*\\)", "re:goto\\s+\\w+", "switch", "opaque")
            ),
            ObfuscationTechnique(
                "Nativisation",
                "Une partie du code est déplacée dans des bibliothèques natives (.so) pour échapper à l'analyse DEX.",
                listOf("System.loadLibrary", "System.load(", ".so", "loadLibrary")
            ),
            ObfuscationTechnique(
                "Packers/protecteurs (jiagu, bangcle, Tencent, SecNeo, StubApp)",
                "L'APK réel est chiffré et chargé par un chargeur (stub) au moment de l'exécution.",
                listOf("jiagu", "bangcle", "SecNeo", "StubApp", "tencent", "qiku", "ijiami", "360", "com.qihoo")
            ),
            ObfuscationTechnique(
                "Minification",
                "Suppression des espaces, commentaires et renommage des variables locales (code JavaScript/JS).",
                listOf("re:\\bconst\\s+[a-z]\\s*=", "re:\\blet\\s+[a-z]\\s*=", "re:\\bfunction\\s*\\(\\s*[a-z]\\s*,")
            ),
            ObfuscationTechnique(
                "Obfuscation de chaînes Smali",
                "Chaînes éclatées et reconstruites dans les méthodes Smali (clinit/strings).",
                listOf("re:const-string[^\\n]*\"\\w{1,3}\"", "re:new-instance[^\\n]*StringBuilder")
            )
        )
    }
}
