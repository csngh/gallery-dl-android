import Foundation

/// Seconds to wait, written as one number ("2") or a range ("1.0-3.0").
enum DurationInput {
    private static let maxSeconds = 300.0

    /// The lower and upper bound (equal for a single number), or nil if the text is not valid.
    static func parse(_ text: String) -> [Double]? {
        let pattern = #"^\s*(\d+(?:\.\d+)?)\s*(?:-\s*(\d+(?:\.\d+)?))?\s*$"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let lowerRange = Range(match.range(at: 1), in: text),
              let lower = Double(text[lowerRange]) else { return nil }
        var upper = lower
        if let range = Range(match.range(at: 2), in: text), let value = Double(text[range]) { upper = value }
        return (lower <= upper && upper <= maxSeconds) ? [lower, upper] : nil
    }
}

/// Whole numbers and single seconds values, with the limits the app allows.
enum NumberInput {
    static func parseInt(_ text: String, range: ClosedRange<Int>) -> Int? {
        Int(text.trimmingCharacters(in: .whitespaces)).flatMap { range.contains($0) ? $0 : nil }
    }

    static func parseSeconds(_ text: String, range: ClosedRange<Double>) -> Double? {
        Double(text.trimmingCharacters(in: .whitespaces)).flatMap { range.contains($0) ? $0 : nil }
    }
}

let retriesRange = 0...20
let timeoutRange = 1.0...300.0

/// The gallery-dl options the user can switch on or off. Each value is kept even while its switch is off, so
/// turning an option back on restores what was typed before.
struct GalleryDlOptions: Codable, Equatable {
    var apiRest = false
    var sleepRequestEnabled = false
    var sleepRequest = "4.0-8.0"
    var sleepEnabled = false
    var sleep = "1.0-3.0"
    var retriesEnabled = false
    var retries = "4"
    var timeoutEnabled = false
    var timeout = "30"
    /// On by default. Off downloads files again even if they were saved before, which can create duplicates.
    var skipExisting = true
    var wifiOnly = false
    var cookiesEnabled = false

    /// Only the switched-on options, under gallery-dl's own option names, as JSON for the Python side.
    func toJson(cookiesPath: String? = nil) -> [String: Any] {
        var json: [String: Any] = [:]
        if retriesEnabled, let value = NumberInput.parseInt(retries, range: retriesRange) { json["retries"] = value }
        if timeoutEnabled, let value = NumberInput.parseSeconds(timeout, range: timeoutRange) { json["timeout"] = value }
        if !skipExisting { json["skip"] = false }
        if cookiesEnabled, let cookiesPath { json["cookies"] = cookiesPath }
        if apiRest { json["api"] = "rest" }
        if sleepRequestEnabled, let value = DurationInput.parse(sleepRequest) { json["sleep-request"] = seconds(value) }
        if sleepEnabled, let value = DurationInput.parse(sleep) { json["sleep"] = seconds(value) }
        return json
    }

    /// gallery-dl takes a plain number, or a two-item list for a random wait in that range.
    private func seconds(_ range: [Double]) -> Any { range[0] == range[1] ? range[0] : range }
}
