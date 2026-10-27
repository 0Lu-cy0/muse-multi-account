#!/bin/bash
# Manual host build — used because the Gradle daemon client socket fails in
# this VM (see BUILD_LOG.md). No AndroidX/Kotlin: framework-only Java, so
# aapt2 + javac + d8 + zipalign + apksigner suffice. Does NOT touch the
# Muse APK and does NOT bundle it.
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK=~/workspace/tools/android-sdk
JDK=~/workspace/tools/jdk-17.0.21+9
export PATH="$JDK/bin:$PATH"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
WORK="$ROOT/manual-build/work"
rm -rf "$WORK"; mkdir -p "$WORK/compiled_res" "$WORK/classes" "$WORK/gen"
# 1. Compile resources (layout XML is shared with the Gradle project)
"$BT/aapt2" compile --dir "$ROOT/app/src/main/res" -o "$WORK/compiled_res.zip"
# 2. Link -> R.java + unsigned APK skeleton
"$BT/aapt2" link -o "$WORK/unsigned.apk" -I "$PLATFORM" \
  --manifest "$ROOT/manual-build/AndroidManifest.xml" \
  --java "$WORK/gen" "$WORK/compiled_res.zip" --auto-add-overlay
# 3. Compile R first, then app Java against android.jar + R classes
find "$ROOT/java-src" -name "*.java" > "$WORK/sources.txt"
"$JDK/bin/javac" -encoding UTF-8 -source 17 -target 17 -cp "$PLATFORM" -d "$WORK/classes" $(find "$WORK/gen" -name "*.java")
"$JDK/bin/javac" -encoding UTF-8 -source 17 -target 17 -cp "$PLATFORM:$WORK/classes" -d "$WORK/classes" @"$WORK/sources.txt"
# 4. DEX (via jar: d8 rejects the raw classes dir in this build-tools version)
cd "$WORK/classes" && "$JDK/bin/jar" cf "$WORK/app.jar" . && cd "$WORK"
mkdir -p "$WORK/dexout"
"$BT/d8" --lib "$PLATFORM" --output "$WORK/dexout" "$WORK/app.jar" 2>&1 | tail -5
# 5. Add dex to APK, align, sign with a throwaway debug key (generated here, never the Muse key)
cd "$WORK" && zip -j -q unsigned.apk dexout/classes.dex
"$BT/zipalign" -f 4 unsigned.apk "$ROOT/manual-build/muse-container-host-debug.apk"
KEYSTORE="$WORK/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
  "$JDK/bin/keytool" -genkeypair -v -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/muse-container-host-debug.apk" "$ROOT/manual-build/muse-container-host-debug.apk"
"$BT/apksigner" verify "$ROOT/muse-container-host-debug.apk" && echo "VERIFY_OK $ROOT/muse-container-host-debug.apk"
ls -lh "$ROOT/muse-container-host-debug.apk"
