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
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
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
            selected = currentRoute == Screen.Tools.route,
            onClick = { onNavigate(Screen.Tools.route) }
        )
        NavTab(
            label = "Fichiers",
            icon = Icons.Default.Folder,
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
            label = "Reglages",
            icon = Icons.Default.Settings,
            selected = currentRoute == Screen.Settings.route,
            onClick = { onNavigate(Screen.Settings.route) }
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        val tint = if (selected) VoltColors.NavSelected else VoltColors.NavDefault
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
    val tint = when {
        isChatRoute && !isTerminalMode -> VoltColors.NavTerminalChat
        isChatRoute && isTerminalMode -> VoltColors.NavSelected
        else -> VoltColors.NavDefault
    }

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
