import AVFoundation
import SwiftUI

/// A layer-backed view that shows a player's video, letterboxed to fit.
private final class VideoLayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}

private struct VideoSurface: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> VideoLayerView {
        let view = VideoLayerView()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        return view
    }

    func updateUIView(_ view: VideoLayerView, context: Context) {}

    static func dismantleUIView(_ view: VideoLayerView, coordinator: ()) { view.playerLayer.player = nil }
}

/// Owns the player of the full-screen viewer: play and pause, seeking, and the playback speed.
@MainActor
final class VideoController: ObservableObject {
    let player = AVPlayer()
    @Published private(set) var isPlaying = false
    @Published var current = 0.0
    @Published private(set) var duration = 0.0
    @Published private(set) var speed = {
        let saved = UserDefaults.standard.double(forKey: "videoSpeed")
        return VideoController.speeds.contains(saved) ? saved : 1.0
    }()
    @Published private(set) var unplayable = false
    var scrubbing = false
    private var lastScrub = 0.0

    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var statusObservation: NSKeyValueObservation?

    static let speeds: [Double] = [0.5, 0.75, 1, 1.25, 1.5, 2]

    func load(_ item: LibraryItem) async {
        guard player.currentItem == nil else { return }
        let asset = AVURLAsset(url: item.fileURL)
        guard (try? await asset.load(.isPlayable)) == true else { unplayable = true; return }
        duration = ((try? await asset.load(.duration))?.seconds).flatMap { $0.isFinite ? $0 : nil } ?? 0
        // Sound on, mixing off: this is the one place the app plays audio on purpose.
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        let playerItem = AVPlayerItem(asset: asset)
        player.replaceCurrentItem(with: playerItem)
        player.actionAtItemEnd = .pause

        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.2, preferredTimescale: 600), queue: .main) { [weak self] time in
            Task { @MainActor in
                guard let self, !self.scrubbing, time.seconds.isFinite else { return }
                self.current = time.seconds
            }
        }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: playerItem, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.isPlaying = false }
        }
        statusObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self] player, _ in
            Task { @MainActor in self?.isPlaying = player.timeControlStatus != .paused }
        }
    }

    func play() {
        if duration > 0, current >= duration - 0.25 { seek(to: 0) }
        // Setting the rate starts playback at that speed; the speed survives pausing because it is kept here.
        player.rate = Float(speed)
    }

    func pause() { player.pause() }

    func toggle() { isPlaying ? pause() : play() }

    func seek(to seconds: Double) {
        current = seconds
        player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
    }

    /// Moves the picture while a finger drags across the screen. The tolerance keeps it quick; the final seek is exact.
    func scrub(to seconds: Double) {
        current = seconds
        // A seek per touch event would queue up behind the decoder; one every 40 ms is plenty for a smooth picture.
        let now = CACurrentMediaTime()
        guard now - lastScrub >= 0.04 else { return }
        lastScrub = now
        let tolerance = CMTime(seconds: 0.15, preferredTimescale: 600)
        player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600), toleranceBefore: tolerance, toleranceAfter: tolerance)
    }

    func setSpeed(_ value: Double) {
        speed = value
        UserDefaults.standard.set(value, forKey: "videoSpeed")
        if isPlaying { player.rate = Float(value) }
    }

    func stop() {
        player.pause()
        if let timeObserver { player.removeTimeObserver(timeObserver) }
        timeObserver = nil
        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = nil
        statusObservation = nil
        player.replaceCurrentItem(with: nil)
        isPlaying = false
        // Back to the quiet category the live previews use, so they never interrupt the user's music.
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
    }
}

/// Stops the swipeable pager from paging while a video is zoomed in, so a drag pans the picture instead.
struct PagerLock: UIViewRepresentable {
    let locked: Bool

    final class Coordinator { weak var scroll: UIScrollView? }

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        let coordinator = context.coordinator
        DispatchQueue.main.async {
            var parent = view.superview
            while let candidate = parent {
                if let scroll = candidate as? UIScrollView {
                    scroll.isScrollEnabled = !locked
                    coordinator.scroll = scroll
                    return
                }
                parent = candidate.superview
            }
        }
    }

    /// When the page goes away the pager is given back, so it can never stay locked.
    static func dismantleUIView(_ view: UIView, coordinator: Coordinator) {
        coordinator.scroll?.isScrollEnabled = true
    }
}

/// The viewer's video (or audio) player: pinch to zoom, drag to pan, double-tap to zoom in or out, a speed menu, a
/// scrubber, and play and pause. Formats the system cannot play fall back to the open button.
struct VideoViewer: View {
    let item: LibraryItem
    let active: Bool
    @Binding var zoomed: Bool

    @StateObject private var controller = VideoController()
    @State private var scale: CGFloat = 1
    @State private var baseScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var baseOffset: CGSize = .zero
    @State private var controlsVisible = true
    /// Set while a horizontal swipe scrubs through the video: where it started, whether it was playing, how far it moved.
    @State private var scrubStart: (time: Double, wasPlaying: Bool)?
    @State private var scrubDelta = 0.0

    private let maxScale: CGFloat = 5

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.black
                if controller.unplayable {
                    Button { ShareSheet.present(item.fileURL) } label: {
                        Image(systemName: item.isVideo ? "play.circle.fill" : "music.note")
                            .font(.system(size: 64)).foregroundColor(.white).frame(width: 96, height: 96)
                    }
                    .accessibilityLabel(S.actionOpen)
                } else {
                    VideoSurface(player: controller.player)
                        .scaleEffect(scale)
                        .offset(offset)
                        .contentShape(Rectangle())
                        .onTapGesture(count: 2) { toggleZoom(in: geo.size) }
                        .onTapGesture { withAnimation(.easeInOut(duration: 0.2)) { controlsVisible.toggle() } }
                        .simultaneousGesture(item.isVideo ? zoomGesture(in: geo.size) : nil)
                        .simultaneousGesture(item.isVideo ? panGesture(in: geo.size) : nil)
                    if item.isAudio {
                        Image(systemName: "music.note").font(.system(size: 64)).foregroundColor(Brand.onSurfaceVariant).allowsHitTesting(false)
                    }
                    if scrubStart != nil { scrubHud }
                    if controlsVisible { controls.frame(maxHeight: .infinity, alignment: .bottom) }
                }
            }
            .clipped()
        }
        // Swiping sideways on a video scrubs it, so the pager never pages away from a video page.
        // Only the page on screen may lock the pager: neighbours are built in advance and would otherwise lock it too.
        .background { if active { PagerLock(locked: zoomed || item.isVideo || item.isAudio) } }
        .task(id: active) {
            if active {
                await controller.load(item)
                #if DEBUG
                // `-testScrubHud 1` leaves the video paused at the scrub position, so the frame can be checked.
                let holdPaused = ProcessInfo.processInfo.arguments.contains("-testScrubHud")
                let defaults = UserDefaults.standard
                if defaults.object(forKey: "testSpeed") != nil { controller.setSpeed(defaults.double(forKey: "testSpeed")) }
                if defaults.object(forKey: "testZoom") != nil { setScale(CGFloat(defaults.double(forKey: "testZoom"))) }
                if defaults.bool(forKey: "testScrubHud") {
                    scrubStart = (0, false); scrubDelta = 3; controller.scrubbing = true; controller.scrub(to: 3)
                }
                if defaults.object(forKey: "testSeek") != nil { controller.seek(to: defaults.double(forKey: "testSeek")) }
                #else
                let holdPaused = false
                #endif
                if !holdPaused { controller.play() }
            } else {
                controller.stop()
                reset()
            }
        }
        .onDisappear { controller.stop() }
    }

    // MARK: Zoom

    private func zoomGesture(in size: CGSize) -> some Gesture {
        MagnificationGesture()
            .onChanged { value in setScale(baseScale * value, size: size) }
            .onEnded { _ in
                baseScale = scale
                if scale < 1.05 { withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { reset() } }
            }
    }

    /// One drag, two jobs: zoomed in, it pans the picture; otherwise a sideways swipe scrubs through the video.
    private func panGesture(in size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                if scale > 1.02 {
                    offset = clamped(CGSize(width: baseOffset.width + value.translation.width, height: baseOffset.height + value.translation.height), in: size)
                    return
                }
                // A drag from the very edge belongs to the swipe-back gesture.
                guard controller.duration > 0, value.startLocation.x > 28 else { return }
                if scrubStart == nil {
                    guard abs(value.translation.width) > abs(value.translation.height) else { return }
                    scrubStart = (controller.current, controller.isPlaying)
                    controller.scrubbing = true
                    controller.pause()
                }
                guard let start = scrubStart else { return }
                // Across the whole screen covers up to 90 seconds, or the whole video if it is shorter.
                let span = min(max(controller.duration, 1), 90)
                let target = min(max(start.time + Double(value.translation.width / max(size.width, 1)) * span, 0), controller.duration)
                scrubDelta = target - start.time
                controller.scrub(to: target)
            }
            .onEnded { _ in
                baseOffset = offset
                guard let start = scrubStart else { return }
                scrubStart = nil
                controller.scrubbing = false
                controller.seek(to: controller.current)
                if start.wasPlaying { controller.play() }
            }
    }

    private var scrubHud: some View {
        VStack(spacing: 2) {
            Text("\(scrubDelta >= 0 ? "+" : "-")\(format(abs(scrubDelta)))").textStyle(.titleMedium, color: .white)
            Text("\(format(controller.current)) / \(format(controller.duration))").textStyle(.labelLarge, color: Color.white.opacity(0.85))
        }
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .background(Capsule().fill(Color.black.opacity(0.6)))
        .allowsHitTesting(false)
    }

    private func setScale(_ value: CGFloat, size: CGSize = UIScreen.main.bounds.size) {
        scale = min(max(value, 0.8), maxScale)
        zoomed = scale > 1.02
        offset = clamped(offset, in: size)
    }

    private func toggleZoom(in size: CGSize) {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
            if scale > 1.02 { reset() } else { setScale(2.5, size: size); baseScale = scale }
        }
    }

    private func reset() {
        scale = 1; baseScale = 1; offset = .zero; baseOffset = .zero; zoomed = false
    }

    /// A zoomed picture can be dragged only until its edge meets the edge of the screen.
    private func clamped(_ value: CGSize, in size: CGSize) -> CGSize {
        let limitX = max(0, size.width * (scale - 1) / 2), limitY = max(0, size.height * (scale - 1) / 2)
        return CGSize(width: min(max(value.width, -limitX), limitX), height: min(max(value.height, -limitY), limitY))
    }

    // MARK: Controls

    private var controls: some View {
        HStack(spacing: Spacing.sm) {
            Button { controller.toggle() } label: {
                Image(systemName: controller.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 20, weight: .semibold)).foregroundColor(.white).frame(width: 40, height: 40)
            }
            .accessibilityLabel(controller.isPlaying ? "Pause" : "Play")
            Text(format(controller.current)).textStyle(.labelMedium, color: .white).frame(minWidth: 36)
            // Dragging the bar shows the picture at that moment live, like swiping across the video does.
            Slider(
                value: Binding(get: { controller.current }, set: { value in
                    guard controller.scrubbing else { controller.current = value; return }
                    scrubDelta = value - (scrubStart?.time ?? value)
                    controller.scrub(to: value)
                }),
                in: 0...max(controller.duration, 0.1),
                onEditingChanged: { editing in
                    if editing {
                        scrubStart = (controller.current, controller.isPlaying)
                        scrubDelta = 0
                        controller.scrubbing = true
                        controller.pause()
                    } else if let start = scrubStart {
                        scrubStart = nil
                        controller.scrubbing = false
                        controller.seek(to: controller.current)
                        if start.wasPlaying { controller.play() }
                    }
                },
            )
            .accentColor(Brand.primary)
            Text(format(controller.duration)).textStyle(.labelMedium, color: .white).frame(minWidth: 36)
            speedMenu
        }
        .padding(.horizontal, Spacing.sm)
        .padding(.top, Spacing.md)
        .padding(.bottom, 28)
        .background(LinearGradient(colors: [.clear, Color.black.opacity(0.7)], startPoint: .top, endPoint: .bottom))
    }

    private var speedMenu: some View {
        Menu {
            ForEach(VideoController.speeds, id: \.self) { value in
                Button { controller.setSpeed(value) } label: {
                    if value == controller.speed { Label(speedLabel(value), systemImage: "checkmark") } else { Text(speedLabel(value)) }
                }
            }
        } label: {
            Text(speedLabel(controller.speed))
                .textStyle(.labelLarge, color: .white)
                .padding(.horizontal, 10)
                .frame(height: 32)
                .background(Capsule().fill(Color.white.opacity(0.18)))
        }
        .accessibilityLabel("Playback speed")
    }

    private func speedLabel(_ value: Double) -> String {
        (value == value.rounded() ? String(Int(value)) : String(format: "%g", value)) + "×"
    }

    private func format(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let total = Int(seconds.rounded())
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}
