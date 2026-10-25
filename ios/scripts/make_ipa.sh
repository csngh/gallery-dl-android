#!/bin/bash
# Build an unsigned Release .ipa for AltStore / SideStore, which sign it with your own Apple ID on install.
set -euo pipefail
cd "$(dirname "$0")/.."

DERIVED="${TMPDIR:-/tmp}/gallerydl-ipa-build"
rm -rf "$DERIVED" dist
xcodebuild -project GalleryDL.xcodeproj -scheme GalleryDL -configuration Release \
    -destination 'generic/platform=iOS' -derivedDataPath "$DERIVED" \
    CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" build | tail -1

mkdir -p dist/Payload
cp -R "$DERIVED/Build/Products/Release-iphoneos/GalleryDL.app" dist/Payload/
(cd dist && zip -qry GalleryDL.ipa Payload && rm -rf Payload)
echo "Wrote ios/dist/GalleryDL.ipa ($(du -h dist/GalleryDL.ipa | cut -f1))"
