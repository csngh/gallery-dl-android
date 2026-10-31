package app.gallerydl.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The full-screen viewer, handed to the app's root so it covers everything, system bars included. While it is set the
 * app's own navigation bar and the system bars step aside.
 */
object ViewerState {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    val open get() = content != null
}
