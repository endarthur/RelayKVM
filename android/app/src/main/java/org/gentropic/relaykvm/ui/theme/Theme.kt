package org.gentropic.relaykvm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/**
 * RelayKVM color scheme - matches web UI themes
 */
data class RelayKVMColors(
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val textBright: Color,
    val textNormal: Color,
    val textDim: Color,
    val textLabel: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val logBackground: Color,
    val logText: Color,
    val isDark: Boolean
)

// Theme definitions matching web UI
object RelayKVMThemes {
    val Default = RelayKVMColors(
        background = Color(0xFF0D0F12),
        surface = Color(0xFF1A1D21),
        surfaceAlt = Color(0xFF252830),
        textBright = Color(0xFFE8EAED),
        textNormal = Color(0xFF9AA0A6),
        textDim = Color(0xFF5F6368),
        textLabel = Color(0xFF80868B),
        accentPrimary = Color(0xFF00B894),    // Teal
        accentSecondary = Color(0xFFE17055),  // Coral
        logBackground = Color(0xFF0A0C0E),
        logText = Color(0xFF00FF88),
        isDark = true
    )

    val Industrial = RelayKVMColors(
        background = Color(0xFF0D0F12),
        surface = Color(0xFF1A1D21),
        surfaceAlt = Color(0xFF252830),
        textBright = Color(0xFFE8EAED),
        textNormal = Color(0xFF9AA0A6),
        textDim = Color(0xFF5F6368),
        textLabel = Color(0xFF80868B),
        accentPrimary = Color(0xFF00FF66),
        accentSecondary = Color(0xFFFFAA00),
        logBackground = Color(0xFF0A0C0E),
        logText = Color(0xFF00FF66),
        isDark = true
    )

    val Signal = RelayKVMColors(
        background = Color(0xFF0D0F12),
        surface = Color(0xFF1A1D21),
        surfaceAlt = Color(0xFF252830),
        textBright = Color(0xFFE8EAED),
        textNormal = Color(0xFF9AA0A6),
        textDim = Color(0xFF5F6368),
        textLabel = Color(0xFF80868B),
        accentPrimary = Color(0xFF0099FF),
        accentSecondary = Color(0xFFFF3333),
        logBackground = Color(0xFF0A0C0E),
        logText = Color(0xFF0099FF),
        isDark = true
    )

    val Amber = RelayKVMColors(
        background = Color(0xFF0D0F12),
        surface = Color(0xFF1A1D21),
        surfaceAlt = Color(0xFF252830),
        textBright = Color(0xFFE8EAED),
        textNormal = Color(0xFF9AA0A6),
        textDim = Color(0xFF5F6368),
        textLabel = Color(0xFF80868B),
        accentPrimary = Color(0xFFFFAA00),
        accentSecondary = Color(0xFFFF6600),
        logBackground = Color(0xFF0A0C0E),
        logText = Color(0xFFFFAA00),
        isDark = true
    )

    val Koma = RelayKVMColors(
        background = Color(0xFF1A1A1A),
        surface = Color(0xFF252525),
        surfaceAlt = Color(0xFF333333),
        textBright = Color(0xFFE0E0E0),
        textNormal = Color(0xFF999999),
        textDim = Color(0xFF666666),
        textLabel = Color(0xFF808080),
        accentPrimary = Color(0xFFFF6B35),
        accentSecondary = Color(0xFF00FF88),
        logBackground = Color(0xFF0D0D0D),
        logText = Color(0xFFFF6B35),
        isDark = true
    )

    val Light = RelayKVMColors(
        background = Color(0xFFF5F5F5),
        surface = Color(0xFFFFFFFF),
        surfaceAlt = Color(0xFFE8E8E8),
        textBright = Color(0xFF1A1A1A),
        textNormal = Color(0xFF404040),
        textDim = Color(0xFF707070),
        textLabel = Color(0xFF606060),
        accentPrimary = Color(0xFF00A080),
        accentSecondary = Color(0xFFD06040),
        logBackground = Color(0xFFE0E0E0),
        logText = Color(0xFF006644),
        isDark = false
    )

    val CatppuccinMocha = RelayKVMColors(
        background = Color(0xFF1E1E2E),
        surface = Color(0xFF313244),
        surfaceAlt = Color(0xFF45475A),
        textBright = Color(0xFFCDD6F4),
        textNormal = Color(0xFFBAC2DE),
        textDim = Color(0xFF6C7086),
        textLabel = Color(0xFF9399B2),
        accentPrimary = Color(0xFFCBA6F7),
        accentSecondary = Color(0xFFFAB387),
        logBackground = Color(0xFF11111B),
        logText = Color(0xFFA6E3A1),
        isDark = true
    )

    val CatppuccinMacchiato = RelayKVMColors(
        background = Color(0xFF24273A),
        surface = Color(0xFF363A4F),
        surfaceAlt = Color(0xFF494D64),
        textBright = Color(0xFFCAD3F5),
        textNormal = Color(0xFFB8C0E0),
        textDim = Color(0xFF6E738D),
        textLabel = Color(0xFF939AB7),
        accentPrimary = Color(0xFFC6A0F6),
        accentSecondary = Color(0xFFF5A97F),
        logBackground = Color(0xFF181926),
        logText = Color(0xFFA6DA95),
        isDark = true
    )

    val CatppuccinFrappe = RelayKVMColors(
        background = Color(0xFF303446),
        surface = Color(0xFF414559),
        surfaceAlt = Color(0xFF51576D),
        textBright = Color(0xFFC6D0F5),
        textNormal = Color(0xFFB5BFE2),
        textDim = Color(0xFF737994),
        textLabel = Color(0xFF949CBB),
        accentPrimary = Color(0xFFCA9EE6),
        accentSecondary = Color(0xFFEF9F76),
        logBackground = Color(0xFF232634),
        logText = Color(0xFFA6D189),
        isDark = true
    )

    val CatppuccinLatte = RelayKVMColors(
        background = Color(0xFFEFF1F5),
        surface = Color(0xFFE6E9EF),
        surfaceAlt = Color(0xFFDCE0E8),
        textBright = Color(0xFF4C4F69),
        textNormal = Color(0xFF5C5F77),
        textDim = Color(0xFF8C8FA1),
        textLabel = Color(0xFF6C6F85),
        accentPrimary = Color(0xFF8839EF),
        accentSecondary = Color(0xFFFE640B),
        logBackground = Color(0xFFCCD0DA),
        logText = Color(0xFF40A02B),
        isDark = false
    )

    val all = listOf(
        "Default" to Default,
        "Industrial" to Industrial,
        "Signal" to Signal,
        "Amber" to Amber,
        "Koma" to Koma,
        "Light" to Light,
        "Mocha" to CatppuccinMocha,
        "Macchiato" to CatppuccinMacchiato,
        "Frappé" to CatppuccinFrappe,
        "Latte" to CatppuccinLatte
    )

    fun byName(name: String): RelayKVMColors {
        return all.find { it.first == name }?.second ?: Default
    }
}

// CompositionLocal for accessing colors throughout the app
val LocalRelayKVMColors = staticCompositionLocalOf { RelayKVMThemes.Default }

@Composable
fun RelayKVMTheme(
    colors: RelayKVMColors = RelayKVMThemes.Default,
    content: @Composable () -> Unit
) {
    // Create Material3 color scheme from our colors
    val colorScheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accentPrimary,
            secondary = colors.accentSecondary,
            background = colors.background,
            surface = colors.surface,
            onPrimary = if (colors.isDark) Color.Black else Color.White,
            onSecondary = if (colors.isDark) Color.Black else Color.White,
            onBackground = colors.textNormal,
            onSurface = colors.textNormal
        )
    } else {
        lightColorScheme(
            primary = colors.accentPrimary,
            secondary = colors.accentSecondary,
            background = colors.background,
            surface = colors.surface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = colors.textNormal,
            onSurface = colors.textNormal
        )
    }

    CompositionLocalProvider(LocalRelayKVMColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// Extension to easily access our colors
object RelayKVM {
    val colors: RelayKVMColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRelayKVMColors.current
}
