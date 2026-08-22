package com.voltai.doai.data.intelligence

import com.voltai.doai.data.terminal.CommandExecutorImpl
import com.voltai.doai.domain.interfaces.Complexity
import com.voltai.doai.domain.interfaces.ContextUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceTest {

    private val intentAnalyzer = IntentAnalyzerImpl()
    private val taskPlanner = TaskPlannerImpl()
    private val toolRouter = ToolRouterImpl(CommandExecutorImpl())
    private val contextManager = ContextManagerImpl()

    @Test
    fun analyze_apk_request_detectsAnalyzeApk() {
        val intent = intentAnalyzer.analyzeRequest("Analyse cette APK")
        assertEquals(IntentAnalyzerImpl.ACTION_ANALYZE_APK, intent.action)
        assertEquals(Complexity.EXPERT, intent.complexity)
        assertTrue(intent.tools.containsAll(listOf("apktool", "jadx", "androguard")))
    }

    @Test
    fun analyze_apk_request_detectsApkFile() {
        val intent = intentAnalyzer.analyzeRequest("Analyse cette application.apk")
        assertTrue(intent.files.contains("application.apk"))
        assertEquals("application.apk", intent.target)
    }

    @Test
    fun analyze_extractArchive_detectsFileAndTool() {
        val intent = intentAnalyzer.analyzeRequest("Décompresse archive.zip")
        assertEquals(IntentAnalyzerImpl.ACTION_EXTRACT_ARCHIVE, intent.action)
        assertTrue(intent.files.contains("archive.zip"))
        assertEquals(Complexity.SIMPLE, intent.complexity)
    }

    @Test
    fun analyze_decrypt_detectsDecrypt() {
        val intent = intentAnalyzer.analyzeRequest("Déchiffre le code dans ces fichiers")
        assertEquals(IntentAnalyzerImpl.ACTION_DECRYPT, intent.action)
        assertEquals(Complexity.EXPERT, intent.complexity)
    }

    @Test
    fun analyze_installPython_targetsPython() {
        val intent = intentAnalyzer.analyzeRequest("Installe python")
        assertEquals(IntentAnalyzerImpl.ACTION_INSTALL_PACKAGE, intent.action)
        assertEquals("python", intent.target)
    }

    @Test
    fun analyze_modifyCode_detectsModify() {
        val intent = intentAnalyzer.analyzeRequest("Modifie le code dans Main.kt")
        assertEquals(IntentAnalyzerImpl.ACTION_MODIFY_CODE, intent.action)
        assertTrue(intent.files.contains("Main.kt"))
    }

    @Test
    fun analyze_download_detectsUrl() {
        val intent = intentAnalyzer.analyzeRequest("Télécharge https://example.com/file.zip")
        assertEquals(IntentAnalyzerImpl.ACTION_DOWNLOAD, intent.action)
        assertEquals("https://example.com/file.zip", intent.target)
    }

    @Test
    fun plan_apkAnalysis_isValidAndOrdered() {
        val intent = intentAnalyzer.analyzeRequest("Analyse cette application.apk")
        val task = taskPlanner.createPlan(intent)
        assertTrue(task.steps.isNotEmpty())
        assertTrue(taskPlanner.validatePlan(task))
        assertEquals("apktool", task.steps.first().tool)
    }

    @Test
    fun plan_extractArchive_usesUnzip() {
        val intent = intentAnalyzer.analyzeRequest("Décompresse archive.zip")
        val task = taskPlanner.createPlan(intent)
        assertTrue(taskPlanner.validatePlan(task))
        assertEquals("unzip -o archive.zip", task.steps.first().command)
    }

    @Test
    fun plan_optimize_deduplicatesSteps() {
        val intent = intentAnalyzer.analyzeRequest("Décompresse archive.zip")
        val task = taskPlanner.createPlan(intent)
        val duplicated = taskPlanner.addStep(task, task.steps.first())
        val optimized = taskPlanner.optimizePlan(duplicated)
        assertEquals(task.steps.size, optimized.steps.size)
    }

    @Test
    fun router_selectsUnrarForRar() {
        assertEquals("unrar", toolRouter.selectTool(IntentAnalyzerImpl.ACTION_EXTRACT_ARCHIVE, "file.rar"))
    }

    @Test
    fun router_selectsApktoolForApkAnalysis() {
        assertEquals("apktool", toolRouter.selectTool(IntentAnalyzerImpl.ACTION_ANALYZE_APK, "app.apk"))
    }

    @Test
    fun router_availability() {
        assertTrue(toolRouter.isToolAvailable("jadx"))
        assertTrue(toolRouter.isToolAvailable("frida-gadget"))
        assertFalse(toolRouter.isToolAvailable("outil-inconnu"))
    }

    @Test
    fun router_capabilities_containsDecompileForJadx() {
        assertTrue(toolRouter.getToolCapabilities("jadx").contains("decompile"))
    }

    @Test
    fun router_routesAiStepWithoutExecution() {
        val step = com.voltai.doai.domain.models.TaskStep(
            id = "step-0",
            description = "Synthèse",
            tool = "ai",
            command = "echo Analyse",
            dependencies = emptyList(),
            expectedOutput = null,
            timeout = 10L
        )
        val result = toolRouter.routeStep(step)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.isNotBlank())
    }

    @Test
    fun context_createAndGet() {
        contextManager.clearContext("sess-test")
        val ctx = contextManager.createContext("sess-test")
        assertEquals("sess-test", ctx.sessionId)
        assertEquals(ctx, contextManager.getContext("sess-test"))
    }

    @Test
    fun context_updateKeepsState() {
        contextManager.clearContext("sess-test")
        contextManager.createContext("sess-test")
        contextManager.updateContext(
            "sess-test",
            ContextUpdate(
                recentFiles = listOf("a.apk", "b.zip"),
                recentCommands = listOf("apktool d a.apk")
            )
        )
        assertEquals(listOf("a.apk", "b.zip"), contextManager.getRecentFiles("sess-test"))
        assertEquals(listOf("apktool d a.apk"), contextManager.getRecentCommands("sess-test"))
    }

    @Test
    fun context_clearRemoves() {
        contextManager.createContext("sess-test")
        contextManager.clearContext("sess-test")
        assertEquals(null, contextManager.getContext("sess-test"))
    }
}
