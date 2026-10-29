---
target: Library screen
total_score: 29
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 0
target_identity: "file:/Users/sunnypc/Documents/GitHub/gallery-dl-android/app/src/main/java/app/gallerydl/ui/library/LibraryScreen.kt"
target_fingerprint: "sha256:d278448ab516558b760a73a1e13dad572a77afd414e95a861a9086819c86ffce"
target_path: /Users/sunnypc/Documents/GitHub/gallery-dl-android/app/src/main/java/app/gallerydl/ui/library/LibraryScreen.kt
timestamp: 2026-10-29T13-48-54Z
slug: app-gallerydl-ui-library-libraryscreen-kt-a11424b3
---
Method: design review only (detector not applicable, native platform). Source review only, no emulator running.

Target: Library screen (LibraryScreen.kt plus the Details dialog, folder naming and Stop all added since the last run).
Score: 29/40 (Good). Heuristics 6 and 10 at 2, 1, 5 at 3, rest 3.

Priority issues
- [P2] Folder tiles now show gallery-dl's raw innermost folder name (e.g. an album id), so many tiles are unreadable. Command: clarify
- [P2] Details "i" appears only at exactly 1 selected, shifting Delete's position. Command: layout
- [P2] Long folder names are still never readable in full (card ellipsis, header 2 lines). Command: typeset
- [P3] Stop all is a second full-width filled red button stacked above Start all on Home and Queue. Command: quieter
- [P3] Gestures still undiscoverable. Command: onboard
