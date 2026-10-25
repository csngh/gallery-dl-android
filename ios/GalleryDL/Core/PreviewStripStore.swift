import AVFoundation
import UIKit

extension Notification.Name {
    /// Posted (on the main thread) when a preview strip has been saved; the object is the library item's id.
    static let previewStripReady = Notification.Name("previewStripReady")
}

/// A short silent preview of a video, kept as a row of small JPEG frames in one file: `GDLS`, a version, the frame rate,
/// the frame count, a table of where each frame starts, then the frames. Playing it means decoding one small frame at a
/// time, so it needs no video decoder and only a megabyte or two of memory.
final class Strip {
    let frameCount: Int
    let fps: Double
    private let handle: FileHandle
    private let offsets: [UInt64]
    private let lock = NSLock()

    init?(url: URL) {
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        let head = handle.readData(ofLength: 8)
        guard head.count == 8, head.prefix(4) == Data("GDLS".utf8), head[4] == 1 else { try? handle.close(); return nil }
        let count = Int(head[6]) | Int(head[7]) << 8
        let table = handle.readData(ofLength: (count + 1) * 4)
        guard count > 0, table.count == (count + 1) * 4 else { try? handle.close(); return nil }
        self.handle = handle
        self.fps = Double(head[5])
        self.frameCount = count
        self.offsets = (0...count).map { i in
            let b = table.startIndex + i * 4
            return UInt64(table[b]) | UInt64(table[b + 1]) << 8 | UInt64(table[b + 2]) << 16 | UInt64(table[b + 3]) << 24
        }
    }

    deinit { try? handle.close() }

    func frame(_ index: Int) -> UIImage? {
        guard index >= 0, index < frameCount else { return nil }
        lock.lock()
        handle.seek(toFileOffset: offsets[index])
        let data = handle.readData(ofLength: Int(offsets[index + 1] - offsets[index]))
        lock.unlock()
        guard let image = UIImage(data: data) else { return nil }
        return image.preparingForDisplay() ?? image
    }
}

/// How many preview strips are waiting to be made and how many are done, for the progress bar in the library. The count
/// starts again from zero a moment after everything is finished.
@MainActor
final class PreviewProgress: ObservableObject {
    static let shared = PreviewProgress()
    @Published private(set) var total = 0
    @Published private(set) var done = 0

    func added() { total += 1 }

    func removed(_ count: Int) {
        total = max(0, total - count)
        settle()
    }

    func finished() {
        done += 1
        settle()
    }

    private func settle() {
        guard total > 0 else { done = 0; return }
        guard done >= total else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in
            guard let self, self.done >= self.total else { return }
            self.total = 0
            self.done = 0
        }
    }
}

/// Makes and keeps the preview strips. One is made for each video when it is downloaded (and the first time an older
/// video is shown), from a random 3 second window of the video, in the background one at a time.
///
/// They live in Application Support, which iOS never empties on its own, out of backups, and are removed only when their
/// video or folder is deleted, or when the user clears the cache.
final class PreviewStripStore {
    static let shared = PreviewStripStore()

    /// A strip keeps the video's own frame rate, up to this, so motion looks as it does in the video.
    static let maxFPS = 30.0
    static let windowSeconds = 3.0
    private let edge: CGFloat = 360

    private let directory: URL
    private let queue: OperationQueue = {
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .utility
        return queue
    }()
    private var pending = Set<Int64>()
    private let lock = NSLock()

    init() {
        // The first version kept 12 frames a second, which looked choppy; those clips are discarded and made again.
        try? FileManager.default.removeItem(at: MediaFiles.support.appendingPathComponent("previews", isDirectory: true))
        directory = MediaFiles.support.appendingPathComponent("previews2", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var url = directory
        try? url.setResourceValues(values)
    }

    private func file(for id: Int64) -> URL { directory.appendingPathComponent("\(id).strip") }

    /// The strip of a video, if it has been made.
    func strip(for id: Int64) -> Strip? {
        let url = file(for: id)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        if let strip = Strip(url: url) { return strip }
        // A file that cannot be read (cut short, or from an older version) is removed so it is made again.
        try? FileManager.default.removeItem(at: url)
        return nil
    }

    private var failed = Set<Int64>()
    private var sweepOps: [(id: Int64, operation: Operation)] = []

    /// Makes the strip of a video in the background, unless it exists or is already being made. A video that is on screen
    /// jumps ahead of the whole-library sweep; the sweep (`sweep: true`) goes at the back.
    func requestMake(_ item: LibraryItem, sweep: Bool = false) {
        guard item.isVideo, !FileManager.default.fileExists(atPath: file(for: item.id).path) else { return }
        lock.lock()
        if failed.contains(item.id) { lock.unlock(); return }
        if !pending.insert(item.id).inserted {
            // Already waiting as part of the sweep: something on screen needs it now.
            if !sweep { sweepOps.first { $0.id == item.id }?.operation.queuePriority = .veryHigh }
            lock.unlock()
            return
        }
        let operation = BlockOperation()
        operation.queuePriority = sweep ? .low : .high
        operation.addExecutionBlock { [unowned self, unowned operation] in
            let made = make(item)
            lock.lock()
            pending.remove(item.id)
            sweepOps.removeAll { $0.operation === operation }
            if !made { failed.insert(item.id) }
            lock.unlock()
            Task { @MainActor in
                PreviewProgress.shared.finished()
                if made { NotificationCenter.default.post(name: .previewStripReady, object: item.id) }
            }
        }
        if sweep { sweepOps.append((item.id, operation)) }
        lock.unlock()
        Task { @MainActor in PreviewProgress.shared.added() }
        queue.addOperation(operation)
    }

    /// Queues a strip for every video that does not have one yet. Called while the library is open.
    func requestAll(_ items: [LibraryItem]) {
        for item in items where item.isVideo { requestMake(item, sweep: true) }
    }

    /// Takes back the sweep work that has not started, for when the library is left. The video being made, and anything
    /// a tile on screen asked for, still finishes.
    func cancelSweep() {
        lock.lock()
        var removed = 0
        for entry in sweepOps where !entry.operation.isExecuting && !entry.operation.isFinished {
            entry.operation.cancel()
            pending.remove(entry.id)
            removed += 1
        }
        sweepOps.removeAll { $0.operation.isCancelled }
        lock.unlock()
        if removed > 0 { Task { @MainActor in PreviewProgress.shared.removed(removed) } }
    }

    private static let imageContext = CIContext()

    private func make(_ item: LibraryItem) -> Bool {
        let asset = AVURLAsset(url: item.fileURL)
        let duration = asset.duration.seconds
        guard duration.isFinite, duration > 0.2, let track = asset.tracks(withMediaType: .video).first else { return false }
        // Shorter videos give shorter strips; longer ones give a random window, away from the very start and end.
        let sourceFPS = Double(track.nominalFrameRate)
        let fps = sourceFPS >= 8 ? min(Self.maxFPS, sourceFPS.rounded()) : Self.maxFPS
        let count = min(Int(Self.windowSeconds * fps), max(6, Int(duration * fps)))
        let span = Double(count) / fps
        let latestStart = max(0, duration - span - 0.15)
        let start = latestStart > 0.3 ? Double.random(in: 0.15...latestStart) : 0

        // The window is read once, front to back, so every frame is decoded a single time. Asking for each frame on its
        // own would restart the decoder at the previous keyframe every time, which is many times slower.
        guard let reader = try? AVAssetReader(asset: asset) else { return false }
        reader.timeRange = CMTimeRange(start: CMTime(seconds: start, preferredTimescale: 600), duration: CMTime(seconds: span + 0.2, preferredTimescale: 600))
        let output = AVAssetReaderTrackOutput(track: track, outputSettings: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA])
        output.alwaysCopiesSampleData = false
        guard reader.canAdd(output) else { return false }
        reader.add(output)
        guard reader.startReading() else { return false }
        defer { reader.cancelReading() }

        let orientation = Self.orientation(of: track.preferredTransform)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let quality = [kCGImageDestinationLossyCompressionQuality as CIImageRepresentationOption: 0.72]
        var jpegs: [Data] = []
        var nextTime = start
        while jpegs.count < count, let sample = output.copyNextSampleBuffer() {
            let time = CMSampleBufferGetPresentationTimeStamp(sample).seconds
            guard time + 0.0005 >= nextTime, let buffer = CMSampleBufferGetImageBuffer(sample) else { continue }
            nextTime += 1 / fps
            var image = CIImage(cvPixelBuffer: buffer).oriented(orientation)
            let scale = edge / max(image.extent.width, image.extent.height)
            if scale < 1 { image = image.transformed(by: CGAffineTransform(scaleX: scale, y: scale)) }
            if let jpeg = Self.imageContext.jpegRepresentation(of: image, colorSpace: colorSpace, options: quality) { jpegs.append(jpeg) }
        }
        guard jpegs.count >= max(4, count / 2) else { return false }

        var out = Data("GDLS".utf8)
        out.append(contentsOf: [1, UInt8(fps), UInt8(jpegs.count & 0xFF), UInt8(jpegs.count >> 8)])
        var position = UInt32(8 + (jpegs.count + 1) * 4)
        var offsets: [UInt32] = []
        for jpeg in jpegs { offsets.append(position); position += UInt32(jpeg.count) }
        offsets.append(position)
        for offset in offsets { out.append(contentsOf: [UInt8(offset & 0xFF), UInt8((offset >> 8) & 0xFF), UInt8((offset >> 16) & 0xFF), UInt8(offset >> 24)]) }
        jpegs.forEach { out.append($0) }

        let temp = directory.appendingPathComponent("\(item.id).tmp")
        guard (try? out.write(to: temp, options: .atomic)) != nil else { return false }
        try? FileManager.default.removeItem(at: file(for: item.id))
        return (try? FileManager.default.moveItem(at: temp, to: file(for: item.id))) != nil
    }

    /// Phone videos are often stored sideways with a rotation flag; frames read from the file are not rotated.
    private static func orientation(of t: CGAffineTransform) -> CGImagePropertyOrientation {
        switch (Int(t.a.rounded()), Int(t.b.rounded()), Int(t.c.rounded()), Int(t.d.rounded())) {
        case (0, 1, -1, 0): return .right
        case (0, -1, 1, 0): return .left
        case (-1, 0, 0, -1): return .down
        default: return .up
        }
    }

    // MARK: Keeping the cache tidy

    func delete(_ ids: some Sequence<Int64>) {
        for id in ids { try? FileManager.default.removeItem(at: file(for: id)) }
    }

    func prune(keeping ids: Set<Int64>) {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        for name in names {
            let id = Int64((name as NSString).deletingPathExtension)
            if id == nil || !ids.contains(id!) { try? FileManager.default.removeItem(at: directory.appendingPathComponent(name)) }
        }
    }

    func sizeBytes() -> Int64 {
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        return names.reduce(Int64(0)) { total, name in
            let size = (try? FileManager.default.attributesOfItem(atPath: directory.appendingPathComponent(name).path)[.size] as? Int64) ?? 0
            return total + (size ?? 0)
        }
    }

    func clear() {
        // Videos that failed once get another chance after the cache is cleared.
        lock.lock()
        failed.removeAll()
        lock.unlock()
        let names = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        for name in names { try? FileManager.default.removeItem(at: directory.appendingPathComponent(name)) }
    }
}
