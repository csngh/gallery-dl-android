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
