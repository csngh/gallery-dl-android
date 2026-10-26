import Foundation
import UniformTypeIdentifiers

/// gallery-dl categories that describe a kind of link rather than a website; these are grouped by host.
private let hostBasedCategories: Set<String> = ["", "directlink", "generic", "recursive", "ytdl", "noop", "urls", "text"]

private func stripWww(_ host: String) -> String {
    host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
}

/// Parses a link. Foundation on older iPhones refuses links with characters outside plain ASCII (accents, Japanese, and so
/// on), which are common in real links, so those are percent-encoded first. Only used to read the link; the original text
/// is what gets downloaded.
func parseLink(_ raw: String) -> URLComponents? {
    if let components = URLComponents(string: raw) { return components }
    var allowed = CharacterSet.urlHostAllowed
    allowed.formUnion(.urlPathAllowed)
    allowed.formUnion(.urlQueryAllowed)
    allowed.formUnion(.urlFragmentAllowed)
    allowed.formUnion(CharacterSet(charactersIn: "%:/?#[]@!$&'()*+,;=-._~"))
    guard let encoded = raw.addingPercentEncoding(withAllowedCharacters: allowed) else { return nil }
    return URLComponents(string: encoded)
}

/// The host of a link without "www.", for example "upload.wikimedia.org".
func hostOf(_ url: String) -> String {
    let host = stripWww(parseLink(url)?.host?.lowercased() ?? "")
    return host.isEmpty ? "other" : host
}

/// The website a link belongs to: gallery-dl's category where it names a website, the host otherwise.
func siteKeyFor(category: String?, url: String) -> String {
    if let category = category?.lowercased(), !hostBasedCategories.contains(category) { return category }
    return hostOf(url)
}

/// Second-level labels such as the "co" in example.co.uk, so those sites keep three labels.
private let secondLevelLabels: Set<String> = ["co", "com", "org", "net", "gov", "edu", "ac", "ne", "or"]

/// The host to ask for a website icon: the site's main domain (i.imgur.com becomes imgur.com). Nil for
/// addresses that have no public icon, such as localhost and IP addresses.
func iconHostOf(_ url: String) -> String? {
    guard let host = parseLink(url)?.host?.lowercased() else { return nil }
    if !host.contains(".") || host.contains(":") || host.allSatisfy({ $0.isNumber || $0 == "." }) { return nil }
    let labels = host.split(separator: ".").map(String.init)
    if labels.count <= 2 { return host }
    let keep = (secondLevelLabels.contains(labels[labels.count - 2]) && labels.last!.count == 2) ? 3 : 2
    return labels.suffix(keep).joined(separator: ".")
}

/// "imgur" becomes "Imgur"; a host such as "upload.wikimedia.org" is shown as it is.
func siteDisplayName(_ site: String) -> String {
    site.contains(".") ? site : site.prefix(1).uppercased() + site.dropFirst()
}

/// Short, readable name for a link folder: the last path segment, e.g. "AbC123" or "My Photo".
func linkDisplayName(_ url: String?, fallback: String) -> String {
    guard let url, let components = parseLink(url) else { return fallback }
    let last = components.path.split(separator: "/").last.map(String.init)
    let segment = last ?? stripWww(components.host ?? "")
    let ext = segment.contains(".") ? String(segment.split(separator: ".", omittingEmptySubsequences: false).last ?? "") : ""
    let stem = (2...4).contains(ext.count) ? String(segment.dropLast(ext.count + 1)) : segment
    let name = stem.replacingOccurrences(of: "_", with: " ").trimmingCharacters(in: .whitespaces)
    return name.isEmpty ? fallback : name
}

/// The title a folder shows: its tag when the user gave it one (the name moves to the line below), its name otherwise.
func folderTitle(_ displayName: String, _ tag: String?) -> String {
    guard let tag = tag?.trimmingCharacters(in: .whitespaces), !tag.isEmpty else { return displayName }
    return tag
}

/// The folder's own name, shown beside the item count while its tag is the title; nil when the name is the title.
func folderSubtitle(_ displayName: String, _ tag: String?) -> String? {
    guard let tag = tag?.trimmingCharacters(in: .whitespaces), !tag.isEmpty else { return nil }
    return displayName
}

/// Folder name for one link: host plus the last two path segments, e.g. "imgur.com_a_AbC123".
func linkFolderName(_ url: String) -> String {
    guard let components = parseLink(url) else { return "download" }
    let host = stripWww(components.host ?? "")
    let segments = components.path.split(separator: "/").map(String.init)
    let joined = ([host] + segments.suffix(2)).filter { !$0.isEmpty }.joined(separator: "_")
    let safe = joined.replacingOccurrences(of: "[^A-Za-z0-9._-]+", with: "_", options: .regularExpression)
    let trimmed = safe.trimmingCharacters(in: CharacterSet(charactersIn: "_."))
    let name = String(trimmed.prefix(60))
    return name.isEmpty ? "download" : name
}

func mimeType(forExtension ext: String) -> String {
    UTType(filenameExtension: ext.lowercased())?.preferredMIMEType ?? "application/octet-stream"
}
