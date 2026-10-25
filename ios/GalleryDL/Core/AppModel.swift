import SwiftUI

/// Builds and owns everything the screens share (the counterpart of Android's AppContainer).
@MainActor
final class AppModel: ObservableObject {
    let store = AppStore()
    let settings = SettingsStore()
    let network = NetworkMonitor()
    let validator = UrlValidator()
    let storage = StorageMonitor()
    let engine: DownloadEngine

    @Published private(set) var pythonReady = false
    @Published private(set) var galleryDlVersion = ""
    @Published var transfer: Transfer?

    init() {
        engine = DownloadEngine(store: store, settings: settings, network: network, storage: storage)
    }

    // MARK: Export and import of links

    private static let maxListBytes = 5 * 1024 * 1024

    /// Writes every link to a text file in the temporary folder and returns it, or nil when there are none.
    func exportFile() -> (url: URL, count: Int)? {
        let urls = store.tasks.sorted { $0.id < $1.id }.map(\.url)
        let text = LinkList.format(urls)
        let count = text.split(separator: "\n").count
        guard count > 0 else { return nil }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(S.exportFileName)
        guard (try? text.write(to: url, atomically: true, encoding: .utf8)) != nil else { transfer = .failed; return nil }
        return (url, count)
    }

    /// Adds the links of a text file as "ready to start" links. Links already in the list and links on websites that
    /// gallery-dl does not support are skipped, and the result says how many of each.
    func importLinks(from url: URL) async {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url), data.count <= Self.maxListBytes,
              let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else {
            transfer = .failed
            return
        }
        let lines = LinkList.parse(text)
        var result = ImportResult()
        var known = Set(store.tasks.map(\.url))
        transfer = .importing(done: 0, total: lines.count)
        for (index, line) in lines.enumerated() {
            switch line {
            case .notALink:
                result.notLinks += 1
            case .link(let link):
                if !known.insert(link).inserted {
                    result.duplicates += 1
                } else {
                    let check = await validator.check(link)
                    if check.check == .valid {
                        store.enqueue(url: link, site: check.site)
                        result.added += 1
                    } else {
                        result.unsupported += 1
                    }
                }
            }
            transfer = .importing(done: index + 1, total: lines.count)
        }
        transfer = .imported(result)
    }

    /// Starts the embedded Python off the main thread, so the intro animation keeps running.
    func boot() async {
        guard !pythonReady else { return }
        let started: Bool = await Task.detached { (try? PythonRuntime.shared.start()) != nil }.value
        guard started else { return }
        let info = try? await PythonRuntime.shared.call("info")
        galleryDlVersion = info?["gallery_dl"] as? String ?? ""
        pythonReady = true
        store.pruneMissing()
        engine.resumeOnLaunch()
    }

    func waitUntilReady() async {
        while !pythonReady { try? await Task.sleep(nanoseconds: 50_000_000) }
    }

    func sceneChanged(_ phase: ScenePhase) {
        switch phase {
        case .active: engine.sceneBecameActive(); store.pruneMissing()
        case .inactive: store.flush()
        case .background:
            store.flush()
            engine.sceneEnteredBackground()
        default: break
        }
    }
}
