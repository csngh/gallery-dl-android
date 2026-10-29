package app.gallerydl.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gallerydl.R
import app.gallerydl.data.DownloadTask
import app.gallerydl.download.FolderChoice
import app.gallerydl.download.cleanFolderName
import app.gallerydl.ui.theme.Spacing

/**
 * Asked before a link starts for the first time: which library folder its files go in. The name gallery-dl would use is
 * looked up and filled in; keeping it leaves the folder to gallery-dl, typing another name pins the link to that folder.
 * A name another link already uses gets a warning, and can still be chosen.
 */
@Composable
fun StartFolderDialog(
    task: DownloadTask,
    choice: FolderChoice,
    onStart: (folderName: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // null while the name is being looked up.
    val suggested by produceState<String?>(null, task.url) { value = choice.suggest(task.url) }
    var typed by remember { mutableStateOf<String?>(null) }
    val shown = typed ?: suggested.orEmpty()
    val others by produceState(emptyList<String>(), shown) {
        value = emptyList()
        if (shown.isNotBlank()) {
            kotlinx.coroutines.delay(250)
            value = choice.linksUsing(shown, task.id)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.start_folder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                Text(task.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.start_folder_message), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = shown,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.start_folder_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier,
                )
                when {
                    suggested == null && typed == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.start_folder_looking), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    suggested.isNullOrBlank() && typed.isNullOrBlank() ->
                        Text(stringResource(R.string.start_folder_unknown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (others.isNotEmpty()) {
                    Text(stringResource(R.string.start_folder_exists), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    others.take(2).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (others.size > 2) {
                        Text(stringResource(R.string.start_folder_more, others.size - 2), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val chosen = cleanFolderName(shown)
                // The name gallery-dl suggested (or none) is not pinned: the link keeps following gallery-dl.
                onStart(chosen?.takeIf { !it.equals(suggested?.let(::cleanFolderName), ignoreCase = false) })
            }) { Text(stringResource(R.string.action_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
