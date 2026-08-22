package com.voltai.doai.di

import android.content.Context
import java.io.File
import com.voltai.doai.data.agent.AgentEngineImpl
import com.voltai.doai.data.agent.ExecutionManagerImpl
import com.voltai.doai.data.agent.OrchestratorImpl
import com.voltai.doai.data.agent.TaskValidatorImpl
import com.voltai.doai.data.analysis.APKAnalyzerImpl
import com.voltai.doai.data.analysis.CodeExplorerImpl
import com.voltai.doai.data.analysis.DecompilerImpl
import com.voltai.doai.data.analysis.ReportGeneratorImpl
import com.voltai.doai.data.code.CodeIndexerImpl
import com.voltai.doai.data.code.DependencyAnalyzerImpl
import com.voltai.doai.data.code.LanguageDetectorImpl
import com.voltai.doai.data.code.SymbolResolverImpl
import com.voltai.doai.data.github.GithubManager
import com.voltai.doai.data.github.GithubSettings
import com.voltai.doai.data.intelligence.ContextManagerImpl
import com.voltai.doai.data.intelligence.IntentAnalyzerImpl
import com.voltai.doai.data.intelligence.TaskPlannerImpl
import com.voltai.doai.data.intelligence.ToolRouterImpl
import com.voltai.doai.data.llama.MemoryManagerImpl
import com.voltai.doai.data.llama.PromptManagerImpl
import com.voltai.doai.data.qwen.QwenClient
import com.voltai.doai.data.qwen.QwenEngineImpl
import com.voltai.doai.data.qwen.QwenModelManagerImpl
import com.voltai.doai.data.qwen.QwenSettings
import com.voltai.doai.data.storage.ArchiveManagerImpl
import com.voltai.doai.data.storage.FileManagerImpl
import com.voltai.doai.data.storage.WorkspaceManagerImpl
import com.voltai.doai.data.terminal.CommandExecutorImpl
import com.voltai.doai.data.terminal.EnvironmentManagerImpl
import com.voltai.doai.data.terminal.PackageManagerImpl
import com.voltai.doai.data.terminal.TerminalEngineImpl
import com.voltai.doai.domain.interfaces.APKAnalyzer
import com.voltai.doai.domain.interfaces.AgentEngine
import com.voltai.doai.domain.interfaces.ArchiveManager
import com.voltai.doai.domain.interfaces.CodeExplorer
import com.voltai.doai.domain.interfaces.CodeIndexer
import com.voltai.doai.domain.interfaces.CommandExecutor
import com.voltai.doai.domain.interfaces.ContextManager
import com.voltai.doai.domain.interfaces.Decompiler
import com.voltai.doai.domain.interfaces.DependencyAnalyzer
import com.voltai.doai.domain.interfaces.EnvironmentManager
import com.voltai.doai.domain.interfaces.ExecutionManager
import com.voltai.doai.domain.interfaces.FileManager
import com.voltai.doai.domain.interfaces.IntentAnalyzer
import com.voltai.doai.domain.interfaces.LanguageDetector
import com.voltai.doai.domain.interfaces.LlamaEngine
import com.voltai.doai.domain.interfaces.MemoryManager
import com.voltai.doai.domain.interfaces.ModelManager
import com.voltai.doai.domain.interfaces.Orchestrator
import com.voltai.doai.domain.interfaces.PackageManager
import com.voltai.doai.domain.interfaces.PromptManager
import com.voltai.doai.domain.interfaces.ReportGenerator
import com.voltai.doai.domain.interfaces.SymbolResolver
import com.voltai.doai.domain.interfaces.TaskPlanner
import com.voltai.doai.domain.interfaces.TaskValidator
import com.voltai.doai.domain.interfaces.TerminalEngine
import com.voltai.doai.domain.interfaces.ToolRouter
import com.voltai.doai.domain.interfaces.WorkspaceManager

object ServiceLocator {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun appContext(): Context =
        requireNotNull(appContext) { "ServiceLocator.init(context) doit être appelé" }

    val environmentManager: EnvironmentManager by lazy { EnvironmentManagerImpl() }
    val packageManager: PackageManager by lazy { PackageManagerImpl() }
    val terminalEngine: TerminalEngine by lazy { TerminalEngineImpl() }
    val commandExecutor: CommandExecutor by lazy { CommandExecutorImpl() }

    val intentAnalyzer: IntentAnalyzer by lazy { IntentAnalyzerImpl() }
    val taskPlanner: TaskPlanner by lazy { TaskPlannerImpl() }
    val toolRouter: ToolRouter by lazy { ToolRouterImpl(commandExecutor) }
    val contextManager: ContextManager by lazy { ContextManagerImpl() }

    val fileManager: FileManager by lazy { FileManagerImpl() }
    val archiveManager: ArchiveManager by lazy { ArchiveManagerImpl() }
    val workspaceManager: WorkspaceManager by lazy {
        WorkspaceManagerImpl(requireNotNull(appContext) { "ServiceLocator.init(context) doit être appelé" })
    }

    val apkAnalyzer: APKAnalyzer by lazy { APKAnalyzerImpl(archiveManager) }
    val decompiler: Decompiler by lazy { DecompilerImpl(commandExecutor) }
    val codeExplorer: CodeExplorer by lazy { CodeExplorerImpl() }
    val reportGenerator: ReportGenerator by lazy { ReportGeneratorImpl() }

    val languageDetector: LanguageDetector by lazy { LanguageDetectorImpl() }
    val symbolResolver: SymbolResolver by lazy { SymbolResolverImpl() }
    val codeIndexer: CodeIndexer by lazy { CodeIndexerImpl(symbolResolver) }
    val dependencyAnalyzer: DependencyAnalyzer by lazy { DependencyAnalyzerImpl() }

    val executionManager: ExecutionManager by lazy { ExecutionManagerImpl() }
    val taskValidator: TaskValidator by lazy { TaskValidatorImpl() }
    val orchestrator: Orchestrator by lazy { OrchestratorImpl(toolRouter, executionManager, taskValidator) }
    val agentEngine: AgentEngine by lazy { AgentEngineImpl() }

    val qwenSettings: QwenSettings by lazy {
        QwenSettings(requireNotNull(appContext) { "ServiceLocator.init(context) doit être appelé" })
    }
    val qwenClient: QwenClient by lazy { QwenClient(baseUrlProvider = { qwenSettings.currentUrl() }) }
    val qwenEngine: LlamaEngine by lazy { QwenEngineImpl(qwenClient) }
    val qwenModelManager: ModelManager by lazy {
        QwenModelManagerImpl(qwenClient)
    }
    val modelManager: ModelManager by lazy { qwenModelManager }
    val llamaEngine: LlamaEngine by lazy { qwenEngine }
    val promptManager: PromptManager by lazy {
        PromptManagerImpl(
            cacheDir = File(requireNotNull(appContext) { "ServiceLocator.init(context) doit être appelé" }.cacheDir, "llama")
        )
    }
    val memoryManager: MemoryManager by lazy {
        MemoryManagerImpl(
            memoryFile = File(
                requireNotNull(appContext) { "ServiceLocator.init(context) doit être appelé" }.filesDir,
                "qwen_memory.txt"
            )
        )
    }

    val githubSettings: GithubSettings by lazy {
        GithubSettings(requireNotNull(appContext) { "ServiceLocator.init(context) doit être appelé" })
    }
    val githubManager: GithubManager by lazy {
        GithubManager(
            settings = githubSettings,
            reposDir = workspaceManager.getReposDir()
        )
    }
}
