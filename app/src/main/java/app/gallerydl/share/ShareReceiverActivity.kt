package app.gallerydl.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gallerydl.R
import app.gallerydl.download.UrlCheck
import app.gallerydl.ui.AppViewModelProvider
import app.gallerydl.ui.components.TileSize
import app.gallerydl.ui.home.SiteTile
import app.gallerydl.ui.home.displayName
import app.gallerydl.ui.theme.ActionColors
import app.gallerydl.ui.theme.GalleryDlTheme
import app.gallerydl.ui.theme.Spacing

/**
 * The popup shown when a link is shared to the app from a browser. It floats over the page, so the user never
 * leaves it: Add queues the link and closes, Cancel just closes.
 */
class ShareReceiverActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND) {
            viewModel.start(intent.getStringExtra(Intent.EXTRA_TEXT))
        } else {
            finish()
            return
        }
        val addedMessage = getString(R.string.share_added)
        setContent {
            GalleryDlTheme {
                SharePopup(
                    viewModel = viewModel,
                    onClose = { finish() },
                    onAdd = {
                        viewModel.add {
                            Toast.makeText(this, addedMessage, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SharePopup(viewModel: ShareViewModel, onClose: () -> Unit, onAdd: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.share_title)) },
        text = { SharePopupBody(state) },
        confirmButton = {
            if (state is ShareState.Ready) {
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActionColors.Positive,
                        contentColor = ActionColors.OnPositive,
                    ),
                ) { Text(stringResource(R.string.action_add_queue)) }
            } else if (state !is ShareState.Checking) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (state is ShareState.Ready || state is ShareState.Checking) {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

@Composable
private fun SharePopupBody(state: ShareState) {
    when (state) {
        ShareState.Checking -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.share_checking))
        }
        ShareState.NoLink -> Text(stringResource(R.string.share_no_link))
        is ShareState.Rejected -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
            LinkLine(state.url)
            Text(
                stringResource(
                    if (state.reason == UrlCheck.UNSUPPORTED) R.string.error_url_unsupported else R.string.error_url_malformed,
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        is ShareState.Ready -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.Md)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Md)) {
                SiteTile(state.site, null, size = TileSize.Large)
                Text(state.site.displayName(), style = MaterialTheme.typography.titleSmall)
            }
            LinkLine(state.url)
        }
    }
}

@Composable
private fun LinkLine(url: String) {
    Text(
        url,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}
