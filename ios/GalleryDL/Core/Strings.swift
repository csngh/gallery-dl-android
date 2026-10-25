import Foundation

/// Every piece of text the app shows, copied from the Android app's strings.xml so both read the same.
enum S {
    static let appName = "Gallery DL Android"
    static let navHome = "Home", navQueue = "Queue", navLibrary = "Library", navActivity = "Activity"

    static let exportFileName = "gallery-dl-links.txt"
    static let notifStorageTitle = "Downloads paused: storage almost full"
    static func notifStorageBody(_ percent: Int) -> String { "The storage is \(percent)% full. Free some space and downloads carry on by themselves." }
    static func storageWarning(_ percent: Int) -> String { "Storage is almost full (\(percent)%). Downloads are paused until some space is freed." }
    static let actionRenew = "Renew all links"
    static let actionLinksMenu = "Export or import links", actionExportLinks = "Export links", actionImportLinks = "Import links"
    static let exportEmpty = "There are no links to export yet."
    static let transferFailed = "Unable to use this file. Please try another one."
    static let importTitle = "Importing links", importDoneTitle = "Import finished"
    static let importNone = "No links were found in this file. It should have one link per line."
    static func importProgress(_ done: Int, _ total: Int) -> String { "\(done) of \(total) checked" }
    static func importAdded(_ n: Int) -> String { n == 1 ? "1 link was added. Press Start to download it." : "\(n) links were added. Press Start to download them." }
    static func importDuplicates(_ n: Int) -> String { "Already in your list: \(n)" }
    static func importUnsupported(_ n: Int) -> String { "On unsupported websites: \(n)" }
    static func importNotLinks(_ n: Int) -> String { "Lines that were not links: \(n)" }
    static let actionOk = "OK"

    static let urlLabel = "Paste a link"
    static let actionAdd = "Add"
    static let errorUrlMalformed = "Please enter a valid http or https link."
    static let errorUrlUnsupported = "This website is not supported."

    static let statusQueued = "Queued", statusDownloading = "Downloading", statusCompleted = "Completed"
    static let statusFailed = "Failed", statusCancelled = "Cancelled", statusPartial = "Completed with errors"
    static let statusReady = "Ready to start"
    static func filesSaved(_ n: Int) -> String { n == 1 ? "\(n) file saved" : "\(n) files saved" }
    static func filesFailed(_ n: Int) -> String { n == 1 ? "\(n) file failed" : "\(n) files failed" }
    static let partialErrors = "Some files could not be downloaded."
    static let errorNoFiles = "Unable to download any files from this link."

    static let actionCancel = "Cancel", actionRetry = "Retry", actionRemove = "Remove"
    static let actionCheckNew = "Check for new files"
    static let actionClearFinished = "Clear finished"

    static let queueEmptyTitle = "The queue is empty"
    static let queueEmptyBody = "Please paste a link above to start downloading."

    static let noAppToOpen = "Unable to find an app that can open this file."

    static let downloadLocation = "Saved inside the app"
    static let downloadLocationHint = "Each link is saved in its own folder, and appears in your library."
    static let actionClose = "Close"

    static let libraryEmptyTitle = "Your library is empty"
    static let libraryEmptyBody = "Finished downloads appear here, with one folder for each link."
    static let libraryNoResults = "No results found"
    static let libraryNoResultsBody = "Please try a different search term."
    static let searchLabel = "Search"
    static let actionBack = "Back", actionOpen = "Open", actionDelete = "Delete"
    static func folderItems(_ n: Int) -> String { n == 1 ? "\(n) item" : "\(n) items" }
    static func selectedCount(_ n: Int) -> String { "\(n) selected" }
    static let actionTagFolder = "Tag folder", actionDeleteFolder = "Delete folder", actionRemoveTag = "Remove tag"
    static let tagFolderTitle = "Tag this folder", tagLabel = "Tag"
    static let actionColumns = "Columns"
    static func columnsOption(_ n: Int) -> String { "\(n) columns" }
    static let deleteFolderTitle = "Delete this folder?"
    static func deleteFolderMessage(_ n: Int) -> String {
        n == 1 ? "This will permanently delete \(n) file saved from this link from your phone."
            : "This will permanently delete all \(n) files saved from this link from your phone."
    }
    static let deleteSelectedTitle = "Delete the selected items?"
    static func deleteSelectedMessage(_ n: Int) -> String {
        n == 1 ? "This will permanently delete \(n) file from your phone." : "This will permanently delete \(n) files from your phone."
    }

    static let statLinks = "Links", statCompleted = "Completed", statPartial = "Partial", statFailed = "Failed"
    static let statFilesSaved = "Files saved", statFilesFailed = "Files failed"
    static let filterAll = "All"
    static func actionRetryFailed(_ n: Int) -> String { "Retry failed (\(n))" }
    static let activityEmptyTitle = "No activity yet"
    static let activityEmptyBody = "Results for every link that you download will appear here."

    static let actionExpand = "Expand", actionCollapse = "Collapse"
    static let detailFolder = "Folder", detailAdded = "Added", detailFinished = "Finished"

    static func progressPercent(_ n: Int) -> String { "\(n)%" }
    static func progressFileOf(_ i: Int, _ total: Int) -> String { "File \(i) of \(total)" }
    static func progressFile(_ i: Int) -> String { "File \(i)" }

    static let confirmQuestion = "Do you want to continue?"
    static let deleteItemTitle = "Delete this file?"
    static let deleteItemMessage = "This will permanently delete this file from your phone."
    static let clearFinishedTitle = "Clear the finished links?"
    static let clearFinishedMessage = "This will remove all finished links from the queue and from the activity history. The downloaded files will remain in your library."
    static let actionClear = "Clear"
    static let removeLinkTitle = "Remove this link?"
    static let removeLinkMessage = "This will remove this link from the queue and from the activity history. The downloaded files will remain in your library."
    static let cancelDownloadTitle = "Cancel this download?"
    static let cancelDownloadMessage = "This will stop the download of this link. Files that have already been saved will remain in your library."
    static let actionCancelDownload = "Cancel download"
    static let actionKeepDownloading = "Keep downloading"

    static let actionMore = "More options"

    static let homeEmptyTitle = "No websites yet"
    static let homeEmptyBody = "Please paste a link above. Links from the same website are grouped here."
    static func siteLinks(_ n: Int) -> String { n == 1 ? "\(n) link" : "\(n) links" }
    static func siteInProgress(_ n: Int) -> String { "\(n) in progress" }
    static func siteAttention(_ n: Int) -> String { n == 1 ? "\(n) needs attention" : "\(n) need attention" }
    static func siteReady(_ n: Int) -> String { "\(n) ready to start" }
    static func siteDownloadingPercent(_ n: Int) -> String { "Downloading, \(n)%" }

    static let actionEdit = "Edit link", editLinkTitle = "Edit link", editLinkLabel = "Link"
    static let actionSaveRetry = "Save and retry", actionSave = "Save"

    static let actionViewLink = "View full link", fullLinkTitle = "Full link", actionCopy = "Copy"

    static let actionStart = "Start"
    static func actionStartAll(_ n: Int) -> String { "Start all (\(n))" }

    static let settingsTitle = "Settings"
    static let settingsPacingTitle = "Pacing"
    static let settingsPacingBody = "Slow downloads down so that websites are less likely to block you."
    static let optionSleepRequestTitle = "Wait between requests"
    static let optionSleepRequestBody = "Pause between requests to a website. Enter seconds as one number or as a range, for example 4.0-8.0."
    static let optionSleepTitle = "Wait between downloads"
    static let optionSleepBody = "Pause before each file is downloaded. Enter seconds as one number or as a range, for example 1.0-3.0."
    static let optionSecondsLabel = "Seconds"
    static let errorDuration = "Please enter seconds from 0 to 300, as one number or a range such as 4.0-8.0."
    static let settingsSitesTitle = "Websites"
    static let settingsSitesBody = "Choices that only some websites offer."
    static let optionApiRestTitle = "Use the REST API"
    static let optionApiRestBody = "Asks websites that offer more than one API to use the REST API. Only some websites, such as Twitter, give this choice."
    static let settingsApplyNote = "Changes apply to the next download that starts."

    static let settingsLocationTitle = "Download location"
    static let settingsLocationBody = "Where finished files are saved on your phone."
    static let settingsConnectionTitle = "Connection"
    static let settingsConnectionBody = "How downloads behave on slow or limited connections."
    static let optionWifiTitle = "Wi-Fi only"
    static let optionWifiBody = "Wait for Wi-Fi and never use mobile data. Downloads in the queue start when Wi-Fi is available."
    static let optionRetriesTitle = "Retries"
    static let optionRetriesBody = "How many times a file is tried again after it fails. The default is 4."
    static let optionRetriesLabel = "Number of retries"
    static let errorRetries = "Please enter a whole number from 0 to 20."
    static let optionTimeoutTitle = "Timeout"
    static let optionTimeoutBody = "How many seconds to wait for a slow server before giving up. The default is 30."
    static let errorTimeout = "Please enter seconds from 1 to 300."
    static let optionSkipTitle = "Skip files already downloaded"
    static let optionSkipBody = "When this is off, every file is downloaded again even if it was saved before, which can create duplicates."
    static let settingsAccountsTitle = "Accounts"
    static let settingsAccountsBody = "Download content that needs you to be signed in."
    static let optionCookiesTitle = "Use cookies"
    static let optionCookiesBody = "Sends your cookies.txt to websites so that you appear signed in. Export the file from a desktop browser and import it here."
    static let cookiesNone = "No cookies.txt has been imported."
    static func cookiesImported(_ n: Int) -> String { n == 1 ? "cookies.txt imported with \(n) cookie." : "cookies.txt imported with \(n) cookies." }
    static let errorCookiesInvalid = "Unable to read this file. Please choose a cookies.txt file in the Netscape format."
    static let actionImportFile = "Import file", actionReplaceFile = "Replace file"
    static let removeCookiesTitle = "Remove the cookies?"
    static let removeCookiesMessage = "This will delete the imported cookies.txt from the app. You will need to import it again to download content that needs a sign-in."
    static let settingsStorageTitle = "Storage and reset"
    static let settingsStorageBody = "Manage what the app keeps on your phone."
    static func storageRecords(_ size: String) -> String { "The app's records use \(size)." }
    static func storageThumbnails(_ size: String) -> String { "The saved thumbnails and preview clips of your library use \(size)." }
    static let actionClearThumbnails = "Clear thumbnail cache"
    static let clearThumbnailsTitle = "Clear the thumbnail cache?"
    static let clearThumbnailsMessage = "This removes the saved thumbnails and preview clips of the pictures and videos in your library. They are made again when needed. Your files are not touched."
    static let actionClearHistory = "Clear finished history", actionResetSettings = "Reset settings"
    static let resetSettingsTitle = "Reset all settings?"
    static let resetSettingsMessage = "This will return every option to its default. Your imported cookies, downloaded files and history will not be changed."
    static let actionReset = "Reset"
    static let settingsAboutTitle = "About", settingsAboutBody = "Versions and licences."
    static let aboutAppVersion = "App version", aboutGalleryDlVersion = "gallery-dl version"
    static let aboutLicences = "Open-source software used: gallery-dl (GPL-2.0), Python (PSF), BeeWare Python-Apple-support (BSD-3-Clause), and the Inter typeface (SIL Open Font License 1.1)."

    static let optionStartupSoundTitle = "Startup sound"
    static let optionStartupSoundBody = "Plays a short sound while the logo appears when the app opens. It stays silent when your phone is on silent or vibrate."
    static let settingsAppearanceTitle = "Startup and previews", settingsAppearanceBody = "What happens when the app opens, and how the library looks."
    static let previewsPreparing = "Preparing previews"
    static func previewsProgress(_ done: Int, _ total: Int) -> String { "\(done) of \(total)" }
    static let optionLivePreviewsTitle = "Live previews"
    static let optionLivePreviewsBody = "Plays a silent, looping preview of each video in the library, from a short clip that is saved once for every video. It pauses when Low Power Mode or Reduce Motion is on."

    static let notifComplete = "Download complete"
    static let notifPartial = "Download complete with errors"
    static let notifFailed = "Download failed"
    static let notifPausedTitle = "Downloads paused"
    static func notifPausedBody(_ n: Int) -> String {
        n == 1 ? "\(n) link is waiting. Open Gallery DL to continue." : "\(n) links are waiting. Open Gallery DL to continue."
    }
    static let errorDestinationUnavailable = "Unable to access the download folder."
}
