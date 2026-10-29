<div align="center">

<img src="docs/brand/logo-lockup.png" alt="Gallery DL Android" width="560">

**A native Android (and iPhone) front end and download manager for [gallery-dl](https://github.com/mikf/gallery-dl).**

gallery-dl runs *inside the app* (embedded Python), so there is no server and no backend.

![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Android TV](https://img.shields.io/badge/Android%20TV-supported-4285F4?logo=googletv&logoColor=white)
![iOS 15+](https://img.shields.io/badge/iOS-15%2B-000000?logo=apple&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![gallery-dl](https://img.shields.io/badge/gallery--dl-1.32.18-blue)
![Theme](https://img.shields.io/badge/theme-dark-0A0F1F)

</div>

<br>

<div align="center">
<table>
  <tr>
    <td><img src="docs/screenshots/home.png" width="180" alt="Home"></td>
    <td><img src="docs/screenshots/queue.png" width="180" alt="Queue"></td>
    <td><img src="docs/screenshots/library.png" width="180" alt="Library"></td>
    <td><img src="docs/screenshots/activity.png" width="180" alt="Activity"></td>
    <td><img src="docs/screenshots/settings.png" width="180" alt="Settings"></td>
  </tr>
  <tr align="center">
    <td><b>Home</b></td>
    <td><b>Queue</b></td>
    <td><b>Library</b></td>
    <td><b>Activity</b></td>
    <td><b>Settings</b></td>
  </tr>
</table>
</div>

## Features

### Downloading

- **One link at a time.** Paste a link (or share one from a browser); it is checked against gallery-dl's supported
  sites and added. Nothing downloads until you press **Start** (or **Start all**), and one link downloads at a time.
- **Stop all.** A red **Stop all** button on Home and Queue appears while anything is downloading or waiting. One tap cancels the
  running download and every waiting link. Each link keeps its record of what was saved, so a retry only fetches the rest.
- **Live progress.** Overall percent when the site reports an album size, otherwise per-file percent, in the app and in
  a notification with a Cancel button.
- **A folder per link.** Saved to shared storage (Pictures / Movies / Music / Downloads under `GalleryDl/`, no storage
  permission needed) or to a folder you pick.
- **Where a file came from.** In the viewer, the ⋮ menu has **Details**: the link you added and that file's own direct address,
  each with a Copy button. In a folder, long-press a file and an **i** button next to Delete shows the same details. Renewing a link updates the address of every file it covers, including files it already had.
  Files downloaded before this was added show "Not saved" until the link is renewed.
- **No second copies.** Files the library already has for a link are never fetched or saved again, even if you removed the link
  and added it again (which deletes its "already downloaded" record). The existing file just gets the link's new download.
- **Home keeps your links.** Home is the collection of links you have downloaded, not a history. "Clear finished" on the Queue only
  tidies the Queue: the links stay on Home (and in Activity), with their memory of what was saved, so Renew only fetches
  what is new. Settings > Clear history is the one that really deletes them.
- **Renew.** The refresh button on Home runs every link again to pick up new files, and starts links that were only
  added. Files you already have are skipped.
- **Check for new files / Retry.** A finished link can be run again from its menu (or in Activity); a retry only
  fetches what is still missing.
- **Export and import links.** The three-dot menu on Home saves every link to a plain text file (one link per line)
  and reads such a file back. Import skips duplicates and unsupported sites and says how many of each. The same file
  works on the iOS app.
- **Low-storage protection.** When the storage is 99% full, downloads pause (the running link goes back to the queue and
  keeps its progress), a red warning shows on Home and Queue, and a notification says so. They carry on by themselves
  once the storage is below 98.5% again.
- **Download controls.** Wi-Fi-only mode, retries, timeouts, request pacing, and cookies.txt import for sites that need
  a login.

### Library

- **Folders named by gallery-dl.** Each file goes in a folder with exactly the name of the innermost folder gallery-dl
  made for it (a thread's own folder, say), without its parents; direct file links all land in gallery-dl's `directlink`
  folder. A file saved earlier under an older folder name moves into the folder gallery-dl names now the next time its link
  is refreshed (Renew, or adding the link again): nothing is downloaded again (a file counts as already saved by its name or by its direct address, so names with a shifting position number still match), and an emptied old folder is removed, its tag
  passing to the new one. Also search of file names (currently hidden), multi-select delete, and **folder tags**
  (long-press a folder, or use the tag button inside it): the tag becomes the folder's title and its own name moves to the line below it.
- **Live previews.** Every video plays a silent, looping preview, and folder covers cycle through clips from random
  videos in their folder. A preview is a random 3 second window of the video, saved once at the video's own frame rate
  (up to 30 fps) as a short strip of small JPEG frames, so playing it needs no video decoder and about 1-2 MB of
  memory per tile; every visible tile can animate at once. Clips are made right after a download, and a background job
  (it carries on after you leave the Library, shows a quiet notification, waits while the battery is low) makes the
  clips for every video that lacks one, with a "Preparing previews" bar. Thumbnails, previews and website icons are
  kept on the device until the video is deleted or you clear them.
- **Columns.** 2, 3 or 4 columns inside a folder.
- **Folder for a link.** Pressing Start on a waiting link asks which library folder its files go in. The name gallery-dl would use is looked up and filled in; keep it, or type another and that link always saves there, also on every refresh. A name another link already uses gets a warning (it can still be chosen). Start all and Renew do not ask. On Home, each link shows its folder's name as the main text with the link underneath.
- **Files saved.** The count on a link is what the library holds for it, recalculated whenever the link is refreshed.
- **Favorites.** Long-press a file in a folder, then tap the star to copy it into a Favorites folder (pinned first in the Library). The copy is kept inside the app (on Android in the app's private storage, not in your Pictures or Movies), keeps the link and the file's own address (see Details), and stays when the link or its folder is deleted. It goes only when you delete it from Favorites.
- **Folder order.** The folder list can show the newest folder first (the default) or the oldest first, from the grid button in the Library's top bar. The choice is remembered. Inside a folder the files have the same choice, and the full-screen viewer follows it.
- **Built-in video player.** Videos and audio play inside the app (Media3 ExoPlayer): swipe sideways to scrub with a
  live picture and a "+0:12" pill, a seek bar that previews as you drag, playback speed 0.5x to 2x (remembered), pinch or
  double-tap to zoom and drag to pan, tap to hide the controls, a loop button (repeat the video), a lock button (ignores every touch until you unlock it), back and forward 10 seconds, and a rotate-to-landscape button on phones. Formats the phone cannot play fall back to opening
  another app.
- **Entries disappear** when their files are deleted outside the app.

### Android TV

- A separate TV build appears on the TV home screen with a banner and does not need a touchscreen; use a mouse or a
  touch-capable remote.
- Wide screens (TV, tablets, landscape) show the folder list on the left (one column, 20%) and the open folder on the
  right (80%).

### Other

- **Home** groups links by website, with each site's own icon (cached on the device).
- **Activity** shows per-link results (completed / partial / failed), file counts and error details.
- **Settings** cover pacing, website options, download location, connection, accounts (cookies), startup sound, live
  previews, storage and reset.
- **Dark theme only.**

## Android: tech stack and layout

| Area | Choice |
| --- | --- |
| UI | Kotlin, Jetpack Compose, Material 3 |
| State | ViewModel + coroutines / Flow |
| Storage | Room, DataStore |
| Background work | WorkManager (download queue and preview job, both as foreground services) |
| Images | Coil 3 |
| Video and audio | Media3 ExoPlayer |
| Downloader | Chaquopy running gallery-dl 1.32.18 (Python 3.12; 3.11 in the TV build) |
| DI | Manual (`AppContainer`) |

```
app/src/main/
  python/gdl_bridge.py     gallery-dl wrapper: progress, failure counts, skipping files the library already has
  java/app/gallerydl/
    data/                  Room entities, DAOs, migrations, repositories, settings, thumbnail and preview caches
    download/              Python bridge, download and preview workers, notifications, MediaStore/SAF saving
    share/                 the popup for links shared from a browser
    ui/                    screens, ViewModels, shared components (video player, live previews)
app/src/tv/                TV-only manifest entries and launcher banner
ios/                       the iPhone app (see "iPhone (iOS)" below)
```

Room schemas are exported to `app/schemas/` and are committed; keep them when changing the database.

## Android: building

### Requirements

- JDK **17** (the Android Gradle Plugin used here does not support newer JDKs)
- Android SDK with platform 35 and build-tools 35 (`ANDROID_HOME` set, or `sdk.dir` in `local.properties`)
- Python 3 on the build machine (Chaquopy uses it to install gallery-dl); the TV build also needs **`python3.11`**
- Android 10 (API 29) or newer to run

### Builds

There are two product flavors, one APK each:

| Flavor | For | ABIs | Python |
| --- | --- | --- | --- |
| `phone` | phones and tablets | arm64-v8a, x86_64 | 3.12 |
| `tv` | Android TV, projectors, TV boxes | armeabi-v7a, arm64-v8a, x86_64 | 3.11 |

The TV build adds 32-bit ARM because many TVs and projectors run 32-bit software, and a 64-bit-only app is rejected
there as "not compatible". Chaquopy has no 32-bit Python 3.12, which is why that build uses 3.11.

```sh
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS

./gradlew :app:assemblePhoneDebug     # app/build/outputs/apk/phone/debug/app-phone-debug.apk
./gradlew :app:installPhoneDebug      # build and install on a connected device/emulator
./gradlew :app:assembleTvDebug        # app/build/outputs/apk/tv/debug/app-tv-debug.apk
```

### Signed release builds

1. Create a keystore and copy `keystore.properties.example` to `keystore.properties` (git-ignored).
2. `./gradlew :app:assemblePhoneRelease` -> `app/build/outputs/apk/phone/release/app-phone-release.apk`
3. `./gradlew :app:assembleTvRelease` -> `app/build/outputs/apk/tv/release/app-tv-release.apk`

> [!IMPORTANT]
> Back up the keystore: updates to an installed app must be signed with the same key.
> Without `keystore.properties` the release build is produced unsigned.

Debug and release builds use different signing keys, so switching an installed copy requires
`adb uninstall app.gallerydl` first (this clears app data).

### Release builds use R8

`app/proguard-rules.pro` keeps `com.chaquo.python.**` and the `DownloadListener` implementations. Chaquopy's native
code finds its Java classes by name, so without these rules `Python.start()` crashes with a native `SIGSEGV` in
release builds only. After changing shrinker settings or upgrading Chaquopy, AGP or Media3, install the **release** APK
and run a real download and play a video before shipping.

## iPhone (iOS)

A SwiftUI port of the same app lives in [`ios/`](ios/). It has the same screens, copy, colours and behaviour, embeds
Python 3.13 ([BeeWare Python-Apple-support](https://github.com/beeware/Python-Apple-support)) and runs gallery-dl
in-process. It reuses `app/src/main/python/gdl_bridge.py` unchanged; `ios/python/gdl_ios.py` is the thin layer between
that file and Swift. iOS 15 or newer.

### Set up and run

```sh
brew install xcodegen
ios/scripts/setup.sh        # downloads Python.xcframework, installs gallery-dl, builds the WebM decoder, generates GalleryDL.xcodeproj
open ios/GalleryDL.xcodeproj
```

Run on the simulator straight away. For a real iPhone, pick your free Apple ID team under Signing & Capabilities (or
set `DEVELOPMENT_TEAM` in `ios/project.yml`). A free-account install expires after 7 days; reinstall from Xcode, or
refresh with AltStore / SideStore.

### Installing without a paid Apple account

Build an unsigned `.ipa` with `ios/scripts/make_ipa.sh` (it lands in `ios/dist/GalleryDL.ipa`), then install it with
[AltServer](https://altstore.io) on a Mac: hold Option and click the AltServer menu bar icon, choose
**Sideload .ipa...**, pick the iPhone and the file, and sign in with your Apple ID. A free Apple ID works; the app has to
be refreshed every 7 days (AltStore on the phone does that over Wi-Fi).

### What is ported

Home (links grouped by website, with each site's icon), Queue, Library (folders, tags, 2 to 4 columns, multi-select
delete, swipeable full-screen viewer with in-app video and audio playback), Activity (stats, filters, retry, Check for
new files) and Settings (every option, cookies import, storage, reset). The queue works the same way: nothing starts
until you press Start, one link downloads at a time, a retry only fetches what is missing. Renew, export and import of
links, and the folder tags work as described above.

- **Export** opens the share sheet (save the file to Files); **import** opens the file picker and reports how many
  links were added, were duplicates, were on unsupported sites, or were not links.
- **Website icons** are fetched once and kept in Application Support (a site with no icon is asked again after a week).
- The library search box is hidden for now, as on Android.

The Android Room database is replaced by a JSON store (`Core/AppStore.swift`), DataStore by UserDefaults, and
WorkManager by `Core/DownloadEngine.swift`.

### Layout

| Path | Purpose |
| --- | --- |
| `ios/GalleryDL/Core/` | Models, store, settings, download engine, notifications |
| `ios/GalleryDL/UI/` | Screens (`Screens/`) and shared controls (`Components/`) |
| `ios/GalleryDL/Theme/` | Material 3 dark scheme, type scale, spacing, tones (copied from Android) |
| `ios/GalleryDL/PythonShim.m`, `PythonRuntime.swift` | Embedded Python |
| `ios/python/gdl_ios.py` | JSON entry points: `check`, `download`, `poll`, `cancel` |
| `ios/scripts/embed_python.sh` | Build phase: copies stdlib and packages into the bundle, turns `.so` files into frameworks |
| `ios/GalleryDL/Core/WebM/` | WebM playback: file reader, libvpx decoder, Opus sound, player (see "WebM on iOS") |
| `ios/scripts/build_webm.sh` | Builds libvpx for device and simulator into `ios/Vendor/Vpx.xcframework` (run by `setup.sh`) |
| `ios/scripts/make_ipa.sh` | Builds an unsigned `.ipa` for AltStore / AltServer |
| `ios/requirements.txt` | Pure-Python packages only (iOS cannot load macOS binaries) |

Files are kept in the app's private storage (Application Support) and shown in the Library; they do not appear in the
Files app. Debug builds accept launch arguments for simulator checks (`-testURLs`, `-testTab`, `-testSettings`,
`-testFolder`, `-testViewer`, `-noPermissionPrompt`).

### Live previews on iOS

When a video is downloaded (and the first time an older one is shown) `Core/PreviewStripStore.swift` reads a random
3 second window of it, front to back with `AVAssetReader`, and saves its frames as small JPEGs in one `.strip` file. A
strip keeps the video's own frame rate, up to 30 (a 60 fps video is saved at 30, a 24 fps one at 24), so it is
typically 0.5-1 MB. `UI/Components/LivePreview.swift` plays them: one shared display link steps every animating tile,
each tile decodes just the frame that belongs to the elapsed time off the main thread, and the frame is put straight on
a `CALayer`, so SwiftUI redraws nothing at 30 frames a second. A tile needs no video decoder and only a megabyte or two,
and every visible tile animates at once. A folder cover cycles through the strips of up to 6 random videos from its
folder, cross-fading over 0.4 s. Until a strip exists the tile shows its still thumbnail and the strip is requested.
While the library is open, a sweep also makes the strip of every video that is still missing one, one at a time and
behind anything on screen, with a "Preparing previews, 12 of 40" bar; leaving the library pauses the sweep work that has
not started (iOS does not allow the long background job that Android uses). Previews stop while the app is in the
background, the viewer is open, Low Power Mode or Reduce Motion is on, or the system warns about memory.

`Core/ThumbnailStore.swift` keeps the still thumbnails. Both live in Application Support, which iOS never purges, out of
backups, and are removed only when their file or folder is deleted, or from Settings > Storage and reset > Clear
thumbnail cache.

### Video player on iOS

`UI/Components/VideoViewer.swift` is the viewer's own player (the stock SwiftUI player has no speed menu and would scale
its controls when zoomed). Pinch to zoom 1x to 5x, drag to pan while zoomed, double-tap to zoom in and out. A swipe
sideways scrubs through the video (the whole screen width covers up to 90 seconds, or the whole video if shorter), so
paging between files is off on a video page. Dragging the scrubber shows the picture at that moment live, with the same
time bubble as the swipe. The left-edge back swipe is off on video and audio pages (moving a playing video across the
screen made it lag); use the back button or swipe down. Speed menu 0.5x to 2x, play and pause. Tap to hide or show the
controls. The Android player behaves the same way.

### WebM on iOS

iOS cannot play WebM (the system player has no VP8 or VP9 decoder), so the app decodes it itself, and the original file
is never converted or changed. `Core/WebM/WebMFile.swift` reads the Matroska structure straight from the mapped file and
indexes every frame. `WebMDecoder.swift` decodes VP8 and VP9 with libvpx (BSD licence, built by `scripts/build_webm.sh`
from a pinned release into `ios/Vendor/`, which is not committed) and hands out pictures. `WebMPlayer.swift` shows them
on an `AVSampleBufferDisplayLayer` driven by a render synchronizer, so play, pause, speed and seeking use the system clock.
The same decoder makes the library thumbnail and the live preview strip, so a `.webm` tile looks like any other.
Opus sound is decoded with the decoder built into iOS (`WebMAudio.swift`). A file whose first picture comes late (or that starts on a non-key frame, as clips cut from a longer video can) shows its first
usable picture from the start instead of an empty screen; without sound the timeline begins at that picture. Limits: Vorbis sound (older WebM files) is not
decoded, so those videos play without sound; only mono and stereo Opus; 8-bit 4:2:0 video only (no VP9 10-bit or 4:4:4);
decoding is in software, so 4K video may stutter on older phones.

### Differences from Android

- **Background downloads.** iOS has no foreground service. Downloads run while the app is open and for a short while
  after leaving it; then the running link goes back to the queue and a notification says so. Keep the app open for long
  downloads.
- **Download location.** Always inside the app; there is no folder picker.
- **Share from the browser** (Android's share popup) needs an iOS share extension, which is not built.
- **Notifications** announce finished links only; iOS cannot update a live progress notification in the background.
- **Preview catch-up** runs only while the Library is open.

## Known limitations

- Private or login-gated content needs a cookies.txt exported from your browser (Settings > Accounts); there is no
  in-app login.
- No overall percentage for links that expand into many other links (profiles, threads); those show per-file progress.
- Images in the viewer cannot be zoomed yet (videos can).
- The library search box is hidden for now.
- On a video page, swiping scrubs instead of moving to the next file; go back to the grid to open another one.
- The TV build is made for a mouse or touch input; it has no remote (D-pad) focus navigation.
- Preview clips are made on the device after a download; on a very slow phone the first visit to a large library takes a
  while (it keeps going in the background).

## Credits

- [gallery-dl](https://github.com/mikf/gallery-dl) by Mike Fährmann does the actual downloading.
- [Inter](https://rsms.me/inter/) font, licensed under the SIL OFL (see [docs/licenses](docs/licenses/Inter-OFL.txt)).
- Screenshot images are abstract art generated with a small script, so there is nothing to attribute.
