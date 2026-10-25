import SwiftUI

/// The launcher artwork (res/drawable/ic_splash_art.xml) drawn in SwiftUI on the same 108 x 108 grid.
/// The cards and the badge are separate layers so the splash can animate them independently.
private let artShift = CGSize(width: -3.5, height: -2)

private struct RoundedBox: Shape {
    let x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, r: CGFloat
    func path(in rect: CGRect) -> Path {
        let k = rect.width / 108
        return Path(roundedRect: CGRect(x: x * k, y: y * k, width: w * k, height: h * k), cornerRadius: r * k)
    }
}

private struct Disc: Shape {
    let cx: CGFloat, cy: CGFloat, r: CGFloat
    func path(in rect: CGRect) -> Path {
        let k = rect.width / 108
        return Path(ellipseIn: CGRect(x: (cx - r) * k, y: (cy - r) * k, width: 2 * r * k, height: 2 * r * k))
    }
}

private struct Polygon: Shape {
    let points: [CGPoint]
    func path(in rect: CGRect) -> Path {
        let k = rect.width / 108
        var path = Path()
        path.addLines(points.map { CGPoint(x: $0.x * k, y: $0.y * k) })
        path.closeSubpath()
        return path
    }
}

/// The two photo cards with the mountain and sun. `scale` pops them in around the artwork's centre.
struct LogoCards: View {
    var scale: CGFloat = 1

    var body: some View {
        ZStack {
            RoundedBox(x: 38, y: 27, w: 40, h: 40, r: 5).fill(Color.white.opacity(0.45))
            RoundedBox(x: 32, y: 34, w: 40, h: 40, r: 5).fill(Color.white)
            Polygon(points: [.init(x: 35, y: 71), .init(x: 35, y: 67.5), .init(x: 43, y: 57), .init(x: 49.5, y: 64.5),
                             .init(x: 56, y: 55), .init(x: 69, y: 68.5), .init(x: 69, y: 71)]).fill(Brand.mountain)
            Disc(cx: 63, cy: 39.5, r: 3.5).fill(Brand.teal)
        }
        .scaleEffect(scale, anchor: UnitPoint(x: 52 / 108, y: 50 / 108))
    }
}

/// The download badge in the corner. `drop` is the vertical offset in artwork units (0 = resting place).
struct LogoBadge: View {
    var drop: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Disc(cx: 71, cy: 73, r: 12.5).fill(Brand.background)
                Disc(cx: 71, cy: 73, r: 10.5).fill(Brand.mint)
                RoundedBox(x: 69.6, y: 66.5, w: 2.8, h: 7, r: 0).fill(Brand.background)
                Polygon(points: [.init(x: 64.8, y: 72.8), .init(x: 77.2, y: 72.8), .init(x: 71, y: 79.5)])
                    .fill(Brand.background)
            }
            .offset(y: drop * geo.size.width / 108)
        }
    }
}

/// The full logo on a transparent background, sized `size` points square.
struct LogoArt: View {
    let size: CGFloat
    var cardsScale: CGFloat = 1
    var badgeDrop: CGFloat = 0

    var body: some View {
        ZStack {
            LogoCards(scale: cardsScale)
            LogoBadge(drop: badgeDrop)
        }
        .frame(width: size, height: size)
        .offset(x: artShift.width * size / 108, y: artShift.height * size / 108)
        .frame(width: size, height: size)
    }
}

/// The launcher icon as a tile: the artwork on the brand gradient, clipped to a rounded square.
struct BrandMark: View {
    var size: CGFloat = 44

    var body: some View {
        ZStack {
            Brand.gradient
            // Drawn larger than the tile and clipped, like the Android BrandMark, so the artwork fills it.
            LogoArt(size: size * 1.3)
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.27, style: .continuous))
        .accessibilityHidden(true)
    }
}
