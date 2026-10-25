import Foundation

/// PENDING is a link that was added but not started yet; nothing downloads until the user starts it.
enum TaskStatus: String, Codable {
    case pending, queued, downloading, completed, failed, cancelled
}

/// What the user cares about: a completed task with failed files or errors is only partly successful.
enum TaskResult {
    case ready, inProgress, completed, partial, failed, cancelled
}

struct DownloadTask: Identifiable, Codable, Equatable {
    var id: Int64
    var url: String
    var status: TaskStatus = .queued
    var filesDownloaded = 0
    var currentFile: String?
    var error: String?
    var failedFiles = 0
    /// 1-based position of the file being downloaded in this run; 0 if none yet.
    var fileIndex = 0
    /// Number of files in the link when the site reports it reliably; 0 if unknown.
    var totalFiles = 0
    /// Percent (0-100) of the current file, or -1 when its size is unknown.
    var fileProgress = -1
    /// The website of the link (gallery-dl category, or the host).
    var site: String?
    var createdAt: Date
    var finishedAt: Date?

    var result: TaskResult {
        switch status {
        case .pending: return .ready
        case .queued, .downloading: return .inProgress
        case .completed: return (failedFiles > 0 || error != nil) ? .partial : .completed
        case .failed: return .failed
        case .cancelled: return .cancelled
        }
    }

    /// Overall progress of a running download in 0...1, or nil if it cannot be determined.
    var overallProgress: Double? {
        let file = fileProgress >= 0 ? Double(fileProgress) / 100 : 0
        let value: Double
        if totalFiles > 0 && fileIndex > 0 {
            value = (Double(fileIndex - 1) + file) / Double(totalFiles)
        } else if fileProgress >= 0 {
            value = file
        } else {
            return nil
        }
        return min(max(value, 0), 1)
    }

    var siteKey: String { site ?? hostOf(url) }
    var isFinished: Bool { status == .completed || status == .failed || status == .cancelled }
}

struct Folder: Identifiable, Codable, Equatable {
    var id: Int64
    var name: String
    /// A label the user gave the folder; it shows as "name / tag".
    var tag: String?
}

struct LibraryItem: Identifiable, Codable, Equatable {
    var id: Int64
    /// Path below the app's GalleryDl folder. Relative, because the app's container path can change on reinstall.
    var relativePath: String
    var displayName: String
    var mimeType: String
    var sourceUrl: String
    var taskId: Int64?
    var folderId: Int64?
    var addedAt: Date

    var fileURL: URL { MediaFiles.root.appendingPathComponent(relativePath) }
    var isImage: Bool { mimeType.hasPrefix("image/") }
    var isVideo: Bool { mimeType.hasPrefix("video/") }
    var isAudio: Bool { mimeType.hasPrefix("audio/") }
    var isVisual: Bool { isImage || isVideo }
}

/// One row per link folder in the library, with a cover and the number of items.
struct FolderSummary: Identifiable, Equatable {
    let id: Int64
    let name: String
    let itemCount: Int
    let cover: LibraryItem?
    let sourceUrl: String?
    let tag: String?

    /// What the library shows: the last part of the link rather than the full folder name.
    var displayName: String { linkDisplayName(sourceUrl, fallback: name) }

    /** What the library shows: the name, then " / tag" when the folder has a tag. */
    var title: String { folderTitle(displayName, tag) }
}
