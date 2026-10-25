package app.gallerydl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gallerydl.R
import app.gallerydl.data.PreviewStripCache
import app.gallerydl.ui.theme.Spacing

/** A slim bar that shows while preview clips are being made: "Preparing previews, 12 of 40". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreviewProgressBanner(cache: PreviewStripCache) {
    val progress by cache.progress.collectAsStateWithLifecycle()
    if (progress.total <= 0) return
    val done = progress.done.coerceAtMost(progress.total)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.Md, vertical = Spacing.Xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
    ) {
        // Side by side when there is room; in a narrow pane (the folder list of a wide screen) the count drops below the
        // title instead of both being squeezed into single letters per line.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
        ) {
            Text(stringResource(R.string.previews_preparing), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false)
            Text(stringResource(R.string.previews_progress, done, progress.total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false)
        }
        LinearProgressIndicator(progress = { done.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
    }
}
