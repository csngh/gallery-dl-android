import UserNotifications

/// Local notifications for finished links. iOS cannot update a live progress notification while the app is in
/// the background, so only the result of each link is announced.
enum Notifications {
    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    static func finished(task: DownloadTask, status: TaskStatus, filesSaved: Int, failedFiles: Int) {
        if status == .cancelled { return }
        let title: String
        if status == .failed { title = S.notifFailed } else if failedFiles > 0 { title = S.notifPartial } else { title = S.notifComplete }
        var body = siteDisplayName(task.site ?? hostOf(task.url))
        if filesSaved > 0 { body += " · " + S.filesSaved(filesSaved) }
        if failedFiles > 0 { body += " · " + S.filesFailed(failedFiles) }
        post(id: "result-\(task.id)", title: title, body: body, subtitle: String(task.url.replacingOccurrences(of: "https://", with: "").replacingOccurrences(of: "http://", with: "").prefix(60)))
    }

    /// Downloads cannot continue once iOS suspends the app, so say so instead of leaving the queue silently stalled.
    static func paused(waiting: Int) {
        post(id: "paused", title: S.notifPausedTitle, body: S.notifPausedBody(waiting), subtitle: nil)
    }

    private static func post(id: String, title: String, body: String, subtitle: String?) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        if let subtitle { content.subtitle = subtitle }
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
    }
}
