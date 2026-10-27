# BUILD_V4B — fix for the v4a phone crash (2026-10-27)

Host: `muse-container-host-v4b-debug.apk`, versionCode 6 / 0.4.2,
same debug signer as v2/v3/v4/v4a, so `adb install -r` updates v4a
without uninstall (clones + imported APK in app-private storage are kept).
Size 1,690,431 bytes, SHA-256
`b9ec3f3a5ff0d16444e22a3fa3f24e96752ad1d23f155ee16dd835b1a86d4f45`.
VERIFY_OK. Permissions 222, providers exported 0, missing exported 0,
exactly 1 LAUNCHER activity (MainActivity).

## What crashed in v4a (proven from logcat, not guessed)

Phone test: Pick APK + Create clone succeeded. Launch crashed:
- Process `com.example.musecontainer:black` died 4 times
  (PIDs 6068, 6149, 6379, 6517) with FATAL
  `ExceptionInInitializerError` at `BlackBoxSystem.startup:59`,
  caused by NPE `BlackBoxCore.getContext().getCacheDir()` on null
  in `BEnvironment.<clinit>:19`.
- Main process then timed out publishing the provider and ANR'd
  ("Input dispatching timed out"); the ProxyVpnService timeout was
  secondary, not the root cause.

Root cause: v4a had no Application class. The engine was only
attached from MainActivity, in the main process, on Launch.
Android runs a ContentProvider in each process BEFORE any Activity,
so the :black process's SystemCallProvider ran startup() with
BlackBoxCore.getContext() still null. BEnvironment's static field
initializers touched the context at class-load time, so the class
failed to initialize. SystemCallProvider only caught Exception,
and this was an Error, so it propagated as FATAL.

## Fixes in v4b (three layers, any one alone would be fragile)

1. Host Application class `MuseContainerApp` (new, host-src-v4b):
   `attachBaseContext` calls `BlackBoxCore.doAttachBaseContext`
   with the same ClientConfiguration as EngineLauncher (daemon OFF,
   launcher activity ON). This runs in EVERY process of the app
   (main, :black, guest-client) BEFORE any provider onCreate in
   that process, so sContext is set before BEnvironment can be used.
   `onCreate` deliberately does NOT call `doCreate()` (that is the
   main-process launch path; calling it in :black would self-wait).
   Manifest: `android:name=".MuseContainerApp"` on <application>,
   preserved through merge-manifest-v4b.py (verified in the merged
   APK manifest).

2. Engine `BEnvironment` lazy init (bcore-v4b.aar, 2.1 MB):
   sVirtualRoot / sExternalVirtualRoot are no longer static field
   initializers. `ensureInit()` runs on first load()/getter, with an
   explicit null-context check that throws IllegalStateException
   with a clear message instead of an NPE at class-load time.
   JUNIT_JAR / EMPTY_JAR (only referenced from commented code) are
   set in load(), not at class-load time.

3. Engine `SystemCallProvider` fallback + Throwable catch:
   If BlackBoxCore.getContext() is still null when the provider
   runs, it attaches from the provider's own context before startup
   (defence in depth if the Application path is ever missed).
   onCreate/initSystem now catch Throwable, so a startup failure
   is logged and returns false instead of a FATAL crash loop.

4. MainActivity Launch off the UI thread (host-src-v4b):
   launchClone -> doCreate waits for the :black process. In v4a
   that ran on the UI thread and ANR'd. v4b runs it on a background
   thread, disables the Launch button while running, shows
   "Launching..." status, then posts the result back to the UI thread.

## What v4b does NOT yet prove

Same as v4/v4a: guest launch, Muse login in a clone, labelled
notifications, tap routing, delay, RAM/battery/kill/lag are all
still unproven until this build is tested on the phone. v4b only
fixes the specific :black crash above; it does not claim launch works.

## Phone test for v4b

1. `adb install -r muse-container-host-v4b-debug.apk` (update, do NOT
   uninstall first, or clones/imported APK are lost).
2. Verify size 1,690,431 bytes + SHA above BEFORE installing if the
   download came via chat (v4a's chat download was corrupted to
   2,854,904 bytes; the direct link is the reliable route).
3. Open Muse Container, Refresh clone list — the clone created in
   v4a should still be there. Set it active, press Launch.
4. Expected new behaviour: no FATAL for :black, no MainActivity ANR;
   status should become Launched or a specific engine error, not a
   freeze. If it still fails, send the status text + a short logcat
   around the Launch press (filter for :black / SystemCallProvider).
