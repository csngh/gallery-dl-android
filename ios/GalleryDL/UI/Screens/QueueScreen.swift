import SwiftUI

struct QueueScreen: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var ui: UIState
    @ObservedObject var input: LinkInputModel

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: S.navQueue) {
                if store.tasks.contains(where: \.isFinished) {
                    TextPill(title: S.actionClearFinished, color: ActionColors.negative) { ui.confirmClearFinished(store: store) }
                }
            }
            StorageBanner(storage: app.storage)
            LinkField(input: input)
            StartAllButton(count: store.tasks.filter { $0.status == .pending }.count) { app.engine.startAll() }

            if !store.loaded {
                ShimmerCardList()
            } else if store.tasks.isEmpty {
                EmptyState(title: S.queueEmptyTitle, message: S.queueEmptyBody, symbol: "arrow.down.to.line", tone: .blue)
            } else {
                SoftScrollView {
                    LazyVStack(spacing: Spacing.md) {
                        ForEach(store.tasks) { task in TaskCard(task: task) }
                    }
                    .padding(Spacing.md)
                }
            }
        }
    }
}

private struct TaskCard: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var ui: UIState
    let task: DownloadTask

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                HStack(spacing: Spacing.md) {
                    let (symbol, tone) = task.result.tile
                    IconTile(symbol: symbol, tone: tone)
                    VStack(alignment: .leading, spacing: 0) {
                        Text(task.url).textStyle(.bodyLarge).lineLimit(2).onTapGesture { ui.showFullLink(task.url) }
                        Text(task.statusLabel).textStyle(.labelLarge, color: task.result.color)
                        if task.status == .pending { StartButton { app.engine.start(task.id) }.padding(.top, Spacing.sm) }
                    }
                    Spacer(minLength: 0)
                    trailing
                }

                switch task.status {
                case .downloading: DownloadProgress(task: task)
                case .completed: LinearProgress(value: 1)
                default: EmptyView()
                }

                if !task.summaryParts.isEmpty {
                    Text(task.summaryParts.joined(separator: " · ")).textStyle(.bodyMedium)
                }
                if let file = task.currentFile {
                    Text(file).textStyle(.bodySmall).lineLimit(1)
                }
                if let error = task.error {
                    Text(error).textStyle(.bodySmall, color: Brand.error)
                }
            }
            .padding(Spacing.md)
        }
    }

    @ViewBuilder private var trailing: some View {
        switch task.status {
        case .pending:
            // A link that was only added: View full link and Remove sit in the menu; Start is below the status.
            LinkActionsMenu(showRetry: false, onRetry: {}, onRemove: { ui.confirmRemove(task.id, store: store) }, onViewLink: { ui.showFullLink(task.url) })
        case .queued, .downloading:
            // Stopping a download is urgent, so it stays one tap away.
            IconButton(symbol: "xmark", color: ActionColors.negative, label: S.actionCancel) {
                if task.status == .downloading { ui.confirmCancelDownload(task.id, engine: app.engine) } else { app.engine.cancel(task.id) }
            }
        case .failed, .cancelled:
            LinkActionsMenu(showRetry: true, onRetry: { app.engine.retry(task.id) }, onRemove: { ui.confirmRemove(task.id, store: store) }, onViewLink: { ui.showFullLink(task.url) })
        case .completed:
            // A finished link can be run again to pick up anything new; its memory of saved files means only new files
            // are fetched.
            LinkActionsMenu(showRetry: true, retryLabel: task.result == .partial ? S.actionRetry : S.actionCheckNew,
                            onRetry: { app.engine.retry(task.id) },
                            onRemove: { ui.confirmRemove(task.id, store: store) }, onViewLink: { ui.showFullLink(task.url) })
        }
    }
}

/// Determinate when gallery-dl reports sizes or an album count; indeterminate until then.
private struct DownloadProgress: View {
    let task: DownloadTask

    var body: some View {
        if let progress = task.overallProgress {
            VStack(spacing: Spacing.xs) {
                LinearProgress(value: progress)
                HStack {
                    Text(S.progressPercent(Int(progress * 100))).textStyle(.labelLarge)
                    Spacer()
                    if task.fileIndex > 0 {
                        Text(task.totalFiles > 0 ? S.progressFileOf(task.fileIndex, task.totalFiles) : S.progressFile(task.fileIndex))
                            .textStyle(.labelLarge, color: Brand.onSurfaceVariant)
                    }
                }
            }
        } else {
            LinearProgress(value: nil)
        }
    }
}
