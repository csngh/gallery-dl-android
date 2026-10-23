package app.gallerydl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.gallerydl.R

/**
 * Inter (SIL Open Font License, see docs/licenses/Inter-OFL.txt), bundled as one variable font so every
 * weight comes from a single file.
 */
@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            resId = R.font.inter_variable,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private val base = Typography()

private fun TextStyle.inter() = copy(fontFamily = Inter)

/**
 * Clear hierarchy: big and bold for headings, comfortable and readable for body text, smaller and
 * quieter for secondary labels.
 */
val AppTypography = Typography(
    displayLarge = base.displayLarge.inter(),
    displayMedium = base.displayMedium.inter(),
    displaySmall = base.displaySmall.inter(),
    headlineLarge = base.headlineLarge.inter(),
    headlineMedium = base.headlineMedium.inter(),
    headlineSmall = base.headlineSmall.inter().copy(fontWeight = FontWeight.SemiBold),
    // Screen titles
    titleLarge = base.titleLarge.inter().copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    // Section and empty-state headings
    titleMedium = base.titleMedium.inter().copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.inter().copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.inter().copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.inter().copy(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = base.bodySmall.inter().copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.inter().copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.inter().copy(fontWeight = FontWeight.Medium),
)
