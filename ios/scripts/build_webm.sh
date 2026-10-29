#!/bin/bash
# Builds the WebM video decoder (libvpx, BSD licence) for iOS: one static library for real iPhones and one
# for the Apple-silicon simulator, packaged as ios/Vendor/Vpx.xcframework.
# Source is cloned from Google's official repository at a pinned release tag into a scratch folder.
# Run once from anywhere:  bash ios/scripts/build_webm.sh
set -euo pipefail

VPX_TAG="v1.15.0"
MIN_IOS="15.0"
HERE="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WEBM_WORK:-$(mktemp -d /tmp/gdl-webm.XXXXXX)}"
OUT="$HERE/Vendor"

echo "Working in $WORK"
git clone --depth 1 --branch "$VPX_TAG" https://chromium.googlesource.com/webm/libvpx "$WORK/libvpx"

# libvpx's configure hard-codes an old iOS minimum version, so the compiler is called through a wrapper
# that drops those flags and passes the right -target and SDK instead.
wrapper() { # file compiler target sdk
  cat > "$1" <<EOF
#!/bin/bash
args=(); skip=0
for a in "\$@"; do
  if [ \$skip = 1 ]; then skip=0; continue; fi
  case "\$a" in
    -miphoneos-version-min=*|-mios-simulator-version-min=*|-fembed-bitcode) ;;
    -isysroot|-arch) skip=1 ;;
    *) args+=("\$a") ;;
  esac
done
exec $2 -target $3 -isysroot $4 "\${args[@]}"
EOF
  chmod +x "$1"
}

build() { # name sdk target
  local name="$1" sdk target="$3" sysroot
  sysroot="$(xcrun --sdk "$2" --show-sdk-path)"
  wrapper "$WORK/cc-$name" clang "$target" "$sysroot"
  wrapper "$WORK/cxx-$name" clang++ "$target" "$sysroot"
  mkdir -p "$WORK/build-$name"
  (
    cd "$WORK/build-$name"
    CC="$WORK/cc-$name" LD="$WORK/cc-$name" AS="$WORK/cc-$name" CXX="$WORK/cxx-$name" \
    ../libvpx/configure --target=arm64-darwin-gcc \
      --disable-examples --disable-tools --disable-docs --disable-unit-tests \
      --disable-vp8-encoder --disable-vp9-encoder --enable-vp8-decoder --enable-vp9-decoder \
      --disable-install-bins --disable-install-docs --enable-pic --disable-webm-io --disable-libyuv \
      --disable-runtime-cpu-detect
    # configure ignores CC for Darwin targets, so the wrapper is also handed to make directly.
    make clean >/dev/null 2>&1 || true
    make -j"$(sysctl -n hw.ncpu)" libvpx.a \
      CC="$WORK/cc-$name" CXX="$WORK/cxx-$name" AS="$WORK/cc-$name" LD="$WORK/cc-$name"
  )
  # Fail early if the objects were not built for the platform they are meant for.
  local obj platform
  obj="$(mktemp -d "$WORK/check.XXXXXX")"
  (cd "$obj" && ar x "$WORK/build-$name/libvpx.a" vp9_dx_iface.c.o)
  platform="$(xcrun vtool -show-build "$obj/vp9_dx_iface.c.o" | awk '/platform/ {print $2}')"
  echo "$name library platform: $platform"
  case "$name:$platform" in
    device:IOS | sim:IOSSIMULATOR) ;;
    *) echo "Wrong platform for $name build: $platform" >&2; exit 1 ;;
  esac
}

build device iphoneos "arm64-apple-ios$MIN_IOS"
build sim iphonesimulator "arm64-apple-ios$MIN_IOS-simulator"

mkdir -p "$WORK/headers/vpx"
cp "$WORK"/libvpx/vpx/vpx_*.h "$WORK"/libvpx/vpx/vp8*.h "$WORK/headers/vpx/"
# A few of the headers are generated into the build folder.
cp "$WORK"/build-device/vpx_config.h "$WORK/headers/vpx/" 2>/dev/null || true

mkdir -p "$OUT"
rm -rf "$OUT/Vpx.xcframework"
xcodebuild -create-xcframework \
  -library "$WORK/build-device/libvpx.a" -headers "$WORK/headers" \
  -library "$WORK/build-sim/libvpx.a" -headers "$WORK/headers" \
  -output "$OUT/Vpx.xcframework"

echo "Done: $OUT/Vpx.xcframework"
echo "Scratch folder (safe to delete): $WORK"
