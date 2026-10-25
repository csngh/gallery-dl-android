#!/bin/bash
# Build phase: copy Python's standard library, our Python code and the pip packages into the app bundle,
# and turn every .so into a framework (iOS only loads binaries that live in frameworks).
set -euo pipefail

APP="$CODESIGNING_FOLDER_PATH"
REPO_ROOT="$PROJECT_DIR/.."

# Frameworks created below are signed here; fall back to ad-hoc when Xcode gives no identity.
export EXPANDED_CODE_SIGN_IDENTITY="${EXPANDED_CODE_SIGN_IDENTITY:--}"
[ -n "$EXPANDED_CODE_SIGN_IDENTITY" ] || export EXPANDED_CODE_SIGN_IDENTITY="-"
export EXPANDED_CODE_SIGN_IDENTITY_NAME="${EXPANDED_CODE_SIGN_IDENTITY_NAME:-ad hoc}"

mkdir -p "$APP/app"
rsync -au --delete "$PROJECT_DIR/python/" "$APP/app/"
# One source of truth: the Android app's bridge runs unchanged on iOS.
cp "$REPO_ROOT/app/src/main/python/gdl_bridge.py" "$APP/app/gdl_bridge.py"
rsync -au --delete "$PROJECT_DIR/app_packages/" "$APP/app_packages/"

source "$PROJECT_DIR/Python.xcframework/build/utils.sh"
install_python Python.xcframework app_packages
