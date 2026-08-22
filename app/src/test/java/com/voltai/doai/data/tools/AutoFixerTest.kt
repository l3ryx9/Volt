package com.voltai.doai.data.tools

import com.voltai.doai.domain.models.CommandResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoFixerTest {

    private fun result(exitCode: Int, output: String = "", error: String = "") =
        CommandResult("test", output, error, exitCode, 0L)

    @Test
    fun `exit 0 never triggers repair`() {
        assertFalse(AutoFixer.shouldRepair(result(0, "ok")))
        assertFalse(AutoFixer.shouldRepair(result(0, "", "command not found")))
    }

    @Test
    fun `exit 127 always triggers repair`() {
        assertTrue(AutoFixer.shouldRepair(result(127)))
    }

    @Test
    fun `detects command not found in stderr`() {
        assertTrue(AutoFixer.shouldRepair(result(1, "", "proot: command not found")))
    }

    @Test
    fun `detects no such file or directory`() {
        assertTrue(AutoFixer.shouldRepair(result(1, "/root/voltai/usr/bin/apktool: No such file or directory")))
    }

    @Test
    fun `detects inaccessible or not found`() {
        assertTrue(AutoFixer.shouldRepair(result(1, "python3.14: inaccessible or not found")))
    }

    @Test
    fun `case insensitive matching`() {
        assertTrue(AutoFixer.shouldRepair(result(1, "COMMAND NOT FOUND")))
    }

    @Test
    fun `plain syntax error does not trigger repair`() {
        assertFalse(AutoFixer.shouldRepair(result(2, "syntax error near unexpected token")))
    }

    @Test
    fun `permission denied does not trigger repair`() {
        assertFalse(AutoFixer.shouldRepair(result(126, "Permission denied")))
    }
}