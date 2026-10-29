import SwiftUI

// MARK: - Folder for a link

/// Asked before a link starts for the first time: which library folder its files go in. The name gallery-dl would use is
/// looked up and filled in; keeping it leaves the folder to gallery-dl, typing another name pins the link to that folder.
/// A name another link already uses gets a warning, and can still be chosen.
struct StartFolderDialog: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var ui: UIState
    @EnvironmentObject private var store: AppStore
    let task: DownloadTask
    /// nil while the name is being looked up.
    @State private var suggested: String?
    @State private var typed: String?

    var body: some View {
        let shown = typed ?? suggested ?? ""
        let others = store.linksUsing(folder: shown, except: task.id)
        DialogCard(title: S.startFolderTitle) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(task.url).textStyle(.bodySmall, color: Brand.onSurfaceVariant).lineLimit(2)
                Text(S.startFolderMessage).textStyle(.bodyMedium)
                OutlinedField(label: S.startFolderLabel, text: Binding(get: { shown }, set: { typed = $0 }), submitLabel: .done, onSubmit: begin)
                if suggested == nil && typed == nil {
                    HStack(spacing: Spacing.sm) {
                        ProgressView().scaleEffect(0.7)
                        Text(S.startFolderLooking).textStyle(.bodySmall, color: Brand.onSurfaceVariant)
                    }
                } else if (suggested ?? "").isEmpty && (typed ?? "").isEmpty {
                    Text(S.startFolderUnknown).textStyle(.bodySmall, color: Brand.onSurfaceVariant)
                }
                if !others.isEmpty {
                    Text(S.startFolderExists).textStyle(.bodySmall, color: ActionColors.negative)
                    ForEach(Array(others.prefix(2)), id: \.self) { Text($0).textStyle(.bodySmall, color: ActionColors.negative).lineLimit(1) }
                    if others.count > 2 { Text(S.startFolderMore(others.count - 2)).textStyle(.bodySmall, color: ActionColors.negative) }
                }
            }
        } actions: {
            TextPill(title: S.actionCancel, color: Brand.onSurfaceVariant) { ui.dismissDialog() }
            FilledPill(title: S.actionStart, container: ActionColors.positive, content: ActionColors.onPositive, action: begin)
        }
        .task { suggested = await app.engine.suggestFolder(for: task.url) }
    }

    private func begin() {
        let chosen = cleanFolderName(typed ?? suggested)
        // The name gallery-dl suggested (or none) is not pinned: the link keeps following gallery-dl.
        let pinned = chosen == cleanFolderName(suggested) ? nil : chosen
        ui.dismissDialog()
        app.engine.start(task.id, folderName: pinned)
    }
}

/// The confirmation dialogs the Android app shows, in one place.
@MainActor
extension UIState {
    func confirmCancelDownload(_ id: Int64, engine: DownloadEngine) {
        present(ConfirmDialog(
            title: S.cancelDownloadTitle, message: S.cancelDownloadMessage,
            confirmLabel: S.actionCancelDownload, dismissLabel: S.actionKeepDownloading,
        ) { engine.cancel(id) })
    }

    func confirmRemove(_ id: Int64, store: AppStore) {
        present(ConfirmDialog(title: S.removeLinkTitle, message: S.removeLinkMessage, confirmLabel: S.actionRemove) { store.remove(id) })
    }

    func confirmClearFinished(store: AppStore) {
        present(ConfirmDialog(title: S.clearQueueTitle, message: S.clearQueueMessage, confirmLabel: S.actionClear) { store.hideFinishedFromQueue() })
    }

    func showFullLink(_ url: String) { present(FullLinkDialog(url: url)) }

    func showEdit(_ task: DownloadTask) { present(EditLinkDialog(task: task)) }
}

/// Edit the link of a link that has not worked (or has not started). Saving checks the link and queues it again.
struct EditLinkDialog: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var ui: UIState
    let task: DownloadTask
    @State private var text: String
    @State private var error: UrlCheck?
    @State private var busy = false

    init(task: DownloadTask) {
        self.task = task
        _text = State(initialValue: task.url)
    }

    var body: some View {
        DialogCard(title: S.editLinkTitle) {
            OutlinedField(
                label: S.editLinkLabel, text: Binding(get: { text }, set: { text = $0; error = nil }),
                error: urlErrorMessage(error), keyboard: .URL, submitLabel: .go, onSubmit: save,
            )
            .padding(.top, 8)
        } actions: {
            TextPill(title: S.actionCancel, color: Brand.onSurfaceVariant) { ui.dismissDialog() }
            // Saving queues the link again, a positive action, so the confirm button is green.
            FilledPill(
                title: busy ? "…" : (task.status == .pending ? S.actionSave : S.actionSaveRetry),
                container: ActionColors.positive, content: ActionColors.onPositive,
                enabled: !text.trimmingCharacters(in: .whitespaces).isEmpty && !busy, action: save,
            )
        }
    }

    private func save() {
        let url = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !busy, !url.isEmpty else { return }
        busy = true
        error = nil
        Task {
            await app.waitUntilReady()
            let result = await app.validator.check(url)
            if result.check == .valid {
                app.engine.editLink(task.id, url: url, site: result.site)
                ui.dismissDialog()
            } else {
                busy = false
                error = result.check
            }
        }
    }
}
