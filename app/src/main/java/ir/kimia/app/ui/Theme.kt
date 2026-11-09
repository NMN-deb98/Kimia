package ir.kimia.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import ir.kimia.app.R

val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_bold, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.SemiBold),
)

private val Gold = Color(0xFFE0B25C)
private val Teal = Color(0xFF1F6F6A)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF2A1F05),
    primaryContainer = Color(0xFF3A2F12),
    onPrimaryContainer = Color(0xFFF6E3B4),
    secondary = Color(0xFF7CC6BF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE8E6E1),
    surface = Color(0xFF171A21),
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = Color(0xFF232731),
    onSurfaceVariant = Color(0xFFB9B7B0),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE9E6),
    onPrimaryContainer = Color(0xFF0B2E2B),
    secondary = Color(0xFF8A6A1E),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1B1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C1E),
    surfaceVariant = Color(0xFFEAE6DC),
    onSurfaceVariant = Color(0xFF55524A),
    error = Color(0xFFB3261E),
)

private fun Typography.withFont(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

/** Persian-first theme: Vazirmatn font, forced right-to-left layout, light/dark follows the system. */
@Composable
fun KimiaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography().withFont(Vazir),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
