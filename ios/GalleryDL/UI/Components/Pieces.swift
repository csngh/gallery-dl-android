import SwiftUI

// MARK: - Download results

extension TaskResult {
    /// Icon and tone that always stand for a download result.
    var tile: (symbol: String, tone: Tone) {
        switch self {
        case .ready: return ("play.circle.fill", .blue)
        case .inProgress: return ("clock.fill", .violet)
        case .completed: return ("checkmark.circle.fill", .green)
        case .partial: return ("exclamationmark.triangle.fill", .amber)
        case .failed: return ("exclamationmark.circle.fill", .red)
        case .cancelled: return ("nosign", .pink)
        }
    }

    var label: String {
        switch self {
        case .ready: return S.statusReady
        case .inProgress: return S.statusDownloading
        case .completed: return S.statusCompleted
        case .partial: return S.statusPartial
        case .failed: return S.statusFailed
        case .cancelled: return S.statusCancelled
        }
    }

    var color: Color {
        switch self {
        case .ready: return Tone.blue.icon
        case .failed: return Brand.error
        case .partial: return Brand.tertiary
        case .completed: return Brand.primary
        default: return Brand.onSurfaceVariant
        }
    }
}

extension DownloadTask {
    /// A link waiting in the download queue reads "Queued"; the other states read as their result.
    var statusLabel: String { status == .queued ? S.statusQueued : result.label }

    /// "2 files saved · 1 file failed", or empty.
    var summaryParts: [String] {
        var parts: [String] = []
        if filesDownloaded > 0 { parts.append(S.filesSaved(filesDownloaded)) }
        if failedFiles > 0 { parts.append(S.filesFailed(failedFiles)) }
        return parts
    }
}

// MARK: - Link field

/// The paste-a-link logic shared by every screen that has a link field.
@MainActor
final class LinkInputModel: ObservableObject {
    @Published var text = "" { didSet { if text != oldValue { error = nil } } }
    @Published var error: UrlCheck?
    @Published var validating = false

    func submit(_ app: AppModel) async {
        let url = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !validating, !url.isEmpty else { return }
        validating = true
        error = nil
        await app.waitUntilReady()
        let result = await app.validator.check(url)
        if result.check == .valid {
            app.store.enqueue(url: url, site: result.site)
            text = ""
            error = nil
        } else {
            error = result.check
        }
        validating = false
    }
}

func urlErrorMessage(_ check: UrlCheck?) -> String? {
    switch check {
    case .unsupported: return S.errorUrlUnsupported
    case .malformed: return S.errorUrlMalformed
    default: return nil
    }
}

/// The field where one link is pasted and added.
struct LinkField: View {
    @EnvironmentObject private var app: AppModel
    @ObservedObject var input: LinkInputModel

    var body: some View {
        OutlinedField(
            label: S.urlLabel,
            text: $input.text,
            error: urlErrorMessage(input.error),
            keyboard: .URL,
            submitLabel: .go,
            onSubmit: { Task { await input.submit(app) } },
            trailing: AnyView(trailing),
        )
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .padding(.top, 6)   // room for the floating label
    }

    @ViewBuilder private var trailing: some View {
        if input.validating {
            ProgressView().frame(width: 48, height: 48)
        } else {
            IconButton(
                symbol: "plus",
                color: input.text.trimmingCharacters(in: .whitespaces).isEmpty ? Brand.onSurface.opacity(0.38) : ActionColors.positive,
                label: S.actionAdd,
            ) { Task { await input.submit(app) } }
            .disabled(input.text.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }
}

// MARK: - Link menu

/// The less frequent actions for a link, kept behind one three-dot button.
struct LinkActionsMenu: View {
    var showRetry: Bool
    /// What the retry item says: "Retry" for a link that went wrong, "Check for new files" for one that finished.
    var retryLabel = S.actionRetry
    var onRetry: () -> Void
    var onRemove: () -> Void
    var onEdit: (() -> Void)?
    var onViewLink: (() -> Void)?

    var body: some View {
        Menu {
            if let onViewLink { Button(action: onViewLink) { Label(S.actionViewLink, systemImage: "link") } }
            if let onEdit { Button(action: onEdit) { Label(S.actionEdit, systemImage: "pencil") } }
            if showRetry { Button(action: onRetry) { Label(retryLabel, systemImage: "arrow.clockwise") } }
            Button(role: .destructive, action: onRemove) { Label(S.actionRemove, systemImage: "trash") }
        } label: {
            Image(systemName: "ellipsis")
                .rotationEffect(.degrees(90))
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(Brand.onSurfaceVariant)
                .frame(width: 48, height: 48)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(S.actionMore)
    }
}

// MARK: - Website icons

/// Website icons are fetched from the site itself (https only) and kept on the phone until the app is removed, so a
/// site's icon is asked for once and not again on every launch. The larger touch icon is tried first, then the classic
/// favicon. A site with neither is remembered for a week before it is asked again.
final class SiteIcons {
    static let shared = SiteIcons()
    private let cache = NSCache<NSString, UIImage>()
    private let lock = NSLock()
    private let directory: URL = {
        let url = MediaFiles.support.appendingPathComponent("siteicons", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var excluded = url
        try? excluded.setResourceValues(values)
        return url
    }()

    private func name(_ host: String) -> String {
        String(host.lowercased().map { $0.isLetter || $0.isNumber || $0 == "." || $0 == "-" ? $0 : "_" })
    }

    func icon(for host: String) async -> UIImage? {
        if let cached = cache.object(forKey: host as NSString) { return cached }
        let file = directory.appendingPathComponent(name(host) + ".img")
        let miss = directory.appendingPathComponent(name(host) + ".none")
        if let data = try? Data(contentsOf: file) {
            if let image = UIImage(data: data) {
                cache.setObject(image, forKey: host as NSString)
                return image
            }
            try? FileManager.default.removeItem(at: file)
        }
        if let modified = (try? FileManager.default.attributesOfItem(atPath: miss.path))?[.modificationDate] as? Date,
           Date().timeIntervalSince(modified) < 7 * 24 * 3600 { return nil }
        for path in ["apple-touch-icon.png", "favicon.ico"] {
            guard let url = URL(string: "https://\(host)/\(path)") else { continue }
            var request = URLRequest(url: url)
            request.timeoutInterval = 8
            if let (data, response) = try? await URLSession.shared.data(for: request),
               (response as? HTTPURLResponse)?.statusCode == 200, data.count <= 1_048_576, let image = UIImage(data: data) {
                try? data.write(to: file, options: .atomic)
                try? FileManager.default.removeItem(at: miss)
                cache.setObject(image, forKey: host as NSString)
                return image
            }
        }
        FileManager.default.createFile(atPath: miss.path, contents: Data())
        return nil
    }
}

private func javaHash(_ text: String) -> Int32 {
    var hash: Int32 = 0
    for unit in text.utf16 { hash = hash &* 31 &+ Int32(unit) }
    return hash
}

/// Tones with no fixed meaning, so a website never looks like a success or a failure.
private let siteTones: [Tone] = [.blue, .violet, .teal, .pink]

/// The website's own icon when it can be loaded; otherwise its coloured initial, where the same site always
/// gets the same colour.
struct SiteTile: View {
    let site: String
    let iconHost: String?
    var size: CGFloat = TileSize.large
    @State private var icon: UIImage?

    var body: some View {
        let tone = siteTones[Int(javaHash(site) & Int32.max) % siteTones.count]
        ZStack {
            RoundedRectangle(cornerRadius: Radius.sm, style: .continuous).fill(tone.tint)
            if let icon {
                Image(uiImage: icon).resizable().scaledToFit().padding(Spacing.sm)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(hex: 0xEDEFF7))
            } else {
                Text(site.first(where: { $0.isLetter || $0.isNumber }).map { String($0).uppercased() } ?? "?")
                    .textStyle(.titleMedium, color: tone.icon)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
        .task(id: iconHost) {
            guard let iconHost else { return }
            icon = await SiteIcons.shared.icon(for: iconHost)
        }
    }
}

// MARK: - Saved files

/// A saved image or video preview: a shimmer while it loads and a tile if it cannot be read.
struct GalleryImage: View {
    let item: LibraryItem
    var fit = false
    var showShimmer = true
    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        ZStack {
            if let image {
                if fit { Image(uiImage: image).resizable().scaledToFit() } else { Image(uiImage: image).resizable().scaledToFill() }
            } else if failed {
                IconTile(symbol: "xmark.square.fill", tone: .amber, size: TileSize.large)
            } else if showShimmer {
                ShimmerBox(radius: 0)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipped()
        .task(id: item.id) {
            image = nil; failed = false
            let loaded = fit ? await FullImages.image(for: item) : await ThumbnailStore.shared.image(for: item)
            if let loaded { image = loaded } else { failed = true }
        }
    }
}

// MARK: - Navigation

enum Destination: CaseIterable, Hashable {
    case home, queue, library, activity

    var label: String {
        switch self {
        case .home: return S.navHome
        case .queue: return S.navQueue
        case .library: return S.navLibrary
        case .activity: return S.navActivity
        }
    }

    var symbol: String {
        switch self {
        case .home: return "house.fill"
        case .queue: return "arrow.down.to.line"
        case .library: return "photo.on.rectangle.angled"
        case .activity: return "chart.bar.fill"
        }
    }

    var tone: Tone {
        switch self {
        case .home: return .pink
        case .queue: return .blue
        case .library: return .violet
        case .activity: return .teal
        }
    }
}

/// The Material 3 navigation bar: an icon on a tinted pill when selected, with its label below.
struct BottomBar: View {
    @Binding var selection: Destination

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Destination.allCases, id: \.self) { destination in
                let selected = selection == destination
                Button { selection = destination } label: {
                    VStack(spacing: Spacing.xs) {
                        Image(systemName: destination.symbol)
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundColor(selected ? destination.tone.icon : destination.tone.icon.opacity(0.75))
                            .frame(width: 64, height: 32)
                            .background(Capsule().fill(selected ? destination.tone.tint : Color.clear))
                        Text(destination.label)
                            .textStyle(.labelMedium, color: selected ? Brand.onSurface : Brand.onSurfaceVariant)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 12)
        .padding(.bottom, 16)
        .background(Brand.surfaceContainer.ignoresSafeArea(edges: .bottom))
    }
}


// MARK: - Storage warning

/// Shown on Home and Queue while the storage is 99% full and downloads are paused for that reason.
struct StorageBanner: View {
    @ObservedObject var storage: StorageMonitor

    var body: some View {
        if storage.isFull {
            HStack(spacing: Spacing.sm) {
                Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 22)).foregroundColor(Color(hex: 0xF9DEDC))
                Text(S.storageWarning(storage.usedPercent)).textStyle(.bodyMedium, color: Color(hex: 0xF9DEDC))
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(Spacing.md)
            .background(RoundedRectangle(cornerRadius: Radius.md, style: .continuous).fill(Color(hex: 0x8C1D18)))
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.xs)
        }
    }
}
