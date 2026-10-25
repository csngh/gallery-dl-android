package app.gallerydl.ui.settings

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.gallerydl.R
import app.gallerydl.download.DurationInput
import app.gallerydl.download.NumberInput
import app.gallerydl.download.RETRIES_RANGE
import app.gallerydl.download.TIMEOUT_RANGE
import app.gallerydl.ui.AppViewModelProvider
import app.gallerydl.ui.components.ConfirmDialog
import app.gallerydl.ui.components.ScreenTitle
import app.gallerydl.ui.components.ShimmerCardList
import app.gallerydl.ui.components.readableWidth
import app.gallerydl.ui.components.softScrollEdges
import app.gallerydl.ui.theme.ActionColors
import app.gallerydl.ui.theme.Spacing

/** Settings actions that ask for confirmation first. */
private enum class SettingsConfirm { RemoveCookies, ClearHistory, ResetAll, ClearThumbnails }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val options by viewModel.options.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val cookiesCount by viewModel.cookiesCount.collectAsStateWithLifecycle()
    val startupSound by viewModel.startupSound.collectAsStateWithLifecycle()
    val livePreviews by viewModel.livePreviews.collectAsStateWithLifecycle()
    val cookieError by viewModel.cookieError.collectAsStateWithLifecycle()
    val recordsBytes by viewModel.recordsBytes.collectAsStateWithLifecycle()
    val thumbnailBytes by viewModel.thumbnailBytes.collectAsStateWithLifecycle()
    val about by viewModel.about.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var confirm by remember { mutableStateOf<SettingsConfirm?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setDestination(uri)
    }
    val cookiePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importCookies(uri)
    }

    Column(Modifier.readableWidth()) {
        TopAppBar(
            title = { ScreenTitle(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
        )
        val current = options
        if (current == null) {
            ShimmerCardList(count = 2, cardHeight = Spacing.Xxl * 3)
            return@Column
        }
        Column(
            Modifier.softScrollEdges(scroll).verticalScroll(scroll).padding(Spacing.Md),
            verticalArrangement = Arrangement.spacedBy(Spacing.Lg),
        ) {
            Section(R.string.settings_appearance_title, R.string.settings_appearance_body) {
                SwitchRow(
                    R.string.option_startup_sound_title,
                    R.string.option_startup_sound_body,
                    startupSound,
                    viewModel::setStartupSound,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    R.string.option_live_previews_title,
                    R.string.option_live_previews_body,
                    livePreviews,
                    viewModel::setLivePreviews,
                )
            }

            Section(R.string.settings_location_title, R.string.settings_location_body) {
                Text(
                    destination?.name ?: stringResource(R.string.download_location_default),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.download_location_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    OutlinedButton(onClick = { folderPicker.launch(null) }) {
                        Text(stringResource(R.string.action_choose_folder))
                    }
                    if (destination != null) {
                        TextButton(onClick = viewModel::resetDestination) { Text(stringResource(R.string.action_use_default)) }
                    }
                }
            }

            Section(R.string.settings_connection_title, R.string.settings_connection_body) {
                SwitchRow(R.string.option_wifi_title, R.string.option_wifi_body, current.wifiOnly, viewModel::setWifiOnly)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ValueOption(
                    title = R.string.option_retries_title,
                    description = R.string.option_retries_body,
                    enabled = current.retriesEnabled,
                    value = current.retries,
                    label = R.string.option_retries_label,
                    error = R.string.error_retries,
                    isValid = { NumberInput.parseInt(it, RETRIES_RANGE) != null },
                    onEnabledChange = viewModel::setRetriesEnabled,
                    onValueChange = viewModel::setRetries,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ValueOption(
                    title = R.string.option_timeout_title,
                    description = R.string.option_timeout_body,
                    enabled = current.timeoutEnabled,
                    value = current.timeout,
                    label = R.string.option_seconds_label,
                    error = R.string.error_timeout,
                    isValid = { NumberInput.parseSeconds(it, TIMEOUT_RANGE) != null },
                    onEnabledChange = viewModel::setTimeoutEnabled,
                    onValueChange = viewModel::setTimeout,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(R.string.option_skip_title, R.string.option_skip_body, current.skipExisting, viewModel::setSkipExisting)
            }

            Section(R.string.settings_pacing_title, R.string.settings_pacing_body) {
                ValueOption(
                    title = R.string.option_sleep_request_title,
                    description = R.string.option_sleep_request_body,
                    enabled = current.sleepRequestEnabled,
                    value = current.sleepRequest,
                    label = R.string.option_seconds_label,
                    error = R.string.error_duration,
                    isValid = { DurationInput.parse(it) != null },
                    onEnabledChange = viewModel::setSleepRequestEnabled,
                    onValueChange = viewModel::setSleepRequest,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ValueOption(
                    title = R.string.option_sleep_title,
                    description = R.string.option_sleep_body,
                    enabled = current.sleepEnabled,
                    value = current.sleep,
                    label = R.string.option_seconds_label,
                    error = R.string.error_duration,
                    isValid = { DurationInput.parse(it) != null },
                    onEnabledChange = viewModel::setSleepEnabled,
                    onValueChange = viewModel::setSleep,
                )
            }

            Section(R.string.settings_sites_title, R.string.settings_sites_body) {
                SwitchRow(R.string.option_api_rest_title, R.string.option_api_rest_body, current.apiRest, viewModel::setApiRest)
            }

            Section(R.string.settings_accounts_title, R.string.settings_accounts_body) {
                val imported = cookiesCount > 0
                SwitchRow(
                    title = R.string.option_cookies_title,
                    description = R.string.option_cookies_body,
                    checked = current.cookiesEnabled && imported,
                    enabled = imported,
                    onCheckedChange = viewModel::setCookiesEnabled,
                )
                Text(
                    if (imported) pluralStringResource(R.plurals.cookies_imported, cookiesCount, cookiesCount)
                    else stringResource(R.string.cookies_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                cookieError?.let {
                    Text(
                        stringResource(R.string.error_cookies_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    OutlinedButton(onClick = {
                        viewModel.dismissCookieError()
                        cookiePicker.launch(arrayOf("*/*"))
                    }) {
                        Text(stringResource(if (imported) R.string.action_replace_file else R.string.action_import_file))
                    }
                    if (imported) {
                        TextButton(
                            onClick = { confirm = SettingsConfirm.RemoveCookies },
                            colors = ButtonDefaults.textButtonColors(contentColor = ActionColors.Negative),
                        ) { Text(stringResource(R.string.action_remove)) }
                    }
                }
            }

            Section(R.string.settings_storage_title, R.string.settings_storage_body) {
                recordsBytes?.let {
                    Text(
                        stringResource(R.string.storage_records, Formatter.formatShortFileSize(context, it)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                thumbnailBytes?.let {
                    Text(
                        stringResource(R.string.storage_thumbnails, Formatter.formatShortFileSize(context, it)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedButton(onClick = { confirm = SettingsConfirm.ClearThumbnails }) {
                    Text(stringResource(R.string.action_clear_thumbnails))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    OutlinedButton(onClick = { confirm = SettingsConfirm.ClearHistory }) {
                        Text(stringResource(R.string.action_clear_history))
                    }
                    TextButton(
                        onClick = { confirm = SettingsConfirm.ResetAll },
                        colors = ButtonDefaults.textButtonColors(contentColor = ActionColors.Negative),
                    ) { Text(stringResource(R.string.action_reset_settings)) }
                }
            }

            Section(R.string.settings_about_title, R.string.settings_about_body) {
                about?.let {
                    AboutLine(R.string.about_app_version, it.appVersion)
                    AboutLine(R.string.about_gallery_dl_version, it.galleryDlVersion)
                }
                Text(
                    stringResource(R.string.about_licences),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                stringResource(R.string.settings_apply_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    when (confirm) {
        SettingsConfirm.RemoveCookies -> ConfirmDialog(
            title = stringResource(R.string.remove_cookies_title),
            message = stringResource(R.string.remove_cookies_message),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                viewModel.removeCookies()
                confirm = null
            },
            onDismiss = { confirm = null },
        )
        SettingsConfirm.ClearHistory -> ConfirmDialog(
            title = stringResource(R.string.clear_finished_title),
            message = stringResource(R.string.clear_finished_message),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                viewModel.clearFinishedHistory()
                confirm = null
            },
            onDismiss = { confirm = null },
        )
        SettingsConfirm.ClearThumbnails -> ConfirmDialog(
            title = stringResource(R.string.clear_thumbnails_title),
            message = stringResource(R.string.clear_thumbnails_message),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                viewModel.clearThumbnails()
                confirm = null
            },
            onDismiss = { confirm = null },
        )
        SettingsConfirm.ResetAll -> ConfirmDialog(
            title = stringResource(R.string.reset_settings_title),
            message = stringResource(R.string.reset_settings_message),
            confirmLabel = stringResource(R.string.action_reset),
            onConfirm = {
                viewModel.resetAllSettings()
                confirm = null
            },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

/** A titled group of related options: heading and one-line explanation above a card of controls. */
@Composable
private fun Section(title: Int, body: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        Column(Modifier.padding(horizontal = Spacing.Xs), verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.Md), verticalArrangement = Arrangement.spacedBy(Spacing.Md)) { content() }
        }
    }
}

/** A switch with its title and a sentence saying what it does. The whole row is the touch target. */
@Composable
private fun SwitchRow(
    title: Int,
    description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A switch plus a value to type. Only a valid entry is saved, so the last good value stays in use while a
 * typo is being corrected, and the value is kept while the switch is off.
 */
@Composable
private fun ValueOption(
    title: Int,
    description: Int,
    enabled: Boolean,
    value: String,
    label: Int,
    error: Int,
    isValid: (String) -> Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
) {
    var draft by rememberSaveable(value) { mutableStateOf(value) }
    val valid = isValid(draft)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        SwitchRow(title, description, enabled, onEnabledChange)
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                if (isValid(it)) onValueChange(it.trim())
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(label)) },
            isError = !valid,
            supportingText = if (valid) null else ({ Text(stringResource(error)) }),
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
    }
}

@Composable
private fun AboutLine(label: Int, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Md)) {
        Text(
            stringResource(label),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value.ifEmpty { "-" }, style = MaterialTheme.typography.bodyMedium)
    }
}
