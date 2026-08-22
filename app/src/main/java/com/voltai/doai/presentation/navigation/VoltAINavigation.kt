package com.voltai.doai.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import com.voltai.doai.presentation.editor.EditorScreen
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

    // L'installation des dépendances (bootstrap + Ubuntu + bundle) n'est plus
    // déclenchée automatiquement au lancement. Elle est proposée via une
    // fenêtre (ToolchainInstallPromptDialog ci-dessous) et ne démarre que
    // lorsque l'utilisateur appuie sur le bouton « Installer ».
    //
    // Remarque : l'ancienne version utilisait LaunchedEffect(toolchainStatus.phase),
    // c'est-à-dire que la clé de l'effet changeait à chaque mise à jour de
    // statut (IDLE → RUNNING → …). Comme Compose annule et relance
    // LaunchedEffect dès que sa clé change, l'installation en cours était
    // interrompue dès qu'elle passait à RUNNING, ce qui provoquait la boucle
    // RUNNING → annulation → FAILED → relance → RUNNING… visible comme un
    // scintillement de la barre de progression qui n'avançait jamais.

    Scaffold(
        modifier = Modifier.background(VoltColors.Background),
        contentWindowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top),
        topBar = {
            when (currentRoute) {
                Screen.Chat.route, Screen.QwenConnect.route, null -> Unit
                Screen.Files.route -> VoltAIPageHeader(title = "Fichiers")
                Screen.Tools.route -> VoltAIPageHeader(title = "Outils")
                Screen.Editor.route -> VoltAIPageHeader(title = "Éditeur")
                Screen.Settings.route -> VoltAIPageHeader(title = "Paramètres")
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
            composable(Screen.Editor.route) { EditorScreen() }
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

        if (toolchainStatus.phase == ToolchainPhase.IDLE &&
            !ToolchainManager.isInstalled(context)
        ) {
            ToolchainInstallPromptDialog(
                onInstall = {
                    scope.launch { ToolchainManager.ensureTools(context) }
                }
            )
        }

        if (toolchainStatus.phase == ToolchainPhase.RUNNING) {
            ToolchainProgressDialog(status = toolchainStatus)
        }

        // Barre de progression visible pendant l'auto-réparation déclenchée
        // par une erreur de commande ou par le bouton « Réparer ».
        if (repairStatus.phase == ToolchainPhase.RUNNING) {
            ToolchainRepairDialog(status = repairStatus)
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
private fun ToolchainInstallPromptDialog(onInstall: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "Dépendances requises",
                color = VoltColors.Text
            )
        },
        text = {
            Text(
                text = "Installer les dépendances nécessaires",
                color = VoltColors.MutedText
            )
        },
        confirmButton = {
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = VoltColors.Accent,
                    contentColor = VoltColors.Text
                )
            ) {
                Text("Installer")
            }
        },
        backgroundColor = VoltColors.Surface
    )
}

@Composable
private fun ToolchainFailedDialog(message: String, onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
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
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = VoltColors.Accent,
                    contentColor = VoltColors.Text
                )
            ) {
                Text("Réessayer")
            }
        },
        backgroundColor = VoltColors.Surface
    )
}

@Composable
private fun ToolchainRepairDialog(status: com.voltai.doai.data.tools.ToolchainStatus) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Réparation de l'environnement", color = VoltColors.Text) },
        text = {
            Column {
                val progress = status.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                        color = VoltColors.Accent,
                        backgroundColor = VoltColors.Divider
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = status.message ?: "Réparation en cours…",
                        color = VoltColors.Accent,
                        style = MaterialTheme.typography.caption
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = VoltColors.Accent,
                        backgroundColor = VoltColors.Divider
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Réparation en cours…",
                        color = VoltColors.Text,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        },
        confirmButton = {},
        backgroundColor = VoltColors.Surface
    )
}

@Composable
private fun ToolchainProgressDialog(status: com.voltai.doai.data.tools.ToolchainStatus) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("téléchargement en cours", color = VoltColors.Text) },
        text = {
            Column {
                val progress = status.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                        color = VoltColors.Accent,
                        backgroundColor = VoltColors.Divider
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "téléchargement en cours",
                        color = VoltColors.Accent,
                        style = MaterialTheme.typography.caption
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = VoltColors.Accent,
                        backgroundColor = VoltColors.Divider
                    )
                }
                if (progress == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "téléchargement en cours",
                        color = VoltColors.Text,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        },
        confirmButton = {},
        backgroundColor = VoltColors.Surface
    )
}

private fun navigateTo(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId)
        launchSingleTop = true
    }
}
