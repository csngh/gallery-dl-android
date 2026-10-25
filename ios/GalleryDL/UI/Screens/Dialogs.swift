import SwiftUI

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
        present(ConfirmDialog(title: S.clearFinishedTitle, message: S.clearFinishedMessage, confirmLabel: S.actionClear) { store.clearFinished() })
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
