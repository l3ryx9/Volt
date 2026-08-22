package com.voltai.doai.presentation.terminal

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.voltai.doai.data.terminal.TermuxRuntimeManager
import com.voltai.doai.presentation.VoltColors

/**
 * Terminal interactif style Termux : émulateur ANSI/VT + session bash persistante
 * sur un pty (JNI createSubprocess), rendu via TerminalView.
 *
 * Visuellement aligné sur le chat : même palette VoltColors (fond #212121,
 * texte #ECECEC, accent #10A37F). La barre de raccourcis (Tab, Ctrl, Alt,
 * Esc, flèches, /, -) apparaît au-dessus du clavier virtuel.
 */
@Composable
fun TerminalContent() {
    val appContext = LocalContext.current.applicationContext
    val holder = remember { TerminalSessionHolder() }

    DisposableEffect(Unit) {
        holder.ensureRuntime(appContext)
        holder.start()
        onDispose { holder.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoltColors.Background)
    ) {
        // Zone du terminal (prend tout l'espace disponible)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    TerminalView(ctx, null).apply {
                        setTerminalViewClient(holder.viewClient)
                        setTextSize(24)
                        setBackgroundColor(VoltColors.Background.value.toInt())
                        holder.attach(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Barre de raccourcis au-dessus du clavier
        TerminalShortcutBar(holder = holder)
    }
}

/**
 * Barre de touches raccourcis : Tab, Ctrl, Alt, Esc, flèches, /, -, |, ~
 * Même style que la barre de saisie du chat (fond Surface, boutons ElevatedSurface).
 */
@Composable
private fun TerminalShortcutBar(holder: TerminalSessionHolder) {
    Divider(color = VoltColors.Divider, thickness = 1.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoltColors.Surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Touches spéciales
        ShortcutKey("ESC", holder) { it.sendEsc() }
        ShortcutKey("TAB", holder) { it.sendTab() }
        ShortcutKey("CTRL", holder, toggle = true) { it.toggleCtrl() }
        ShortcutKey("ALT", holder, toggle = true) { it.toggleAlt() }

        // Flèches directionnelles
        ShortcutKey("←", holder) { it.sendArrow(KeyEvent.KEYCODE_DPAD_LEFT) }
        ShortcutKey("↑", holder) { it.sendArrow(KeyEvent.KEYCODE_DPAD_UP) }
        ShortcutKey("↓", holder) { it.sendArrow(KeyEvent.KEYCODE_DPAD_DOWN) }
        ShortcutKey("→", holder) { it.sendArrow(KeyEvent.KEYCODE_DPAD_RIGHT) }

        // Caractères utiles absents du clavier standard
        ShortcutKey("/", holder) { it.sendChar('/') }
        ShortcutKey("-", holder) { it.sendChar('-') }
        ShortcutKey("|", holder) { it.sendChar('|') }
        ShortcutKey("~", holder) { it.sendChar('~') }
        ShortcutKey("_", holder) { it.sendChar('_') }
    }
}

@Composable
private fun ShortcutKey(
    label: String,
    holder: TerminalSessionHolder,
    toggle: Boolean = false,
    action: (TerminalSessionHolder) -> Unit
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .widthIn(min = 38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(VoltColors.ElevatedSurface)
            .clickable { action(holder) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (toggle) VoltColors.AccentBright else VoltColors.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Gère une session terminal persistante : crée le TerminalSession avec proot +
 * shell Termux, applique le thème du chat, et relaye les événements.
 */
class TerminalSessionHolder {

    private var terminalView: TerminalView? = null
    private var session: TerminalSession? = null

    @Volatile
    private var ctrlDown = false
    @Volatile
    private var altDown = false

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(session: TerminalSession) {
            terminalView?.onScreenUpdated()
        }

        override fun onTitleChanged(session: TerminalSession) {}

        override fun onSessionFinished(session: TerminalSession) {
            terminalView?.post {
                terminalView?.onScreenUpdated()
            }
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}

        override fun onPasteTextFromClipboard(session: TerminalSession?) {}

        override fun onBell(session: TerminalSession) {}

        override fun onColorsChanged(session: TerminalSession) {}

        override fun onTerminalCursorStateChange(state: Boolean) {}

        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}

        override fun getTerminalCursorStyle(): Int? = null

        override fun logError(tag: String, message: String) {}
        override fun logWarn(tag: String, message: String) {}
        override fun logInfo(tag: String, message: String) {}
        override fun logDebug(tag: String, message: String) {}
        override fun logVerbose(tag: String, message: String) {}
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
        override fun logStackTrace(tag: String, e: Exception) {}
    }

    val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float = 1.0f

        override fun onSingleTapUp(e: MotionEvent) {}

        override fun shouldBackButtonBeMappedToEscape(): Boolean = true

        override fun shouldEnforceCharBasedInput(): Boolean = false

        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

        override fun isTerminalViewSelected(): Boolean = true

        override fun copyModeChanged(copyMode: Boolean) {}

        override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
            // Injecte le modificateur Ctrl/Alt si activé via la barre de raccourcis
            if (ctrlDown) {
                ctrlDown = false
                val code = keyCode - KeyEvent.KEYCODE_A + 1
                if (code in 1..26) {
                    session.write(byteArrayOf(code.toByte()), 0, 1)
                    return true
                }
            }
            return false
        }

        override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

        override fun onLongPress(event: MotionEvent): Boolean = false

        override fun readControlKey(): Boolean = ctrlDown
        override fun readAltKey(): Boolean = altDown
        override fun readShiftKey(): Boolean = false
        override fun readFnKey(): Boolean = false

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false

        override fun onEmulatorSet() {}

        override fun logError(tag: String, message: String) {}
        override fun logWarn(tag: String, message: String) {}
        override fun logInfo(tag: String, message: String) {}
        override fun logDebug(tag: String, message: String) {}
        override fun logVerbose(tag: String, message: String) {}
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {}
        override fun logStackTrace(tag: String, e: Exception) {}
    }

    fun attach(view: TerminalView) {
        terminalView = view
        view.attachSession(createOrGetSession())
    }

    fun ensureRuntime(context: android.content.Context) {
        if (!TermuxRuntimeManager.isRuntimeInstalled) {
            TermuxRuntimeManager.init(context)
        }
    }

    fun start() {
        applyTheme()
    }

    fun stop() {
        session?.finishIfRunning()
        session = null
        terminalView = null
    }

    // --- Actions de la barre de raccourcis ---

    fun sendEsc() {
        session?.write(byteArrayOf(27), 0, 1) // ESC = 0x1B
    }

    fun sendTab() {
        session?.write(byteArrayOf(9), 0, 1) // TAB = 0x09
    }

    fun toggleCtrl() {
        ctrlDown = !ctrlDown
    }

    fun toggleAlt() {
        altDown = !altDown
    }

    fun sendArrow(keyCode: Int) {
        val seq = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> "\u001b[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> "\u001b[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001b[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> "\u001b[D"
            else -> return
        }
        val bytes = seq.toByteArray(Charsets.UTF_8)
        session?.write(bytes, 0, bytes.size)
    }

    fun sendChar(c: Char) {
        val bytes = c.toString().toByteArray(Charsets.UTF_8)
        session?.write(bytes, 0, bytes.size)
    }

    private fun createOrGetSession(): TerminalSession {
        session?.let { return it }
        applyTheme()

        val shellPath = TermuxRuntimeManager.PROOT_PATH
        val prefixReal = TermuxRuntimeManager.PREFIX_DIR
        val homeReal = TermuxRuntimeManager.HOME_DIR
        val vPrefix = TermuxRuntimeManager.VIRTUAL_PREFIX
        val vHome = TermuxRuntimeManager.VIRTUAL_HOME

        if (!TermuxRuntimeManager.isRuntimeInstalled ||
            !java.io.File(shellPath).exists()
        ) {
            android.util.Log.e("Terminal", "Runtime embarqué indisponible ($shellPath)")
            return TerminalSession("/system/bin/sh", homeReal, arrayOf("/system/bin/sh"), emptyArray(), null, sessionClient)
        }

        val args = arrayOf(
            shellPath,
            "-0",
            "-b", "$prefixReal:$vPrefix",
            "-b", "$homeReal:$vHome",
            "-w", vHome,
            "$vPrefix/bin/login"
        )
        val env = arrayOf(
            "HOME=$vHome",
            "PREFIX=$vPrefix",
            "PATH=$vPrefix/bin:$vPrefix/bin/applets:/usr/local/bin:/root/voltai/usr/bin:/usr/bin:/bin",
            "JAVA_HOME=/root/voltai/usr/lib/jvm/java-17-openjdk",
            "PYTHONPATH=/root/voltai/usr/lib/python3.14/site-packages",
            "TMPDIR=$vPrefix/tmp",
            "TERM=xterm-256color",
            "LD_LIBRARY_PATH=${TermuxRuntimeManager.PROOT_LIB_DIR}"
        )

        val created = try {
            TerminalSession(shellPath, homeReal, args, env, null, sessionClient)
        } catch (e: Throwable) {
            android.util.Log.e("Terminal", "Échec création session terminal", e)
            TerminalSession("/system/bin/sh", homeReal, arrayOf("/system/bin/sh"), emptyArray(), null, sessionClient)
        }
        session = created
        return created
    }

    /**
     * Thème aligné sur VoltColors : même palette que le chat pour
     * une transition visuelle fluide entre les deux modes.
     */
    private fun applyTheme() {
        val scheme = TerminalColors.COLOR_SCHEME
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_BACKGROUND] = 0xFF212121.toInt() // VoltColors.Background
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_FOREGROUND] = 0xFFECECEC.toInt() // VoltColors.Text
        scheme.mDefaultColors[TextStyle.COLOR_INDEX_CURSOR] = 0xFF10A37F.toInt()     // VoltColors.Accent
        scheme.mDefaultColors[8] = 0xFF8E8E9A.toInt()                                 // VoltColors.MutedText
        scheme.setCursorColorForBackground()
    }
}