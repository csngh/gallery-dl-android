package app.gallerydl.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.gallerydl.R
import app.gallerydl.download.UrlCheck
import app.gallerydl.ui.theme.ActionColors

/** Edit the link of a failed or cancelled download. Saving checks the link and queues it again. */
@Composable
fun EditLinkDialog(
    state: EditLinkState,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_link_title)) },
        text = {
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.edit_link_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                isError = state.error != null,
                supportingText = state.error?.let { error ->
                    {
                        Text(
                            stringResource(
                                if (error == UrlCheck.UNSUPPORTED) R.string.error_url_unsupported else R.string.error_url_malformed,
                            ),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSave() }),
            )
        },
        confirmButton = {
            // Saving queues the link again, a positive action, so the confirm button is green.
            Button(
                onClick = onSave,
                enabled = state.text.isNotBlank() && !state.busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ActionColors.Positive,
                    contentColor = ActionColors.OnPositive,
                ),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ActionColors.OnPositive)
                } else {
                    Text(stringResource(if (state.notStarted) R.string.action_save else R.string.action_save_retry))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
