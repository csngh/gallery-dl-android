package app.gallerydl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Brand palette matching the launcher icon: deep navy surfaces with teal and violet accents. */
private val BrandDarkColors = darkColorScheme(
    primary = Color(0xFF2EE6C9),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFF8FF5E0),
    secondary = Color(0xFFB9C3FF),
    onSecondary = Color(0xFF1B2678),
    secondaryContainer = Color(0xFF2E3A8C),
    onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF4A2800),
    error = ActionColors.Negative,
    onError = ActionColors.OnNegative,
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE3E6F3),
    surface = Color(0xFF0B1020),
    onSurface = Color(0xFFE3E6F3),
    surfaceVariant = Color(0xFF1F2742),
    onSurfaceVariant = Color(0xFFA9B0C9),
    outline = Color(0xFF5A627F),
    outlineVariant = Color(0xFF2B3354),
    surfaceContainerLowest = Color(0xFF070B17),
    surfaceContainerLow = Color(0xFF10162A),
    surfaceContainer = Color(0xFF151B30),
    surfaceContainerHigh = Color(0xFF1B2238),
    surfaceContainerHighest = Color(0xFF232B45),
)

/** The app is dark-only by design; the system light/dark setting is deliberately ignored. */
@Composable
fun GalleryDlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BrandDarkColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
