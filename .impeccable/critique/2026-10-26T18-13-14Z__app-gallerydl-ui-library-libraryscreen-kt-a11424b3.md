---
target: Library screen
total_score: 29
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 0
target_identity: "file:/Users/sunnypc/Documents/GitHub/gallery-dl-android/app/src/main/java/app/gallerydl/ui/library/LibraryScreen.kt"
target_fingerprint: "sha256:b4fea525f768bf3e78bd4708192dacc8482a57e9ac62060c54d08100c4b880c3"
target_path: /Users/sunnypc/Documents/GitHub/gallery-dl-android/app/src/main/java/app/gallerydl/ui/library/LibraryScreen.kt
timestamp: 2026-10-26T18-13-14Z
slug: app-gallerydl-ui-library-libraryscreen-kt-a11424b3
---
Method: design review only (detector not applicable, native platform)

Target: Library screen (phone build, Pixel emulator; folder grid 2 and 3 columns, open folder, viewer).
Score: 29/40 (Good). Heuristic 6 improved 2 to 3 (folder actions now visible); heuristic 10 still 2.

Priority issues
- [P2] Gestures undiscoverable (scrub, double-tap zoom, tap to hide controls, long-press select). Command: onboard
- [P2] Long folder names never readable in full (card, header both truncate). Command: typeset
- [P2] Viewer title shows source host prefix for direct links. Command: clarify
- [P2] No select-all or sort in a large library. Command: shape
- [P3] 3-column corner disc blends into dark covers. Command: polish
