package com.voltai.doai.data.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolchainManagerTest {

    @Test
    fun installSuccessMarksDone() = runBlocking {
        ToolchainManager.resetForTest()
        val status = ToolchainManager.run { true }
        assertEquals(ToolchainPhase.DONE, status.phase)
    }

    @Test
    fun installFailureMarksFailed() = runBlocking {
        ToolchainManager.resetForTest()
        val status = ToolchainManager.run { false }
        assertEquals(ToolchainPhase.FAILED, status.phase)
    }

    @Test
    fun installExceptionReportsMessage() = runBlocking {
        ToolchainManager.resetForTest()
        val status = ToolchainManager.run { error("boom") }
        assertEquals(ToolchainPhase.FAILED, status.phase)
        assertNotNull(status.message)
    }

    @Test
    fun onlyOneInstallRunsAtATime() = runBlocking {
        ToolchainManager.resetForTest()
        val first = ToolchainManager.run { true }
        assertEquals(ToolchainPhase.DONE, first.phase)

        var secondCalled = false
        val second = ToolchainManager.run { secondCalled = true; false }
        assertEquals(ToolchainPhase.DONE, second.phase)
        assertTrue("l'installation ne doit pas repartir une fois DONE", !secondCalled)
    }

    @Test
    fun statusIsObservable() = runBlocking {
        ToolchainManager.resetForTest()
        ToolchainManager.run { true }
        assertEquals(ToolchainPhase.DONE, ToolchainManager.status.value.phase)
    }
}
