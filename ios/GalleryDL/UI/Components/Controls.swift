import SwiftUI

// MARK: - App-wide UI state (dialogs and the full-screen viewer sit above the navigation bar, like Android windows)

struct ViewerRequest: Equatable {
    let folderId: Int64
    let query: String
    let startIndex: Int
}

@MainActor
final class UIState: ObservableObject {
    @Published var dialog: AnyView?
    @Published var viewer: ViewerRequest?

    func present<V: View>(_ view: V) { dialog = AnyView(view) }
    func dismissDialog() { dialog = nil }
}

// MARK: - Buttons

struct FilledPill: View {
    let title: String
    var symbol: String?
    var container: Color = Brand.primary
    var content: Color = Brand.onPrimary
    var enabled = true
    var fillWidth = false
    var compact = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                if let symbol { Image(systemName: symbol).font(.system(size: 17, weight: .semibold)) }
                Text(title).textStyle(.labelLarge, color: enabled ? content : Brand.onSurface.opacity(0.38))
            }
            .foregroundColor(enabled ? content : Brand.onSurface.opacity(0.38))
            .padding(.horizontal, compact ? Spacing.md : (symbol == nil ? Spacing.lg : Spacing.md + 8))
            .frame(minHeight: 40)
            .frame(maxWidth: fillWidth ? .infinity : nil)
            .background(Capsule().fill(enabled ? container : Brand.onSurface.opacity(0.12)))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct OutlinedPill: View {
    let title: String
    var symbol: String?
    var color: Color = Brand.primary
    var border: Color = Brand.outline
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                if let symbol { Image(systemName: symbol).font(.system(size: 17, weight: .semibold)) }
                Text(title).textStyle(.labelLarge, color: color)
            }
            .foregroundColor(color)
            .padding(.horizontal, Spacing.lg)
            .frame(minHeight: 40)
            .overlay(Capsule().stroke(border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct TextPill: View {
    let title: String
    var symbol: String?
    var color: Color = Brand.primary
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                if let symbol { Image(systemName: symbol).font(.system(size: 17, weight: .semibold)) }
                Text(title).textStyle(.labelLarge, color: color)
            }
            .foregroundColor(color)
            .padding(.horizontal, Spacing.md)
            .frame(minHeight: 40)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// An icon-only button with a 48 pt touch target.
struct IconButton: View {
    let symbol: String
    var color: Color = Brand.onSurfaceVariant
    var label: String = ""
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 20, weight: .medium))
                .foregroundColor(color)
                .frame(width: 48, height: 48)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// Starts one link that is waiting. Starting is a positive action, so it is green.
struct StartButton: View {
    let action: () -> Void
    var body: some View {
        FilledPill(title: S.actionStart, symbol: "play.fill", container: ActionColors.positive, content: ActionColors.onPositive, compact: true, action: action)
    }
}

/// Starts every link that is waiting, shown only while at least one is.
struct StartAllButton: View {
    let count: Int
    let action: () -> Void
    var body: some View {
        if count > 0 {
            FilledPill(title: S.actionStartAll(count), symbol: "play.fill", container: ActionColors.positive, content: ActionColors.onPositive, fillWidth: true, action: action)
                .padding(.horizontal, Spacing.md)
        }
    }
}

// MARK: - Switch (Material 3)

struct M3Switch: View {
    @Binding var isOn: Bool
    var enabled = true

    var body: some View {
        ZStack(alignment: isOn ? .trailing : .leading) {
            Capsule()
                .fill(isOn ? Brand.primary : Brand.surfaceContainerHighest)
                .overlay(Capsule().stroke(Brand.outline, lineWidth: isOn ? 0 : 2))
            Circle()
                .fill(isOn ? Brand.onPrimary : Brand.outline)
                .frame(width: isOn ? 24 : 16, height: isOn ? 24 : 16)
                .padding(.horizontal, isOn ? 4 : 8)
        }
        .frame(width: 52, height: 32)
        .opacity(enabled ? 1 : 0.38)
        .animation(.easeOut(duration: 0.15), value: isOn)
    }
}

// MARK: - Text field (Material 3 outlined)

/// The colour behind a field, so its floating label can cut a clean gap in the border.
private struct FieldBackgroundKey: EnvironmentKey { static let defaultValue = Brand.background }

extension EnvironmentValues {
    var fieldBackground: Color {
        get { self[FieldBackgroundKey.self] }
        set { self[FieldBackgroundKey.self] = newValue }
    }
}

struct OutlinedField: View {
    let label: String
    @Binding var text: String
    var error: String?
    var enabled = true
    var keyboard: UIKeyboardType = .default
    var submitLabel: SubmitLabel = .done
    var onSubmit: (() -> Void)?
    var trailing: AnyView?

    @FocusState private var focused: Bool
    @Environment(\.fieldBackground) private var fieldBackground

    private var floating: Bool { focused || !text.isEmpty }
    private var borderColor: Color { error != nil ? Brand.error : (focused ? Brand.primary : Brand.outline) }
    private var labelColor: Color { error != nil ? Brand.error : (focused ? Brand.primary : Brand.onSurfaceVariant) }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: Radius.sm)
                    .stroke(borderColor, lineWidth: focused || error != nil ? 2 : 1)
                HStack {
                    TextField("", text: $text)
                        .textStyle(.bodyLarge)
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                        .keyboardType(keyboard)
                        .submitLabel(submitLabel)
                        .focused($focused)
                        .disabled(!enabled)
                        .onSubmit { onSubmit?() }
                    if let trailing { trailing }
                }
                .padding(.leading, Spacing.md)
                .padding(.trailing, trailing == nil ? Spacing.md : Spacing.xs)
                Text(label)
                    .textStyle(floating ? .bodySmall : .bodyLarge, color: labelColor)
                    .padding(.horizontal, floating ? 4 : 0)
                    .background(floating ? fieldBackground : Color.clear)
                    .padding(.leading, floating ? 12 : Spacing.md)
                    .offset(y: floating ? -28 : 0)
                    .allowsHitTesting(false)
                    .animation(.easeOut(duration: 0.15), value: floating)
            }
            .frame(height: 56)
            .opacity(enabled ? 1 : 0.38)
            if let error {
                Text(error).textStyle(.bodySmall, color: Brand.error).padding(.horizontal, Spacing.md)
            }
        }
    }
}

// MARK: - Cards, tiles and small pieces

struct Card<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).fill(Brand.surfaceContainerHighest))
            .clipShape(RoundedRectangle(cornerRadius: Radius.md, style: .continuous))
            .environment(\.fieldBackground, Brand.surfaceContainerHighest)
    }
}

enum TileSize {
    static let small: CGFloat = 28, regular: CGFloat = 36, large: CGFloat = 44, hero: CGFloat = 72
}

/// An icon on a tinted rounded square. Corner radius is 8 for small tiles, 12 up to 44, 16 from 48 up.
struct IconTile: View {
    let symbol: String
    let tone: Tone
    var size: CGFloat = TileSize.regular

    var body: some View {
        let radius: CGFloat = size >= 48 ? 16 : (size >= 36 ? 12 : 8)
        RoundedRectangle(cornerRadius: radius, style: .continuous)
            .fill(tone.tint)
            .frame(width: size, height: size)
            .overlay(Image(systemName: symbol).font(.system(size: size * 0.5, weight: .semibold)).foregroundColor(tone.icon))
    }
}

/// An icon, a short headline and one helpful sentence.
struct EmptyState: View {
    let title: String
    let message: String
    let symbol: String
    let tone: Tone

    var body: some View {
        VStack(spacing: Spacing.sm) {
            IconTile(symbol: symbol, tone: tone, size: TileSize.hero)
            Text(title).textStyle(.titleMedium).multilineTextAlignment(.center).padding(.top, Spacing.sm)
            Text(message).textStyle(.bodyMedium, color: Brand.onSurfaceVariant).multilineTextAlignment(.center)
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// A linear progress bar: the primary colour on the secondary-container track.
struct LinearProgress: View {
    var value: Double?

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Brand.secondaryContainer)
                if let value {
                    Capsule().fill(Brand.primary).frame(width: max(4, geo.size.width * value))
                } else {
                    IndeterminateBar(width: geo.size.width)
                }
            }
        }
        .frame(height: 4)
        .clipShape(Capsule())
        .animation(.easeOut(duration: 0.25), value: value)
    }
}

private struct IndeterminateBar: View {
    let width: CGFloat
    @State private var phase = false
    var body: some View {
        Capsule().fill(Brand.primary)
            .frame(width: width * 0.35)
            .offset(x: phase ? width * 0.75 : -width * 0.1)
            .onAppear { withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) { phase = true } }
    }
}

struct FilterChip: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                if selected { Image(systemName: "checkmark").font(.system(size: 13, weight: .bold)) }
                Text(title).textStyle(.labelLarge, color: selected ? Brand.onSecondaryContainer : Brand.onSurfaceVariant)
            }
            .foregroundColor(selected ? Brand.onSecondaryContainer : Brand.onSurfaceVariant)
            .padding(.horizontal, 12)
            .frame(height: 32)
            .background(RoundedRectangle(cornerRadius: Radius.xs).fill(selected ? Brand.secondaryContainer : Color.clear))
            .overlay(RoundedRectangle(cornerRadius: Radius.xs).stroke(Brand.outline, lineWidth: selected ? 0 : 1))
        }
        .buttonStyle(.plain)
    }
}

/// A screen title bar: large bold title on the left, actions on the right.
struct TopBar<Leading: View, Trailing: View>: View {
    let title: String
    @ViewBuilder var leading: Leading
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(spacing: Spacing.sm) {
            leading
            // A long title (a folder with a tag) shrinks a little and may take a second line instead of being cut off.
            Text(title).textStyle(.titleLarge).lineLimit(2).minimumScaleFactor(0.7)
            Spacer(minLength: 0)
            trailing
        }
        .padding(.horizontal, Spacing.md)
        .frame(height: 64)
    }
}

extension TopBar where Leading == EmptyView {
    init(title: String, @ViewBuilder trailing: () -> Trailing) {
        self.init(title: title, leading: { EmptyView() }, trailing: trailing)
    }
}

extension TopBar where Leading == EmptyView, Trailing == EmptyView {
    init(title: String) { self.init(title: title, leading: { EmptyView() }, trailing: { EmptyView() }) }
}

// MARK: - Shimmer

struct ShimmerBox: View {
    var radius: CGFloat = Radius.md
    @State private var phase: CGFloat = -1
    private let animate = !UIAccessibility.isReduceMotionEnabled

    var body: some View {
        GeometryReader { geo in
            let base = Brand.surfaceVariant
            RoundedRectangle(cornerRadius: radius, style: .continuous)
                .fill(base)
                .overlay(
                    LinearGradient(colors: [.clear, Color.white.opacity(0.08), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: geo.size.width)
                        .offset(x: phase * geo.size.width * 1.5)
                        .opacity(animate ? 1 : 0)
                )
                .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        }
        .onAppear {
            guard animate else { return }
            withAnimation(.linear(duration: 1.3).repeatForever(autoreverses: false)) { phase = 1 }
        }
    }
}

struct ShimmerCardList: View {
    var count = 3
    var cardHeight: CGFloat = 96
    var body: some View {
        VStack(spacing: Spacing.md) {
            ForEach(0..<count, id: \.self) { _ in ShimmerBox().frame(height: cardHeight) }
        }
        .padding(Spacing.md)
        Spacer(minLength: 0)
    }
}

struct ShimmerGrid: View {
    var minSize: CGFloat
    var count: Int
    var radius: CGFloat = Radius.md
    var gap: CGFloat = Spacing.md
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: minSize), spacing: gap)], spacing: gap) {
            ForEach(0..<count, id: \.self) { _ in ShimmerBox(radius: radius).aspectRatio(1, contentMode: .fit) }
        }
        .padding(Spacing.md)
        Spacer(minLength: 0)
    }
}

// MARK: - Scrolling with soft edges

private struct ScrollMetrics: PreferenceKey {
    static var defaultValue = CGSize.zero      // width: content offset (0 at the top), height: content height
    static func reduce(value: inout CGSize, nextValue: () -> CGSize) { value = nextValue() }
}

/// A scroll view that fades the top and bottom edges to transparent, so content never ends in a hard cut-off line.
/// An edge only fades while there is more content in that direction, and eases in and out.
struct SoftScrollView<Content: View>: View {
    var fade: CGFloat = 28
    @ViewBuilder var content: Content
    @State private var offset: CGFloat = 0
    @State private var contentHeight: CGFloat = 0

    var body: some View {
        GeometryReader { outer in
            let top = offset < -1 ? 1.0 : 0.0
            let bottom = (contentHeight + offset - outer.size.height) > 1 ? 1.0 : 0.0
            ScrollView {
                content
                    .background(
                        GeometryReader { geo in
                            Color.clear.preference(
                                key: ScrollMetrics.self,
                                value: CGSize(width: geo.frame(in: .named("soft-scroll")).minY, height: geo.size.height),
                            )
                        }
                    )
            }
            .coordinateSpace(name: "soft-scroll")
            .onPreferenceChange(ScrollMetrics.self) { offset = $0.width; contentHeight = $0.height }
            .modifier(DismissKeyboardOnScroll())
            .mask(
                VStack(spacing: 0) {
                    LinearGradient(colors: [Color.black.opacity(1 - top), .black], startPoint: .top, endPoint: .bottom).frame(height: fade)
                    Rectangle().fill(Color.black)
                    LinearGradient(colors: [.black, Color.black.opacity(1 - bottom)], startPoint: .top, endPoint: .bottom).frame(height: fade)
                }
                .animation(.easeOut(duration: 0.2), value: top)
                .animation(.easeOut(duration: 0.2), value: bottom)
            )
        }
    }
}

/// Drag-to-dismiss keyboard exists from iOS 16.
struct DismissKeyboardOnScroll: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.0, *) {
            content.scrollDismissesKeyboard(.interactively)
        } else {
            content
        }
    }
}

// MARK: - Dialogs

/// The single confirmation dialog used for every important or hard-to-undo action.
struct ConfirmDialog: View {
    @EnvironmentObject private var ui: UIState
    let title: String
    let message: String
    let confirmLabel: String
    var dismissLabel = S.actionCancel
    var destructive = true
    let onConfirm: () -> Void

    var body: some View {
        DialogCard(title: title) {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text(message).textStyle(.bodyMedium, color: Brand.onSurfaceVariant)
                Text(S.confirmQuestion).textStyle(.titleSmall, color: Brand.onSurfaceVariant)
            }
        } actions: {
            TextPill(title: dismissLabel, color: Brand.onSurfaceVariant) { ui.dismissDialog() }
            FilledPill(
                title: confirmLabel,
                container: destructive ? Brand.error : Brand.primary,
                content: destructive ? Brand.onError : Brand.onPrimary,
            ) { ui.dismissDialog(); onConfirm() }
        }
    }
}

/// The shape every dialog shares: a rounded card with a title, content and right-aligned buttons.
struct DialogCard<Content: View, Actions: View>: View {
    let title: String
    @ViewBuilder var content: Content
    @ViewBuilder var actions: Actions

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(title).textStyle(.headlineSmall)
            content
            HStack(spacing: Spacing.sm) {
                Spacer(minLength: 0)
                actions
            }
            .padding(.top, Spacing.sm)
        }
        .padding(Spacing.lg)
        .frame(maxWidth: 420)
        .background(RoundedRectangle(cornerRadius: Radius.xl, style: .continuous).fill(Brand.surfaceContainerHigh))
        .environment(\.fieldBackground, Brand.surfaceContainerHigh)
        .padding(.horizontal, Spacing.lg)
    }
}

/// Draws the current dialog over everything, with a dim layer behind it.
struct DialogHost: View {
    @EnvironmentObject private var ui: UIState

    var body: some View {
        if let dialog = ui.dialog {
            ZStack {
                Color.black.opacity(0.45).ignoresSafeArea().onTapGesture { ui.dismissDialog() }
                dialog
            }
            .transition(.opacity)
        }
    }
}

/// Shows a link in full, wrapped, with the text selectable and a Copy button.
struct FullLinkDialog: View {
    @EnvironmentObject private var ui: UIState
    let url: String

    var body: some View {
        DialogCard(title: S.fullLinkTitle) {
            ScrollView { Text(url).textStyle(.bodyLarge).frame(maxWidth: .infinity, alignment: .leading).textSelectionIfAvailable() }
                .frame(maxHeight: 220)
        } actions: {
            TextPill(title: S.actionClose, color: Brand.onSurfaceVariant) { ui.dismissDialog() }
            FilledPill(title: S.actionCopy) { UIPasteboard.general.string = url; ui.dismissDialog() }
        }
    }
}

extension View {
    @ViewBuilder func textSelectionIfAvailable() -> some View {
        if #available(iOS 15.0, *) { self.textSelection(.enabled) } else { self }
    }
}
