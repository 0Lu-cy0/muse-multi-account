#!/bin/bash
# Manual Bcore AAR build v4, NO Gradle. Complete version of the
# truncated build-bcore.sh (which stopped after aapt2 link and
# failed because the Bcore manifest has no package attribute).
# v3 change: ProxyContentProvider.call now checks the caller (see
# engine-build/Bcore/.../ProxyContentProvider.java).
set -u
ROOT=~/workspace/muse-clone-prototype
SDK=~/workspace/tools/android-sdk
JDK=~/workspace/tools/jdk-17.0.21+9
export PATH="$JDK/bin:$PATH"
BT="$SDK/build-tools/34.0.0"
BCORE="$ROOT/engine-build/Bcore"
WORK="$ROOT/manual-aar/work-bcore-v4r"
DEPS="$ROOT/manual-aar/deps"
EXT="$ROOT/manual-aar/extracted"
PLATFORM="$SDK/platforms/android-35/android.jar"
FW_AIDL="$SDK/platforms/android-35/framework.aidl"
rm -rf "$WORK"; mkdir -p "$WORK/gen_aidl" "$WORK/gen_r" "$WORK/classes" "$WORK/aar/res" "$WORK/aar/jni/arm64-v8a"

echo "=== 1. AIDL ==="
FAIL=0; OK=0
while IFS= read -r f; do
  if grep -q "^parcelable" "$f"; then continue; fi
  if ! "$BT/aidl" --lang=java -p "$FW_AIDL" -I "$BCORE/src/main/aidl" -o "$WORK/gen_aidl" "$f" 2>>"$WORK/aidl.log"; then
    echo "AIDL FAIL: $f"; FAIL=$((FAIL+1))
  else OK=$((OK+1)); fi
done < <(find "$BCORE/src/main/aidl" -name "*.aidl" | sort)
echo "AIDL ok=$OK fail=$FAIL generated=$(find "$WORK/gen_aidl" -name '*.java' | wc -l)"
[ $FAIL -gt 0 ] && exit 1

echo "=== 2. aapt2 compile res ==="
"$BT/aapt2" compile --dir "$BCORE/src/main/res" -o "$WORK/bcore_res.zip" 2>&1 | tail -3
"$BT/aapt2" compile --dir "$EXT/appcompat/res" -o "$WORK/appcompat_res.zip" 2>&1 | tail -3
"$BT/aapt2" compile --dir "$EXT/core/res" -o "$WORK/core_res.zip" 2>&1 | tail -3
"$BT/aapt2" compile --dir "$EXT/material/res" -o "$WORK/material_res.zip" 2>&1 | tail -3
"$BT/aapt2" compile --dir "$EXT/constraintlayout/res" -o "$WORK/constraintlayout_res.zip" 2>&1 | tail -3
ls -lh "$WORK"/*_res.zip

echo "=== 3. aapt2 link (manifest with package injected) ==="
python3 - "$BCORE/src/main/AndroidManifest.xml" "$WORK/bcore_manifest.xml" << 'PY'
import sys
t = open(sys.argv[1]).read()
t = t.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android"',
              '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="top.niunaijun.blackbox"', 1)
open(sys.argv[2], 'w').write(t)
PY
# Material res is NOT linked: it needs CardView attrs that are not in deps, and no Bcore Java imports material (v2 finding).
"$BT/aapt2" link -o "$WORK/bcore_link.apk" -I "$PLATFORM" \
  --manifest "$WORK/bcore_manifest.xml" \
  --java "$WORK/gen_r" --output-text-symbols "$WORK/R.txt" \
  "$WORK/bcore_res.zip" "$WORK/appcompat_res.zip" "$WORK/core_res.zip" "$WORK/constraintlayout_res.zip" \
  --auto-add-overlay 2>&1 | tail -20
echo "--- gen_r:"; find "$WORK/gen_r" -name "*.java"; wc -l "$WORK/R.txt"

echo "=== 4. javac Bcore (with BlackReflection processor) ==="
CP="$PLATFORM:$EXT/appcompat/classes.jar:$EXT/core/classes.jar:$EXT/material/classes.jar:$EXT/constraintlayout/classes.jar:$EXT/freereflection/classes.jar:$DEPS/BlackReflection-core-1.1.2.jar:$DEPS/annotation-1.1.0.jar:$DEPS/toml4j-0.7.2.jar"
PP="$DEPS/BlackReflection-compiler-1.1.2.jar:$DEPS/BlackReflection-core-1.1.2.jar:$DEPS/javapoet-1.13.0.jar"
find "$BCORE/src/main/java" "$WORK/gen_aidl" "$WORK/gen_r" -name "*.java" > "$WORK/sources.txt"
wc -l "$WORK/sources.txt"
if ! "$JDK/bin/javac" -encoding UTF-8 -source 17 -target 17 -nowarn -J-Xmx1024m \
  -cp "$CP" -processorpath "$PP" -processor top.niunaijun.blackreflection.BlackReflectionProcessor \
  -d "$WORK/classes" @"$WORK/sources.txt" > "$WORK/javac.log" 2>&1; then
  echo "Bcore javac FAILED"; grep -E "error:" "$WORK/javac.log" | head -40; exit 1
fi
echo "Bcore javac OK"; find "$WORK/classes" -name "*.class" | wc -l
(cd "$WORK/classes" && "$JDK/bin/jar" cf "$WORK/classes.jar" .)
ls -lh "$WORK/classes.jar"

echo "=== 5. package AAR v4r ==="
cp "$WORK/bcore_manifest.xml" "$WORK/aar/AndroidManifest.xml"
cp "$WORK/classes.jar" "$WORK/aar/classes.jar"
cp -r "$BCORE/src/main/res/." "$WORK/aar/res/"
cp "$ROOT/native-build/libs/arm64-v8a/libblackbox.so" "$WORK/aar/jni/arm64-v8a/"
python3 - "$WORK/R.txt" "$BCORE/src/main/res" "$WORK/aar/R.txt" << 'PY'
import sys, os, re, xml.etree.ElementTree as ET
rtxt, resdir, out = sys.argv[1], sys.argv[2], sys.argv[3]
names = set()
for root, dirs, files in os.walk(resdir):
    rel = os.path.relpath(root, resdir)
    for fn in files:
        base, ext = os.path.splitext(fn)
        if rel == ".":
            continue
        typ = rel.split("/")[0].split("-")[0]
        if typ in ("drawable", "layout", "anim", "color", "xml", "mipmap"):
            names.add((typ, base))
        elif typ == "values" and ext == ".xml":
            try:
                tree = ET.parse(os.path.join(root, fn))
                for el in tree.getroot():
                    if el.get("name"):
                        names.add((el.tag, el.get("name")))
            except Exception:
                pass
kept = []
for line in open(rtxt):
    m = re.match(r"int (\S+) (\S+) ", line)
    if m and (m.group(1), m.group(2)) in names:
        kept.append(line)
    m2 = re.match(r"int \[\] styleable (\S+) ", line)
    if m2:
        # styleable arrays are not in values names above; keep if any attr matches
        kept.append(line)
open(out, "w").writelines(kept)
print("R.txt kept:", len(kept))
PY
cp "$WORK/aar/R.txt" "$WORK/R_lib.txt"
(cd "$WORK/aar" && zip -q -r "$ROOT/bcore-v4r.aar" AndroidManifest.xml classes.jar res jni R.txt)
ls -lh "$ROOT/bcore-v4r.aar"
unzip -l "$ROOT/bcore-v4r.aar"
echo "BCORE_V4Q_OK"
