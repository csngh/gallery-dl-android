import SwiftUI

/// All the links of one website.
struct SiteGroup: Identifiable {
    let site: String
    let tasks: [DownloadTask]
    var id: String { site }

    var filesSaved: Int { tasks.reduce(0) { $0 + $1.filesDownloaded } }
    /// Links that failed or finished with errors.
    var needAttention: Int { tasks.filter { $0.result == .failed || $0.result == .partial }.count }
    var inProgress: Int { tasks.filter { $0.result == .inProgress }.count }
    var ready: Int { tasks.filter { $0.result == .ready }.count }
    var lastActivity: Date { tasks.map(\.createdAt).max() ?? .distantPast }
    /// The host to ask for this website's icon, taken from its newest link that has one.
    var iconHost: String? { tasks.sorted { $0.createdAt > $1.createdAt }.compactMap { iconHostOf($0.url) }.first }
}

struct HomeScreen: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var ui: UIState
    @ObservedObject var input: LinkInputModel
    let onOpenSettings: () -> Void
    @State private var importing = false

    private var groups: [SiteGroup] {
        Dictionary(grouping: store.tasks, by: \.siteKey)
            .map { SiteGroup(site: $0.key, tasks: $0.value) }
            .sorted { $0.lastActivity > $1.lastActivity }
    }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: S.navHome) {
                IconButton(symbol: "arrow.clockwise", color: Brand.onSurfaceVariant, label: S.actionRenew) { app.engine.renewAll() }
                Menu {
                    Button { exportLinks() } label: { Label(S.actionExportLinks, systemImage: "square.and.arrow.up") }
                    Button { importing = true } label: { Label(S.actionImportLinks, systemImage: "square.and.arrow.down") }
                } label: {
                    Image(systemName: "ellipsis")
                        .rotationEffect(.degrees(90))
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(Brand.onSurfaceVariant)
                        .frame(width: 48, height: 48)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(S.actionLinksMenu)
                IconButton(symbol: "gearshape.fill", color: Brand.onSurfaceVariant, label: S.settingsTitle, action: onOpenSettings)
            }
            StorageBanner(storage: app.storage)
            LinkField(input: input)
            StopAllButton(count: store.tasks.filter { $0.status == .queued || $0.status == .downloading }.count) { app.engine.cancelAll() }
            StartAllButton(count: store.tasks.filter { $0.status == .pending }.count) { app.engine.startAll() }

            if !store.loaded {
                ShimmerCardList()
            } else if groups.isEmpty {
                EmptyState(title: S.homeEmptyTitle, message: S.homeEmptyBody, symbol: "globe", tone: .blue)
            } else {
                SoftScrollView {
                    LazyVStack(spacing: Spacing.md) {
                        ForEach(groups) { group in SiteGroupCard(group: group) }
                    }
                    .padding(Spacing.md)
                }
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.plainText, .text, .data]) { result in
            guard case .success(let url) = result else { return }
            ui.present(TransferDialog())
            Task { await app.importLinks(from: url) }
        }
    }

    private func exportLinks() {
        guard let file = app.exportFile() else {
            if app.transfer == nil { ui.present(TransferDialog(message: S.exportEmpty)) } else { ui.present(TransferDialog()) }
            return
        }
        ShareSheet.present(file.url)
    }
}

/// Progress and outcome of an import, or a plain message.
struct TransferDialog: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var ui: UIState
    var message: String?

    private func close() { app.transfer = nil; ui.dismissDialog() }

    var body: some View {
        if let message {
            DialogCard(title: S.actionExportLinks) {
                Text(message).textStyle(.bodyMedium, color: Brand.onSurfaceVariant)
            } actions: { FilledPill(title: S.actionOk) { close() } }
        } else {
            switch app.transfer {
            case .importing(let done, let total):
                DialogCard(title: S.importTitle) {
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        Text(S.importProgress(done, total)).textStyle(.bodyMedium, color: Brand.onSurfaceVariant)
                        ProgressView(value: total == 0 ? 1 : Double(done) / Double(total)).tint(Brand.primary)
                    }
                } actions: { EmptyView() }
            case .imported(let result):
                DialogCard(title: S.importDoneTitle) {
                    VStack(alignment: .leading, spacing: Spacing.sm) {
                        if result.found == 0 { Text(S.importNone) }
                        if result.added > 0 { Text(S.importAdded(result.added)) }
                        if result.duplicates > 0 { Text(S.importDuplicates(result.duplicates)) }
                        if result.unsupported > 0 { Text(S.importUnsupported(result.unsupported)) }
                        if result.notLinks > 0 { Text(S.importNotLinks(result.notLinks)) }
                    }
                    .textStyle(.bodyMedium, color: Brand.onSurfaceVariant)
                } actions: { FilledPill(title: S.actionOk) { close() } }
            case .failed:
                DialogCard(title: S.actionImportLinks) {
                    Text(S.transferFailed).textStyle(.bodyMedium, color: Brand.onSurfaceVariant)
                } actions: { FilledPill(title: S.actionOk) { close() } }
            case nil:
                EmptyView()
            }
        }
    }
}

private struct SiteGroupCard: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var ui: UIState
    let group: SiteGroup
    // `-testExpandHome 1` opens every site group, to check the link rows in the simulator.
    @State private var expanded = UserDefaults.standard.bool(forKey: "testExpandHome")

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 0) {
                Button { withAnimation(.easeInOut(duration: 0.25)) { expanded.toggle() } } label: {
                    HStack(spacing: Spacing.md) {
                        SiteTile(site: group.site, iconHost: group.iconHost)
                        VStack(alignment: .leading, spacing: Spacing.xs) {
                            Text(siteDisplayName(group.site)).textStyle(.titleSmall).lineLimit(1)
                            summary
                        }
                        Spacer(minLength: 0)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Brand.onSurfaceVariant)
                            .rotationEffect(.degrees(expanded ? 180 : 0))
                            .accessibilityLabel(expanded ? S.actionCollapse : S.actionExpand)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if expanded {
                    VStack(alignment: .leading, spacing: 0) {
                        Rectangle().fill(Brand.outlineVariant).frame(height: 1).padding(.top, Spacing.sm)
                        ForEach(group.tasks) { task in SiteLinkRow(task: task) }
                    }
                }
            }
            .padding(Spacing.md)
        }
    }

    /// How many links, how many files, and what needs attention, in one quiet line.
    private var summary: some View {
        var parts: [(String, Color)] = [(S.siteLinks(group.tasks.count), Brand.onSurfaceVariant)]
        if group.filesSaved > 0 { parts.append((S.filesSaved(group.filesSaved), Brand.onSurfaceVariant)) }
        if group.ready > 0 { parts.append((S.siteReady(group.ready), Tone.blue.icon)) }
        if group.inProgress > 0 { parts.append((S.siteInProgress(group.inProgress), Tone.violet.icon)) }
        if group.needAttention > 0 { parts.append((S.siteAttention(group.needAttention), ActionColors.negative)) }
        let text = parts.enumerated().reduce(Text("")) { text, entry in
            let separator = entry.offset > 0 ? Text(" · ").foregroundColor(Brand.onSurfaceVariant) : Text("")
            return text + separator + Text(entry.element.0).foregroundColor(entry.element.1)
        }
        return text.font(.inter(size: 14, weight: .medium)).lineLimit(2)
    }
}

private struct SiteLinkRow: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var ui: UIState
    let task: DownloadTask

    var body: some View {
        let (symbol, tone) = task.result.tile
        let status: String = {
            if task.status == .downloading, let progress = task.overallProgress { return S.siteDownloadingPercent(Int(progress * 100)) }
            return task.statusLabel
        }()
        HStack(spacing: Spacing.md) {
            IconTile(symbol: symbol, tone: tone, size: TileSize.small)
            VStack(alignment: .leading, spacing: 0) {
                let link = task.url.replacingOccurrences(of: "https://", with: "").replacingOccurrences(of: "http://", with: "")
                // The folder's name tells which folder the menu on this row acts on; the link sits below it. Until a link
                // has a folder, the link is the main text.
                let folder = task.folderName ?? store.folderName(forLink: task.url)
                Text(folder ?? link)
                    .textStyle(.bodyMedium).lineLimit(1)
                    .onTapGesture { ui.showFullLink(task.url) }
                if folder != nil {
                    Text(link).textStyle(.bodySmall, color: Brand.onSurfaceVariant).lineLimit(1)
                        .onTapGesture { ui.showFullLink(task.url) }
                }
                Text(status).textStyle(.labelMedium, color: tone.icon)
                if task.status == .pending { StartButton { ui.present(StartFolderDialog(task: task)) }.padding(.top, Spacing.xs) }
            }
            Spacer(minLength: 0)
            trailing
        }
        .padding(.top, Spacing.sm)
    }

    @ViewBuilder private var trailing: some View {
        switch task.status {
        case .pending:
            // Not started yet: view the link, correct it, or remove it; Start sits under the status.
            LinkActionsMenu(showRetry: false, onRetry: {}, onRemove: { ui.confirmRemove(task.id, store: store) },
                            onEdit: { ui.showEdit(task) }, onViewLink: { ui.showFullLink(task.url) })
        case .queued, .downloading:
            IconButton(symbol: "xmark", color: ActionColors.negative, label: S.actionCancel) {
                // A running download is confirmed first; a queued one can simply be retried later.
                if task.status == .downloading { ui.confirmCancelDownload(task.id, engine: app.engine) } else { app.engine.cancel(task.id) }
            }
        case .failed, .cancelled:
            // A link that did not work can be corrected; finished links are left as they are.
            LinkActionsMenu(showRetry: true, onRetry: { app.engine.retry(task.id) }, onRemove: { ui.confirmRemove(task.id, store: store) },
                            onEdit: { ui.showEdit(task) }, onViewLink: { ui.showFullLink(task.url) })
        case .completed:
            // A finished link can be run again to pick up anything new; its memory of saved files means only new files
            // are fetched.
            LinkActionsMenu(showRetry: true, retryLabel: task.result == .partial ? S.actionRetry : S.actionCheckNew,
                            onRetry: { app.engine.retry(task.id) },
                            onRemove: { ui.confirmRemove(task.id, store: store) }, onViewLink: { ui.showFullLink(task.url) })
        }
    }
}
