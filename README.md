# Muse Container — prototype v1 (host only, engine NOT integrated)

## What this is

A host app that can record unlimited named clones of a user-supplied
APK, each with its own host-side data dir, with a hard cap of 4
ACTIVE clones. It is NOT a working Muse clone yet: the launch path
is an explicit stub that refuses to launch and explains why, because
no virtual engine is integrated and proven (see ENGINE_EVALUATION.md).

## What it does (v1 scope, implemented)

1. Pick/import a user-supplied APK via the system file picker
   (SAF). The file is copied once to app-private `imports/` storage;
   its package, version and SHA-256 are recorded. It is NOT bundled
   into this host APK, NOT modified, NOT re-signed.
2. Create named clones (Clone 1, 2, 3, 4, …). Unlimited creation;
   each clone gets `filesDir/clones/<id>/` and a record in
   `clones.json`. The APK file is shared (path only) — never copied
   per clone.
3. List clones and active clones. "Active" in v1 means you marked it
   active; it does NOT mean a guest process is running.
4. Attempt to launch a clone → stub returns NOT_INTEGRATED and the UI
   shows that message. It deliberately does NOT launch the normally
   installed Muse app, which would fake a successful clone.
5. Request battery-optimisation exemption (system intent) and start
   a minimal foreground keep-alive service (one notification, no
   loop, no polling, no clone preloading).

## Performance rules (user requirement)

See PERFORMANCE.md. In short: max 4 active, dormant clones have no
process/sync/polling, shared APK code, lazy launch, push (not
polling) for notifications, no ads SDK, no heavy libraries in the
shipped manual APK (framework-only Java, 21 KB).

## Build

- Shipped APK (built in this VM): `muse-container-host-debug.apk`
  — built by `build-manual.sh` (aapt2 + javac + d8 + zipalign +
  apksigner). See BUILD_LOG.md for why Gradle failed in this VM.
- Gradle project (`app/`, Kotlin sources) is kept for a normal
  Android Studio build on a PC; it was not the build used here.

## Phone test — REQUIRED before any claim that cloning works

Do this on the real Samsung phone, in order. Stop at the first
failure and report it; do not work around it by repackaging Muse.

1. Install `muse-container-host-debug.apk` (debug-signed; allow
   install from this source when prompted).
2. Open it, tap "Pick / import user-supplied APK", choose the Muse
   APK file you already have (do NOT let the host download or bundle
   it). Confirm the status shows package `com.facebook.aura` and
   version `12.0.0.14.169`.
3. Create "Clone 1". Confirm it appears in the list, idle.
4. Tap "Request battery-optimisation exemption" and allow it.
   Tap "Start keep-alive service" and confirm its notification appears.
5. Tap "Set active" on Clone 1, then "Launch clone". EXPECTED in v1:
   a clear NOT_INTEGRATED message — this is the honest current
   behaviour, NOT a successful Muse launch. If it launches the
   normally installed Muse app instead, that is a bug: report it.
6. Try activating a 5th clone after 4 are active: EXPECTED refusal
   with the cap message.
7. Once an engine build exists (see ENGINE_EVALUATION.md for the
   candidate), repeat with a real launch: log in to Clone 1, trigger
   an approval notification, measure its delay vs. the native app;
   repeat with 3, then 4 active clones; record RAM, battery %,
   and any background kill. Do not raise the cap above 4 without
   those measurements.

## Proven vs unproven (summary)

- PROVEN in this VM: host APK builds, signs and verifies; APK
  import metadata + clone records + isolated host data dirs +
  active-cap enforcement are implemented in code; host contains
  no Muse APK/code; Muse APK on the VM is the original Meta-signed
  file (per the parent conversation).
- UNPROVEN (all require a real Samsung phone + an integrated
  engine): virtual clone launch, Muse login inside a clone,
  continuous notifications for 3–4 active clones, RAM/battery/
  launch-time/notification-delay measurements, and lag-free
  operation. None of these are claimed.

## Update 2026-10-27 — deploy first, UI is NOT the goal

User direction (2026-10-27): deploy it, UI is not needed, performance
is what matters. So:

- UI stays at the bare minimum already in v1: one Activity, one
  plain list, pick APK / create clone / launch / active toggle.
  No time is spent on themes, animations, layouts beyond a plain
  list, or Play Store presentation. If UI work ever blocks engine
  work, UI work is dropped first.
- Language stack (user-approved): host in Kotlin (Gradle project)
  + engine core in C++ via NDK (BlackBox Bcore, ndkBuild/Dobby).
  NOT Flutter, NOT React Native, NOT a Rust rewrite of the engine.
  The shipped manual APK remains framework-only Java only because
  the Gradle engine build was not yet available in this VM; it is
  not a change of stack.
- Integration code is now written, not just planned:
  `app/src/main/java/com/example/musecontainer/BlackBoxEngineLauncher.kt`
  implements install-per-virtual-user + launchApk, active-cap
  re-check, shared APK path, lazy install-on-launch, daemon off,
  and NEVER falls back to the normally installed Muse app. It is
  NOT wired into MainActivity yet, because no Bcore AAR exists yet
  in this VM — wiring it in without the AAR would break the build
  and fake integration.
- Engine source copied for persistence (not /tmp only):
  `engine-blackbox/Bcore/` from sudami/NewBlackbox-15.

## Phone test — stepwise, Galaxy A23 (Android 14 / One UI 6.1, 6 GB RAM)

The user's phone typically has 4+ GB used, ~1.5-2 GB free. So the
code cap stays 4, but the TEST cap is stepwise: 2, then 3, then 4
only if 3 passes. Do NOT start at 4.

For each stage (2 active, then 3 active), measure and record:
1. RAM: free RAM before, with clones idle, with clones online
   (Settings > Device care > Memory, plus `adb shell dumpsys meminfo`
   if available). Report per-clone guest + engine overhead, not host RAM.
2. Launch time: cold start of Clone 1, then Clone 2 (then 3) —
   time from tap to Muse login/home screen, in seconds.
3. Notification delay: trigger one Muse approval notification per
   active clone; record delay vs. the normally installed Muse app,
   and whether any notification was lost or late.
4. Battery: battery % at start and after 1 hour online with the
   active clones, with the phone otherwise idle.
5. Background kill: leave the phone idle 1 hour, then check which
   active clones are still alive / still receiving notifications,
   and after how long any clone was killed by Android/Samsung.
Pass criteria for moving 2 -> 3, and 3 -> 4: no clone killed in
1 hour, notification delay not materially worse than native, and
no visible lag in the foreground app. If 3 fails, the practical
cap for this phone is 2 — report that, do not force 4.

## Update 2026-10-27 (second attempt) — what actually changed

- Toolchain fixed in this VM: platform android-35 present
  (`android.jar`, API 35), NDK r29 installed
  (`android-ndk-r29`, revision 29.0.14206865, clang verified),
  cmdline-tools installed, Gradle 8.7 downloaded. Engine build
  copy patched to the installed NDK revision and to 512 MB heap,
  daemon off, in `engine-build/`.
- Native engine core BUILT (the one non-Gradle piece):
  `native-build/libs/arm64-v8a/libblackbox.so` — 300 KB, ELF
  64-bit ARM aarch64, stripped, via ndk-build (arm64, android-29).
  This proves the C++ hook/IO core compiles with NDK r29. It
  does NOT by itself launch anything: without the Java engine
  (500 files + 55 AIDL) it is an unused .so, and it was NOT
  bundled into the shipped host APK to fake integration.
- Gradle build BLOCKED, verified twice and with a trivial
  project: Gradle 8.7, `:Bcore:assembleDebug --no-daemon`,
  fails with `Could not receive a message from the daemon` /
  `IllegalArgumentException: Unexpected type tag 109`, daemon
  log `/root/.gradle/daemon/8.7/daemon-7483.out.log` (started
  18:13:34, stopped 18:13:44 after the client socket died).
  A minimal hello project fails the same way, so this is a
  VM-wide Gradle daemon-protocol failure, not engine code.
  Full log: `engine-build.log`.
- Manual Java build NOT pursued to completion: AndroidX AARs
  returned 404 from Maven Central (they live on Google Maven),
  BlackReflection core returned 404 from JitPack under the
  guessed AAR path, and resolving the full transitive graph
  (appcompat, material, core, annotation, BlackReflection
  annotation processing, 55 AIDL files) by hand is not a
  reliable substitute for Gradle here. Stopping point recorded,
  not faked.
- Shipped host APK is UNCHANGED from v1:
  `muse-container-host-debug.apk` (21 KB, stub launch). No new
  host APK was produced in this run, because an integrated host
  without a built Java engine would be dishonest. The real
  launcher code is written (`BlackBoxEngineLauncher.kt`) and
  waits for a Bcore AAR.
- Muse APK on this VM still untouched (SHA-256 d57282b2...).
