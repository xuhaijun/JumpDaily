#!/usr/bin/env bash
# ============================================================
# JumpDaily one-click build script (bash version, 2026-09-07)
#
# Usage:
#   ./build_apk.sh                          -> all channels, release
#   ./build_apk.sh huawei                   -> huawei channel, release
#   ./build_apk.sh xiaomi debug             -> xiaomi channel, debug
#   ./build_apk.sh official release install -> build + adb install
#
# Output archived to: dist/<today>/  (folder named by date)
# APK name: JumpDaily_v1.0.0_huawei_release_20260907.apk
#
# Works in Git Bash on Windows, Linux, and macOS.
# ============================================================

set -euo pipefail

# ===== Toolchain paths (edit here when switching dev machines) =====
# Windows Git Bash: use /c/... or /d/... style paths (or keep native if already set)
JAVA_HOME="${JAVA_HOME:-C:\Program Files\Java\jdk-21.0.10}"
ANDROID_HOME="${ANDROID_HOME:-D:\dev\Android\Sdk}"
export JAVA_HOME ANDROID_HOME

# ===== Args =====
FLAVOR="${1:-all}"
BUILDTYPE="${2:-release}"
DO_INSTALL="${3:-}"

case "$(echo "$FLAVOR" | tr '[:upper:]' '[:lower:]')" in
    all) FLAVOR=all ;;
    official) FLAVOR=official ;;
    huawei) FLAVOR=huawei ;;
    xiaomi) FLAVOR=xiaomi ;;
    *)
        echo "[ERROR] invalid channel: $FLAVOR"
        echo "Usage: build_apk.sh [all|official|huawei|xiaomi] [release|debug] [install]"
        exit 1
        ;;
esac

case "$(echo "$BUILDTYPE" | tr '[:upper:]' '[:lower:]')" in
    release) BUILDTYPE=release ;;
    debug) BUILDTYPE=debug ;;
    *)
        echo "[ERROR] invalid build type: $BUILDTYPE - release/debug only"
        exit 1
        ;;
esac

# Gradle task needs capitalized channel/type: assembleHuaweiRelease
case "$FLAVOR" in
    official) FLAVOR_CAP=Official ;;
    huawei) FLAVOR_CAP=Huawei ;;
    xiaomi) FLAVOR_CAP=Xiaomi ;;
    all) FLAVOR_CAP="" ;;
esac
TYPE_CAP="$(echo "$BUILDTYPE" | sed 's/^\(.\)/\U\1/')"

# ===== Project root (script lives in tools/, root is one level up) =====
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ===== Windows path note =====
# On Windows (Git Bash) we call gradlew.bat, which is a batch script: it needs
# Windows-style JAVA_HOME/ANDROID_HOME (C:\...), so do NOT cygpath-convert them.
# On Linux/macOS, export your real JAVA_HOME/ANDROID_HOME before running.

echo
echo "=================================================="
echo "  JumpDaily build    channel=$FLAVOR    type=$BUILDTYPE"
echo "=================================================="
echo

# ===== Pick gradle wrapper: gradlew.bat on Windows (Git Bash), ./gradlew elsewhere =====
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) GRADLE="./gradlew.bat" ;;
    *)                    GRADLE="./gradlew" ;;
esac

# ===== Build =====
if [ "$FLAVOR" = "all" ]; then
    "$GRADLE" "assemble${BUILDTYPE^}"
else
    "$GRADLE" "assemble${FLAVOR_CAP}${TYPE_CAP}"
fi

# ===== Archive: copy APKs to dist/<today>/ =====
TODAY="$(date +%Y%m%d)"
DIST="dist/$TODAY"
mkdir -p "$DIST"

APKDIR="app/build/outputs/apk"
if [ "$FLAVOR" = "all" ]; then
    for ch in official huawei xiaomi; do
        find "$APKDIR/$ch/$BUILDTYPE" -name '*.apk' -maxdepth 1 2>/dev/null | while read -r apk; do
            cp -f "$apk" "$DIST/"
        done
    done
else
    find "$APKDIR/$FLAVOR/$BUILDTYPE" -name '*.apk' -maxdepth 1 2>/dev/null | while read -r apk; do
        cp -f "$apk" "$DIST/"
    done
fi

# ===== Optional: install to connected device after build =====
# For "all" only the official APK is installed (same applicationId, one is enough)
if [ "$DO_INSTALL" = "install" ]; then
    echo
    echo "===== Installing to device ====="
    if [ "$FLAVOR" = "all" ]; then
        INSTALL_APK="$APKDIR/official/$BUILDTYPE"
    else
        INSTALL_APK="$APKDIR/$FLAVOR/$BUILDTYPE"
    fi
    for apk in "$INSTALL_APK"/*.apk; do
        [ -e "$apk" ] || continue
        adb install -r "$apk"
    done
fi

# ===== Result list =====
echo
echo "===== DONE! Artifacts in $DIST ====="
for apk in "$DIST"/*.apk; do
    [ -e "$apk" ] || continue
    MB=$(( $(stat -c%s "$apk" 2>/dev/null || stat -f%z "$apk") / 1048576 ))
    echo "  $(basename "$apk")   ($MB MB)"
done
echo
