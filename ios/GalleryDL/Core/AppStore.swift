import Foundation

private struct StoreData: Codable {
    var nextTaskId: Int64 = 1
    var nextFolderId: Int64 = 1
    var nextItemId: Int64 = 1
    var tasks: [DownloadTask] = []
    var folders: [Folder] = []
    var items: [LibraryItem] = []
}

/// The queue and the library, kept in one JSON file. This is the iOS counterpart of Android's Room database and
/// repositories: the same operations, applied to in-memory arrays that SwiftUI observes and saved to disk.
@MainActor
final class AppStore: ObservableObject {
    @Published private(set) var tasks: [DownloadTask] = []      // newest first, like observeAll()
    @Published private(set) var folders: [Folder] = []
    @Published private(set) var items: [LibraryItem] = []
    /// False until the stored data has been read, so screens do not flash their empty state.
    @Published private(set) var loaded = false

    private var data = StoreData()
    private var saveTask: Task<Void, Never>?

    init() {
        load()
    }

    // MARK: Persistence

    private func load() {
        if let bytes = try? Data(contentsOf: MediaFiles.storeFile),
           let decoded = try? JSONDecoder().decode(StoreData.self, from: bytes) {
            data = decoded
        }
        publish()
        loaded = true
    }

    /// Hands the screens only what actually changed. A progress update would otherwise make every screen that shows the
    /// library redraw four times a second, because assigning to a `@Published` property always notifies.
    private func publish() {
        let sorted = data.tasks.sorted { $0.id > $1.id }
        if tasks != sorted { tasks = sorted }
        if folders != data.folders { folders = data.folders }
        if items != data.items { items = data.items }
    }

    /// Writes everything now. Called when the app is left: the delayed write below could otherwise be lost if the app is
    /// suspended and then ended before it fires.
    func flush() {
        saveTask?.cancel()
        saveTask = nil
        if let bytes = try? JSONEncoder().encode(data) { try? bytes.write(to: MediaFiles.storeFile, options: .atomic) }
    }

    /// Every change is written shortly afterwards; a burst of progress updates becomes one write.
    private func commit() {
        publish()
        saveTask?.cancel()
        let snapshot = data
        saveTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            if Task.isCancelled { return }
            await Task.detached(priority: .utility) {
                if let bytes = try? JSONEncoder().encode(snapshot) { try? bytes.write(to: MediaFiles.storeFile, options: .atomic) }
            }.value
        }
    }

    private func update(_ id: Int64, _ change: (inout DownloadTask) -> Void) {
        guard let index = data.tasks.firstIndex(where: { $0.id == id }) else { return }
        change(&data.tasks[index])
    }

    func task(_ id: Int64) -> DownloadTask? { data.tasks.first { $0.id == id } }

    // MARK: Tasks (TaskDao)

    /// Saves a link as "ready to start". Nothing downloads until it is started.
    func enqueue(url: String, site: String?) {
        let task = DownloadTask(id: data.nextTaskId, url: url, status: .pending, site: site, createdAt: Date())
        data.nextTaskId += 1
        data.tasks.append(task)
        commit()
    }

    func nextQueued() -> DownloadTask? { data.tasks.filter { $0.status == .queued }.min { $0.id < $1.id } }
    func countQueued() -> Int { data.tasks.filter { $0.status == .queued }.count }
    var hasQueued: Bool { data.tasks.contains { $0.status == .queued } }

    /// Moves one waiting link into the download queue. Returns false if it is no longer waiting.
    @discardableResult
    func start(_ id: Int64, folderName: String? = nil) -> Bool {
        guard task(id)?.status == .pending else { return false }
        update(id) {
            $0.status = .queued
            if let folderName { $0.folderName = folderName }
        }
        commit()
        return true
    }

    /// Moves every waiting link into the download queue. Returns how many were started.
    @discardableResult
    func startAll() -> Int {
        var count = 0
        for index in data.tasks.indices where data.tasks[index].status == .pending {
            data.tasks[index].status = .queued
            count += 1
        }
        if count > 0 { commit() }
        return count
    }

    /// Changes the link of one that has not started; it stays waiting. Returns false if it already started.
    @discardableResult
    func replacePendingLink(_ id: Int64, url: String, site: String?) -> Bool {
        guard task(id)?.status == .pending else { return false }
        update(id) { $0.url = url; $0.site = site }
        commit()
        return true
    }

    /// Returns false if the task is no longer queued, e.g. the user cancelled it in the meantime.
    @discardableResult
    func markDownloading(_ id: Int64) -> Bool {
        guard task(id)?.status == .queued else { return false }
        update(id) {
            $0.status = .downloading; $0.error = nil; $0.fileIndex = 0; $0.totalFiles = 0; $0.fileProgress = -1
        }
        // The count starts from what the library really holds for this link, so a refresh never adds to a stale number.
        fileSaved(id)
        return true
    }

    /// The download was stopped (network lost, app left): wait in the queue to be resumed.
    func pause(_ id: Int64) {
        guard task(id)?.status == .downloading else { return }
        update(id) {
            $0.status = .queued; $0.currentFile = nil; $0.fileIndex = 0; $0.totalFiles = 0; $0.fileProgress = -1
        }
        commit()
    }

    /// The number of files saved is what the library holds for the link, so it stays right after deletions and refreshes.
    func fileSaved(_ id: Int64) {
        guard let url = task(id)?.url else { return }
        let count = data.items.filter { $0.sourceUrl == url }.count
        update(id) { $0.filesDownloaded = count }
        commit()
    }

    func updateProgress(_ id: Int64, index: Int, total: Int, percent: Int, file: String) {
        guard task(id)?.status == .downloading else { return }
        update(id) { $0.fileIndex = index; $0.totalFiles = total; $0.fileProgress = percent; $0.currentFile = file }
        publish()   // progress is not worth a disk write of its own
    }

    func countSaved(_ id: Int64) -> Int {
        fileSaved(id)
        return task(id)?.filesDownloaded ?? 0
    }

    func finish(_ id: Int64, status: TaskStatus, error: String?, failedFiles: Int) {
        update(id) {
            $0.status = status; $0.error = error; $0.failedFiles = failedFiles; $0.currentFile = nil
            $0.fileIndex = 0; $0.totalFiles = 0; $0.fileProgress = -1; $0.finishedAt = Date()
        }
        commit()
    }

    @discardableResult
    func cancelIfQueued(_ id: Int64) -> Bool {
        guard task(id)?.status == .queued else { return false }
        update(id) { $0.status = .cancelled; $0.finishedAt = Date() }
        commit()
        return true
    }

    func resetInterrupted() {
        for index in data.tasks.indices where data.tasks[index].status == .downloading { data.tasks[index].status = .queued }
        commit()
    }

    func requeue(_ id: Int64) {
        update(id) { $0.status = .queued; $0.currentFile = nil; $0.error = nil; $0.failedFiles = 0; $0.finishedAt = nil; $0.hiddenFromQueue = nil }
        commit()
    }

    /// Gives a failed or cancelled task a new link and queues it again from a clean start. Returns false if the
    /// task is not in one of those states, so a running download is never touched.
    @discardableResult
    func replaceLink(_ id: Int64, url: String, site: String?) -> Bool {
        guard let status = task(id)?.status, status == .failed || status == .cancelled else { return false }
        update(id) {
            $0.url = url; $0.site = site; $0.status = .queued; $0.filesDownloaded = 0; $0.failedFiles = 0
            $0.currentFile = nil; $0.error = nil; $0.fileIndex = 0; $0.totalFiles = 0; $0.fileProgress = -1
            $0.finishedAt = nil; $0.hiddenFromQueue = nil
        }
        commit()
        return true
    }

    /// Failed links and links that finished with failed files or errors. Saved files are kept.
    /// Queues every link that is not already queued or running, whatever it did last. Returns how many were queued.
    @discardableResult
    func requeueAll() -> Int {
        var count = 0
        for index in data.tasks.indices {
            let status = data.tasks[index].status
            guard status == .pending || status == .completed || status == .failed || status == .cancelled else { continue }
            data.tasks[index].status = .queued; data.tasks[index].currentFile = nil
            data.tasks[index].error = nil; data.tasks[index].failedFiles = 0; data.tasks[index].finishedAt = nil; data.tasks[index].hiddenFromQueue = nil
            count += 1
        }
        if count > 0 { commit() }
        return count
    }

    func requeueFailed() {
        for index in data.tasks.indices {
            let task = data.tasks[index]
            if task.status == .failed || (task.status == .completed && (task.failedFiles > 0 || task.error != nil)) {
                data.tasks[index].status = .queued; data.tasks[index].currentFile = nil
                data.tasks[index].error = nil; data.tasks[index].failedFiles = 0; data.tasks[index].finishedAt = nil; data.tasks[index].hiddenFromQueue = nil
            }
        }
        commit()
    }

    func remove(_ id: Int64) {
        MediaFiles.deleteArchive(for: id)
        data.tasks.removeAll { $0.id == id }
        commit()
    }

    /// The Queue's "Clear finished": the links leave the Queue but stay on Home, which is the collection of links to
    /// refresh. A later Renew fetches only what the library does not have.
    func hideFinishedFromQueue() {
        for index in data.tasks.indices where data.tasks[index].isFinished { data.tasks[index].hiddenFromQueue = true }
        commit()
    }

    /// Settings > Clear history: really deletes the finished links, and the memory of what each had saved.
    func clearFinished() {
        let finished = data.tasks.filter(\.isFinished).map(\.id)
        finished.forEach(MediaFiles.deleteArchive)
        data.tasks.removeAll(where: \.isFinished)
        commit()
    }

    // MARK: Library (LibraryDao and LibraryRepository)

    /// Returns the id of the folder called `name`, creating it if needed.
    func ensureFolder(_ name: String) -> Int64 {
        if let existing = data.folders.first(where: { $0.name.caseInsensitiveCompare(name) == .orderedSame }) { return existing.id }
        let folder = Folder(id: data.nextFolderId, name: name)
        data.nextFolderId += 1
        data.folders.append(folder)
        return folder.id
    }

    /// The library folder a link's files are in, or nil while it has none.
    func folderName(forLink url: String) -> String? {
        guard let item = data.items.first(where: { $0.sourceUrl == url && $0.folderId != nil }) else { return nil }
        return data.folders.first(where: { $0.id == item.folderId })?.name
    }

    /// The other links that already use a folder with this name: ones saved in it, and ones given the name.
    func linksUsing(folder name: String, except taskId: Int64) -> [String] {
        guard let clean = cleanFolderName(name) else { return [] }
        var links: [String] = data.tasks.filter { $0.id != taskId && $0.folderName?.caseInsensitiveCompare(clean) == .orderedSame }.map(\.url)
        if let folder = data.folders.first(where: { $0.name.caseInsensitiveCompare(clean) == .orderedSame }) {
            links += data.items.filter { $0.folderId == folder.id }.map(\.sourceUrl)
        }
        var seen = Set<String>()
        return links.filter { seen.insert($0).inserted }
    }

    /// The direct addresses of the files the library already has for a link.
    func fileUrls(forSource sourceUrl: String) -> [String] {
        data.items.filter { $0.sourceUrl == sourceUrl }.compactMap(\.fileUrl).filter { !$0.isEmpty }
    }

    /// The names of the files the library already has for a link.
    func fileNames(forSource sourceUrl: String) -> [String] {
        data.items.filter { $0.sourceUrl == sourceUrl }.map(\.displayName)
    }

    /// A file a download already had is not fetched again: it gets the address it has now and, if an earlier download of the
    /// same link saved it (the link was removed and added again), it now belongs to this one. Returns true when handed over.
    @discardableResult
    func adoptFile(sourceUrl: String, name: String, fileUrl: String, taskId: Int64, folder: String = "") -> Bool {
        var changed = false, handedOver = false
        for index in data.items.indices where isSameFile(data.items[index], sourceUrl: sourceUrl, name: name, fileUrl: fileUrl) {
            if data.items[index].fileUrl != fileUrl { data.items[index].fileUrl = fileUrl; changed = true }
            if data.items[index].taskId != taskId { data.items[index].taskId = taskId; changed = true; handedOver = true }
            if !folder.isEmpty, moveToFolder(index, folder) { changed = true }
        }
        if changed { commit() }
        return handedOver
    }

    /// The same file of the same link: by its name (in one fixed spelling, so accents compare equal however they were stored)
    /// or by its direct address, which stays the same when a name that carries a position number changes.
    private func isSameFile(_ item: LibraryItem, sourceUrl: String, name: String, fileUrl: String) -> Bool {
        guard item.sourceUrl == sourceUrl else { return false }
        if item.displayName.precomposedStringWithCanonicalMapping == name.precomposedStringWithCanonicalMapping { return true }
        return !fileUrl.isEmpty && item.fileUrl == fileUrl
    }

    /// A file saved under an older folder name, met again by a refreshed link, goes into the folder gallery-dl names for it
    /// now: on disk and in the library. A folder this empties is removed, and its tag goes to the new folder if that has none.
    private func moveToFolder(_ index: Int, _ folder: String) -> Bool {
        let current = data.items[index].folderId
        if let existing = data.folders.first(where: { $0.name.caseInsensitiveCompare(folder) == .orderedSame }), existing.id == current { return false }
        let target = ensureFolder(folder)
        if let moved = MediaFiles.move(relative: data.items[index].relativePath, toFolder: folder) { data.items[index].relativePath = moved }
        data.items[index].folderId = target
        if let old = current, !data.items.contains(where: { $0.folderId == old }), let oldFolder = data.folders.first(where: { $0.id == old }) {
            if let tag = oldFolder.tag, let at = data.folders.firstIndex(where: { $0.id == target }), data.folders[at].tag == nil { data.folders[at].tag = tag }
            data.folders.removeAll { $0.id == old }
            MediaFiles.removeIfEmpty(folder: oldFolder.name)
        }
        return true
    }

    @discardableResult
    func addItem(relativePath: String, displayName: String, mimeType: String, sourceUrl: String, taskId: Int64, folderId: Int64, fileUrl: String? = nil) -> LibraryItem {
        let item = LibraryItem(
            id: data.nextItemId, relativePath: relativePath, displayName: displayName, mimeType: mimeType,
            sourceUrl: sourceUrl, taskId: taskId, folderId: folderId, addedAt: Date(), fileUrl: fileUrl,
        )
        data.nextItemId += 1
        data.items.append(item)
        commit()
        return item
    }

    /// Gives a folder a tag, or takes it off when `tag` is blank. Long tags are cut to 40 characters.
    func setFolderTag(_ id: Int64, tag: String) {
        guard let index = data.folders.firstIndex(where: { $0.id == id }) else { return }
        let cleaned = String(tag.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40))
        data.folders[index].tag = cleaned.isEmpty ? nil : cleaned
        commit()
    }

    /// Only folders that still contain items. Most recently updated first.
    func folderSummaries(query: String, newestFirst: Bool = true) -> [FolderSummary] {
        let needle = query.trimmingCharacters(in: .whitespaces)
        let byFolder = Dictionary(grouping: data.items.filter { $0.folderId != nil }, by: { $0.folderId! })
        let summaries = data.folders.compactMap { folder -> (FolderSummary, Date)? in
            guard let inside = byFolder[folder.id], !inside.isEmpty else { return nil }
            if !needle.isEmpty && !folder.name.localizedCaseInsensitiveContains(needle)
                && !(folder.tag ?? "").localizedCaseInsensitiveContains(needle) { return nil }
            let newest = inside.max { ($0.addedAt, $0.id) < ($1.addedAt, $1.id) }
            let summary = FolderSummary(id: folder.id, name: folder.name, itemCount: inside.count, cover: newest, sourceUrl: newest?.sourceUrl, tag: folder.tag)
            return (summary, inside.map(\.addedAt).max() ?? .distantPast)
        }
        .sorted { newestFirst ? $0.1 > $1.1 : $0.1 < $1.1 }
        .map(\.0)
        // Favorites stays at the top, whatever the date order.
        let ordered = summaries
        return ordered.filter { isFavoritesFolder($0.name) } + ordered.filter { !isFavoritesFolder($0.name) }
    }

    /// Copies files into the Favorites folder. The copies stay when the link or the folder they came from is deleted, and go
    /// only when deleted here. Each copy keeps the link and the file's own address. Returns how many were added; a file
    /// already in Favorites is skipped.
    func addFavorites(_ items: [LibraryItem]) -> Int {
        var added = 0
        for item in items where !item.sourceUrl.hasPrefix("favorite:") {
            let source = favoriteSource(item.sourceUrl)
            let name = item.displayName.precomposedStringWithCanonicalMapping
            if data.items.contains(where: {
                $0.sourceUrl == source && ($0.displayName.precomposedStringWithCanonicalMapping == name || (item.fileUrl != nil && $0.fileUrl == item.fileUrl))
            }) { continue }
            guard let saved = try? MediaFiles.copyFile(of: item, toFolder: favoritesFolder) else { continue }
            addItem(
                relativePath: saved.relative, displayName: item.displayName, mimeType: item.mimeType,
                sourceUrl: source, taskId: 0, folderId: ensureFolder(favoritesFolder), fileUrl: item.fileUrl,
            )
            added += 1
        }
        return added
    }

    func items(inFolder folderId: Int64, query: String, newestFirst: Bool = true) -> [LibraryItem] {
        let needle = query.trimmingCharacters(in: .whitespaces)
        return data.items
            .filter { $0.folderId == folderId && (needle.isEmpty || $0.displayName.localizedCaseInsensitiveContains(needle)) }
            .sorted { newestFirst ? ($0.addedAt, $0.id) > ($1.addedAt, $1.id) : ($0.addedAt, $0.id) < ($1.addedAt, $1.id) }
    }

    /// Removes the files as well as the library entries.
    func deleteItems(_ ids: Set<Int64>) {
        for item in data.items where ids.contains(item.id) { MediaFiles.remove(item) }
        ThumbnailStore.shared.delete(ids)
        PreviewStripStore.shared.delete(ids)
        data.items.removeAll { ids.contains($0.id) }
        commit()
    }

    /// Deletes every file saved for a link, then the folder itself.
    func deleteFolder(_ id: Int64) {
        let removed = data.items.filter { $0.folderId == id }
        for item in removed { MediaFiles.remove(item) }
        ThumbnailStore.shared.delete(removed.map(\.id))
        PreviewStripStore.shared.delete(removed.map(\.id))
        data.items.removeAll { $0.folderId == id }
        data.folders.removeAll { $0.id == id }
        commit()
    }

    /// Drops library entries whose file was deleted outside the app.
    func pruneMissing() {
        let before = data.items.count
        data.items.removeAll { !MediaFiles.exists($0) }
        if data.items.count != before { commit() }
        // Thumbnails whose item is gone are cleaned up too.
        ThumbnailStore.shared.prune(keeping: Set(data.items.map(\.id)))
        PreviewStripStore.shared.prune(keeping: Set(data.items.map(\.id)))
    }
}
