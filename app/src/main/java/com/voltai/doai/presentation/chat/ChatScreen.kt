package com.voltai.doai.presentation.chat

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.voltai.doai.presentation.VoltLogo
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltai.doai.domain.models.ExecutionStatus
import com.voltai.doai.domain.models.ModelPhase
import com.voltai.doai.domain.models.ModelStatus
import com.voltai.doai.domain.models.PhaseState
import com.voltai.doai.presentation.VoltColors
import com.voltai.doai.presentation.terminal.TerminalContent
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private val EXECUTABLE_TOOLS = listOf(
    "apktool" to "apktool decode ",
    "jadx" to "jadx -d ",
    "smali" to "smali assemble ",
    "baksmali" to "baksmali disassemble ",
    "7z" to "7z x ",
    "rg" to "rg -n ",
    "androguard" to "python3 -m androguard ",
    "frida" to "frida -U -f "
)

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    isTerminalMode: Boolean = false,
    onToggleTerminal: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val currentPhase by viewModel.currentPhase.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val executionStatus by viewModel.executionStatus.collectAsState()
    val repos by viewModel.repos.collectAsState()
    val selectedRepo by viewModel.selectedRepo.collectAsState()
    val pushing by viewModel.pushing.collectAsState()
    val pushResult by viewModel.pushResult.collectAsState()

    val chatSessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()

    var repoMenuExpanded by remember { mutableStateOf(false) }
    var sidebarOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val dest = saveAttachment(context, uri)
            viewModel.insertIntoInput(dest.absolutePath)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(VoltColors.Background),
            topBar = {
                ChatTopBar(
                    modelStatus = modelStatus,
                    onOpenSidebar = { sidebarOpen = true }
                )
            },
            bottomBar = {
                Column {
                    if (!isTerminalMode) {
                        if (busy || currentPhase == PhaseState.AWAITING_CONFIRMATION) {
                            ExecutionControls(
                                phase = currentPhase,
                                onPause = viewModel::pause,
                                onResume = viewModel::resume,
                                onStop = viewModel::stop,
                                onConfirm = viewModel::confirmAction,
                                onDeny = viewModel::denyAction
                            )
                        }
                        ChatInputBar(
                            text = inputText,
                            onTextChange = viewModel::onInputChange,
                            onSend = viewModel::sendMessage,
                            onAttach = { attachLauncher.launch(arrayOf("*/*")) },
                            onSelectTool = viewModel::insertIntoInput,
                            repos = repos,
                            selectedRepo = selectedRepo,
                            pushing = pushing,
                            pushResult = pushResult,
                            onRepoMenuClick = { repoMenuExpanded = true },
                            onSelectRepo = { path ->
                                repoMenuExpanded = false
                                viewModel.selectRepo(path)
                            },
                            onDismissRepoMenu = { repoMenuExpanded = false },
                            repoMenuExpanded = repoMenuExpanded,
                            onPush = viewModel::pushSelectedRepo,
                            onClearPushResult = { viewModel.clearSelectedRepo() },
                            enabled = !busy
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isTerminalMode) {
                    TerminalContent()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            ModelStatusBanner(status = modelStatus)
                        }
                        items(messages) { message ->
                            MessageBubble(message = message)
                        }
                        if (busy) {
                            item {
                                ExecutionProgress(executionStatus)
                            }
                        }
                    }
                }
            }
        }

        SessionsSidebar(
            isOpen = sidebarOpen,
            sessions = chatSessions,
            activeSessionId = activeSessionId,
            canCreateSession = chatSessions.size < ChatViewModel.MAX_SESSIONS,
            onClose = { sidebarOpen = false },
            onNewSession = viewModel::newSession,
            onSwitchSession = viewModel::switchSession,
            onDeleteSession = viewModel::deleteSession
        )
    }
}

private fun saveAttachment(context: android.content.Context, uri: Uri): File {
    val displayName = queryDisplayName(context, uri)
        ?: "fichier_${System.currentTimeMillis()}"
    val dir = context.getExternalFilesDir("attachments") ?: context.filesDir
    dir.mkdirs()
    val dest = File(dir, displayName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(dest).use { output -> input.copyTo(output, bufferSize = 1 shl 16) }
    }
    return dest
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}

/**
 * Barre du haut façon voltai-v4 : icône hamburger à gauche (ouvre le menu
 * des sessions), logo « Volt » centré, pastille de statut de connexion à
 * droite — sa couleur reflète l'état réel du serveur Qwen distant.
 */
@Composable
private fun ChatTopBar(
    modelStatus: ModelStatus,
    onOpenSidebar: () -> Unit
) {
    val online = modelStatus.phase == ModelPhase.READY
    val hasError = modelStatus.phase == ModelPhase.ERROR || modelStatus.phase == ModelPhase.UNAVAILABLE
    val dotColor = when {
        online -> VoltColors.AccentBright
        hasError -> VoltColors.Error
        else -> VoltColors.MutedText
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VoltColors.Surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = VoltColors.Divider,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenSidebar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Sessions",
                    tint = VoltColors.AccentBright,
                    modifier = Modifier.size(22.dp)
                )
            }

            VoltLogo(modifier = Modifier.align(Alignment.Center))

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor),
                contentAlignment = Alignment.Center
            ) {}
        }
    }
}

@Composable
private fun ExecutionControls(
    phase: PhaseState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VoltColors.Surface,
        elevation = 4.dp
    ) {
        if (phase == PhaseState.AWAITING_CONFIRMATION) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Qwen demande confirmation",
                    color = VoltColors.Accent,
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDeny,
                        colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Error),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Refuser", color = VoltColors.Text)
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(backgroundColor = VoltColors.Accent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Confirmer", color = VoltColors.Text)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(VoltColors.AccentBright)
                    )
                    Text(
                        text = "Exécution en cours",
                        color = VoltColors.Text,
                        style = MaterialTheme.typography.body2
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExecIconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = VoltColors.MutedText, modifier = Modifier.size(16.dp))
                    }
                    ExecIconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "Arrêter", tint = VoltColors.Error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionProgress(status: ExecutionStatus) {
    val determinate = status.stepsTotal > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        backgroundColor = VoltColors.ElevatedSurface,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (determinate) {
                LinearProgressIndicator(
                    progress = (status.stepsDone.toFloat() / status.stepsTotal).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = VoltColors.Accent,
                    backgroundColor = VoltColors.Divider
                )
                Text(
                    text = "${status.currentStep ?: "Exécution..."} (${status.stepsDone}/${status.stepsTotal})",
                    color = VoltColors.Accent,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = VoltColors.Accent,
                    backgroundColor = VoltColors.Divider
                )
                Text(
                    text = status.currentStep ?: "Qwen analyse et exécute...",
                    color = VoltColors.Accent,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun ModelStatusBanner(status: ModelStatus) {
    val (text, color) = when (status.phase) {
        ModelPhase.DOWNLOADING -> Pair(
            "Connexion au serveur Qwen… ${status.percent}%",
            VoltColors.Accent
        )
        ModelPhase.NOT_DOWNLOADED -> Pair(
            "URL Colab non configurée — définissez-la dans les Paramètres.",
            VoltColors.MutedText
        )
        ModelPhase.DOWNLOADED, ModelPhase.LOADING -> Pair(
            "Connexion au serveur Qwen…",
            VoltColors.Accent
        )
        ModelPhase.READY -> Pair(
            "Qwen distant actif (llama-server Colab)",
            VoltColors.AccentBright
        )
        ModelPhase.ERROR -> Pair(
            "Qwen distant indisponible : ${status.message ?: "vérifiez l'URL Colab"}",
            VoltColors.Error
        )
        ModelPhase.UNAVAILABLE -> Pair(
            "Qwen distant indisponible",
            VoltColors.Error
        )
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(VoltColors.ElevatedSurface)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            Text(text = text, color = color, style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
fun MessageBubble(message: com.voltai.doai.domain.models.Message) {
    if (message.isUser) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Card(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp
                ),
                backgroundColor = VoltColors.Accent,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Text(text = message.content, color = VoltColors.Text, style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(message.timestamp),
                        color = VoltColors.Text.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VoltColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = VoltColors.Text,
                    modifier = Modifier.size(15.dp)
                )
            }
            Card(
                modifier = Modifier.widthIn(max = 270.dp),
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp
                ),
                backgroundColor = VoltColors.ElevatedSurface,
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Text(text = message.content, color = VoltColors.Text, style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(message.timestamp),
                        color = VoltColors.MutedText,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onSelectTool: (String) -> Unit,
    repos: List<File> = emptyList(),
    selectedRepo: String? = null,
    pushing: Boolean = false,
    pushResult: String? = null,
    onRepoMenuClick: () -> Unit = {},
    onSelectRepo: (String) -> Unit = {},
    onDismissRepoMenu: () -> Unit = {},
    repoMenuExpanded: Boolean = false,
    onPush: () -> Unit = {},
    onClearPushResult: () -> Unit = {},
    enabled: Boolean = true
) {
    var toolsExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VoltColors.Surface
    ) {
        Column(
            modifier = Modifier.drawBehind {
                drawLine(
                    color = VoltColors.Divider,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(VoltColors.Input)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onAttach, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Joindre un fichier ou une archive",
                        tint = VoltColors.MutedText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box {
                    IconButton(onClick = onRepoMenuClick, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Dépôts GitHub clonés",
                            tint = VoltColors.MutedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = repoMenuExpanded,
                        onDismissRequest = onDismissRepoMenu
                    ) {
                        Text(
                            text = "Sélectionner un dépôt cloné",
                            color = VoltColors.Accent,
                            style = MaterialTheme.typography.caption,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        if (repos.isEmpty()) {
                            DropdownMenuItem(onClick = onDismissRepoMenu) {
                                Text("Aucun dépôt cloné", color = VoltColors.MutedText)
                            }
                        } else {
                            repos.forEach { repo ->
                                DropdownMenuItem(onClick = { onSelectRepo(repo.absolutePath) }) {
                                    Text(repo.name, color = VoltColors.Text)
                                }
                            }
                        }
                    }
                }

                if (selectedRepo != null) {
                    IconButton(onClick = onPush, enabled = !pushing, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Pousser les modifications (push)",
                            tint = if (pushing) VoltColors.MutedText else VoltColors.AccentBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box {
                    IconButton(onClick = { toolsExpanded = true }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Outils disponibles",
                            tint = VoltColors.MutedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = toolsExpanded,
                        onDismissRequest = { toolsExpanded = false }
                    ) {
                        EXECUTABLE_TOOLS.forEach { (name, template) ->
                            DropdownMenuItem(onClick = {
                                toolsExpanded = false
                                onSelectTool(template)
                            }) {
                                Text(name, color = VoltColors.Text)
                            }
                        }
                    }
                }

                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = VoltColors.Text,
                        fontSize = 14.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(VoltColors.Accent),
                    decorationBox = { innerTextField ->
                        Box {
                            if (text.isEmpty()) {
                                Text(
                                    text = "Écrivez votre demande...",
                                    color = VoltColors.MutedText,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (enabled && text.isNotBlank()) VoltColors.Accent else VoltColors.Divider)
                        .clickable(enabled = enabled && text.isNotBlank(), onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Envoyer",
                        tint = VoltColors.Text,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (selectedRepo != null || pushResult != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedRepo != null) File(selectedRepo).name else "",
                        color = VoltColors.Accent,
                        style = MaterialTheme.typography.caption,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (pushResult != null) {
                        Text(
                            text = pushResult,
                            color = if (pushResult.startsWith("✓")) VoltColors.AccentBright else VoltColors.Error,
                            style = MaterialTheme.typography.caption
                        )
                        TextButton(onClick = onClearPushResult) {
                            Text("✕", color = VoltColors.MutedText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(VoltColors.ElevatedSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
