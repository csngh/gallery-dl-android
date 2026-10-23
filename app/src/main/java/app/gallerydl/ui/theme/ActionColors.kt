package app.gallerydl.ui.theme

import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours for opposing actions: green for the positive or start action, red for the negative or
 * end action. These are the lighter Material 400 tones with dark text, because the deep 700 and 800 tones
 * sink into a dark background.
 */
object ActionColors {
    val Positive = Color(0xFF66BB6A)
    val OnPositive = Color(0xFF0B2A10)
    val Negative = Color(0xFFEF5350)
    val OnNegative = Color(0xFF2B0606)

    @Composable
    fun positiveIcon(): IconButtonColors = iconColors(Positive)

    @Composable
    fun negativeIcon(): IconButtonColors = iconColors(Negative)

    /** An unavailable action keeps the muted disabled style instead of its semantic colour. */
    @Composable
    private fun iconColors(content: Color) = IconButtonDefaults.iconButtonColors(
        contentColor = content,
        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )
}
