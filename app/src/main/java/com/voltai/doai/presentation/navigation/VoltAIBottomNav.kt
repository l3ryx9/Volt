package com.voltai.doai.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voltai.doai.presentation.VoltColors

/**
 * Barre de navigation basse — reprend la maquette voltai-v4 : Outils,
 * Fichiers, un onglet central qui bascule Chat ↔ Terminal (uniquement
 * significatif sur l'écran Chat, il y ramène sinon), Éditeur, Réglages.
 */
@Composable
fun VoltAIBottomNav(
    currentRoute: String?,
    isTerminalMode: Boolean,
    onNavigate: (String) -> Unit,
    onToggleTerminal: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(VoltColors.Surface)
            .drawBehind {
                drawLine(
                    color = VoltColors.Divider,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTab(
            label = "Outils",
            icon = Icons.Default.Build,
            color = VoltColors.TabTools,
            selected = currentRoute == Screen.Tools.route,
            onClick = { onNavigate(Screen.Tools.route) }
        )
        NavTab(
            label = "Fichiers",
            icon = Icons.Default.Folder,
            color = VoltColors.TabFiles,
            selected = currentRoute == Screen.Files.route,
            onClick = { onNavigate(Screen.Files.route) }
        )
        MainNavTab(
            isChatRoute = currentRoute == Screen.Chat.route,
            isTerminalMode = isTerminalMode,
            onClick = {
                if (currentRoute != Screen.Chat.route) {
                    onNavigate(Screen.Chat.route)
                } else {
                    onToggleTerminal()
                }
            }
        )
        NavTab(
            label = "Éditeur",
            icon = Icons.Default.Edit,
            color = VoltColors.TabEditor,
            selected = currentRoute == Screen.Editor.route,
            onClick = { onNavigate(Screen.Editor.route) }
        )
        NavTab(
            label = "Réglages",
            icon = Icons.Default.Settings,
            color = VoltColors.TabSettings,
            selected = currentRoute == Screen.Settings.route,
            onClick = { onNavigate(Screen.Settings.route) }
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val tint = if (selected) color else VoltColors.MutedText
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.height(22.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MainNavTab(
    isChatRoute: Boolean,
    isTerminalMode: Boolean,
    onClick: () -> Unit
) {
    val showTerminalIcon = !isChatRoute || !isTerminalMode
    val icon = if (showTerminalIcon) Icons.Default.Terminal else Icons.Default.Chat
    val label = if (showTerminalIcon) "Terminal" else "Chat"
    val tint = if (isChatRoute && isTerminalMode) VoltColors.Text else VoltColors.AccentBright

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.height(24.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
    }
}
