import AVKit
import SwiftUI

struct OpenFolder: Equatable {
    let id: Int64
    let name: String
}

@MainActor
final class LibraryViewModel: ObservableObject {
    /// Search term for the folder list; the files of an open folder have their own.
    @Published var query = ""
    @Published var itemQuery = ""
    @Published private(set) var openFolder: OpenFolder?
    @Published private(set) var selection = Set<Int64>()

    func open(_ folder: FolderSummary) {
        itemQuery = ""
        selection = []
        openFolder = OpenFolder(id: folder.id, name: folder.displayName)
    }

    /// Back steps out of selection first, then out of the folder.
    func back() {
        if !selection.isEmpty { selection = [] } else { closeFolder() }
    }

    func closeFolder() {
        itemQuery = ""
        selection = []
        openFolder = nil
    }

    func toggleSelected(_ id: Int64) {
        if selection.contains(id) { selection.remove(id) } else { selection.insert(id) }
    }

    func clearSelection() { selection = [] }
    func takeSelection() -> Set<Int64> { defer { selection = [] }; return selection }
}

struct LibraryScreen: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var previews: PreviewHost
    @ObservedObject var model: LibraryViewModel

    var body: some View {
        Group {
            if let folder = model.openFolder {
                // Swipe from the left edge to go back to the folders, which slide in underneath.
                SwipeBackContainer(enabled: model.selection.isEmpty, onBack: { model.closeFolder() }, behind: {
                    FoldersContent(model: model).environment(\.previewsDisabled, true).appBackground()
                }, top: {
                    FolderContent(model: model, folder: folder).appBackground()
                })
            } else {
                FoldersContent(model: model)
            }
        }
        // The preview clips of the whole library are made in the background while it is open, and the bar shows how far
        // that has got. Leaving the library pauses the work that has not started.
        .onAppear { startSweep() }
        .onChange(of: settings.livePreviews) { on in if on { startSweep() } else { PreviewStripStore.shared.cancelSweep() } }
        .onChange(of: store.items.count) { _ in startSweep() }
        .onDisappear {
            previews.releaseAll()
            PreviewStripStore.shared.cancelSweep()
        }
        // Leave a folder once everything in it is gone (deleted here or elsewhere).
        .onChange(of: store.items) { _ in
            if let folder = model.openFolder, model.itemQuery.trimmingCharacters(in: .whitespaces).isEmpty,
               store.items(inFolder: folder.id, query: "").isEmpty {
                model.closeFolder()
            }
        }
    }
}

extension LibraryScreen {
    fileprivate func startSweep() {
        guard settings.livePreviews else { return }
        PreviewStripStore.shared.requestAll(store.items.filter(\.isVideo))
    }
}

/// A slim bar that shows while preview clips are being made: "Preparing previews, 12 of 40".
struct PreviewProgressBanner: View {
    @ObservedObject private var progress = PreviewProgress.shared

    var body: some View {
        if progress.total > 0 {
            VStack(spacing: Spacing.xs) {
                HStack {
                    Text(S.previewsPreparing).textStyle(.labelLarge, color: Brand.onSurfaceVariant)
                    Spacer()
                    Text(S.previewsProgress(min(progress.done, progress.total), progress.total)).textStyle(.labelLarge, color: Brand.onSurfaceVariant)
                }
                LinearProgress(value: Double(progress.done) / Double(progress.total))
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.xs)
        }
    }
}

/// What long-pressing a folder offers: give it a tag, or delete it.
struct FolderActionsDialog: View {
    @EnvironmentObject private var ui: UIState
    let title: String
    let onTag: () -> Void
    let onDelete: () -> Void

    var body: some View {
        DialogCard(title: title) {
            VStack(alignment: .leading, spacing: 0) {
                TextPill(title: S.actionTagFolder, symbol: "tag") { ui.dismissDialog(); onTag() }
                TextPill(title: S.actionDeleteFolder, symbol: "trash", color: ActionColors.negative) { ui.dismissDialog(); onDelete() }
            }
        } actions: {
            TextPill(title: S.actionCancel, color: Brand.onSurfaceVariant) { ui.dismissDialog() }
        }
    }
}

/// Type the tag of a folder; it shows as "name / tag". Saving an empty tag takes the tag off.
struct TagDialog: View {
    @EnvironmentObject private var ui: UIState
    @EnvironmentObject private var store: AppStore
    let folderId: Int64
    let initial: String?
    @State private var text: String

    init(folderId: Int64, initial: String?) {
        self.folderId = folderId
        self.initial = initial
        _text = State(initialValue: initial ?? "")
    }

    var body: some View {
        DialogCard(title: S.tagFolderTitle) {
            OutlinedField(label: S.tagLabel, text: Binding(get: { text }, set: { text = String($0.prefix(40)) }), submitLabel: .done, onSubmit: save)
                .padding(.top, 8)
        } actions: {
            TextPill(title: S.actionCancel, color: Brand.onSurfaceVariant) { ui.dismissDialog() }
            FilledPill(
                title: text.trimmingCharacters(in: .whitespaces).isEmpty && !(initial ?? "").isEmpty ? S.actionRemoveTag : S.actionSave,
                container: ActionColors.positive, content: ActionColors.onPositive, action: save,
            )
        }
    }

    private func save() {
        store.setFolderTag(folderId, tag: text)
        ui.dismissDialog()
    }
}

// MARK: - Folder list

private struct FoldersContent: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var ui: UIState
    @ObservedObject var model: LibraryViewModel

    var body: some View {
        let folders = store.folderSummaries(query: model.query)
        let videosByFolder = Dictionary(grouping: store.items.filter { $0.isVideo && $0.folderId != nil }, by: { $0.folderId! })
        VStack(spacing: 0) {
            TopBar(title: S.navLibrary) { listColumnsMenu }
            PreviewProgressBanner()
            if searchVisible { SearchField(text: $model.query) }
            if !store.loaded {
                ShimmerGrid(minSize: 150, count: 4)
            } else if folders.isEmpty {
                if model.query.trimmingCharacters(in: .whitespaces).isEmpty {
                    EmptyState(title: S.libraryEmptyTitle, message: S.libraryEmptyBody, symbol: "photo.on.rectangle.angled", tone: .violet)
                } else {
                    EmptyState(title: S.libraryNoResults, message: S.libraryNoResultsBody, symbol: "magnifyingglass", tone: .violet)
                }
            } else {
                SoftScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.md), count: settings.libraryColumns), spacing: Spacing.md) {
                        ForEach(folders) { folder in
                            FolderCard(folder: folder, videos: videosByFolder[folder.id] ?? [])
                                .onTapGesture { model.open(folder) }
                                .onLongPressGesture { showActions(folder) }
                        }
                    }
                    .padding(Spacing.md)
                }
            }
        }
    }

    /// The grid button in the Library's top bar: choose how many columns the folders are shown in (2 or 3).
    private var listColumnsMenu: some View {
        Menu {
            ForEach(2...3, id: \.self) { count in
                Button { settings.libraryColumns = count } label: {
                    if count == settings.libraryColumns {
                        Label(S.columnsOption(count), systemImage: "checkmark")
                    } else {
                        Text(S.columnsOption(count))
                    }
                }
            }
        } label: {
            Image(systemName: "square.grid.2x2")
                .font(.system(size: 20, weight: .medium))
                .foregroundColor(Brand.onSurfaceVariant)
                .frame(width: 48, height: 48)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(S.actionColumns)
    }

    /// What long-pressing a folder offers: give it a tag, or delete it.
    private func showActions(_ folder: FolderSummary) {
        ui.present(FolderActionsDialog(
            title: folder.displayName,
            onTag: { ui.present(TagDialog(folderId: folder.id, initial: folder.tag)) },
            onDelete: { confirmDelete(folder) },
        ))
    }

    private func confirmDelete(_ folder: FolderSummary) {
        ui.present(ConfirmDialog(
            title: S.deleteFolderTitle, message: S.deleteFolderMessage(folder.itemCount), confirmLabel: S.actionDelete,
        ) { store.deleteFolder(folder.id) })
    }
}

private struct FolderCard: View {
    let folder: FolderSummary
    let videos: [LibraryItem]

    var body: some View {
        // The square comes first and the content is laid over it, so a wide image can never stretch the tile.
        Brand.surfaceVariant
            .aspectRatio(1, contentMode: .fit)
            .overlay(
                Group {
                    if let cover = folder.cover, cover.isVisual {
                        GalleryImage(item: cover)
                    } else {
                        IconTile(symbol: "folder.fill", tone: .violet, size: TileSize.hero)
                    }
                }
            )
            // A folder with videos plays short pieces of a few of them, picked at random, over its cover.
            .overlay(LivePreview(key: "folder-\(folder.id)", clips: videos))
            .overlay(
                // Name and count on the bottom strip. Long-pressing the card tags or deletes the folder.
                VStack(alignment: .leading, spacing: 0) {
                    Text(folder.title).textStyle(.titleSmall, color: .white).lineLimit(1)
                    // With a tag as the title, the folder's own name follows the count.
                    Text(folderSubtitle(folder.displayName, folder.tag).map { "\(S.folderItems(folder.itemCount)) · \($0)" } ?? S.folderItems(folder.itemCount))
                        .textStyle(.labelMedium, color: Color.white.opacity(0.8)).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.md)
                .padding(.top, Spacing.lg)
                .padding(.bottom, Spacing.md)
                .background(LinearGradient(colors: [.clear, Color.black.opacity(0.75)], startPoint: .top, endPoint: .bottom)),
                alignment: .bottomLeading,
            )
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
    }
}

/// The search box is hidden for now; the search itself stays for when it comes back.
private let searchVisible = false

private struct SearchField: View {
    @Binding var text: String
    var body: some View {
        OutlinedField(label: S.searchLabel, text: $text)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.xs)
            .padding(.top, 6)
    }
}

// MARK: - Folder contents

private struct FolderContent: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var ui: UIState
    @ObservedObject var model: LibraryViewModel
    let folder: OpenFolder

    var body: some View {
        let items = store.items(inFolder: folder.id, query: model.itemQuery)
        let selecting = !model.selection.isEmpty
        let tag = store.folders.first { $0.id == folder.id }?.tag
        VStack(spacing: 0) {
            TopBar(
                title: selecting ? S.selectedCount(model.selection.count) : folderTitle(folder.name, tag),
                subtitle: selecting ? nil : folderSubtitle(folder.name, tag),
                leading: {
                    IconButton(symbol: selecting ? "xmark" : "chevron.left", color: Brand.onSurface,
                               label: selecting ? S.actionClose : S.actionBack) { model.back() }
                },
                trailing: {
                    if selecting {
                        IconButton(symbol: "trash", color: ActionColors.negative, label: S.actionDelete) { confirmDeleteSelected() }
                    } else {
                        IconButton(symbol: "tag", color: Brand.onSurfaceVariant, label: S.actionTagFolder) {
                            ui.present(TagDialog(folderId: folder.id, initial: tag))
                        }
                        columnsMenu
                    }
                },
            )
            PreviewProgressBanner()
            if searchVisible && !selecting { SearchField(text: $model.itemQuery) }
            if items.isEmpty {
                EmptyState(title: S.libraryNoResults, message: S.libraryNoResultsBody, symbol: "magnifyingglass", tone: .violet)
            } else {
                SoftScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.sm), count: settings.folderColumns), spacing: Spacing.sm) {
                        ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                            ThumbnailCell(item: item, selected: model.selection.contains(item.id))
                                .onTapGesture {
                                    if selecting {
                                        model.toggleSelected(item.id)
                                    } else {
                                        ui.viewer = ViewerRequest(folderId: folder.id, query: model.itemQuery, startIndex: index)
                                    }
                                }
                                .onLongPressGesture { model.toggleSelected(item.id) }
                        }
                    }
                    .padding(Spacing.md)
                }
            }
        }
    }

    /// The grid button in a folder's top bar: choose how many columns the files are shown in (2 to 4).
    private var columnsMenu: some View {
        Menu {
            ForEach(2...4, id: \.self) { count in
                Button { settings.folderColumns = count } label: {
                    if count == settings.folderColumns {
                        Label(S.columnsOption(count), systemImage: "checkmark")
                    } else {
                        Text(S.columnsOption(count))
                    }
                }
            }
        } label: {
            Image(systemName: "square.grid.2x2")
                .font(.system(size: 20, weight: .medium))
                .foregroundColor(Brand.onSurfaceVariant)
                .frame(width: 48, height: 48)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(S.actionColumns)
    }

    private func confirmDeleteSelected() {
        let count = model.selection.count
        ui.present(ConfirmDialog(
            title: S.deleteSelectedTitle, message: S.deleteSelectedMessage(count), confirmLabel: S.actionDelete,
        ) { store.deleteItems(model.takeSelection()) })
    }
}

private struct ThumbnailCell: View {
    @EnvironmentObject private var settings: SettingsStore
    let item: LibraryItem
    let selected: Bool

    var body: some View {
        Brand.surfaceVariant
            .aspectRatio(1, contentMode: .fit)
            .overlay(
                Group {
                    if item.isVisual {
                        ZStack {
                            GalleryImage(item: item)
                            if item.isVideo {
                                // Every video plays a silent live preview when it is on screen and has a turn.
                                LivePreview(key: "item-\(item.id)", clips: [item])
                                if settings.livePreviews {
                                    Image(systemName: "play.circle.fill").font(.system(size: 20)).foregroundColor(Color.white.opacity(0.9))
                                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading).padding(Spacing.xs)
                                } else {
                                    Image(systemName: "play.circle.fill").font(.system(size: 30)).foregroundColor(.white)
                                }
                            }
                        }
                    } else {
                        IconTile(symbol: item.isAudio ? "music.note" : "doc.fill", tone: .blue, size: TileSize.large)
                    }
                }
            )
            .overlay(
                ZStack(alignment: .topTrailing) {
                    if selected {
                        Brand.primary.opacity(0.35)
                        Image(systemName: "checkmark.circle.fill").foregroundColor(.white).padding(Spacing.sm)
                    }
                }
            )
            .clipShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
    }
}

// MARK: - Full-screen viewer

/// The swipeable full-screen viewer. Images fill the screen; video and audio play in place; other files open in
/// whatever app the user picks.
struct ViewerOverlay: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var ui: UIState
    let request: ViewerRequest
    @State private var page: Int

    init(request: ViewerRequest) {
        self.request = request
        _page = State(initialValue: request.startIndex)
    }

    @State private var dragY: CGFloat = 0
    /// True while the current video is zoomed in: paging, the edge swipe and swipe-down are off so drags pan the picture.
    @State private var zoomed = false
    /// The video player's touch lock: the top bar, swipe down and swipe back are off too.
    @State private var locked = false

    var body: some View {
        let items = store.items(inFolder: request.folderId, query: request.query)
        // Swipe from the left edge to close (only on the first file, so it never fights the paging between files).
        // The edge swipe is only for pictures: dragging a playing video across the screen made it lag, and a video
        // already has its own sideways swipe (scrubbing).
        let onVideoPage = items.indices.contains(page) && (items[page].isVideo || items[page].isAudio)
        SwipeBackContainer(enabled: page == 0 && !zoomed && !onVideoPage && !locked, onBack: { ui.viewer = nil }) {
            ZStack(alignment: .top) {
                Color.black.opacity(1 - Double(min(dragY / 500, 0.7))).ignoresSafeArea()
                if items.isEmpty {
                    Color.clear.onAppear { ui.viewer = nil }
                } else {
                    Group {
                        if onVideoPage {
                            // A video page cannot be swiped to another file (a sideways swipe scrubs), so it is shown on
                            // its own. Inside the swipeable pager, turning the screen could leave it half-way between two
                            // pages, with two videos sharing one landscape screen.
                            ViewerPage(item: items[page], active: true, zoomed: $zoomed, locked: $locked)
                                .ignoresSafeArea()
                        } else {
                            TabView(selection: $page) {
                                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                                    ViewerPage(item: item, active: index == page, zoomed: $zoomed, locked: $locked).tag(index)
                                }
                            }
                            .tabViewStyle(.page(indexDisplayMode: .never))
                            .ignoresSafeArea()
                        }
                        if !locked { topBar(items) }
                    }
                    .offset(y: dragY)
                }
            }
            // Swipe down to close, as in Photos: the picture follows the finger and the background fades.
            .simultaneousGesture(
                DragGesture(minimumDistance: 20)
                    .onChanged { value in
                        guard !zoomed, !locked, value.translation.height > 0, abs(value.translation.height) > abs(value.translation.width) else { return }
                        dragY = value.translation.height
                    }
                    .onEnded { value in
                        guard dragY > 0 else { return }
                        if value.translation.height > 140 || value.predictedEndTranslation.height > 450 {
                            withAnimation(.easeOut(duration: 0.18)) { dragY = 900 }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) { ui.viewer = nil }
                        } else {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { dragY = 0 }
                        }
                    }
            )
            .onChange(of: page) { _ in zoomed = false }
            .onChange(of: items.count) { count in
                if count > 0 && page >= count { page = count - 1 }
            }
        }
        // Closing the viewer puts the screen back to portrait (a video may have turned it to landscape).
        .onDisappear { ScreenOrientation.lockPortrait() }
    }

    private func topBar(_ items: [LibraryItem]) -> some View {
        let current = items[min(max(page, 0), items.count - 1)]
        return HStack(spacing: Spacing.xs) {
            IconButton(symbol: "chevron.left", color: .white, label: S.actionBack) { ui.viewer = nil }
            Text(current.displayName).textStyle(.titleSmall, color: .white).lineLimit(1)
            Spacer(minLength: 0)
            // Open and Delete share one menu: deleting a file is not a one-tap icon beside a harmless one.
            Menu {
                Button { ShareSheet.present(current.fileURL) } label: { Label(S.actionOpenWith, systemImage: "arrow.up.right.square") }
                Button(role: .destructive) {
                    ui.present(ConfirmDialog(
                        title: S.deleteItemTitle, message: S.deleteItemMessage, confirmLabel: S.actionDelete,
                    ) { store.deleteItems([current.id]) })
                } label: { Label(S.actionDelete, systemImage: "trash") }
            } label: {
                Image(systemName: "ellipsis")
                    .rotationEffect(.degrees(90))
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 48, height: 48)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(S.actionMore)
        }
        .padding(.horizontal, Spacing.xs)
        .padding(.vertical, Spacing.xs)
        .background(LinearGradient(colors: [Color.black.opacity(0.6), .clear], startPoint: .top, endPoint: .bottom).ignoresSafeArea())
    }
}

private struct ViewerPage: View {
    let item: LibraryItem
    let active: Bool
    @Binding var zoomed: Bool
    @Binding var locked: Bool

    var body: some View {
        ZStack {
            Color.black
            if item.isImage {
                GalleryImage(item: item, fit: true, showShimmer: false)
            } else if item.isVideo || item.isAudio {
                VideoViewer(item: item, active: active, zoomed: $zoomed, locked: $locked)
            } else {
                OpenButton(item: item, symbol: "doc.fill")
            }
        }
    }
}

private struct OpenButton: View {
    let item: LibraryItem
    let symbol: String
    var body: some View {
        Button { ShareSheet.present(item.fileURL) } label: {
            Image(systemName: symbol).font(.system(size: 64)).foregroundColor(.white).frame(width: 96, height: 96)
        }
        .accessibilityLabel(S.actionOpen)
    }
}

/// The system share sheet, which is how a file is opened in another app on iOS.
enum ShareSheet {
    @MainActor static func present(_ url: URL) {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first,
              var top = scene.windows.first(where: \.isKeyWindow)?.rootViewController else { return }
        while let presented = top.presentedViewController { top = presented }
        top.present(UIActivityViewController(activityItems: [url], applicationActivities: nil), animated: true)
    }
}
