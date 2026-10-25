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
    private let python = PythonRuntime.shared

    private var draining = false
    private var currentId: Int64?
    private var cancelRequested = Set<Int64>()
    /// Runs that were stopped to wait in the queue (network lost, app left); they resume instead of failing.
    private var pausedRuns = Set<Int64>()
    /// True while no new link may start (no Wi-Fi with Wi-Fi only on, or the background time has run out).
    private var holdQueue = false
    private var backgroundTask: UIBackgroundTaskIdentifier = .invalid

    init(store: AppStore, settings: SettingsStore, network: NetworkMonitor) {
        self.store = store
        self.settings = settings
        self.network = network
        network.onChange = { [weak self] in self?.networkChanged() }
    }

    // MARK: Queue operations (DownloadRepository)

    func start(_ id: Int64) { if store.start(id) { scheduleDrain() } }
    func startAll() { if store.startAll() > 0 { scheduleDrain() } }

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
    /// paused and carries on from its archive, so no file is fetched twice.
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
        guard !draining, networkAllows, !holdQueue else { return }
        draining = true
        Task { await drain() }
    }

    private func drain() async {
        while !holdQueue, networkAllows, let task = store.nextQueued() {
            await process(task)
        }
        draining = false
        endBackgroundTaskIfIdle()
    }

    private func stopPython() { Task { _ = try? await python.call("cancel") } }

    // MARK: One link

    private final class Run {
        var importFailures = 0
        var importError: String?
        var linkFolder = ""
        var scratch = URL(fileURLWithPath: "/")
    }

    private func process(_ task: DownloadTask) async {
        cancelRequested.remove(task.id)
        guard store.markDownloading(task.id) else { return }   // cancelled while waiting its turn
        currentId = task.id
        defer { currentId = nil }

        let run = Run()
        run.linkFolder = linkFolderName(task.url)
        run.scratch = MediaFiles.scratch(for: task.id)
        try? FileManager.default.removeItem(at: run.scratch)
        try? FileManager.default.createDirectory(at: run.scratch, withIntermediateDirectories: true)
        try? FileManager.default.createDirectory(at: MediaFiles.archives, withIntermediateDirectories: true)

        // The options as they are when this download starts; later changes apply to the next one.
        // One archive per link: a retry only fetches the files that are still missing.
        let arguments: [String: Any] = [
            "url": task.url,
            "base_dir": run.scratch.path,
            "archive_path": MediaFiles.archive(for: task.id).path,
            "options": settings.options.toJson(cookiesPath: settings.cookiesPath),
        ]

        let poller = Task { [weak self] in
            while !Task.isCancelled {
                await self?.poll(task, run)
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
            // Stopped by the system or the network, not by the user: keep it queued. The archive makes the
            // resumed run skip the files that were already saved.
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
            guard let path = item["path"] as? String else { continue }
            do {
                try importFile(URL(fileURLWithPath: path), task: task, run: run)
            } catch {
                run.importError = error.localizedDescription
                run.importFailures += 1
            }
        }
    }

    private func importFile(_ file: URL, task: DownloadTask, run: Run) throws {
        let saved = try MediaFiles.importFile(file, scratchRoot: run.scratch, linkFolder: run.linkFolder)
        // Looked up per file, so deleting the folder in the library mid-download cannot leave a dangling id.
        let folderId = store.ensureFolder(run.linkFolder)
        let item = store.addItem(
            relativePath: saved.relative, displayName: saved.name,
            mimeType: mimeType(forExtension: (saved.name as NSString).pathExtension),
            sourceUrl: task.url, taskId: task.id, folderId: folderId,
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
