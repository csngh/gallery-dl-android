import SwiftUI

/// A picture in the viewer: pinch to zoom, drag to pan once zoomed, double-tap to zoom in or out. While it is zoomed in
/// the swipeable pager stays put, so a drag moves the picture instead of turning the page.
struct ZoomableImage: View {
    let item: LibraryItem
    let active: Bool
    @Binding var zoomed: Bool

    @State private var scale: CGFloat = 1
    @State private var baseScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var baseOffset: CGSize = .zero
    private let maxScale: CGFloat = 5

    var body: some View {
        GeometryReader { geo in
            GalleryImage(item: item, fit: true, showShimmer: false)
                .frame(width: geo.size.width, height: geo.size.height)
                .scaleEffect(scale)
                .offset(offset)
                .contentShape(Rectangle())
                .onTapGesture(count: 2) { toggleZoom(in: geo.size) }
                .simultaneousGesture(
                    MagnificationGesture()
                        .onChanged { value in setScale(baseScale * value, size: geo.size) }
                        .onEnded { _ in
                            baseScale = scale
                            if scale < 1.05 { withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { reset() } }
                        }
                )
                .simultaneousGesture(scale > 1.02 ? panGesture(in: geo.size) : nil)
        }
        .background { if active { PagerLock(locked: zoomed) } }
    }

    private func panGesture(in size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                offset = clamped(CGSize(width: baseOffset.width + value.translation.width, height: baseOffset.height + value.translation.height), in: size)
            }
            .onEnded { _ in baseOffset = offset }
    }

    private func setScale(_ value: CGFloat, size: CGSize) {
        scale = min(max(value, 0.8), maxScale)
        zoomed = scale > 1.02
        offset = clamped(offset, in: size)
        baseOffset = offset
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
}
