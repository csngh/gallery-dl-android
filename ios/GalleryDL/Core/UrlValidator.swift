import Foundation

enum UrlCheck { case valid, malformed, unsupported }

/// `site` is only set for a valid link.
struct UrlResult {
    let check: UrlCheck
    var site: String?
}

struct UrlValidator {
    func check(_ raw: String) async -> UrlResult {
        let components = parseLink(raw)
        let wellFormed = !raw.contains(where: \.isWhitespace)
            && ["http", "https"].contains(components?.scheme?.lowercased() ?? "")
            && !(components?.host ?? "").isEmpty
        guard wellFormed else { return UrlResult(check: .malformed) }
        guard let reply = try? await PythonRuntime.shared.call("check", ["url": raw]), reply["supported"] as? Bool == true else {
            return UrlResult(check: .unsupported)
        }
        return UrlResult(check: .valid, site: siteKeyFor(category: reply["site"] as? String, url: raw))
    }
}
