import SwiftUI
import UIKit

/// Decides which way the screen may turn. The app is portrait only, except while a video plays in the viewer.
final class AppDelegate: NSObject, UIApplicationDelegate {
    nonisolated(unsafe) static var orientations: UIInterfaceOrientationMask = .portrait

    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        Self.orientations
    }
}

@main
struct GalleryDLApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
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

/// Turns the screen for the video viewer: portrait or landscape on request, and back to portrait afterwards.
@MainActor
enum ScreenOrientation {
    /// While a video is open the phone may be turned freely.
    static func allowRotation() {
        // Once the rotate button has chosen landscape, it stays landscape from video to video.
        AppDelegate.orientations = landscapeChosen ? .landscape : .allButUpsideDown
        refreshSupportedOrientations()
    }

    private static var landscapeChosen = false

    /// Back to portrait only, and turned upright if it was left in landscape.
    static func lockPortrait() {
        landscapeChosen = false
        AppDelegate.orientations = .portrait
        refreshSupportedOrientations()
        rotate(toLandscape: false)
    }

    /// iOS reads the allowed orientations from the top screen only when asked to look again.
    private static func refreshSupportedOrientations() {
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first else { return }
        for window in scene.windows {
            var top = window.rootViewController
            while let presented = top?.presentedViewController { top = presented }
            if #available(iOS 16.0, *) {
                window.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
                top?.setNeedsUpdateOfSupportedInterfaceOrientations()
            }
        }
        UIViewController.attemptRotationToDeviceOrientation()
    }

    static func rotate(toLandscape: Bool) {
        // Only the chosen direction is allowed, so the phone cannot turn back by itself (older iPhones do that when the
        // video starts, or when the phone is held upright).
        landscapeChosen = toLandscape
        AppDelegate.orientations = toLandscape ? .landscape : .portrait
        refreshSupportedOrientations()
        let mask: UIInterfaceOrientationMask = toLandscape ? .landscapeRight : .portrait
        if #available(iOS 16.0, *) {
            let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
            scene?.requestGeometryUpdate(.iOS(interfaceOrientations: mask)) { _ in }
        } else {
            let target: UIInterfaceOrientation = toLandscape ? .landscapeRight : .portrait
            UIDevice.current.setValue(target.rawValue, forKey: "orientation")
        }
        UIViewController.attemptRotationToDeviceOrientation()
    }
}
