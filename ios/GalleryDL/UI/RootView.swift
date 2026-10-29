import SwiftUI

struct RootView: View {
    @EnvironmentObject private var app: AppModel
    @EnvironmentObject private var settings: SettingsStore
    @EnvironmentObject private var ui: UIState
    @Environment(\.scenePhase) private var scenePhase

    @State private var tab = Destination.home
    @State private var showSettings = false
    @State private var showSplash = true
    @StateObject private var homeInput = LinkInputModel()
    @StateObject private var queueInput = LinkInputModel()
    @StateObject private var library = LibraryViewModel()
    @StateObject private var previews = PreviewHost()

    var body: some View {
        ZStack {
            Brand.background.ignoresSafeArea()

            VStack(spacing: 0) {
                Group {
                    if showSettings {
                        // Swipe from the left edge to go back to Home, which slides in underneath.
                        SwipeBackContainer(onBack: { showSettings = false }, behind: {
                            HomeScreen(input: homeInput) {}.appBackground()
                        }, top: {
                            SettingsScreen { showSettings = false }.appBackground()
                        })
                    } else {
                        switch tab {
                        case .home: HomeScreen(input: homeInput) { showSettings = true }
                        case .queue: QueueScreen(input: queueInput)
                        case .library: LibraryScreen(model: library)
                        case .activity: ActivityScreen()
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                // Settings is a full screen of its own, so the navigation bar steps aside.
                if !showSettings { BottomBar(selection: $tab) }
            }

            if let request = ui.viewer { ViewerOverlay(request: request).transition(.opacity).zIndex(1) }
            DialogHost().zIndex(2)
            if showSplash {
                SplashOverlay(playSound: settings.startupSound) { showSplash = false }.zIndex(3)
            }
        }
        .font(.inter(size: 16))
        .preferredColorScheme(.dark)
        .environmentObject(previews)
        .onAppear { previews.enabledBySetting = settings.livePreviews }
        .onChange(of: settings.livePreviews) { previews.enabledBySetting = $0 }
        // Previews stop behind the full-screen viewer and whenever the app is not in front.
        .onChange(of: ui.viewer) { previews.viewerOpen = $0 != nil }
        .task {
            #if DEBUG
            if !UserDefaults.standard.bool(forKey: "noPermissionPrompt") { Notifications.requestPermission() }
            #else
            Notifications.requestPermission()
            #endif
            await app.boot()
            #if DEBUG
            await debugLaunchActions()
            #endif
        }
        .onChange(of: scenePhase) { phase in
            app.sceneChanged(phase)
            previews.appActive = phase == .active
            if phase == .active { previews.refreshSystemState() }
        }
    }

    #if DEBUG
    /// Launch arguments for checking the app in the simulator: `-testURLs a,b,c` queues and starts downloads,
    /// `-testTab home|queue|library|activity`, `-testSettings 1`, `-testFolder 1` (open the first library folder)
    /// and `-testViewer 1` (open its first file) choose the screen.
    private func debugLaunchActions() async {
        let defaults = UserDefaults.standard
        for link in (defaults.string(forKey: "testURLs") ?? "").split(separator: ",").map(String.init) {
            let result = await app.validator.check(link)
            if result.check == .valid { app.store.enqueue(url: link, site: result.site) }
        }
        if defaults.string(forKey: "testURLs") != nil { app.engine.startAll() }
        // `-testPendingURLs a,b` adds links without starting them; `-testStartDialog 1` asks for the folder of the first
        // waiting link, and `-testStartPinned name` starts it in a folder of that name, as typing the name would.
        for link in (defaults.string(forKey: "testPendingURLs") ?? "").split(separator: ",").map(String.init) {
            let result = await app.validator.check(link)
            if result.check == .valid { app.store.enqueue(url: link, site: result.site) }
        }
        if defaults.bool(forKey: "testStartDialog") || defaults.string(forKey: "testStartPinned") != nil {
            tab = .home
            if let waiting = app.store.tasks.last(where: { $0.status == .pending }) {
                if let name = defaults.string(forKey: "testStartPinned") {
                    app.engine.start(waiting.id, folderName: cleanFolderName(name))
                } else {
                    ui.present(StartFolderDialog(task: waiting))
                }
            }
        }
        switch defaults.string(forKey: "testTab") {
        case "queue": tab = .queue
        case "library": tab = .library
        case "activity": tab = .activity
        default: break
        }
        if let directory = defaults.string(forKey: "testSeed"), app.store.items.isEmpty { seedLibrary(from: directory) }
        // `-testFavorite 1` copies the newest file of the library into Favorites, as the star in the selection bar does.
        if defaults.bool(forKey: "testFavorite"), let item = app.store.items.last { _ = app.store.addFavorites([item]) }
        if let name = defaults.string(forKey: "testDeleteFolder"), let folder = app.store.folders.first(where: { $0.name == name }) {
            app.store.deleteFolder(folder.id)
        }
        // `-testTag name:tag` gives the folder with that name a tag.
        if let spec = defaults.string(forKey: "testTag"), let split = spec.firstIndex(of: ":"),
           let folder = app.store.folders.first(where: { $0.name == String(spec[..<split]) }) {
            app.store.setFolderTag(folder.id, tag: String(spec[spec.index(after: split)...]))
        }
        // `-testQuery text` types that into the library search; `-testDialog tag|actions` opens a folder dialog.
        if let query = defaults.string(forKey: "testQuery") { tab = .library; library.query = query }
        if let kind = defaults.string(forKey: "testDialog"), let folder = app.store.folderSummaries(query: "").first(where: { $0.name == (defaults.string(forKey: "testTag")?.split(separator: ":").first.map(String.init) ?? $0.name) }) {
            tab = .library
            if kind == "tag" { ui.present(TagDialog(folderId: folder.id, initial: folder.tag)) }
            if kind == "actions" { ui.present(FolderActionsDialog(title: folder.title, onTag: {}, onDelete: {})) }
        }
        // `-testImport /path/file.txt` imports that list of links and shows the result dialog.
        if let path = defaults.string(forKey: "testImport") {
            tab = .home
            ui.present(TransferDialog())
            await app.importLinks(from: URL(fileURLWithPath: path))
        }
        if defaults.bool(forKey: "testClearThumbs") { ThumbnailStore.shared.clear() }
        // `-testRetryAfter 8` runs the finished links again after that many seconds, like "Check for new files".
        if defaults.object(forKey: "testRetryAfter") != nil {
            DispatchQueue.main.asyncAfter(deadline: .now() + defaults.double(forKey: "testRetryAfter")) {
                for task in app.store.tasks where task.status == .completed { app.engine.retry(task.id) }
            }
        }
        // `-testClearQueueAfter 20` clears the finished links from the Queue after that many seconds, like the button.
        if defaults.object(forKey: "testClearQueueAfter") != nil {
            DispatchQueue.main.asyncAfter(deadline: .now() + defaults.double(forKey: "testClearQueueAfter")) { app.store.hideFinishedFromQueue() }
        }
        // `-testDetails 1` opens the Details dialog of the newest file.
        if defaults.bool(forKey: "testDetails"), let item = app.store.items.last { ui.present(ItemDetailsDialog(item: item)) }
        // `-testStopAllAfter 8` presses "Stop all" after that many seconds.
        if defaults.object(forKey: "testStopAllAfter") != nil {
            DispatchQueue.main.asyncAfter(deadline: .now() + defaults.double(forKey: "testStopAllAfter")) { app.engine.cancelAll() }
        }
        // `-testExitAfterAdd 1` queues a link and ends the app right away, to check that the change was saved.
        if defaults.bool(forKey: "testExitAfterAdd") {
            app.store.enqueue(url: "http://127.0.0.1:8766/exit-test.png", site: "127.0.0.1")
            if defaults.bool(forKey: "testFlushFirst") { app.store.flush() }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { exit(0) }
        }
        if defaults.bool(forKey: "testSettings") { showSettings = true }
        if defaults.bool(forKey: "testFolder"),
           let folder = app.store.folderSummaries(query: "").first(where: { $0.name == (defaults.string(forKey: "testFolderName") ?? $0.name) }) {
            tab = .library
            library.open(folder)
            // `-testBackAfter 6` goes back to the folder list after that many seconds, like tapping back.
            if defaults.object(forKey: "testBackAfter") != nil {
                DispatchQueue.main.asyncAfter(deadline: .now() + defaults.double(forKey: "testBackAfter")) { library.closeFolder() }
            }
            // `-testSelectFirst 1` selects the first file, as a long press does, to check the selection bar.
            if defaults.bool(forKey: "testSelectFirst"), let first = app.store.items(inFolder: folder.id, query: "").first { library.toggleSelected(first.id) }
            if defaults.bool(forKey: "testViewer") {
                ui.viewer = ViewerRequest(folderId: folder.id, query: "", startIndex: defaults.integer(forKey: "testViewerIndex"))
            }
        }
    }

    /// `-testSeed <dir>` fills the library with the videos (v*.mp4) and pictures (i*.png) in a folder on this Mac.
    private func seedLibrary(from directory: String) {
        let groups: [(String, [String])] = [
            ("clips-a", ["v1.mp4", "v2.mp4", "v3.mp4", "v4.mp4", "v5.mp4", "v6.mp4", "i1.png"]),
            ("clips-b", ["v7.mp4", "v8.mp4"]),
            ("pictures", ["i1.png", "i2.png"]),
            ("many", (1...16).map { "v\($0).mp4" }),
        ]
        for (name, files) in groups {
            let folderId = app.store.ensureFolder(name)
            for file in files {
                let source = URL(fileURLWithPath: directory).appendingPathComponent(file)
                let target = MediaFiles.root.appendingPathComponent(name).appendingPathComponent(file)
                try? FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
                try? FileManager.default.copyItem(at: source, to: target)
                app.store.addItem(
                    relativePath: "\(name)/\(file)", displayName: file, mimeType: mimeType(forExtension: (file as NSString).pathExtension),
                    sourceUrl: "https://example.com/\(name)", taskId: 0, folderId: folderId,
                )
            }
        }
    }
    #endif
}
