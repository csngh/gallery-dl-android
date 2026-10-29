import Foundation

/// One compressed frame of a WebM file: where it sits in the file and when it is shown.
struct WebMPacket {
    let time: Double
    let offset: Int
    let size: Int
    let isKey: Bool
}

struct WebMVideoTrack {
    let number: Int
    let isVP9: Bool
    let width: Int
    let height: Int
}

struct WebMAudioTrack {
    let number: Int
    let channels: Int
    let sampleRate: Double
    /// "A_OPUS", "A_VORBIS" and so on, as the file names it.
    let codec: String
}

/// A WebM (Matroska) file read straight from disk, with every video and sound frame noted down so that playing, seeking
/// and making previews never have to search. The file itself is mapped, not copied.
///
/// Noting the frames means touching a spot in the file for every one of them, which on a big video that is not in the
/// system's cache takes seconds. So the player opens it `progressive`: the first frames are ready at once and the rest is
/// noted in the background while the video plays. Without that flag the whole file is read before `init` returns.
final class WebMFile {
    private let base: UnsafeRawPointer
    private let length: Int
    private let descriptor: Int32
    private let progressive: Bool

    /// Guards everything below that the background reader also touches.
    private let condition = NSCondition()
    private var durationValue = 0.0
    private var videoStore: [WebMPacket] = []
    private var audioStore: [WebMPacket] = []
    private var firstKey: Int?
    private var newestVideoTime = 0.0
    private var indexed = false
    private var cancelled = false
    private var handedOff = false
    private var onGrow: (() -> Void)?
    private var onComplete: (() -> Void)?

    /// What the file says its length is, or where its last frame is when it says nothing.
    var duration: Double { condition.lock(); defer { condition.unlock() }; return durationValue }
    /// Set while the headers are read, before any frame is noted, and never changed after `init` returns.
    private(set) var video: WebMVideoTrack?
    private(set) var audio: WebMAudioTrack?

    /// Nil when the file cannot be read or has no VP8 or VP9 video.
    init?(url: URL, progressive: Bool = false) {
        let fd = open(url.path, O_RDONLY)
        guard fd >= 0 else { return nil }
        var info = stat()
        guard fstat(fd, &info) == 0, info.st_size > 64,
              let map = mmap(nil, Int(info.st_size), PROT_READ, MAP_PRIVATE, fd, 0), map != MAP_FAILED else { close(fd); return nil }
        descriptor = fd
        base = UnsafeRawPointer(map)
        length = Int(info.st_size)
        self.progressive = progressive
        guard scan(), video != nil else { cancelIndexing(); return nil }
        if progressive { waitForFirstKey() } else { finishScan() }
        guard videoCount > 0 else { cancelIndexing(); return nil }
    }

    deinit {
        munmap(UnsafeMutableRawPointer(mutating: base), length)
        close(descriptor)
    }

    // MARK: Reading the frame list

    var videoCount: Int { condition.lock(); defer { condition.unlock() }; return videoStore.count }
    var audioCount: Int { condition.lock(); defer { condition.unlock() }; return audioStore.count }
    var isIndexed: Bool { condition.lock(); defer { condition.unlock() }; return indexed }
    var firstKeyIndex: Int? { condition.lock(); defer { condition.unlock() }; return firstKey }

    func video(_ index: Int) -> WebMPacket { condition.lock(); defer { condition.unlock() }; return videoStore[index] }
    func audio(_ index: Int) -> WebMPacket { condition.lock(); defer { condition.unlock() }; return audioStore[index] }

    /// Called from the background reader after each cluster of frames has been noted, and once when it is all done.
    func setListeners(grow: @escaping () -> Void, complete: @escaping () -> Void) {
        condition.lock()
        onGrow = grow
        onComplete = complete
        condition.unlock()
    }

    /// Waits until frames up to `time` have been noted (or the whole file has). Only for background threads.
    func waitUntil(time: Double) {
        condition.lock()
        while !indexed, !cancelled, newestVideoTime < time + 0.5 { condition.wait() }
        condition.unlock()
    }

    /// Waits until the whole file has been noted. Only for background threads.
    func finishIndexing() {
        condition.lock()
        while !indexed, !cancelled { condition.wait() }
        condition.unlock()
    }

    private func waitForFirstKey() {
        condition.lock()
        while firstKey == nil, !indexed, !cancelled { condition.wait() }
        condition.unlock()
    }

    /// Stops the background reader, for when the video is closed before it has finished.
    func cancelIndexing() {
        condition.lock()
        cancelled = true
        condition.broadcast()
        condition.unlock()
    }

    private var isCancelled: Bool { condition.lock(); defer { condition.unlock() }; return cancelled }

    /// The index of the first sound packet that ends after `time`, which is where playing from `time` starts.
    func audioIndex(at time: Double) -> Int {
        condition.lock(); defer { condition.unlock() }
        var low = 0, high = audioStore.count
        while low < high {
            let mid = (low + high) / 2
            if audioStore[mid].time < time - 0.03 { low = mid + 1 } else { high = mid }
        }
        return min(low, audioStore.count)
    }

    /// The index of the last key frame at or before `time`.
    func keyFrameIndex(atOrBefore time: Double) -> Int {
        condition.lock(); defer { condition.unlock() }
        var low = 0, high = videoStore.count - 1, found = 0
        while low <= high {
            let mid = (low + high) / 2
            if videoStore[mid].time <= time { found = mid; low = mid + 1 } else { high = mid - 1 }
        }
        var index = found
        while index > 0, !videoStore[index].isKey { index -= 1 }
        // A file cut out of a longer video can begin in the middle of a group of frames. Nothing before its first key
        // frame can be decoded, so playback starts there.
        if !videoStore[index].isKey, let firstKey { return firstKey }
        return index
    }

    /// The bytes of one packet. Valid for as long as this file object is alive.
    func bytes(of packet: WebMPacket) -> UnsafeBufferPointer<UInt8> {
        UnsafeBufferPointer(start: (base + packet.offset).assumingMemoryBound(to: UInt8.self), count: packet.size)
    }

    // MARK: Reading the structure

    private enum ID {
        static let segment = 0x18538067, info = 0x1549A966, tracks = 0x1654AE6B, cluster = 0x1F43B675
        static let cues = 0x1C53BB6B, tags = 0x1254C367, seekHead = 0x114D9B74, chapters = 0x1043A770, attachments = 0x1941A469
        static let timecodeScale = 0x2AD7B1, durationElement = 0x4489
        static let trackEntry = 0xAE, trackNumber = 0xD7, trackType = 0x83, codecID = 0x86, videoSettings = 0xE0
        static let pixelWidth = 0xB0, pixelHeight = 0xBA, audioSettings = 0xE1, samplingFrequency = 0xB5, channels = 0x9F
        static let clusterTime = 0xE7, simpleBlock = 0xA3, blockGroup = 0xA0, block = 0xA1, referenceBlock = 0xFB
    }

    private static let segmentLevel: Set<Int> = [ID.cluster, ID.cues, ID.tags, ID.seekHead, ID.chapters, ID.attachments, ID.info, ID.tracks]

    private var timecodeScale = 1_000_000.0
    private var videoNumber = -1
    private var audioNumber = -1

    /// Reads an element's id and size starting at `position`; `bodyStart` is where its contents begin and `end` where they
    /// stop (the end of the file for an element of unknown size).
    private func header(at position: Int, limit: Int) -> (id: Int, bodyStart: Int, end: Int, unknownSize: Bool)? {
        guard position < limit else { return nil }
        let first = base.load(fromByteOffset: position, as: UInt8.self)
        let idLength = first.leadingZeroBitCount + 1
        guard idLength <= 4, position + idLength < limit else { return nil }
        var id = 0
        for i in 0..<idLength { id = id << 8 | Int(base.load(fromByteOffset: position + i, as: UInt8.self)) }
        let sizePosition = position + idLength
        let sizeFirst = base.load(fromByteOffset: sizePosition, as: UInt8.self)
        let sizeLength = sizeFirst.leadingZeroBitCount + 1
        guard sizeLength <= 8, sizePosition + sizeLength <= limit else { return nil }
        var size = Int(sizeFirst) & ((1 << (8 - sizeLength)) - 1)
        var allOnes = size == (1 << (8 - sizeLength)) - 1
        for i in 1..<max(sizeLength, 1) {
            let byte = base.load(fromByteOffset: sizePosition + i, as: UInt8.self)
            if byte != 0xFF { allOnes = false }
            size = size << 8 | Int(byte)
        }
        let bodyStart = sizePosition + sizeLength
        if allOnes { return (id, bodyStart, limit, true) }
        return (id, bodyStart, min(bodyStart + size, limit), false)
    }

    private func uint(_ start: Int, _ end: Int) -> Int {
        var value = 0
        for i in start..<min(end, start + 8) { value = value << 8 | Int(base.load(fromByteOffset: i, as: UInt8.self)) }
        return value
    }

    private func float(_ start: Int, _ end: Int) -> Double {
        let size = end - start
        let raw = uint(start, end)
        if size == 4 { return Double(Float(bitPattern: UInt32(truncatingIfNeeded: raw))) }
        if size == 8 { return Double(bitPattern: UInt64(truncatingIfNeeded: raw)) }
        return 0
    }

    private func string(_ start: Int, _ end: Int) -> String {
        let data = Data(bytes: base + start, count: max(0, end - start))
        return String(decoding: data.prefix { $0 != 0 }, as: UTF8.self)
    }

    private func scan() -> Bool {
        var position = 0
        var foundSegment = false
        while let element = header(at: position, limit: length) {
            if element.id == ID.segment { foundSegment = true; readSegment(element.bodyStart, element.end) }
            position = element.end
            if element.unknownSize { break }
        }
        return foundSegment
    }

    /// Everything has been noted: the length is filled in if the file did not state one, and waiting threads are woken.
    private func finishScan() {
        condition.lock()
        if durationValue <= 0, let last = videoStore.last { durationValue = last.time + 0.04 }
        indexed = true
        let done = onComplete
        condition.broadcast()
        condition.unlock()
        done?()
    }

    private func readSegment(_ start: Int, _ end: Int) {
        var position = start
        while let element = header(at: position, limit: end) {
            if isCancelled { return }
            switch element.id {
            case ID.info: readInfo(element.bodyStart, element.end)
            case ID.tracks: readTracks(element.bodyStart, element.end)
            case ID.cluster:
                // A progressive open hands the clusters to a background thread, so the caller gets the file as soon as
                // the first frames are known instead of after touching every frame in the file.
                if progressive, !handedOff {
                    handedOff = true
                    let from = position
                    DispatchQueue.global(qos: .userInitiated).async { [self] in
                        readSegment(from, end)
                        finishScan()
                    }
                    return
                }
                let clusterEnd = readCluster(element.bodyStart, element.end, unknownSize: element.unknownSize)
                clusterDone()
                position = clusterEnd
                continue
            default: break
            }
            position = element.end
        }
    }

    private func clusterDone() {
        condition.lock()
        let grow = onGrow
        condition.broadcast()
        condition.unlock()
        grow?()
    }

    private func readInfo(_ start: Int, _ end: Int) {
        var position = start
        var rawDuration = 0.0
        while let element = header(at: position, limit: end) {
            if element.id == ID.timecodeScale { timecodeScale = Double(uint(element.bodyStart, element.end)) }
            if element.id == ID.durationElement { rawDuration = float(element.bodyStart, element.end) }
            position = element.end
        }
        condition.lock()
        durationValue = rawDuration * timecodeScale / 1e9
        condition.unlock()
    }

    private func readTracks(_ start: Int, _ end: Int) {
        var position = start
        while let entry = header(at: position, limit: end) {
            if entry.id == ID.trackEntry { readTrackEntry(entry.bodyStart, entry.end) }
            position = entry.end
        }
    }

    private func readTrackEntry(_ start: Int, _ end: Int) {
        var number = 0, type = 0, codec = "", width = 0, height = 0
        var channels = 1, sampleRate = 8000.0
        var position = start
        while let element = header(at: position, limit: end) {
            switch element.id {
            case ID.trackNumber: number = uint(element.bodyStart, element.end)
            case ID.trackType: type = uint(element.bodyStart, element.end)
            case ID.codecID: codec = string(element.bodyStart, element.end)
            case ID.videoSettings:
                var inner = element.bodyStart
                while let field = header(at: inner, limit: element.end) {
                    if field.id == ID.pixelWidth { width = uint(field.bodyStart, field.end) }
                    if field.id == ID.pixelHeight { height = uint(field.bodyStart, field.end) }
                    inner = field.end
                }
            case ID.audioSettings:
                var inner = element.bodyStart
                while let field = header(at: inner, limit: element.end) {
                    if field.id == ID.samplingFrequency { sampleRate = float(field.bodyStart, field.end) }
                    if field.id == ID.channels { channels = uint(field.bodyStart, field.end) }
                    inner = field.end
                }
            default: break
            }
            position = element.end
        }
        if type == 2, audio == nil {
            audio = WebMAudioTrack(number: number, channels: channels, sampleRate: sampleRate, codec: codec)
            audioNumber = number
        }
        if type == 1, video == nil, codec == "V_VP8" || codec == "V_VP9" {
            video = WebMVideoTrack(number: number, isVP9: codec == "V_VP9", width: width, height: height)
            videoNumber = number
        }
    }

    /// Reads the frames of one cluster and returns where the cluster ends. A cluster of unknown size (from a live
    /// recording) ends where the next top-level element starts.
    private func readCluster(_ start: Int, _ end: Int, unknownSize: Bool) -> Int {
        var position = start
        var clusterTime = 0
        while let element = header(at: position, limit: end) {
            if unknownSize, Self.segmentLevel.contains(element.id) { return position }
            switch element.id {
            case ID.clusterTime: clusterTime = uint(element.bodyStart, element.end)
            case ID.simpleBlock: addBlock(element.bodyStart, element.end, clusterTime: clusterTime, isKey: nil)
            case ID.blockGroup:
                var inner = element.bodyStart
                var blockRange: (Int, Int)?
                var hasReference = false
                while let field = header(at: inner, limit: element.end) {
                    if field.id == ID.block { blockRange = (field.bodyStart, field.end) }
                    if field.id == ID.referenceBlock { hasReference = true }
                    inner = field.end
                }
                if let blockRange { addBlock(blockRange.0, blockRange.1, clusterTime: clusterTime, isKey: !hasReference) }
            default: break
            }
            position = element.end
        }
        return end
    }

    private func addBlock(_ start: Int, _ end: Int, clusterTime: Int, isKey: Bool?) {
        guard videoNumber >= 0 || audioNumber >= 0, start + 4 < end else { return }
        let first = base.load(fromByteOffset: start, as: UInt8.self)
        let numberLength = first.leadingZeroBitCount + 1
        guard numberLength <= 8, start + numberLength + 3 <= end else { return }
        var number = Int(first) & ((1 << (8 - numberLength)) - 1)
        for i in 1..<max(numberLength, 1) { number = number << 8 | Int(base.load(fromByteOffset: start + i, as: UInt8.self)) }
        guard number == videoNumber || number == audioNumber else { return }
        let relative = Int(Int16(bitPattern: UInt16(uint(start + numberLength, start + numberLength + 2))))
        let flags = base.load(fromByteOffset: start + numberLength + 2, as: UInt8.self)
        // Laced blocks hold several frames; video from real encoders does not use them.
        guard flags & 0x06 == 0 else { return }
        let payload = start + numberLength + 3
        let time = Double(clusterTime + relative) * timecodeScale / 1e9
        let packet = WebMPacket(time: time, offset: payload, size: end - payload, isKey: isKey ?? (flags & 0x80 != 0))
        condition.lock()
        if number == videoNumber {
            if packet.isKey, firstKey == nil { firstKey = videoStore.count }
            videoStore.append(packet)
            newestVideoTime = max(newestVideoTime, time)
        } else {
            audioStore.append(packet)
        }
        condition.unlock()
    }
}
