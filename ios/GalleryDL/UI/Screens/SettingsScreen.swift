import SwiftUI
import UniformTypeIdentifiers

struct SettingsScreen: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var ui: UIState
    let onBack: () -> Void

    @State private var importing = false
    @State private var cookieError = false
    @State private var recordsBytes: Int64?
    @State private var thumbnailBytes: Int64?

    var body: some View {
        let options = settings.options
        VStack(spacing: 0) {
            TopBar(title: S.settingsTitle, leading: {
                IconButton(symbol: "chevron.left", color: Brand.onSurface, label: S.actionBack, action: onBack)
            }, trailing: {})
            SoftScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    SettingsSection(title: S.settingsAppearanceTitle, message: S.settingsAppearanceBody) {
                        SwitchRow(title: S.optionStartupSoundTitle, message: S.optionStartupSoundBody, isOn: $settings.startupSound)
                        Divider
                        SwitchRow(title: S.optionLivePreviewsTitle, message: S.optionLivePreviewsBody, isOn: $settings.livePreviews)
                    }

                    SettingsSection(title: S.settingsLocationTitle, message: S.settingsLocationBody) {
                        Text(S.downloadLocation).textStyle(.bodyLarge)
                        Text(S.downloadLocationHint).textStyle(.bodySmall, color: Brand.onSurfaceVariant)
                    }

                    SettingsSection(title: S.settingsConnectionTitle, message: S.settingsConnectionBody) {
                        SwitchRow(title: S.optionWifiTitle, message: S.optionWifiBody, isOn: Binding(
                            get: { settings.options.wifiOnly },
                            set: { settings.options.wifiOnly = $0; app.engine.applyNetworkSetting() },
                        ))
                        Divider
                        ValueOption(
                            title: S.optionRetriesTitle, message: S.optionRetriesBody,
                            enabled: $settings.options.retriesEnabled, value: $settings.options.retries,
                            label: S.optionRetriesLabel, error: S.errorRetries,
                            isValid: { NumberInput.parseInt($0, range: retriesRange) != nil },
                        )
                        Divider
                        ValueOption(
                            title: S.optionTimeoutTitle, message: S.optionTimeoutBody,
                            enabled: $settings.options.timeoutEnabled, value: $settings.options.timeout,
                            label: S.optionSecondsLabel, error: S.errorTimeout,
                            isValid: { NumberInput.parseSeconds($0, range: timeoutRange) != nil },
                        )
                        Divider
                        SwitchRow(title: S.optionSkipTitle, message: S.optionSkipBody, isOn: $settings.options.skipExisting)
                    }

                    SettingsSection(title: S.settingsPacingTitle, message: S.settingsPacingBody) {
                        ValueOption(
                            title: S.optionSleepRequestTitle, message: S.optionSleepRequestBody,
                            enabled: $settings.options.sleepRequestEnabled, value: $settings.options.sleepRequest,
                            label: S.optionSecondsLabel, error: S.errorDuration, isValid: { DurationInput.parse($0) != nil },
                        )
                        Divider
                        ValueOption(
                            title: S.optionSleepTitle, message: S.optionSleepBody,
                            enabled: $settings.options.sleepEnabled, value: $settings.options.sleep,
                            label: S.optionSecondsLabel, error: S.errorDuration, isValid: { DurationInput.parse($0) != nil },
                        )
                    }

                    SettingsSection(title: S.settingsSitesTitle, message: S.settingsSitesBody) {
                        SwitchRow(title: S.optionApiRestTitle, message: S.optionApiRestBody, isOn: $settings.options.apiRest)
                    }

                    SettingsSection(title: S.settingsAccountsTitle, message: S.settingsAccountsBody) {
                        let imported = settings.cookiesCount > 0
                        SwitchRow(
                            title: S.optionCookiesTitle, message: S.optionCookiesBody,
                            isOn: Binding(get: { options.cookiesEnabled && imported }, set: { settings.options.cookiesEnabled = $0 }),
                            enabled: imported,
                        )
                        Text(imported ? S.cookiesImported(settings.cookiesCount) : S.cookiesNone)
                            .textStyle(.bodyMedium, color: Brand.onSurfaceVariant)
                        if cookieError { Text(S.errorCookiesInvalid).textStyle(.bodySmall, color: Brand.error) }
                        HStack(spacing: Spacing.sm) {
                            OutlinedPill(title: imported ? S.actionReplaceFile : S.actionImportFile) {
                                cookieError = false
                                importing = true
                            }
                            if imported {
                                TextPill(title: S.actionRemove, color: ActionColors.negative) {
                                    ui.present(ConfirmDialog(title: S.removeCookiesTitle, message: S.removeCookiesMessage, confirmLabel: S.actionRemove) {
                                        settings.removeCookies()
                                        cookieError = false
                                    })
                                }
                            }
                        }
                    }

                    SettingsSection(title: S.settingsStorageTitle, message: S.settingsStorageBody) {
                        if let recordsBytes {
                            Text(S.storageRecords(ByteCountFormatter.string(fromByteCount: recordsBytes, countStyle: .file))).textStyle(.bodyMedium)
                        }
                        if let thumbnailBytes {
                            Text(S.storageThumbnails(ByteCountFormatter.string(fromByteCount: thumbnailBytes, countStyle: .file))).textStyle(.bodyMedium)
                        }
                        OutlinedPill(title: S.actionClearThumbnails) {
                            ui.present(ConfirmDialog(title: S.clearThumbnailsTitle, message: S.clearThumbnailsMessage, confirmLabel: S.actionClear) {
                                ThumbnailStore.shared.clear()
                                PreviewStripStore.shared.clear()
                                thumbnailBytes = ThumbnailStore.shared.sizeBytes() + PreviewStripStore.shared.sizeBytes()
                            })
                        }
                        HStack(spacing: Spacing.sm) {
                            OutlinedPill(title: S.actionClearHistory) {
                                ui.present(ConfirmDialog(title: S.clearFinishedTitle, message: S.clearFinishedMessage, confirmLabel: S.actionClear) {
                                    store.clearFinished()
                                    recordsBytes = MediaFiles.recordsBytes()
                                })
                            }
                            TextPill(title: S.actionResetSettings, color: ActionColors.negative) {
                                ui.present(ConfirmDialog(title: S.resetSettingsTitle, message: S.resetSettingsMessage, confirmLabel: S.actionReset) {
                                    settings.resetAll()
                                    app.engine.applyNetworkSetting()
                                })
                            }
                        }
                    }

                    SettingsSection(title: S.settingsAboutTitle, message: S.settingsAboutBody) {
                        AboutLine(label: S.aboutAppVersion, value: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")
                        AboutLine(label: S.aboutGalleryDlVersion, value: app.galleryDlVersion)
                        Text(S.aboutLicences).textStyle(.bodySmall, color: Brand.onSurfaceVariant)
                    }

                    Text(S.settingsApplyNote).textStyle(.bodySmall, color: Brand.onSurfaceVariant)
                }
                .padding(Spacing.md)
            }
        }
        .onAppear {
            recordsBytes = MediaFiles.recordsBytes()
            thumbnailBytes = ThumbnailStore.shared.sizeBytes() + PreviewStripStore.shared.sizeBytes()
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.data, .plainText]) { result in
            if case .success(let url) = result { cookieError = !settings.importCookies(from: url) }
        }
    }

    private var Divider: some View { Rectangle().fill(Brand.outlineVariant).frame(height: 1) }
}

/// A titled group of related options: heading and one-line explanation above a card of controls.
private struct SettingsSection<Content: View>: View {
    let title: String
    let message: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title).textStyle(.titleMedium)
                Text(message).textStyle(.bodyMedium, color: Brand.onSurfaceVariant)
            }
            .padding(.horizontal, Spacing.xs)
            Card {
                VStack(alignment: .leading, spacing: Spacing.md) { content }.padding(Spacing.md)
            }
        }
    }
}

/// A switch with its title and a sentence saying what it does. The whole row is the touch target.
private struct SwitchRow: View {
    let title: String
    let message: String
    @Binding var isOn: Bool
    var enabled = true

    var body: some View {
        Button { if enabled { isOn.toggle() } } label: {
            HStack(spacing: Spacing.md) {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(title).textStyle(.titleSmall)
                    Text(message).textStyle(.bodySmall, color: Brand.onSurfaceVariant).multilineTextAlignment(.leading)
                }
                Spacer(minLength: 0)
                M3Switch(isOn: $isOn, enabled: enabled)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// A switch plus a value to type. Only a valid entry is saved, so the last good value stays in use while a typo is
/// being corrected, and the value is kept while the switch is off.
private struct ValueOption: View {
    let title: String
    let message: String
    @Binding var enabled: Bool
    @Binding var value: String
    let label: String
    let error: String
    let isValid: (String) -> Bool
    @State private var draft = ""

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            SwitchRow(title: title, message: message, isOn: $enabled)
            OutlinedField(
                label: label,
                text: Binding(get: { draft }, set: { draft = $0; if isValid($0) { value = $0.trimmingCharacters(in: .whitespaces) } }),
                error: isValid(draft) ? nil : error,
                enabled: enabled,
                keyboard: .asciiCapable,
            )
            .padding(.top, 6)
        }
        .onAppear { draft = value }
    }
}

private struct AboutLine: View {
    let label: String
    let value: String
    var body: some View {
        HStack(spacing: Spacing.md) {
            Text(label).textStyle(.labelLarge, color: Brand.onSurfaceVariant).frame(maxWidth: .infinity, alignment: .leading)
            Text(value.isEmpty ? "-" : value).textStyle(.bodyMedium)
        }
    }
}
