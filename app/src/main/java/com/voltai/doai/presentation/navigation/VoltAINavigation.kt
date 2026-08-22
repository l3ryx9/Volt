package com.voltai.doai.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.voltai.doai.data.tools.ToolchainManager
import com.voltai.doai.data.tools.ToolchainPhase
import com.voltai.doai.presentation.chat.ChatScreen
import com.voltai.doai.presentation.connect.QwenConnectScreen
import com.voltai.doai.presentation.files.FilesScreen
import com.voltai.doai.presentation.tools.ToolsScreen
import com.voltai.doai.presentation.settings.SettingsScreen
import com.voltai.doai.presentation.VoltColors
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Chat : Screen("chat")
    object Files : Screen("files")
    object Tools : Screen("tools")
    object Editor : Screen("editor")
    object Settings : Screen("settings")
    object QwenConnect : Screen("qwen-connect")
}

@Composable
fun VoltAINavigation(storageAccessGranted: Boolean) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toolchainStatus by ToolchainManager.status.collectAsState()
    val repairStatus by com.voltai.doai.data.tools.AutoFixer.repairStatus.collectAsState()
    var isTerminalMode by rememberSaveable { mutableStateOf(false) }
    // Exécutions dont la fenêtre de progression a été fermée par
    // l'utilisateur : le travail continue en arrière-plan, seule la
    // fenêtre est masquée (identifiée par son runId).
    var dismissedToolchainRun by rememberSaveable { mutableStateOf(0L) }
    var dismissedRepairRun by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        if (!ToolchainManager.isInstalled(context)) {
            ToolchainManager.ensureTools(context)
        }
    }

    Scaffold(
        modifier = Modifier.background(VoltColors.Background),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            when (currentRoute) {
                Screen.Chat.route, Screen.QwenConnect.route, null -> Unit
                Screen.Files.route -> VoltAIPageHeader()
                Screen.Tools.route -> VoltAIPageHeader()
                Screen.Settings.route -> VoltAIPageHeader()
                else -> Unit
            }
        },
        bottomBar = {
            if (currentRoute != Screen.QwenConnect.route && currentRoute != null) {
                VoltAIBottomNav(
                    currentRoute = currentRoute,
                    isTerminalMode = isTerminalMode,
                    onNavigate = { route -> navigateTo(navController, route) },
                    onToggleTerminal = { isTerminalMode = !isTerminalMode }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(
                    isTerminalMode = isTerminalMode,
                    onToggleTerminal = { isTerminalMode = !isTerminalMode },
                    onNavigate = { route -> navigateTo(navController, route) },
                    onOpenSettings = { navigateTo(navController, Screen.Settings.route) }
                )
            }
            composable(Screen.Files.route) { FilesScreen() }
            composable(Screen.Tools.route) { ToolsScreen() }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onOpenQwenConnect = { navigateTo(navController, Screen.QwenConnect.route) }
                )
            }
            composable(Screen.QwenConnect.route) {
                QwenConnectScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        if (toolchainStatus.phase == ToolchainPhase.RUNNING &&
            toolchainStatus.runId != dismissedToolchainRun
        ) {
            ToolchainProgressDialog(
                status = toolchainStatus,
                onMinimize = { dismissedToolchainRun = toolchainStatus.runId }
            )
        }

        // Barre de progression visible pendant l'auto-réparation déclenchée
        // par une erreur de commande ou par le bouton « Réparer ». Fermable :
        // la réparation continue en arrière-plan.
        if (repairStatus.phase == ToolchainPhase.RUNNING &&
            repairStatus.runId != dismissedRepairRun
        ) {
            ToolchainRepairDialog(
                status = repairStatus,
                onMinimize = { dismissedRepairRun = repairStatus.runId }
            )
        }

        if (toolchainStatus.phase == ToolchainPhase.FAILED &&
            !ToolchainManager.isInstalled(context)
        ) {
            ToolchainFailedDialog(
                message = toolchainStatus.message ?: "L'installation a échoué",
                onRetry = {
                    scope.launch { ToolchainManager.ensureTools(context) }
                }
            )
        }
    }
}

@Composable
private fun ToolchainFailedDialog(message: String, onRetry: () -> Unit) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (dismissed) return
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = {
            Text(
                text = "Installation incomplète",
                color = VoltColors.Text
            )
        },
        text = {
            Text(
                text = message + "\n\nVérifiez la connexion réseau puis réessayez.",
                color = VoltColors.MutedText
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    dismissed = true
                    onRetry()
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = VoltColors.Accent,
                    contentColor = VoltColors.Text
                )
            ) {
                Text("Réessayer")
            }
        },
        dismissButton = {
            TextButton(onClick = { dismissed = true }) {
                Text("Fermer", color = VoltColors.MutedText)
            }
        },
        backgroundColor = VoltColors.Surface
    )
}

@Composable
private fun ToolchainRepairDialog(
    status: com.voltai.doai.data.tools.ToolchainStatus,
    onMinimize: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onMinimize,
        title = { Text("Réparation de l'environnement", color = VoltColors.Text) },
        text = {
            Column {
                val progress = status.progress
                LinearProgressIndicator(
                    progress = (progress ?: 0f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = VoltColors.Accent,
                    backgroundColor = VoltColors.Divider
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = status.message ?: "Réparation en cours…",
                    color = if (progress != null) VoltColors.Accent else VoltColors.Text,
                    style = MaterialTheme.typography.caption
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMinimize) {
                Text("Laisser en arrière-plan", color = VoltColors.Accent)
            }
        },
        backgroundColor = VoltColors.Surface
    )
}

@Composable
private fun ToolchainProgressDialog(
    status: com.voltai.doai.data.tools.ToolchainStatus,
    onMinimize: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onMinimize,
        title = { Text("Installation en arrière-plan", color = VoltColors.Text) },
        text = {
            Column {
                val progress = status.progress
                LinearProgressIndicator(
                    progress = (progress ?: 0f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth(),
                    color = VoltColors.Accent,
                    backgroundColor = VoltColors.Divider
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = status.message ?: "Téléchargement en cours…",
                    color = VoltColors.Accent,
                    style = MaterialTheme.typography.caption
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMinimize) {
                Text("Laisser en arrière-plan", color = VoltColors.Accent)
            }
        },
        backgroundColor = VoltColors.Surface
    )
}

private fun navigateTo(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId)
        launchSingleTop = true
    }
}
