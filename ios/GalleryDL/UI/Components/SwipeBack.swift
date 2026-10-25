import SwiftUI

/// Lets a screen be pushed away with the iPhone's edge swipe: drag from the left edge and the screen follows the finger,
/// sliding off to the right to reveal the screen underneath. Let go past a third of the width, or flick, to go back;
/// otherwise it springs back.
///
/// `behind` is drawn under the moving screen while it is dragged (with a little parallax), so the previous screen
/// is visible as it slides in.
struct SwipeBackContainer<Top: View, Behind: View>: View {
    var enabled = true
    let onBack: () -> Void
    @ViewBuilder var behind: Behind
    @ViewBuilder var top: Top

    @State private var offset: CGFloat = 0

    /// The system's own edge-swipe starts within about 20 pt of the screen edge.
    private let edgeWidth: CGFloat = 28

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            ZStack(alignment: .leading) {
                if offset > 0 {
                    behind
                        .offset(x: -width * 0.28 * (1 - offset / width))
                        .overlay(Color.black.opacity(0.35 * Double(1 - offset / width)).allowsHitTesting(false))
                        .allowsHitTesting(false)
                }
                top
                    .offset(x: offset)
                    .shadow(color: Color.black.opacity(offset > 0 ? 0.35 : 0), radius: 10, x: -4)
                    .simultaneousGesture(drag(width: width), including: enabled ? .all : .subviews)
            }
            .onAppear {
                #if DEBUG
                // `-testSwipe 0.4` shows the screen part-way through a swipe, for checking the look in the simulator.
                let fraction = UserDefaults.standard.double(forKey: "testSwipe")
                if fraction > 0 { offset = width * fraction }
                #endif
            }
        }
    }

    private func drag(width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 12, coordinateSpace: .global)
            .onChanged { value in
                guard value.startLocation.x <= edgeWidth,
                      abs(value.translation.width) > abs(value.translation.height) else { return }
                offset = max(0, value.translation.width)
            }
            .onEnded { value in
                guard offset > 0 else { return }
                let far = value.translation.width > width * 0.33
                let flick = value.predictedEndTranslation.width > width * 0.6
                if far || flick {
                    withAnimation(.easeOut(duration: 0.2)) { offset = width }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                        onBack()
                        // The screen is gone (or replaced), so put the container back without animating.
                        var transaction = Transaction()
                        transaction.disablesAnimations = true
                        withTransaction(transaction) { offset = 0 }
                    }
                } else {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { offset = 0 }
                }
            }
    }
}

extension SwipeBackContainer where Behind == EmptyView {
    init(enabled: Bool = true, onBack: @escaping () -> Void, @ViewBuilder top: () -> Top) {
        self.init(enabled: enabled, onBack: onBack, behind: { EmptyView() }, top: top)
    }
}

/// Set on a screen that is drawn only as a backdrop during a swipe, so its live video previews do not start.
private struct PreviewsDisabledKey: EnvironmentKey { static let defaultValue = false }

extension EnvironmentValues {
    var previewsDisabled: Bool {
        get { self[PreviewsDisabledKey.self] }
        set { self[PreviewsDisabledKey.self] = newValue }
    }
}
