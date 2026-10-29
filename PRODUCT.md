# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

One person: the owner, a power user who archives images and videos from websites that gallery-dl supports. They use it on an Android phone, an Android TV or projector (with a mouse, not a remote), and an iPhone. They value speed and directness over onboarding. Confirmed as a personal tool; there is no public release or store listing planned.

## Product Purpose

Gallery DL is a native download manager and media library for gallery-dl. Paste a link, it is checked against gallery-dl's supported sites and queued, downloads one at a time, and each link becomes a folder in a library with live video previews and a built-in player. Success is that a link goes in and a browsable, playable folder comes out with no setup, no server and no account.

## Positioning

gallery-dl runs inside the app (embedded Python), so there is no server, no backend and no account, and the same product exists on Android, Android TV and iPhone. A web front end or a command-line tool cannot offer an offline-first library with silent live previews of every video on the device itself.

## Operating Context

- Links are added one at a time, imported from a text file, or shared from a browser. Nothing downloads until Start is pressed.
- Downloads run in the background as a foreground service on Android and while the app is open on iOS.
- Files are saved to shared storage (Pictures, Movies, Music, Downloads under `GalleryDl/`) or a folder the user picks; on iOS they stay inside the app.
- The TV build is used with a mouse; touch and mouse are the only inputs it must serve.

## Capabilities and Constraints

- Screens: Home (links grouped by website, Renew, export/import), Queue, Library (folders, tags, 2 to 4 columns, live previews, full-screen viewer, video and audio player), Activity (results, retry, check for new files), Settings.
- Dark theme only.
- The iPhone and TV apps must look and behave like the Android app; Android is the design source.
- No remote (D-pad) navigation on TV: mouse or touch only.
- No accounts, no server, nothing sent off the device other than the downloads themselves.
- Terminology in use: link, folder, queue, Renew, Check for new files, Start / Start all.
- Undecided: whether the library search box (hidden for now) comes back; whether images in the viewer get zoom.

## Brand Commitments

The name is "Gallery DL Android". A logo lockup and master mark exist in `docs/brand/` (SVG and PNG, with the generator scripts). The app uses the Inter font and a Material 3 dark scheme. These are existing assets, not fixed visual direction for new work.

## Evidence on Hand

- The logo files in `docs/brand/` and the app icon and splash assets.
- README screenshots in `docs/screenshots/` (nine from Android, four from iPhone). They show generated abstract art and placeholder `.example` links, not real downloaded content; do not present them as real user content.
- No testimonials, users, benchmarks or download counts exist. Do not invent any.

## Product Principles

1. Zero setup: a pasted link is enough, with no account or server.
2. Nothing happens until the user says so: links wait for Start, and one link downloads at a time.
3. The library should feel alive but stay light: previews are small, cached on the device and kept until the user clears them.
4. One product on every screen: the iPhone and TV apps follow the Android design rather than their own platform's conventions.
5. Be honest about state: progress, pauses (Wi-Fi, low storage) and failures are always visible and say what the user can do.

## Accessibility & Inclusion

No product-specific requirement has been stated beyond the owner's own use. Not established; do not claim a standard.
