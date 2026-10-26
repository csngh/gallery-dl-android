import Foundation

/// The user's options, kept in UserDefaults (the counterpart of Android's DataStore).
@MainActor
final class SettingsStore: ObservableObject {
    @Published var options: GalleryDlOptions { didSet { save() } }
    @Published var startupSound: Bool { didSet { defaults.set(startupSound, forKey: Keys.sound) } }
    /// How many columns the files of a library folder are shown in: 2 to 4. Three by default.
    @Published var folderColumns: Int { didSet { defaults.set(folderColumns, forKey: Keys.columns) } }
    /// How many columns the folder list of the library uses: 2 or 3. Two by default.
    @Published var libraryColumns: Int { didSet { defaults.set(libraryColumns, forKey: Keys.libraryColumns) } }
    /// Silent looping previews of the videos in the library. On by default.
    @Published var livePreviews: Bool { didSet { defaults.set(livePreviews, forKey: Keys.previews) } }
    /// How many cookies the imported cookies.txt holds; 0 when none is imported.
    @Published private(set) var cookiesCount: Int

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let options = "gdl.options", sound = "startupSound", previews = "livePreviews", columns = "folderColumns", libraryColumns = "libraryColumns", cookies = "gdl.cookiesCount"
    }

    init() {
        let defaults = UserDefaults.standard
        if let bytes = defaults.data(forKey: Keys.options), let saved = try? JSONDecoder().decode(GalleryDlOptions.self, from: bytes) {
            options = saved
        } else {
            options = GalleryDlOptions()
        }
        // `bool(forKey:)` and `integer(forKey:)` also read values given as launch arguments, which arrive as text.
        startupSound = defaults.object(forKey: Keys.sound) == nil ? true : defaults.bool(forKey: Keys.sound)
        livePreviews = defaults.object(forKey: Keys.previews) == nil ? true : defaults.bool(forKey: Keys.previews)
        libraryColumns = defaults.object(forKey: Keys.libraryColumns) == nil ? 2 : min(max(defaults.integer(forKey: Keys.libraryColumns), 2), 3)
        folderColumns = defaults.object(forKey: Keys.columns) == nil ? 3 : min(max(defaults.integer(forKey: Keys.columns), 2), 4)
        cookiesCount = defaults.integer(forKey: Keys.cookies)
    }

    private func save() {
        if let bytes = try? JSONEncoder().encode(options) { defaults.set(bytes, forKey: Keys.options) }
    }

    /// The imported cookies file, or nil if there is none. The path is private to the app.
    var cookiesPath: String? {
        FileManager.default.fileExists(atPath: MediaFiles.cookiesFile.path) ? MediaFiles.cookiesFile.path : nil
    }

    /// Copies a cookies.txt (Netscape format) into private storage. Returns false if the file is not a readable
    /// cookies.txt. Cookie values are never logged or shown.
    func importCookies(from url: URL) -> Bool {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let bytes = try? Data(contentsOf: url), bytes.count <= 5 * 1024 * 1024,
              let text = String(data: bytes, encoding: .utf8) else { return false }
        let count = text.split(whereSeparator: \.isNewline).filter { line in
            let entry = line.hasPrefix("#HttpOnly_") ? String(line.dropFirst("#HttpOnly_".count)) : String(line)
            return !entry.trimmingCharacters(in: .whitespaces).isEmpty && !entry.hasPrefix("#") && entry.split(separator: "\t", omittingEmptySubsequences: false).count >= 7
        }.count
        guard count > 0, (try? bytes.write(to: MediaFiles.cookiesFile, options: .atomic)) != nil else { return false }
        cookiesCount = count
        defaults.set(count, forKey: Keys.cookies)
        options.cookiesEnabled = true
        return true
    }

    func removeCookies() {
        try? FileManager.default.removeItem(at: MediaFiles.cookiesFile)
        cookiesCount = 0
        defaults.removeObject(forKey: Keys.cookies)
        options.cookiesEnabled = false
    }

    /// Returns every option to its default. The imported cookies are kept.
    func resetAll() {
        let keepCookies = options.cookiesEnabled
        options = GalleryDlOptions()
        options.cookiesEnabled = keepCookies
        startupSound = true
        livePreviews = true
    }
}
