package app.gallerydl.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.gallerydl.R
import app.gallerydl.ui.theme.ActionColors
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link

/**
 * The less frequent actions for a link, kept behind one three-dot button: View full link, Edit (when offered), Retry (green)
 * and Remove (red).
 * Remove is only a request; the caller shows the confirmation dialog.
 */
@Composable
fun LinkActionsMenu(
    showRetry: Boolean,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onViewLink: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onViewLink != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_view_link)) },
                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onViewLink()
                    },
                )
            }
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onEdit()
                    },
                )
            }
            if (showRetry) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_retry)) },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = ActionColors.Positive,
                        leadingIconColor = ActionColors.Positive,
                    ),
                    onClick = {
                        expanded = false
                        onRetry()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_remove)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                colors = MenuDefaults.itemColors(
                    textColor = ActionColors.Negative,
                    leadingIconColor = ActionColors.Negative,
                ),
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}
