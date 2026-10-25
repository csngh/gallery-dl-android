import Foundation

/// Watches how full the iPhone's storage is. At 99% or more downloads pause and the app says so; they carry on by
/// themselves once the storage is below 98.5% again (the gap keeps it from flipping back and forth near the limit).
@MainActor
final class StorageMonitor: ObservableObject {
    @Published private(set) var isFull = false
    @Published private(set) var usedPercent = 0
    /// Called when the storage has gone from too full to fine, so paused downloads can start again.
    var onResume: (() -> Void)?

    private var timer: Timer?

    init() {
        refresh()
        timer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
    }

    /// Looks at the storage now and returns whether it is too full.
    @discardableResult
    func refresh() -> Bool {
        let used = usedFraction()
        usedPercent = Int((used * 100).rounded())
        let wasFull = isFull
        isFull = wasFull ? used >= 0.985 : used >= 0.99
        if wasFull && !isFull { onResume?() }
        return isFull
    }

    private func usedFraction() -> Double {
        #if DEBUG
        // `-testStorageFull 1` pretends the storage is 99.5% full, to check the warning and the paused queue.
        if UserDefaults.standard.bool(forKey: "testStorageFull") { return 0.995 }
        #endif
        guard let attributes = try? FileManager.default.attributesOfFileSystem(forPath: NSHomeDirectory()),
              let total = (attributes[.systemSize] as? NSNumber)?.doubleValue, total > 0,
              let free = (attributes[.systemFreeSize] as? NSNumber)?.doubleValue else { return 0 }
        return 1 - free / total
    }
}
