import AVFoundation
import UIKit

/// Where downloaded files live: the app's private Application Support folder, never the Files app.
enum MediaFiles {
    static let support: URL = {
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }()

    static let root = support.appendingPathComponent("GalleryDl", isDirectory: true)
    static let archives = support.appendingPathComponent("archives", isDirectory: true)
    static let cookiesFile = support.appendingPathComponent("cookies.txt")
    static let storeFile = support.appendingPathComponent("store.json")

    static func scratch(for taskId: Int64) -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("gdl/\(taskId)", isDirectory: true)
    }

    static func archive(for taskId: Int64) -> URL { archives.appendingPathComponent("\(taskId).sqlite") }

    static func deleteArchive(for taskId: Int64) {
        let manager = FileManager.default
        guard let names = try? manager.contentsOfDirectory(atPath: archives.path) else { return }
        for name in names where name.hasPrefix("\(taskId).sqlite") {
            try? manager.removeItem(at: archives.appendingPathComponent(name))
        }
    }

    /// Moves a finished download into its link folder. Returns the path below the root and the final name.
    /// The name of the folder gallery-dl saved `file` in, and only that one: its parents are left out. Nil when the file
    /// sits straight in the temporary root, with no folder of its own.
    static func innermostFolder(of file: URL, below scratchRoot: URL) -> String? {
        // Both sides resolved the same way: the temporary folder may be reached as /var or /private/var.
        let dir = file.deletingLastPathComponent().resolvingSymlinksInPath()
        guard dir.path != scratchRoot.resolvingSymlinksInPath().path else { return nil }
        let name = dir.lastPathComponent
        return name.isEmpty ? nil : name
    }

    /// Moves a file already in the library into `folder`. Returns its new path below the library, or nil when it was left where
    /// it is (it is missing, already there, or a file of that name is in the way).
    static func move(relative: String, toFolder folder: String) -> String? {
        let manager = FileManager.default
        let source = root.appendingPathComponent(relative)
        let name = source.lastPathComponent
        let dir = root.appendingPathComponent(folder, isDirectory: true)
        let target = dir.appendingPathComponent(name)
        guard manager.fileExists(atPath: source.path), source.standardizedFileURL.path != target.standardizedFileURL.path,
              !manager.fileExists(atPath: target.path) else { return nil }
        do {
            try manager.createDirectory(at: dir, withIntermediateDirectories: true)
            try manager.moveItem(at: source, to: target)
        } catch { return nil }
        return "\(folder)/\(name)"
    }

    /// Removes a folder of the library on disk if nothing is left in it.
    static func removeIfEmpty(folder: String) {
        let dir = root.appendingPathComponent(folder, isDirectory: true)
        if let left = try? FileManager.default.contentsOfDirectory(atPath: dir.path), left.isEmpty { try? FileManager.default.removeItem(at: dir) }
    }

    /// Moves a downloaded file into the library, in the folder named `folder`.
    static func importFile(_ file: URL, folder: String) throws -> (relative: String, name: String) {
        let manager = FileManager.default
        let subDir = folder
        let dir = root.appendingPathComponent(subDir, isDirectory: true)
        try manager.createDirectory(at: dir, withIntermediateDirectories: true)

        var name = file.lastPathComponent
        var target = dir.appendingPathComponent(name)
        var counter = 1
        let base = (name as NSString).deletingPathExtension, ext = (name as NSString).pathExtension
        while manager.fileExists(atPath: target.path) {
            name = ext.isEmpty ? "\(base) (\(counter))" : "\(base) (\(counter)).\(ext)"
            target = dir.appendingPathComponent(name)
            counter += 1
        }
        try manager.moveItem(at: file, to: target)
        return ("\(subDir)/\(name)", name)
    }

    /// Copies a library file into `folder` (a favourite), keeping the original where it is.
    static func copyFile(of item: LibraryItem, toFolder folder: String) throws -> (relative: String, name: String) {
        let manager = FileManager.default
        let dir = root.appendingPathComponent(folder, isDirectory: true)
        try manager.createDirectory(at: dir, withIntermediateDirectories: true)
        var name = item.fileURL.lastPathComponent
        var target = dir.appendingPathComponent(name)
        var counter = 1
        let base = (name as NSString).deletingPathExtension, ext = (name as NSString).pathExtension
        while manager.fileExists(atPath: target.path) {
            name = ext.isEmpty ? "\(base) (\(counter))" : "\(base) (\(counter)).\(ext)"
            target = dir.appendingPathComponent(name)
            counter += 1
        }
        try manager.copyItem(at: item.fileURL, to: target)
        return ("\(folder)/\(name)", name)
    }

    /// Deletes the file, and the folders it was in once they are empty, so deleted links do not leave empty folders behind.
    static func remove(_ item: LibraryItem) {
        let manager = FileManager.default
        try? manager.removeItem(at: item.fileURL)
        var folder = item.fileURL.deletingLastPathComponent()
        let rootPath = root.resolvingSymlinksInPath().path
        while folder.resolvingSymlinksInPath().path.count > rootPath.count,
              (try? manager.contentsOfDirectory(atPath: folder.path))?.isEmpty == true {
            try? manager.removeItem(at: folder)
            folder = folder.deletingLastPathComponent()
        }
    }

    static func exists(_ item: LibraryItem) -> Bool { FileManager.default.fileExists(atPath: item.fileURL.path) }

    /// Bytes used by the app's records (the store and the per-link download memory).
    static func recordsBytes() -> Int64 {
        var total: Int64 = 0
        let manager = FileManager.default
        if let attributes = try? manager.attributesOfItem(atPath: storeFile.path) { total += (attributes[.size] as? Int64) ?? 0 }
        if let names = try? manager.contentsOfDirectory(atPath: archives.path) {
            for name in names {
                let attributes = try? manager.attributesOfItem(atPath: archives.appendingPathComponent(name).path)
                total += (attributes?[.size] as? Int64) ?? 0
            }
        }
        return total
    }
}

/// Full-size images for the viewer. Thumbnails live in `ThumbnailStore`.
enum FullImages {
    static func image(for item: LibraryItem) async -> UIImage? {
        let url = item.fileURL
        return await Task.detached(priority: .userInitiated) {
            UIImage(contentsOfFile: url.path)?.preparingForDisplay()
        }.value
    }
}
