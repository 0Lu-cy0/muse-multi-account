#!/bin/bash
# Manual host v4 build, NO Gradle. Consumes bcore.aar + dep AARs, links merged
# manifest + merged res, generates per-package R from host link R.txt.
set -u
ROOT=~/workspace/muse-clone-prototype
SDK=~/workspace/tools/android-sdk
JDK=~/workspace/tools/jdk-17.0.21+9
export PATH="$JDK/bin:$PATH"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
WORK="$ROOT/manual-aar/work-host-v15"
EXT="$ROOT/manual-aar/extracted"
DEPS="$ROOT/manual-aar/deps"
WB="$ROOT/manual-aar/work-bcore-v4l"
rm -rf "$WORK"; mkdir -p "$WORK/compiled_res" "$WORK/classes" "$WORK/gen" "$WORK/gen_dep" "$WORK/dexout" "$WORK/jars"

echo "=== 0. merge manifest v3 ==="
python3 "$ROOT/manual-aar/merge-manifest-v15.py" "$ROOT/manual-aar/host-manifest-v15-base.xml" "$ROOT/engine-build/Bcore/src/main/AndroidManifest.xml" "$ROOT/manual-aar/host-manifest-v15.xml"

echo "=== 1. compile res ==="
"$BT/aapt2" compile --dir "$ROOT/manual-aar/host-res-v15" -o "$WORK/host_res.zip" 2>&1 | tail -3
"$BT/aapt2" compile --dir "$ROOT/engine-build/Bcore/src/main/res" -o "$WORK/bcore_res2.zip" 2>&1 | tail -3
cp "$WB/appcompat_res.zip" "$WB/core_res.zip" "$WB/constraintlayout_res.zip" "$WORK/"
ls -lh "$WORK"/*.zip

echo "=== 2. link (merged manifest, merged res) ==="
"$BT/aapt2" link -o "$WORK/unsigned.apk" -I "$PLATFORM" \
  --manifest "$ROOT/manual-aar/host-manifest-v15.xml" \
  --java "$WORK/gen" --output-text-symbols "$WORK/host_R.txt" \
  "$WORK/host_res.zip" "$WORK/bcore_res2.zip" "$WORK/appcompat_res.zip" "$WORK/core_res.zip" "$WORK/constraintlayout_res.zip" \
  --auto-add-overlay 2>&1 | tail -20
echo "--- host R:"; find "$WORK/gen" -type f
grep -m2 "activity_launcher\|black_box" "$WORK/host_R.txt" || true

echo "=== 3. per-package R for Bcore, core, constraintlayout ==="
python3 "$ROOT/manual-aar/gen_dep_r.py" "$WORK/host_R.txt" "$WORK/gen_dep" \
  "top.niunaijun.blackbox:$WB/R_lib.txt" \
  "androidx.core:$EXT/core/R.txt" \
  "androidx.constraintlayout.widget:$EXT/constraintlayout/R.txt"
find "$WORK/gen_dep" -type f

echo "=== 4. strip R classes from dep jars ==="
# NOTE: input jar must NOT live inside the extract dir (earlier bug emptied bcore_nor.jar).
strip_r() { # $1=jar $2=out
  local in_jar="$1" out_jar="$2" tmpdir
  tmpdir=$(mktemp -d "$WORK/strip_XXXXXX")
  (cd "$tmpdir" && unzip -q "$in_jar")
  find "$tmpdir" -name "R.class" -delete; find "$tmpdir" -name 'R$*.class' -delete
  (cd "$tmpdir" && "$JDK/bin/jar" cf "$out_jar" .)
  rm -rf "$tmpdir"
}
# bcore.aar is a zip containing classes.jar, not a jar itself: extract it first
mkdir -p "$WORK/aar_extract"; cd "$WORK/aar_extract"; unzip -q -o "$ROOT/bcore-v4l.aar" classes.jar; cd "$WORK"
strip_r "$WORK/aar_extract/classes.jar" "$WORK/jars/bcore_nor.jar"
strip_r "$EXT/core/classes.jar" "$WORK/jars/core_nor.jar"
strip_r "$EXT/constraintlayout/classes.jar" "$WORK/jars/constraintlayout_nor.jar"
strip_r "$EXT/freereflection/classes.jar" "$WORK/jars/freereflection_nor.jar"
ls -lh "$WORK/jars/"

echo "=== 5. javac host + generated R ==="
CP="$PLATFORM:$WORK/jars/bcore_nor.jar:$WORK/jars/core_nor.jar:$WORK/jars/constraintlayout_nor.jar:$WORK/jars/freereflection_nor.jar:$DEPS/BlackReflection-core-1.1.2.jar:$DEPS/annotation-1.1.0.jar"
find "$WORK/gen" "$WORK/gen_dep" -name "*.java" > "$WORK/gen_sources.txt"
find "$ROOT/manual-aar/host-src-v15" -name "*.java" >> "$WORK/gen_sources.txt"
wc -l "$WORK/gen_sources.txt"
if ! "$JDK/bin/javac" -encoding UTF-8 -source 17 -target 17 -nowarn -J-Xmx1024m -cp "$CP" -d "$WORK/classes" @"$WORK/gen_sources.txt" > "$WORK/javac.log" 2>&1; then echo "host javac FAILED"; grep -E "error:" "$WORK/javac.log" | head -20; exit 1; fi
echo "host javac OK"; tail -5 "$WORK/javac.log"
find "$WORK/classes" -name "*.class" | wc -l

echo "=== 6. dex (all classes) ==="
cd "$WORK/classes" && "$JDK/bin/jar" cf "$WORK/all_classes.jar" . && cd "$WORK"
# combine: host classes + stripped dep jars into one jar for d8 (d8 accepts multiple jars too)
"$BT/d8" --lib "$PLATFORM" --output "$WORK/dexout" \
  "$WORK/all_classes.jar" "$WORK/jars/bcore_nor.jar" "$WORK/jars/core_nor.jar" "$WORK/jars/constraintlayout_nor.jar" "$WORK/jars/freereflection_nor.jar" \
  "$DEPS/BlackReflection-core-1.1.2.jar" "$DEPS/annotation-1.1.0.jar" 2>&1 | tail -20
ls -lh "$WORK/dexout/"

echo "=== 7. assemble APK: dex + jni, align, sign ==="
cd "$WORK" && for d in dexout/*.dex; do zip -j -q unsigned.apk "$d"; done
mkdir -p "$WORK/lib/arm64-v8a"
cp "$ROOT/native-build/libs/arm64-v8a/libblackbox.so" "$WORK/lib/arm64-v8a/"
cd "$WORK" && zip -q unsigned.apk lib/arm64-v8a/libblackbox.so
"$BT/zipalign" -f 4 unsigned.apk "$ROOT/manual-aar/muse-container-host-v15-debug.apk"
KEYSTORE="$ROOT/manual-aar/work-host/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then KEYSTORE="$WORK/debug.keystore"; fi
if [ ! -f "$KEYSTORE" ]; then
  "$JDK/bin/keytool" -genkeypair -v -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/muse-container-host-v15-debug.apk" "$ROOT/manual-aar/muse-container-host-v15-debug.apk"
"$BT/apksigner" verify "$ROOT/muse-container-host-v15-debug.apk" && echo "VERIFY_OK"
ls -lh "$ROOT/muse-container-host-v15-debug.apk"
"$BT/aapt2" dump badging "$ROOT/muse-container-host-v15-debug.apk" 2>&1 | head -10
unzip -l "$ROOT/muse-container-host-v15-debug.apk"
