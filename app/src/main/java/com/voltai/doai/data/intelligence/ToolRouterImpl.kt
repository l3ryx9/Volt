package com.voltai.doai.data.intelligence

import com.voltai.doai.domain.interfaces.CommandExecutor
import com.voltai.doai.domain.interfaces.Tool
import com.voltai.doai.domain.interfaces.ToolRouter
import com.voltai.doai.domain.models.CommandResult
import com.voltai.doai.domain.models.TaskStep

class ToolRouterImpl(
    private val commandExecutor: CommandExecutor
) : ToolRouter {

    private val registry: Map<String, Tool> = buildRegistry()

    override fun routeStep(step: TaskStep): CommandResult {
        if (step.tool == TOOL_AI) {
            return CommandResult(
                command = step.command,
                output = "[${step.description}]",
                error = null,
                exitCode = 0,
                duration = 0L
            )
        }
        return commandExecutor.executeCommand(step.command, step.timeout)
    }

    override fun selectTool(action: String, target: String): String {
        return when (action) {
            IntentAnalyzerImpl.ACTION_ANALYZE_APK -> TOOL_APKTOOL
            IntentAnalyzerImpl.ACTION_DECOMPILE -> TOOL_JADX
            IntentAnalyzerImpl.ACTION_DECRYPT -> TOOL_FRIDA_GADGET
            IntentAnalyzerImpl.ACTION_REVERSE_ENGINEER -> TOOL_APKTOOL
            IntentAnalyzerImpl.ACTION_EXTRACT_ARCHIVE -> when {
                target.endsWith(".rar", ignoreCase = true) -> "unrar"
                target.endsWith(".7z", ignoreCase = true) -> "7z"
                target.endsWith(".tar") || target.endsWith(".tar.gz") || target.endsWith(".tgz") -> "tar"
                else -> "unzip"
            }
            IntentAnalyzerImpl.ACTION_CREATE_ARCHIVE -> "zip"
            IntentAnalyzerImpl.ACTION_MODIFY_CODE -> "sed"
            IntentAnalyzerImpl.ACTION_EXPLAIN_CODE -> TOOL_AI
            IntentAnalyzerImpl.ACTION_SEARCH_CODE -> "grep"
            IntentAnalyzerImpl.ACTION_INSTALL_PACKAGE -> "pkg"
            IntentAnalyzerImpl.ACTION_UPDATE_PACKAGES -> "pkg"
            IntentAnalyzerImpl.ACTION_LIST_FILES -> "find"
            IntentAnalyzerImpl.ACTION_DOWNLOAD -> "curl"
            IntentAnalyzerImpl.ACTION_GIT_CLONE -> "git"
            else -> "shell"
        }
    }

    override fun isToolAvailable(tool: String): Boolean {
        return registry.containsKey(tool)
    }

    override fun getToolCapabilities(tool: String): List<String> {
        return registry[tool]?.capabilities ?: emptyList()
    }

    override fun optimizeToolSelection(step: TaskStep): TaskStep {
        val betterTool = selectTool(step.description, step.command)
        return if (betterTool != "shell" && (step.tool.isBlank() || step.tool == "shell")) {
            step.copy(tool = betterTool)
        } else {
            step
        }
    }

    private fun buildRegistry(): Map<String, Tool> {
        val tools = listOf(
            Tool(TOOL_APKTOOL, "Décodage/encodage des ressources APK", listOf("decode", "encode", "install-framework"), listOf("apktool"), listOf("apk", "aab")),
            Tool(TOOL_JADX, "Décompilation DEX vers Java", listOf("decompile", "explore-code", "string-decrypt"), listOf("jadx"), listOf("apk", "dex", "jar", "class")),
            Tool("androguard", "Analyse de manifeste, permissions, code", listOf("analyze", "permissions", "manifest", "deobfuscate"), listOf("androguard"), listOf("apk", "dex")),
            Tool("smali", "Assemblage Smali", listOf("assemble"), listOf("smali"), listOf("smali")),
            Tool("baksmali", "Désassemblage DEX vers Smali", listOf("disassemble"), listOf("baksmali"), listOf("dex", "apk")),
            Tool("unzip", "Extraction d'archives ZIP", listOf("extract", "list"), listOf("unzip", "zip"), listOf("zip", "jar", "apk")),
            Tool("zip", "Création d'archives ZIP", listOf("create", "add"), listOf("zip"), listOf("zip")),
            Tool("unrar", "Extraction d'archives RAR", listOf("extract"), listOf("unrar"), listOf("rar")),
            Tool("7z", "Extraction/création d'archives 7z", listOf("extract", "create"), listOf("p7zip"), listOf("7z")),
            Tool("tar", "Archives tar", listOf("extract", "create"), listOf("tar"), listOf("tar", "tgz")),
            Tool(TOOL_FRIDA_GADGET, "Instrumentation dynamique (Frida Gadget)", listOf("instrument", "hook", "decrypt"), listOf("frida-gadget", "frida-tools"), listOf("so", "dex", "apk")),
            Tool("astana", "Désobfuscation de chaînes par program slicing", listOf("deobfuscate-strings"), listOf("astana"), listOf("dex", "apk")),
            Tool("dalivm", "Émulateur Dalvik pour exécution de méthodes", listOf("emulate", "execute-methods"), listOf("dalivm"), listOf("dex", "apk")),
            Tool("paranoid-deobfuscator", "Désobfuscation Paranoid/LSParanoid", listOf("deobfuscate"), listOf("paranoid-deobfuscator"), listOf("apk", "dex")),
            Tool("proguard-deobfuscator", "Désobfuscation avec mapping ProGuard", listOf("deobfuscate", "map-classes"), listOf("proguard"), listOf("mapping", "apk")),
            Tool("jadx-string-decrypt", "Déchiffrement des chaînes constantes", listOf("decrypt-strings"), listOf("jadx"), listOf("apk", "dex", "java")),
            Tool("frida-gadget", "Instrumentation dynamique", listOf("hook", "trace", "decrypt"), listOf("frida-gadget"), listOf("so", "dex")),
            Tool("python", "Scripting et parsing", listOf("script", "parse", "decrypt", "transform"), listOf("python", "pip"), listOf("py", "json", "xml", "txt")),
            Tool("grep", "Recherche dans le code", listOf("search", "regex"), listOf("grep"), listOf("*")),
            Tool("ripgrep", "Recherche rapide dans le code", listOf("search", "regex"), listOf("ripgrep"), listOf("*")),
            Tool("sed", "Édition et transformation de texte", listOf("edit", "transform"), listOf("sed"), listOf("txt", "xml", "java", "kt", "json")),
            Tool("awk", "Traitement de texte par champs", listOf("parse", "transform"), listOf("awk"), listOf("txt", "csv", "log")),
            Tool("find", "Recherche de fichiers", listOf("search-files"), listOf("findutils"), listOf("*")),
            Tool("wget", "Téléchargement", listOf("download"), listOf("wget"), listOf("url")),
            Tool("curl", "Requêtes HTTP", listOf("download", "request"), listOf("curl"), listOf("url")),
            Tool("openssl", "Chiffrement/déchiffrement, certificats, hachage", listOf("encrypt", "decrypt", "hash", "base64", "rsa", "x509"), listOf("openssl"), listOf("enc", "pem", "cer", "der", "bin", "txt")),
            Tool("git", "Gestion de dépôts", listOf("clone", "version", "diff"), listOf("git"), listOf("url", "repo")),
            Tool("tree-sitter", "Parsing AST multilingue", listOf("parse-ast", "symbols"), listOf("tree-sitter"), listOf("java", "kt", "py", "js", "go", "c", "cpp", "rs")),
            Tool(TOOL_AI, "Analyse et synthèse IA (Qwen)", listOf("analyze", "explain", "synthesize", "plan"), listOf("qwen"), listOf("*")),
            Tool("shell", "Exécution de commandes shell", listOf("execute"), listOf("termux"), listOf("*"))
        )
        return tools.associateBy { it.name }
    }

    companion object {
        const val TOOL_AI = "ai"
        const val TOOL_APKTOOL = "apktool"
        const val TOOL_JADX = "jadx"
        const val TOOL_FRIDA_GADGET = "frida-gadget"
    }
}
