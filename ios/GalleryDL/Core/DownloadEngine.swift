import Foundation
import UIKit

/// Drains the download queue one task at a time, running gallery-dl in-process. This is the iOS counterpart of
/// Android's DownloadWorker and DownloadRepository.
///
/// iOS has no foreground service: downloads run while the app is open, and for a short while after it is left
/// (a background task). When that time runs out the running link goes back to the queue, and a notification says so.
@MainActor
final class DownloadEngine {
    private let store: AppStore
    private let settings: SettingsStore
    private let network: NetworkMonitor
    private let storage: StorageMonitor
    private let python = PythonRuntime.shared

    private var draining = false
    private var currentId: Int64?
    private var cancelRequested = Set<Int64>()
    /// Runs that were stopped to wait in the queue (network lost, app left); they resume instead of failing.
    private var pausedRuns = Set<Int64>()
    /// True while no new link may start (no Wi-Fi with Wi-Fi only on, or the background time has run out).
    private var holdQueue = false
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid

    init(store: AppStore, settings: SettingsStore, network: NetworkMonitor, storage: StorageMonitor) {
        self.store = store
        self.settings = settings
        self.network = network
        self.storage = storage
        storage.onResume = { [weak self] in self?.scheduleDrain() }
        network.onChange = { [weak self] in self?.networkChanged() }
    }

    // MARK: Queue operations (DownloadRepository)

    func start(_ id: Int64, folderName: String? = nil) { if store.start(id, folderName: folderName) { scheduleDrain() } }
    func startAll() { if store.startAll() > 0 { scheduleDrain() } }

    /// Stops everything at once: the links waiting for their turn are cancelled directly, so the queue cannot move on to the
    /// next one, and then the download that is running. Each link keeps its record of what was saved.
    /// The folder gallery-dl would save a link in, looked up before the link starts; "" when it cannot be found out.
    func suggestFolder(for url: String) async -> String {
        let reply = try? await python.call("preview", ["url": url, "options": settings.options.toJson(cookiesPath: settings.cookiesPath)])
        return reply?["folder"] as? String ?? ""
    }

    func cancelAll() {
        for task in store.tasks where task.status == .queued || task.status == .downloading { cancel(task.id) }
    }

    func cancel(_ id: Int64) {
        if store.cancelIfQueued(id) { return }
        cancelRequested.insert(id)
        if currentId == id { stopPython() }
    }

    func retry(_ id: Int64) {
        store.requeue(id)
        scheduleDrain()
    }

    /// Replaces the link of a failed or cancelled task and queues it again. The old archive no longer applies.
    func editLink(_ id: Int64, url: String, site: String?) {
        // A link that has not started just changes; it keeps waiting for the user to start it.
        if store.replacePendingLink(id, url: url, site: site) { return }
        MediaFiles.deleteArchive(for: id)
        if store.replaceLink(id, url: url, site: site) { scheduleDrain() }
    }

    /// The Renew button: every link checks for new files, and links not yet started begin.
    func renewAll() { if store.requeueAll() > 0 { scheduleDrain() } }

    func retryAllFailed() {
        store.requeueFailed()
        scheduleDrain()
    }

    /// Applies a change of the Wi-Fi only setting to work that is already waiting. A download in progress is
    /// paused and carries on from the files the library already has, so no file is fetched twice.
    func applyNetworkSetting() { networkChanged() }

    /// Called at launch: anything that was interrupted last time waits in the queue again.
    func resumeOnLaunch() {
        store.resetInterrupted()
        scheduleDrain()
    }

    // MARK: Scheduling

    private var networkAllows: Bool { !settings.options.wifiOnly || network.isUnmetered }

    private func networkChanged() {
        if !networkAllows {
            holdQueue = true
            if let id = currentId { pausedRuns.insert(id); stopPython() }
        } else {
            holdQueue = false
            scheduleDrain()
        }
    }

    func scheduleDrain() {
        guard !draining, networkAllows, !holdQueue, !storage.refresh() else { return }
        draining = true
        Task { await drain() }
    }

    private func drain() async {
        // Nothing new starts while the storage is almost full; the queue is kept and carries on when there is room.
        while !holdQueue, networkAllows, !storage.refresh(), let task = store.nextQueued() {
            await process(task)
        }
        if storage.isFull, store.hasQueued { Notifications.storageFull(percent: storage.usedPercent) }
        draining = false
        endBackgroundTaskIfIdle()
    }

    private func stopPython() { Task { _ = try? await python.call("cancel") } }

    // MARK: One link

    private final class Run {
        var importFailures = 0
        var importError: String?
        var linkFolder = ""
        /// The folder the user chose for this link; every file goes there.
        var pinnedFolder: String?
        var scratch = URL(fileURLWithPath: "/")
    }

    private func process(_ task: DownloadTask) async {
        cancelRequested.remove(task.id)
        guard store.markDownloading(task.id) else { return }   // cancelled while waiting its turn
        currentId = task.id
        defer { currentId = nil }

        let run = Run()
        run.linkFolder = linkFolderName(task.url)
        run.pinnedFolder = task.folderName
        run.scratch = MediaFiles.scratch(for: task.id)
        try? FileManager.default.removeItem(at: run.scratch)
        try? FileManager.default.createDirectory(at: run.scratch, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: MediaFiles.archives, withIntermediateDirectories: true)

        // The options as they are when this download starts; later changes apply to the next one.
        // What the library has for this link is not fetched again; anything missing from it is. The archive path only lets
        // the bridge remove a record an earlier version left.
        let arguments: [String: Any] = [
            "url": task.url,
            "base_dir": run.scratch.path,
            "archive_path": MediaFiles.archive(for: task.id).path,
            "options": settings.options.toJson(cookiesPath: settings.cookiesPath),
            // Files the library already has for this link are not fetched again (the link may have been removed and added again).
            "known": ["names": store.fileNames(forSource: task.url), "urls": store.fileUrls(forSource: task.url)],
        ]

        let poller = Task { [weak self] in
            while !Task.isCancelled {
                await self?.poll(task, run)
                await self?.checkStorage()
                try? await Task.sleep(nanoseconds: 250_000_000)
            }
        }
        var outcome: [String: Any]?
        var failure: String?
        do {
            outcome = try await python.call("download", arguments)
        } catch {
            failure = error.localizedDescription
        }
        // Wait for a poll that is already in flight to finish importing its files, before the last look and the cleanup.
        poller.cancel()
        await poller.value
        await poll(task, run)
        try? FileManager.default.removeItem(at: run.scratch)

        let userCancelled = cancelRequested.remove(task.id) != nil
        if !userCancelled && pausedRuns.remove(task.id) != nil {
            // Stopped by the system or the network, not by the user: keep it queued. The resumed run
            // skips the files the library already has.
            store.pause(task.id)
            return
        }

        let saved = store.countSaved(task.id)
        let failedFiles = (outcome?["failed"] as? Int ?? 0) + run.importFailures
        let errors = outcome?["errors"] as? [String] ?? []
        var seen = Set<String>()
        let messages = ((run.importError.map { [$0] } ?? []) + errors).filter { seen.insert($0).inserted }
            .joined(separator: "\n").prefix(2_000)
        let message = messages.isEmpty ? nil : String(messages)

        let status: TaskStatus
        let error: String?
        if userCancelled {
            (status, error) = (.cancelled, nil)
        } else if let failure {
            (status, error) = (.failed, failure)
        } else if saved == 0 {
            (status, error) = (.failed, message ?? S.errorNoFiles)
        } else if failedFiles > 0 || (outcome?["status"] as? Int ?? 0) != 0 {
            (status, error) = (.completed, message ?? S.partialErrors)
        } else {
            (status, error) = (.completed, nil)
        }
        store.finish(task.id, status: status, error: error, failedFiles: failedFiles)
        if let finished = store.task(task.id) {
            Notifications.finished(task: finished, status: status, filesSaved: saved, failedFiles: failedFiles)
        }
    }

    private var lastStorageCheck = Date.distantPast

    /// Looked at every couple of seconds: a download that fills the storage is stopped instead of failing.
    private func checkStorage() {
        guard let id = currentId, Date().timeIntervalSince(lastStorageCheck) >= 2 else { return }
        lastStorageCheck = Date()
        if storage.refresh() { pausedRuns.insert(id); stopPython() }
    }

    /// Reads the progress and the files gallery-dl has finished since the last look, and files them in the library.
    private func poll(_ task: DownloadTask, _ run: Run) async {
        guard let state = try? await python.call("poll") else { return }
        store.updateProgress(
            task.id,
            index: state["handled"] as? Int ?? 0,
            total: state["total"] as? Int ?? 0,
            percent: state["percent"] as? Int ?? -1,
            file: state["name"] as? String ?? "",
        )
        for item in state["files"] as? [[String: Any]] ?? [] {
            if let name = item["skipped"] as? String, let url = item["url"] as? String, !url.isEmpty {
                let folder = downloadFolder(run.pinnedFolder ?? item["folder"] as? String ?? "")
                if store.adoptFile(sourceUrl: task.url, name: name, fileUrl: url, taskId: task.id, folder: folder) { store.fileSaved(task.id) }
                continue
            }
            guard let path = item["path"] as? String else { continue }
            do {
                try importFile(URL(fileURLWithPath: path), task: task, run: run, fileUrl: item["url"] as? String)
            } catch {
                run.importError = error.localizedDescription
                run.importFailures += 1
            }
        }
    }

    private func importFile(_ file: URL, task: DownloadTask, run: Run, fileUrl: String?) throws {
        // The folder is the innermost one gallery-dl made for this file, exactly as it named it. A file gallery-dl left
        // directly in the root has no folder of its own and goes in one named after the link.
        let folder = downloadFolder(run.pinnedFolder ?? MediaFiles.innermostFolder(of: file, below: run.scratch) ?? run.linkFolder)
        let saved = try MediaFiles.importFile(file, folder: folder)
        // Looked up per file, so deleting the folder in the library mid-download cannot leave a dangling id.
        let folderId = store.ensureFolder(folder)
        let item = store.addItem(
            relativePath: saved.relative, displayName: saved.name,
            mimeType: mimeType(forExtension: (saved.name as NSString).pathExtension),
            sourceUrl: task.url, taskId: task.id, folderId: folderId,
            fileUrl: fileUrl.flatMap { $0.isEmpty ? nil : $0 },
        )
        store.fileSaved(task.id)
        // The silent preview of a new video is made right away, in the background, one at a time.
        PreviewStripStore.shared.requestMake(item)
    }

    // MARK: Leaving the app

    func sceneBecameActive() {
        endBackgroundTask()
        holdQueue = !networkAllows
        scheduleDrain()
    }

    func sceneEnteredBackground() {
        guard currentId != nil || store.hasQueued, backgroundTask == .invalid else { return }
        backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "downloads") { [weak self] in
            // The system calls this on the main thread; hop onto the main actor (assumeIsolated needs iOS 17).
            Task { @MainActor in self?.backgroundTimeExpired() }
        }
    }

    private func backgroundTimeExpired() {
        holdQueue = true
        if let id = currentId { pausedRuns.insert(id); stopPython() }
        let waiting = store.countQueued() + (currentId == nil ? 0 : 1)
        if waiting > 0 { Notifications.paused(waiting: waiting) }
        endBackgroundTask()
    }

    private func endBackgroundTaskIfIdle() {
        if !store.hasQueued || holdQueue { endBackgroundTask() }
    }

    private func endBackgroundTask() {
        guard backgroundTask != .invalid else { return }
        UIApplication.shared.endBackgroundTask(backgroundTask)
        backgroundTask = .invalid
    }
}
