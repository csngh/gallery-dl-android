#!/bin/bash
# One-time setup: fetch the Python runtime for iOS and the gallery-dl packages, then generate the Xcode project.
set -euo pipefail
cd "$(dirname "$0")/.."

PY_TAG="3.13-b17"
PY_ARCHIVE="Python-3.13-iOS-support.b17.tar.gz"

if [ ! -d Python.xcframework ]; then
    echo "Downloading Python $PY_TAG for iOS..."
    curl -fL -o "/tmp/$PY_ARCHIVE" "https://github.com/beeware/Python-Apple-support/releases/download/$PY_TAG/$PY_ARCHIVE"
    tar xzf "/tmp/$PY_ARCHIVE" Python.xcframework
    rm -f "/tmp/$PY_ARCHIVE"
fi

echo "Installing gallery-dl and its dependencies..."
rm -rf app_packages .venv-setup
python3 -m venv .venv-setup
.venv-setup/bin/pip install -q --upgrade pip
.venv-setup/bin/pip install -q --target app_packages --no-deps \
    --implementation py --abi none --platform any --only-binary=:all: -r requirements.txt
rm -rf .venv-setup
if find app_packages -name '*.so' | grep -q .; then
    echo "error: a package brought a compiled extension, which will not load on iOS" >&2
    exit 1
fi

if [ ! -d Vendor/Vpx.xcframework ]; then
    echo "Building the WebM video decoder (libvpx)..."
    bash scripts/build_webm.sh
fi

command -v xcodegen >/dev/null || { echo "Install XcodeGen first: brew install xcodegen" >&2; exit 1; }
xcodegen generate
echo "Done. Open GalleryDL.xcodeproj."
