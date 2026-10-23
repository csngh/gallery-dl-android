package app.gallerydl.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.gallerydl.R
import app.gallerydl.download.UrlCheck
import app.gallerydl.ui.theme.ActionColors

/** The field where one link is pasted and added. */
@Composable
fun LinkField(
    state: UrlInputState,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = state.text,
        onValueChange = onTextChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.url_label)) },
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
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        trailingIcon = {
            if (state.validating) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(
                    onClick = onSubmit,
                    enabled = state.text.isNotBlank(),
                    colors = ActionColors.positiveIcon(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
                }
            }
        },
    )
}

