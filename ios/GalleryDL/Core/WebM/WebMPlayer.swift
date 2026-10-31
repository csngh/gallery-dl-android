import AVFoundation
import UIKit

/// Plays a WebM video the system player cannot open: libvpx decodes the frames and an `AVSampleBufferDisplayLayer` shows
/// them. A render synchronizer keeps the clock, so play, pause, speed and seeking are the system's own and the picture
/// stays in step with it.
final class WebMPlayer {
    /// The layer the picture is drawn on: the layer of whichever surface view is on screen (see `attach`). Touched only
    /// on `queue`.
    private var display: AVSampleBufferDisplayLayer?
    /// Called on the main queue once the whole file has been noted and the length is final.
    var onIndexComplete: (() -> Void)?

    /// Seconds. What the file says its length is until every frame has been noted, then where the last frame is.
    var duration: Double { measure().duration }
    /// Where playback is over: just after the last frame has been shown. Unknown (infinite) until the length is known.
    var endTime: Double { measure().end }

    private let file: WebMFile
    /// Where the picture starts. Whatever comes before it cannot be shown, so it is not part of the timeline.
    private let firstShown: Int
    /// How much of the file's own clock comes before the first picture, and is left out of the timeline. Only for a
    /// video without sound: with sound, the sound keeps the file's own timing and the first picture waits on screen.
    private let origin: Double
    private let decoder: WebMDecoder
    private let synchronizer = AVSampleBufferRenderSynchronizer()
    /// Nil for a video without sound, or with a sound format this app cannot decode.
    private let audioDecoder: WebMAudioDecoder?
    private let audioRenderer = AVSampleBufferAudioRenderer()
    private let queue = DispatchQueue(label: "app.gallerydl.webm-player", qos: .userInitiated)
    private var timeObserver: Any?

    // Touched only on `queue`.
    private var nextIndex = 0
    private var seekTarget: Double?
    private var held: CMSampleBuffer?
    private var servedSeek = 0
    private var audioIndex = 0
    /// How many times the picture and the sound have gone round again. Each pass is queued with its times moved on by one
    /// whole length, so the clock never has to jump back and a loop has no gap. Touched only on `queue`.
    private var videoLoops = 0
    private var audioLoops = 0

    /// Whether the video plays again from the start when it ends. Read from any thread, set through `setLooping`.
    private let loopFlag = NSLock()
    private var loopingValue = false
    private var looping: Bool { loopFlag.lock(); defer { loopFlag.unlock() }; return loopingValue }

    /// Bumped on every seek, from any thread, so work for a position that has already been replaced can be dropped.
    private let latestSeek = NSLock()
    private var latestSeekValue = 0

    init?(url: URL) {
        // Progressive: the first frames are ready at once and the rest of the file is noted while the video plays.
        guard let file = WebMFile(url: url, progressive: true), let decoder = WebMDecoder(file: file) else { return nil }
        self.file = file
        self.decoder = decoder
        firstShown = file.firstKeyIndex ?? 0
        audioDecoder = file.audio.flatMap { WebMAudioDecoder(track: $0) }
        origin = file.audio == nil ? max(0, file.video(firstShown).time) : 0
        file.setListeners(
            // More frames are known: whatever was waiting for them carries on.
            grow: { [weak self] in self?.queue.async { self?.feed(); self?.feedAudio() } },
            complete: { [weak self] in
                // The length is final now, so a loop that was waiting for it can queue the next pass.
                self?.queue.async { self?.feed(); self?.feedAudio() }
                DispatchQueue.main.async { self?.onIndexComplete?() }
            }
        )
        if audioDecoder != nil {
            synchronizer.addRenderer(audioRenderer)
            audioRenderer.requestMediaDataWhenReady(on: queue) { [weak self] in self?.feedAudio() }
        }
        // Sets the clock to the start. The first picture is put up when a surface is attached.
        seek(to: 0, rate: 0)
    }

    /// Starts drawing on `layer`, which belongs to a surface view. The view's own layer is used, not one placed inside
    /// it, so UIKit keeps its position and size and nothing here ever sets them.
    func attach(_ layer: AVSampleBufferDisplayLayer) {
        queue.async { [self] in
            display = layer
            layer.videoGravity = .resizeAspect
            synchronizer.addRenderer(layer)
            layer.requestMediaDataWhenReady(on: queue) { [weak self] in self?.feed() }
            // Whatever was playing carries on, and the picture for this moment is drawn at once.
            seek(to: currentTime, rate: synchronizer.rate)
        }
    }

    /// Lets go of `layer`. If a newer surface has already been attached, that one is left alone.
    func detach(_ layer: AVSampleBufferDisplayLayer) {
        queue.async { [self] in
            layer.stopRequestingMediaData()
            synchronizer.removeRenderer(layer, at: .zero, completionHandler: nil)
            layer.flush()
            if display === layer { display = nil }
        }
    }

    /// The length and the end of playback as far as the file is known right now.
    private func measure() -> (duration: Double, end: Double) {
        guard file.isIndexed else {
            // Still reading: the length the file states, if it states one.
            let stated = file.duration - origin
            return stated > 0 ? (stated, stated) : (0, .infinity)
        }
        let count = file.videoCount
        let first = file.video(0), last = file.video(count - 1)
        let interval = count > 1 && last.time > first.time ? (last.time - first.time) / Double(count - 1) : 0.04
        let end = last.time - origin + interval
        // The length the file states is used when it agrees with where the last picture is; otherwise the pictures win.
        let stated = file.duration
        return (stated > 0 && origin == 0 && stated >= end - 0.5 ? min(stated, end + 0.5) : end, end)
    }

    /// Why a video with a sound track is silent, or nil when the sound plays or there is none.
    var silentReason: String? {
        guard let track = file.audio else { return nil }
        if audioDecoder == nil {
            let name = track.codec.hasPrefix("A_") ? String(track.codec.dropFirst(2)).capitalized : track.codec
            return S.soundUnsupported(track.channels > 2 && track.codec == "A_OPUS" ? "\(name) with \(track.channels) channels" : name)
        }
        return audioRenderer.status == .failed ? S.soundFailed : nil
    }

    var isPlaying: Bool { synchronizer.rate > 0 }
    /// Where in the video the clock is. While looping the clock keeps counting through every pass, so this is the position
    /// within the current pass.
    var currentTime: Double { wrapped(max(0, synchronizer.currentTime().seconds)) }

    private func wrapped(_ clock: Double) -> Double {
        let end = endTime
        guard looping, end.isFinite, end > 0 else { return clock }
        return clock.truncatingRemainder(dividingBy: end)
    }

    /// Which pass the clock time `clock` falls in.
    private func pass(at clock: Double) -> Int {
        let end = endTime
        guard looping, end.isFinite, end > 0 else { return 0 }
        return Int(clock / end)
    }

    /// Turns looping on or off. A loop is seamless: the next pass is queued while the last one is still playing.
    func setLooping(_ on: Bool) {
        loopFlag.lock(); loopingValue = on; loopFlag.unlock()
        queue.async { [self] in feed(); feedAudio() }
    }

    // These go through the same queue as seeks, so a play that follows a seek cannot be undone by the seek.
    func play(rate: Float) { queue.async { [self] in synchronizer.rate = rate } }
    func pause() { queue.async { [self] in synchronizer.rate = 0 } }
    func setRate(_ rate: Float) { play(rate: rate) }

    /// Calls `body` on the main queue about every `interval` seconds while the clock runs.
    func observeTime(every interval: Double, _ body: @escaping (Double) -> Void) {
        timeObserver = synchronizer.addPeriodicTimeObserver(forInterval: CMTime(seconds: interval, preferredTimescale: 600), queue: .main) { [weak self] time in
            guard let self else { return }
            if time.seconds.isFinite { body(self.wrapped(time.seconds)) }
        }
    }

    /// Moves to `seconds` and carries on at `rate` (0 stays paused). The picture at that moment shows once it is decoded.
    func seek(to seconds: Double, rate: Float) {
        let target = min(max(0, seconds), endTime)
        latestSeek.lock()
        latestSeekValue += 1
        let mine = latestSeekValue
        latestSeek.unlock()
        queue.async { [self] in
            // A newer seek is already waiting, so this one is not worth decoding for.
            guard mine == readLatestSeek() else { return }
            servedSeek = mine
            // Frames up to the target have to be known before the nearest key frame can be found.
            file.waitUntil(time: target + origin)
            display?.flush()
            if audioDecoder != nil {
                audioRenderer.flush()
                audioDecoder?.reset()
                audioIndex = file.audioIndex(at: target)
            }
            held = nil
            videoLoops = 0
            audioLoops = 0
            nextIndex = file.keyFrameIndex(atOrBefore: target + origin)
            seekTarget = target
            synchronizer.setRate(rate, time: CMTime(seconds: target, preferredTimescale: 600))
            feed()
            feedAudio()
        }
    }

    /// Stops playback and drops what is queued. Never waits for the decoder thread: this is called on the main thread, and
    /// a decoder busy with a long seek would otherwise freeze the whole screen until it finished.
    func stop() {
        if let timeObserver { synchronizer.removeTimeObserver(timeObserver) }
        timeObserver = nil
        synchronizer.rate = 0
        file.cancelIndexing()
        if audioDecoder != nil { audioRenderer.stopRequestingMediaData() }
        // Counts as a newer seek, so a decode loop that is running gives up at its next frame.
        latestSeek.lock()
        latestSeekValue += 1
        latestSeek.unlock()
        queue.async { [self] in
            display?.stopRequestingMediaData()
            display?.flush()
            audioRenderer.flush()
        }
    }

    private func readLatestSeek() -> Int {
        latestSeek.lock()
        defer { latestSeek.unlock() }
        return latestSeekValue
    }

    /// Decodes and queues frames for as long as the layer wants more. Runs on `queue`.
    private func feed() {
        guard let layer = display else { return }
        // The system can put the layer in a failed state (for example after the app was in the background). Nothing
        // shows until it is flushed, so it is flushed here and decoding carries on from the next frame.
        if layer.status == .failed || layer.requiresFlushToResumeDecoding {
            layer.flush()
            let clock = max(0, synchronizer.currentTime().seconds)
            let target = seekTarget ?? wrapped(clock)
            videoLoops = pass(at: clock)
            nextIndex = file.keyFrameIndex(atOrBefore: target + origin)
            seekTarget = target
            held = nil
        }
        while layer.isReadyForMoreMediaData {
            // A newer seek has come in: stop here and let it take over.
            if servedSeek != readLatestSeek() { return }
            guard nextIndex < file.videoCount else {
                // Past the last frame: whatever was held back for a seek near the end is shown now. While the file is still
                // being read, the next frames are simply not known yet, and the reader calls this again as they are.
                guard file.isIndexed else { return }
                if let held { layer.enqueue(held); self.held = nil }
                // Looping: carry straight on with the start of the next pass, so the picture never waits for the end.
                guard looping, file.videoCount > 0, endTime.isFinite else { return }
                videoLoops += 1
                nextIndex = file.keyFrameIndex(atOrBefore: origin)
                continue
            }
            let index = nextIndex
            nextIndex += 1
            // The first picture is shown from the very start, even when the file's own clock begins later, so the screen
            // is never empty while the clock runs up to it.
            let time = index == firstShown ? 0 : file.video(index).time - origin
            let stamp = time + Double(videoLoops) * (videoLoops > 0 ? endTime : 0)
            guard let buffer = decoder.decode(index), let sample = Self.sample(buffer, time: stamp) else { continue }
            if let target = seekTarget {
                // Frames before the seek target are decoded but kept back, except the last of them, which is what the
                // target moment actually looks like.
                if time <= target { held = sample; continue }
                if let held { layer.enqueue(held); self.held = nil }
                seekTarget = nil
            }
            layer.enqueue(sample)
        }
    }

    /// Decodes and queues sound for as long as the renderer wants more. Runs on `queue`.
    private func feedAudio() {
        guard let audioDecoder else { return }
        while audioRenderer.isReadyForMoreMediaData {
            if servedSeek != readLatestSeek() { return }
            // Looping: the sound goes round with the picture. A sound track longer than the picture is cut where the picture ends.
            let loops = looping && file.isIndexed && file.audioCount > 0 && endTime.isFinite
            if audioIndex >= file.audioCount || (loops && file.audio(audioIndex).time >= endTime) {
                guard loops else { return }
                audioLoops += 1
                audioIndex = 0
                audioDecoder.reset()
                continue
            }
            let packet = file.audio(audioIndex)
            audioIndex += 1
            let stamp = packet.time + (audioLoops > 0 ? Double(audioLoops) * endTime : 0)
            if let sample = audioDecoder.decode(file.bytes(of: packet), time: stamp) { audioRenderer.enqueue(sample) }
        }
    }

    private static func sample(_ buffer: CVPixelBuffer, time: Double) -> CMSampleBuffer? {
        var format: CMVideoFormatDescription?
        guard CMVideoFormatDescriptionCreateForImageBuffer(allocator: nil, imageBuffer: buffer, formatDescriptionOut: &format) == noErr, let format else { return nil }
        var timing = CMSampleTimingInfo(duration: .invalid, presentationTimeStamp: CMTime(seconds: time, preferredTimescale: 90_000), decodeTimeStamp: .invalid)
        var sample: CMSampleBuffer?
        guard CMSampleBufferCreateReadyWithImageBuffer(allocator: nil, imageBuffer: buffer, formatDescription: format, sampleTiming: &timing, sampleBufferOut: &sample) == noErr else { return nil }
        return sample
    }
}

/// The view the picture is drawn in. Its own layer is the display layer, exactly as an `AVPlayerLayer` view works, so UIKit
/// sizes and places it and there is no frame to set by hand.
final class WebMSurfaceView: UIView {
    override class var layerClass: AnyClass { AVSampleBufferDisplayLayer.self }
    var displayLayer: AVSampleBufferDisplayLayer { layer as! AVSampleBufferDisplayLayer }
}
