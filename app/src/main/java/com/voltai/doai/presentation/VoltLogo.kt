package com.voltai.doai.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Logo "Volt" — texte serif gras avec un empilement de calques légèrement
 * décalés vers le bas pour simuler le relief/text-shadow de la maquette
 * voltai-v4 (Playfair Display + text-shadow multicouche).
 */
@Composable
fun VoltLogo(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Text(
            text = "Volt",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            letterSpacing = 1.sp,
            color = VoltColors.Background.copy(alpha = 0.85f),
            modifier = Modifier.offsetLogo(0.dp, 6.dp)
        )
        Text(
            text = "Volt",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            letterSpacing = 1.sp,
            color = VoltColors.Divider.copy(alpha = 0.85f),
            modifier = Modifier.offsetLogo(0.dp, 3.dp)
        )
        Text(
            text = "Volt",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            letterSpacing = 1.sp,
            color = VoltColors.MutedText.copy(alpha = 0.35f),
            modifier = Modifier.offsetLogo(0.dp, 1.5.dp)
        )
        Text(
            text = "Volt",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            letterSpacing = 1.sp,
            color = VoltColors.Text,
            modifier = Modifier.offsetLogo(0.dp, 0.dp)
        )
    }
}

private fun Modifier.offsetLogo(x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp): Modifier =
    this.offset(x = x, y = y)
