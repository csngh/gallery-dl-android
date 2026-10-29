package app.gallerydl.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.gallerydl.R
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gallerydl.ui.theme.ActionColors
import app.gallerydl.ui.theme.Spacing

/** An action on downloads that needs the user's confirmation first. */
sealed interface PendingAction {
    data object ClearFinished : PendingAction
    data class Remove(val id: Long) : PendingAction
    data class CancelDownload(val id: Long) : PendingAction
}

/** Shows the confirmation dialog for [action], if there is one, and runs the matching handler on confirm. */
@Composable
fun PendingActionDialog(
    action: PendingAction?,
    onClearFinished: () -> Unit,
    onRemove: (Long) -> Unit,
    onCancelDownload: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    when (action) {
        PendingAction.ClearFinished -> ConfirmDialog(
            title = stringResource(R.string.clear_queue_title),
            message = stringResource(R.string.clear_queue_message),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                onClearFinished()
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        is PendingAction.Remove -> ConfirmDialog(
            title = stringResource(R.string.remove_link_title),
            message = stringResource(R.string.remove_link_message),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                onRemove(action.id)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        is PendingAction.CancelDownload -> ConfirmDialog(
            title = stringResource(R.string.cancel_download_title),
            message = stringResource(R.string.cancel_download_message),
            confirmLabel = stringResource(R.string.action_cancel_download),
            dismissLabel = stringResource(R.string.action_keep_downloading),
            onConfirm = {
                onCancelDownload(action.id)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        null -> Unit
    }
}

/** Starts one link that is waiting. Starting is a positive action, so it is green. */
@Composable
fun StartButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.Md, vertical = Spacing.Xs),
        colors = ButtonDefaults.buttonColors(
            containerColor = ActionColors.Positive,
            contentColor = ActionColors.OnPositive,
        ),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.action_start), modifier = Modifier.padding(start = Spacing.Xs))
    }
}

/** Stops every download at once, the running one and the ones waiting; shown only while at least one is active. */
@Composable
fun StopAllButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = ActionColors.Negative,
            contentColor = ActionColors.OnNegative,
        ),
    ) {
        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.action_stop_all, count), modifier = Modifier.padding(start = Spacing.Sm))
    }
}

/** Starts every link that is waiting, shown only while at least one is. */
@Composable
fun StartAllButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = ActionColors.Positive,
            contentColor = ActionColors.OnPositive,
        ),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.action_start_all, count), modifier = Modifier.padding(start = Spacing.Sm))
    }
}
