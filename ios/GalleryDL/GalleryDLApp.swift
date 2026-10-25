import SwiftUI

@main
struct GalleryDLApp: App {
    @StateObject private var app = AppModel()
    @StateObject private var ui = UIState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .environmentObject(app.store)
                .environmentObject(app.settings)
                .environmentObject(app.network)
                .environmentObject(ui)
        }
    }
}
