import AVFoundation
import ImageIO
import UIKit

/// Small still images of the library's pictures and videos, made once and kept on disk, so the library does not
/// decode every file again each time it opens or scrolls.
///
/// The files live in Application Support, which iOS never empties on its own (unlike the Caches folder), and are
/// kept out of backups. They are removed only when their file or folder is deleted, or when the user clears them.
final class ThumbnailStore {
    static let shared = ThumbnailStore()

    private let directory: URL
    /// A fast layer on top of the disk files, bounded so it can never grow large.
    private let memory = NSCache<NSNumber, UIImage>()
    /// At most two thumbnails are made at the same time, so scrolling a big folder never floods memory.
    private let workers: OperationQueue = {
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 2
        queue.qualityOfService = .utility
        return queue
    }()
    private var inFlight: [Int64: Task<UIImage?, Never>] = [:]
    private let lock = NSLock()
    private let edge: CGFloat = 480

    init() {
        directory = MediaFiles.support.appendingPathComponent("thumbnails", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var url = directory
        try? url.setResourceValues(values)
        memory.totalCostLimit = 48 * 1024 * 1024
        memory.countLimit = 150
    }

    private func file(for id: Int64) -> URL { directory.appendingPathComponent("\(id).jpg") }

    /// The thumbnail of `item`, made first if it does not exist yet. Nil when the file cannot be decoded.
    func image(for item: LibraryItem) async -> UIImage? {
        let id = item.id
        if let cached = memory.object(forKey: NSNumber(value: id)) { return cached }

        let (task, isNew) = taskFor(id) { [self] in
            Task<UIImage?, Never> {
                let image: UIImage? = await withCheckedContinuation { continuation in
                    workers.addOperation { continuation.resume(returning: self.loadOrMake(item)) }
                }
                if let image, let cg = image.cgImage {
                    memory.setObject(image, forKey: NSNumber(value: id), cost: cg.bytesPerRow * cg.height)
                }
                return image
            }
        }
        let result = await task.value
        if isNew { finished(id) }
        return result
    }

    /// The task already making this thumbnail, or a new one. Done in plain synchronous code so the lock is never held
    /// across a suspension.
    private func taskFor(_ id: Int64, make: () -> Task<UIImage?, Never>) -> (Task<UIImage?, Never>, Bool) {
        lock.lock()
        defer { lock.unlock() }
        if let running = inFlight[id] { return (running, false) }
        let task = make()
        inFlight[id] = task
        return (task, true)
    }

    private func finished(_ id: Int64) {
        lock.lock()
        inFlight[id] = nil
        lock.unlock()
    }

    private func loadOrMake(_ item: LibraryItem) -> UIImage? {
        let target = file(for: item.id)
        if let data = try? Data(contentsOf: target), let image = UIImage(data: data) {
            return image.preparingForDisplay() ?? image
        }
        guard let cg = item.isVideo ? videoFrame(item) : imageThumbnail(item),
              let jpeg = UIImage(cgImage: cg).jpegData(compressionQuality: 0.85) else { return nil }
        try? jpeg.write(to: target, options: .atomic)
        let image = UIImage(cgImage: cg)
        return image.preparingForDisplay() ?? image
    }

    /// Decoded straight to thumbnail size, so a large photo is never held in memory at full size.
    private func imageThumbnail(_ item: LibraryItem) -> CGImage? {
        guard let source = CGImageSourceCreateWithURL(item.fileURL as CFURL, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: edge,
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    }

    private func videoFrame(_ item: LibraryItem) -> CGImage? {
        if item.isWebM {
            // The system cannot open WebM, so the app's own decoder makes the picture, a little way in like below.
            return WebMFrames.image(of: item.fileURL, near: 1.0, edge: edge)
        }
        let asset = AVURLAsset(url: item.fileURL)
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: edge, height: edge)
        // A little way in, to skip a black first frame.
        let seconds = min(1.0, max(0, asset.duration.seconds / 2))
        let time = CMTime(seconds: seconds.isFinite ? seconds : 0, preferredTimescale: 600)
        return try? generator.copyCGImage(at: time, actualTime: nil)
    }

    // MARK: Keeping the cache tidy

    /// Removes the thumbnails of deleted files.
    func delete(_ ids: some Sequence<Int64>) {
        for id in ids {
            memory.removeObject(forKey: NSNumber(value: id))
            try? FileManager.default.removeItem(at: file(for: id))
        }
    }

    /// Removes every thumbnail that no longer has a library item.
    func prune(keeping ids: Set<Int64>) {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        for name in names {
            let id = Int64((name as NSString).deletingPathExtension)
            if id == nil || !ids.contains(id!) {
                try? FileManager.default.removeItem(at: directory.appendingPathComponent(name))
                if let id { memory.removeObject(forKey: NSNumber(value: id)) }
            }
        }
    }

    func sizeBytes() -> Int64 {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        return names.reduce(Int64(0)) { total, name in
            let size = (try? FileManager.default.attributesOfItem(atPath: directory.appendingPathComponent(name).path)[.size] as? Int64) ?? 0
            return total + (size ?? 0)
        }
    }

    /// The "Clear thumbnail cache" button. They are made again the next time the library needs them.
    func clear() {
        memory.removeAllObjects()
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        for name in names { try? FileManager.default.removeItem(at: directory.appendingPathComponent(name)) }
    }
}
