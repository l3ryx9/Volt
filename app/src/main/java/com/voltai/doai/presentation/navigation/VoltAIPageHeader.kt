package com.voltai.doai.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voltai.doai.presentation.VoltColors
import com.voltai.doai.presentation.VoltLogo

/**
 * En-tête léger des écrans secondaires (Fichiers, Outils, Éditeur,
 * Paramètres) : logo « Volt » centré + titre de la page. La navigation
 * entre écrans se fait désormais via la barre basse (VoltAIBottomNav),
 * cet en-tête n'a donc plus besoin de menu déroulant ni de raccourci
 * réglages.
 */
@Composable
fun VoltAIPageHeader(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = VoltColors.Surface,
        elevation = 0.dp
    ) {
        Row(
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            VoltLogo()
            Text(
                text = "  ·  $title",
                color = VoltColors.Text,
                style = androidx.compose.material.MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
