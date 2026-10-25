package app.gallerydl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gallerydl.GalleryDlApp
import app.gallerydl.R
import app.gallerydl.ui.theme.Spacing

/** Shown on Home and Queue while the storage is 99% full and downloads are paused for that reason. */
@Composable
fun StorageWarningBanner(modifier: Modifier = Modifier) {
    val monitor = (LocalContext.current.applicationContext as GalleryDlApp).container.storageMonitor
    val full by monitor.full.collectAsStateWithLifecycle()
    val percent by monitor.usedPercent.collectAsStateWithLifecycle()
    // Looked at again whenever the screen opens, so the warning is right even if the periodic check has not run yet.
    LaunchedEffect(Unit) { monitor.refresh() }
    if (!full) return
    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.Xs),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(Spacing.Md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.storage_warning, percent), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
