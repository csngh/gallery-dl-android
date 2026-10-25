import Foundation

struct PythonError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// Owns the embedded interpreter. Calls block, so they run on a background queue.
final class PythonRuntime {
    static let shared = PythonRuntime()

    private let queue = DispatchQueue(label: "app.gallerydl.python", attributes: .concurrent)
    private var started = false

    func start() throws {
        guard !started else { return }
        var error: NSString?
        guard GDLStartPython(Bundle.main.bundlePath, &error) else {
            throw PythonError(message: (error as String?) ?? "Python failed to start")
        }
        started = true
    }

    /// Calls `gdl_ios.<function>` with a JSON argument and decodes the JSON it returns.
    func call(_ function: String, _ argument: [String: Any] = [:]) async throws -> [String: Any] {
        let data = try JSONSerialization.data(withJSONObject: argument)
        let json = String(decoding: data, as: UTF8.self)
        let reply: String = await withCheckedContinuation { continuation in
            queue.async { continuation.resume(returning: GDLCall("gdl_ios", function, json)) }
        }
        guard let object = try JSONSerialization.jsonObject(with: Data(reply.utf8)) as? [String: Any] else {
            throw PythonError(message: "Unexpected reply from Python")
        }
        if let message = object["error"] as? String { throw PythonError(message: message) }
        return object
    }
}
