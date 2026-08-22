package com.voltai.doai.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voltai.doai.presentation.VoltColors

/**
 * Onglet « Éditeur » de la barre de navigation basse. La maquette voltai-v4
 * prévoit cet onglet mais aucun éditeur de code n'existe encore dans ce
 * projet — cet écran est un point d'ancrage volontairement simple, à
 * remplacer par un véritable éditeur de fichiers plus tard.
 */
@Composable
fun EditorScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoltColors.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Éditeur de code — bientôt disponible",
            color = VoltColors.MutedText,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
    }
}
