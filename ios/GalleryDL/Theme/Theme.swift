import SwiftUI

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255,
            opacity: opacity,
        )
    }
}

/// The Android app's Material 3 dark scheme (ui/theme/Theme.kt), value for value.
enum Brand {
    static let violet = Color(hex: 0x4A2BD1)
    static let teal = Color(hex: 0x0E8F9A)
    static let mountain = Color(hex: 0x2B2F77)

    static let primary = Color(hex: 0x2EE6C9)
    static let onPrimary = Color(hex: 0x00382F)
    static let secondaryContainer = Color(hex: 0x2E3A8C)
    static let onSecondaryContainer = Color(hex: 0xDDE1FF)
    static let tertiary = Color(hex: 0xFFB86B)
    static let error = Color(hex: 0xEF5350)
    static let onError = Color(hex: 0x2B0606)
    static let background = Color(hex: 0x0B1020)
    static let onSurface = Color(hex: 0xE3E6F3)
    static let onSurfaceVariant = Color(hex: 0xA9B0C9)
    static let surfaceVariant = Color(hex: 0x1F2742)
    static let outline = Color(hex: 0x5A627F)
    static let outlineVariant = Color(hex: 0x2B3354)
    static let surfaceContainer = Color(hex: 0x151B30)
    static let surfaceContainerHigh = Color(hex: 0x1B2238)
    static let surfaceContainerHighest = Color(hex: 0x232B45)

    // Aliases kept for the logo and splash.
    static let mint = primary
    static let text = onSurface
    static let card = surfaceContainerHighest

    static let gradient = LinearGradient(colors: [violet, teal], startPoint: .topLeading, endPoint: .bottomTrailing)
}

/// Green for the positive or start action, red for the negative or end action.
enum ActionColors {
    static let positive = Color(hex: 0x66BB6A)
    static let onPositive = Color(hex: 0x0B2A10)
    static let negative = Color(hex: 0xEF5350)
    static let onNegative = Color(hex: 0x2B0606)
}

/// The fixed set of accent tones for icons: the icon colour and a soft tint for its tile.
enum Tone: CaseIterable {
    case blue, green, amber, red, violet, teal, pink

    var icon: Color {
        switch self {
        case .blue: return Color(hex: 0x6DB0F8)
        case .green: return Color(hex: 0x5FD096)
        case .amber: return Color(hex: 0xF0B45A)
        case .red: return Color(hex: 0xFF8A7E)
        case .violet: return Color(hex: 0xC4B5FD)
        case .teal: return Color(hex: 0x5FCDE0)
        case .pink: return Color(hex: 0xF59AC4)
        }
    }

    var tint: Color {
        switch self {
        case .blue: return Color(hex: 0x14253B)
        case .green: return Color(hex: 0x112A1C)
        case .amber: return Color(hex: 0x33260C)
        case .red: return Color(hex: 0x3A1815)
        case .violet: return Color(hex: 0x2A1F45)
        case .teal: return Color(hex: 0x0F2D35)
        case .pink: return Color(hex: 0x3D1830)
        }
    }
}

/// A repeatable 4/8/16/24/32/48 rhythm.
enum Spacing {
    static let xs: CGFloat = 4, sm: CGFloat = 8, md: CGFloat = 16, lg: CGFloat = 24, xl: CGFloat = 32, xxl: CGFloat = 48
}

/// Corner radii by role: 8 extra small, 12 small, 16 medium, 24 large, 28 extra large (dialogs).
enum Radius {
    static let xs: CGFloat = 8, sm: CGFloat = 12, md: CGFloat = 16, lg: CGFloat = 24, xl: CGFloat = 28
}

enum InterWeight {
    case regular, medium, semibold, bold

    var swiftUI: Font.Weight {
        switch self {
        case .regular: return .regular
        case .medium: return .medium
        case .semibold: return .semibold
        case .bold: return .bold
        }
    }
}

extension Font {
    /// Inter, bundled from the Android app's res/font. The variable font exposes one family; the weight picks the
    /// instance along its wght axis.
    static func inter(size: CGFloat, weight: InterWeight = .regular) -> Font {
        Font.custom("Inter", size: size).weight(weight.swiftUI)
    }

    /// Kept for the splash and the logo.
    static func inter(_ style: Font.TextStyle, weight: InterWeight = .regular) -> Font {
        inter(size: style == .title2 ? 22 : 17, weight: weight)
    }
}

/// The Android type scale (ui/theme/Type.kt): size, line height and weight.
enum TextStyle {
    case titleLarge, titleMedium, titleSmall, headlineSmall
    case bodyLarge, bodyMedium, bodySmall
    case labelLarge, labelMedium, labelSmall

    var spec: (size: CGFloat, line: CGFloat, weight: InterWeight) {
        switch self {
        case .titleLarge: return (28, 36, .bold)
        case .titleMedium: return (18, 26, .semibold)
        case .titleSmall: return (14, 20, .semibold)
        case .headlineSmall: return (24, 32, .semibold)
        case .bodyLarge: return (16, 24, .regular)
        case .bodyMedium: return (14, 22, .regular)
        case .bodySmall: return (12, 18, .regular)
        case .labelLarge: return (14, 20, .medium)
        case .labelMedium: return (12, 16, .medium)
        case .labelSmall: return (11, 16, .medium)
        }
    }
}

private struct TextStyleModifier: ViewModifier {
    let style: TextStyle
    var color: Color?

    func body(content: Content) -> some View {
        let spec = style.spec
        content
            .font(.inter(size: spec.size, weight: spec.weight))
            // Inter's natural line height is about 1.21 times the size; the rest becomes extra line spacing.
            .lineSpacing(max(0, spec.line - spec.size * 1.21))
            .foregroundColor(color ?? Brand.onSurface)
    }
}

extension View {
    func textStyle(_ style: TextStyle, color: Color? = nil) -> some View {
        modifier(TextStyleModifier(style: style, color: color))
    }

    /// Fills the screen with the brand background.
    func appBackground() -> some View {
        background(Brand.background.ignoresSafeArea())
    }
}
