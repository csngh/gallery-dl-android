import SwiftUI
import UIKit

/// How many random videos a folder cover cycles through, and how long clips cross-fade into each other.
private let folderClips = 6
private let blendSeconds = 0.4

/// Decides when the library's video previews may animate, and counts the tiles that are on screen.
///
/// A preview is a short strip of small JPEG frames saved once for each video (see `PreviewStripStore`), so playing one
/// needs no video decoder and very little memory: every visible tile can animate at once. Everything stops while the
/// app is in the background, the viewer is open, Low Power Mode or Reduce Motion is on, or the system warns about memory.
@MainActor
final class PreviewHost: ObservableObject {
    /// Tiles on screen, with how many views currently ask for each. Counted, because the same tile can briefly exist twice
    /// (for example the real folder list and the copy drawn underneath during a swipe back), and one going away must not
    /// cancel the other.
    @Published private var requests: [String: Int] = [:]
    /// The order tiles first asked in, oldest first, so that when there are too many the newest ones win.
    private var order: [String] = []
    @Published var enabledBySetting = true
    @Published var appActive = true
    @Published var viewerOpen = false
    @Published private var lowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
    @Published private var reduceMotion = UIAccessibility.isReduceMotionEnabled
    @Published private var memoryLow = false

    /// A safety limit on tiles animating together; far more than fit on a screen.
    private let maxActive = 36
    private var observers: [NSObjectProtocol] = []

    init() {
        let center = NotificationCenter.default
        observers = [
            center.addObserver(forName: .NSProcessInfoPowerStateDidChange, object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor in self?.lowPower = ProcessInfo.processInfo.isLowPowerModeEnabled }
            },
            center.addObserver(forName: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor in self?.reduceMotion = UIAccessibility.isReduceMotionEnabled }
            },
            center.addObserver(forName: UIApplication.didReceiveMemoryWarningNotification, object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor in self?.memoryLow = true }
            },
        ]
    }

    deinit { observers.forEach(NotificationCenter.default.removeObserver) }

    /// Called whenever the app comes back to the front.
    func refreshSystemState() {
        lowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
        reduceMotion = UIAccessibility.isReduceMotionEnabled
        memoryLow = false
    }

    private var allowed: Bool { enabledBySetting && appActive && !viewerOpen && !lowPower && !reduceMotion && !memoryLow }

    func request(_ key: String) {
        if requests[key] == nil { order.append(key) }
        requests[key, default: 0] += 1
    }

    func cancel(_ key: String) {
        guard let count = requests[key] else { return }
        if count <= 1 {
            requests[key] = nil
            order.removeAll { $0 == key }
        } else {
            requests[key] = count - 1
        }
    }

    /// Past the safety limit the oldest tiles stop (they are the ones scrolled furthest away), not all of them.
    func isActive(_ key: String) -> Bool { allowed && requests[key] != nil && order.suffix(maxActive).contains(key) }

    /// Called when the library leaves the screen.
    func releaseAll() {
        requests.removeAll()
        order.removeAll()
    }
}

/// One shared clock for every animating tile: a display link at 30 steps a second that tells each tile how much time has
/// passed. A tile works out which frame of its strip belongs to that time, so playback keeps its pace even when a frame
/// or two is late, and a strip made from a 24 fps video plays at 24 fps.
@MainActor
final class StripClock: NSObject {
    static let shared = StripClock()
    private let tiles = NSHashTable<StripController>.weakObjects()
    private var link: CADisplayLink?
    private var last: CFTimeInterval = 0

    func add(_ tile: StripController) {
        tiles.add(tile)
        guard link == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(fire(_:)))
        link.preferredFramesPerSecond = 30
        link.add(to: .main, forMode: .common)
        self.link = link
        last = 0
    }

    func remove(_ tile: StripController) {
        tiles.remove(tile)
        guard tiles.count == 0 else { return }
        link?.invalidate()
        link = nil
    }

    @objc private func fire(_ link: CADisplayLink) {
        let now = link.timestamp
        let step = last == 0 ? 1.0 / 30 : min(now - last, 0.25)
        last = now
        for tile in tiles.allObjects { tile.advance(by: step) }
    }
}

/// Shows the frames of a strip by setting a layer's contents directly, so a tile animating at 30 frames a second does
/// not make SwiftUI redraw anything. The last frame of a strip that just ended is kept on a second layer and fades out
/// over the next strip.
final class StripLayerView: UIView {
    private let current = CALayer()
    private let held = CALayer()

    override init(frame: CGRect) {
        super.init(frame: frame)
        clipsToBounds = true
        for layer in [current, held] {
            layer.contentsGravity = .resizeAspectFill
            layer.masksToBounds = true
            layer.actions = ["contents": NSNull(), "opacity": NSNull(), "bounds": NSNull(), "position": NSNull()]
            self.layer.addSublayer(layer)
        }
        held.opacity = 0
    }

    required init?(coder: NSCoder) { fatalError("not used") }

    override func layoutSubviews() {
        super.layoutSubviews()
        current.frame = bounds
        held.frame = bounds
    }

    func show(_ image: CGImage) { current.contents = image }

    /// Keeps what is on screen and fades it out, while the next strip starts underneath.
    func blend(seconds: Double) {
        held.removeAllAnimations()
        held.contents = current.contents
        held.opacity = 1
        let fade = CABasicAnimation(keyPath: "opacity")
        fade.fromValue = 1
        fade.toValue = 0
        fade.duration = seconds
        fade.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        held.opacity = 0
        held.add(fade, forKey: "fade")
    }

    func clear() {
        current.contents = nil
        held.contents = nil
        held.removeAllAnimations()
    }
}

private struct StripLayerRepresentable: UIViewRepresentable {
    let controller: StripController

    func makeUIView(context: Context) -> StripLayerView {
        let view = StripLayerView()
        controller.sink = view
        return view
    }

    func updateUIView(_ view: StripLayerView, context: Context) {}

    static func dismantleUIView(_ view: StripLayerView, coordinator: ()) { view.clear() }
}

/// Plays the saved strips of one tile: a single video loops its own strip; a folder cover cycles through the strips of a
/// few random videos from its folder. Each frame is decoded just before it is shown, off the main thread, so only a
/// frame or two is ever held in memory. Where a strip has not been made yet it is requested, and the tile shows its still
/// thumbnail until the first one is ready.
@MainActor
final class StripController: ObservableObject {
    /// True once the first frame is on screen, so the tile can fade its preview in over the still image.
    @Published private(set) var hasFrame = false
    fileprivate weak var sink: StripLayerView?

    private var candidates: [LibraryItem] = []
    private var loaded: [Int64: Strip] = [:]
    private var order: [Int64] = []
    private var currentID: Int64?
    private var elapsed = 0.0
    private var lastIndex = -1
    private var running = false
    private var readyObserver: NSObjectProtocol?
    private var sequence = 0
    private var applied = 0
    private static let decoding = DispatchQueue(label: "app.gallerydl.strips", qos: .userInitiated, attributes: .concurrent)

    func start(clips: [LibraryItem]) {
        guard candidates.isEmpty, !clips.isEmpty else { return }
        candidates = clips.count > 1 ? Array(clips.shuffled().prefix(folderClips)) : clips
        readyObserver = NotificationCenter.default.addObserver(forName: .previewStripReady, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
        refresh()
    }

    func stop() {
        if running { StripClock.shared.remove(self) }
        running = false
        if let readyObserver { NotificationCenter.default.removeObserver(readyObserver) }
        readyObserver = nil
        loaded.removeAll()
        candidates.removeAll()
        // Forget where playback was: a later start() picks new videos, and a leftover order or current strip from the old
        // pick would point at strips that are not loaded, which froze the tile on its still image.
        order.removeAll()
        currentID = nil
        elapsed = 0
        lastIndex = -1
        hasFrame = false
    }

    /// Opens the strips that exist and asks for the ones that do not.
    private func refresh() {
        for item in candidates where loaded[item.id] == nil {
            if let strip = PreviewStripStore.shared.strip(for: item.id) { loaded[item.id] = strip } else { PreviewStripStore.shared.requestMake(item) }
        }
        guard !running, !loaded.isEmpty else { return }
        pickNext(blend: false)
        running = true
        StripClock.shared.add(self)
    }

    private func pickNext(blend: Bool) {
        order.removeAll { loaded[$0] == nil }
        if order.isEmpty { order = loaded.keys.shuffled() }
        // Never the same strip twice in a row, unless it is the only one.
        if order.count > 1, order.first == currentID { order.append(order.removeFirst()) }
        currentID = order.removeFirst()
        elapsed = 0
        lastIndex = -1
        if blend { sink?.blend(seconds: blendSeconds) }
    }

    /// Called by the shared clock about 30 times a second.
    fileprivate func advance(by seconds: Double) {
        guard let id = currentID, let strip = loaded[id] else { return }
        elapsed += seconds
        let index = Int(elapsed * strip.fps)
        if index >= strip.frameCount {
            pickNext(blend: true)
            return
        }
        guard index != lastIndex else { return }
        lastIndex = index
        sequence += 1
        let mine = sequence
        Self.decoding.async {
            let frame = strip.frame(index)
            DispatchQueue.main.async { [weak self] in
                guard let self, mine > self.applied, let cg = frame?.cgImage else { return }
                self.applied = mine
                self.sink?.show(cg)
                if !self.hasFrame { self.hasFrame = true }
            }
        }
    }
}

/// A silent live preview laid over a tile's still image. It plays only while the tile is on screen and previews are
/// allowed; otherwise it draws nothing, so the still image underneath shows. It never blocks touches.
struct LivePreview: View {
    @EnvironmentObject private var host: PreviewHost
    @Environment(\.previewsDisabled) private var disabled
    let key: String
    let clips: [LibraryItem]
    /// Whether this very view asked for a turn, so it only ever takes back what it asked for. A backdrop copy that never
    /// asked must not cancel the real tile with the same key.
    @State private var registered = false

    var body: some View {
        ZStack {
            Color.clear
            if !disabled, !clips.isEmpty, host.isActive(key) { ActivePreview(clips: clips) }
        }
        .allowsHitTesting(false)
        .onAppear {
            guard !disabled, !clips.isEmpty, !registered else { return }
            host.request(key)
            registered = true
        }
        .onDisappear {
            guard registered else { return }
            host.cancel(key)
            registered = false
        }
    }
}

private struct ActivePreview: View {
    @StateObject private var controller = StripController()
    let clips: [LibraryItem]

    var body: some View {
        StripLayerRepresentable(controller: controller)
            .opacity(controller.hasFrame ? 1 : 0)
            .animation(.easeOut(duration: 0.25), value: controller.hasFrame)
            .onAppear { controller.start(clips: clips) }
            .onDisappear { controller.stop() }
    }
}
