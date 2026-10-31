import Foundation

/// The plain-text list of links used to move them between devices: one link per line, blank lines and lines starting
/// with "#" ignored. The same format is used by the Android app.
enum LinkList {
    enum Line: Equatable { case link(String), notALink }

    /// The first http or https link in a line of text, without the punctuation that often follows it.
    static func firstLink(in text: String) -> String? {
        guard let range = text.range(of: #"https?://[^\s<>"']+"#, options: [.regularExpression, .caseInsensitive]) else { return nil }
        var link = String(text[range])
        while let last = link.last, ".,;:!?)]}".contains(last) { link.removeLast() }
        return link.count > "https://".count ? link : nil
    }

    static func parse(_ text: String) -> [Line] {
        text.split(whereSeparator: \.isNewline)
            .map { $0.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "\u{FEFF}", with: "") }
            .filter { !$0.isEmpty && !$0.hasPrefix("#") }
            .map { line in firstLink(in: line).map(Line.link) ?? .notALink }
    }

    static func format(_ urls: [String]) -> String {
        var seen = Set<String>()
        return urls.filter { seen.insert($0).inserted }.map { $0 + "\n" }.joined()
    }

    /// The sheet written by the Favorites export, a CSV that Excel opens: one row per video, with the link it came from
    /// and the video's own link. A row repeated by two copies of the same video is written once.
    static func formatFavoriteVideos(_ rows: [(parent: String, video: String)]) -> String {
        func cell(_ text: String) -> String { "\"" + text.replacingOccurrences(of: "\"", with: "\"\"") + "\"" }
        var seen = Set<String>()
        let lines = rows.filter { seen.insert($0.parent + "\n" + $0.video).inserted }.map { cell($0.parent) + "," + cell($0.video) }
        return "\u{FEFF}\"Parent link\",\"Video link\"\r\n" + lines.map { $0 + "\r\n" }.joined()
    }
}

struct ImportResult {
    var added = 0, duplicates = 0, unsupported = 0, notLinks = 0
    var found: Int { added + duplicates + unsupported + notLinks }
}

/// What the export and import dialogs show.
enum Transfer {
    case importing(done: Int, total: Int)
    case imported(ImportResult)
    case failed
}
