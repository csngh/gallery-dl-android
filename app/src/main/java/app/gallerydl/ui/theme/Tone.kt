package app.gallerydl.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The fixed set of accent tones for icons. Each tone is a pair: the icon colour and a soft tint for its tile.
 * Tone follows meaning and stays consistent: a neutral count is blue, success green, a warning or a partial
 * result amber, failure red, a pending item violet. Contrast of icon on tint is 6.8 to 8.2.
 */
enum class Tone(val icon: Color, val tint: Color) {
    Blue(Color(0xFF6DB0F8), Color(0xFF14253B)),
    Green(Color(0xFF5FD096), Color(0xFF112A1C)),
    Amber(Color(0xFFF0B45A), Color(0xFF33260C)),
    Red(Color(0xFFFF8A7E), Color(0xFF3A1815)),
    Violet(Color(0xFFC4B5FD), Color(0xFF2A1F45)),
    Teal(Color(0xFF5FCDE0), Color(0xFF0F2D35)),
    Pink(Color(0xFFF59AC4), Color(0xFF3D1830)),
}
