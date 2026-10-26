---
target: Library screen
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 1
target_identity: "file:/Users/sunnypc/Documents/GitHub/gallery-dl-android/app/src/main/java/app/gallerydl/ui/library/LibraryScreen.kt"
target_fingerprint: "sha256:90c7f322800cdf2ba1382d717d54dbf023a95ec484428e4bd080a9f858b5fbf7"
target_path: /Users/sunnypc/Documents/GitHub/gallery-dl-android/app/src/main/java/app/gallerydl/ui/library/LibraryScreen.kt
timestamp: 2026-10-26T16-48-22Z
slug: app-gallerydl-ui-library-libraryscreen-kt-a11424b3
---
Method: design review only (detector not applicable, native platform)

Target: Library screen (phone build, emulator at phone size; folder grid, open folder, folder dialog, video viewer, 1.3x font).

Score: 28/40 (Good). Heuristics 6 (recognition) and 10 (help) scored 2; the rest 3.

Design specificity: live-preview covers are distinctive; chrome is plain Material 3 dark. Right for an Operate surface.

Priority issues
- [P1] Folder Tag and Delete only reachable by long-press on the cover; no visible hint. Fix: overflow menu on each card, keep long-press. Command: layout
- [P2] Viewer top bar puts Delete beside Open-in-another-app over the video. Fix: move into an overflow menu. Command: quieter
- [P2] Viewer has no fullscreen/rotate button and no +-10 s skip; landscape video is tiny in portrait. Command: layout
- [P2] Folder tiles ~195 dp squares, no column choice on the folder list. Fix: 2 or 3 columns like inside a folder. Command: adapt
- [P3] Folder name wraps mid-word at 1.3x font ("long-weekend-t / rip"). Fix: single line with ellipsis. Command: typeset

Minor: viewer title shows source address in file name for direct links; folder dialog uses body buttons instead of a sheet or menu; icon-only tag and grid buttons; no Dynamic Color (brand choice).
