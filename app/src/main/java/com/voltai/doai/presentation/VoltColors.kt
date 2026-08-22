package com.voltai.doai.presentation

import androidx.compose.ui.graphics.Color
import com.voltai.doai.presentation.theme.VoltDarkBackground
import com.voltai.doai.presentation.theme.VoltDarkBorder
import com.voltai.doai.presentation.theme.VoltDarkElevatedSurface
import com.voltai.doai.presentation.theme.VoltDarkInput
import com.voltai.doai.presentation.theme.VoltDarkSurface
import com.voltai.doai.presentation.theme.VoltElectricLime
import com.voltai.doai.presentation.theme.VoltElectricLimeBright
import com.voltai.doai.presentation.theme.VoltError
import com.voltai.doai.presentation.theme.VoltTextPrimary
import com.voltai.doai.presentation.theme.VoltTextSecondary
import com.voltai.doai.presentation.theme.VoltWarning

/**
 * Palette partagée de l'interface. Les noms historiques sont conservés pour
 * éviter de toucher à la logique des écrans et des view models ; leurs valeurs
 * proviennent maintenant du thème Volt fourni.
 */
object VoltColors {
    val Background = VoltDarkBackground
    val Surface = VoltDarkSurface
    val ElevatedSurface = VoltDarkElevatedSurface
    val Input = VoltDarkInput
    val Accent = VoltElectricLime
    val AccentBright = VoltElectricLimeBright
    val Text = VoltTextPrimary
    val MutedText = VoltTextSecondary
    val Divider = VoltDarkBorder
    val Error = VoltError
    val Warning = VoltWarning
    val TerminalBackground = Color(0xFF020302)

    // Variations de l'accent Volt pour garder les onglets lisibles sans
    // réintroduire la palette bleue de l'ancien habillage.
    val TabTools = VoltElectricLimeBright
    val TabFiles = VoltElectricLime
    val TabEditor = Color(0xFFA8DC28)
    val TabSettings = Color(0xFFC8F36A)
    val ClipAccent = Color(0xFFE5FF9C)

    val NavDefault = Color(0xFF2196F3)
    val NavSelected = Color(0xFF4CAF50)
    val NavTerminalChat = Color(0xFFF44336)
}
