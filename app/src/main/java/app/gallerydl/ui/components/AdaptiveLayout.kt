package app.gallerydl.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Windows at least this wide (dp) use a side navigation rail instead of the bottom bar. */
val RailMinWidth = 600.dp

/** The Library shows folders and files side by side when its own area is at least this wide (dp). */
val TwoPaneMinWidth = 720.dp

/** Text-heavy content stays at a comfortable reading width on large screens. */
val ReadableWidth = 560.dp

/** Fills the screen but keeps the content to a readable width, centred. */
fun Modifier.readableWidth(max: Dp = ReadableWidth): Modifier =
    fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max).fillMaxWidth()
