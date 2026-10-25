import SwiftUI
import UIKit

/// The animated intro: cards pop in, the badge drops and bounces, the name fades in, then everything fades out.
/// The launch screen is plain dark, so this logo is the first and only one the user sees (same as Android).
struct SplashOverlay: View {
    let playSound: Bool
    let onFinished: () -> Void

    @State private var cardsScale: CGFloat = 0.55
    @State private var badgeDrop: CGFloat = -26
    @State private var wordmark = 0.0
    @State private var overlay = 1.0

    var body: some View {
        ZStack {
            Brand.background.ignoresSafeArea()
            LogoArt(size: 288, cardsScale: cardsScale, badgeDrop: badgeDrop)
            Text("Gallery DL")
                .font(.inter(.title2, weight: .semibold))
                .foregroundColor(Brand.text)
                .opacity(wordmark)
                .offset(y: 114)
        }
        .opacity(overlay)
        .contentShape(Rectangle())   // swallow touches so nothing underneath is pressed during the intro
        .onTapGesture {}
        .task { await run() }
    }

    private func run() async {
        // Reduce Motion is the iOS counterpart of Android's "animations off": skip the intro entirely.
        if UIAccessibility.isReduceMotionEnabled {
            onFinished()
            return
        }
        if playSound { StartupSound.shared.play() }
        withAnimation(.spring(response: 0.55, dampingFraction: 0.5)) { cardsScale = 1 }
        withAnimation(.interpolatingSpring(mass: 1, stiffness: 150, damping: 9)) { badgeDrop = 0 }
        try? await Task.sleep(nanoseconds: 450_000_000)
        withAnimation(.easeInOut(duration: 0.4)) { wordmark = 1 }
        try? await Task.sleep(nanoseconds: 1_150_000_000)
        withAnimation(.easeOut(duration: 0.25)) { overlay = 0 }
        try? await Task.sleep(nanoseconds: 250_000_000)
        onFinished()
    }
}
