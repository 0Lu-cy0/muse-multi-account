# Engine evaluation — checked from cloned repos, not README claims alone

Target guest: Muse, `com.facebook.aura` 12.0.0.14.169, versionCode 1061701582,
minSdk 29, targetSdk 36, arm64-v8a only, 41.4 MB, original Meta signature
(apksigner VERIFY_OK, re-checked in the parent conversation). Host must NOT
repackage/re-sign the guest and must NOT bundle it; the user supplies the APK.

## Verdict

**Most plausible for a modern Samsung phone: sudami/NewBlackbox-15.**
It is the only candidate whose host build actually targets a modern Android
(targetSdk 34, compileSdk 35) and that contains Android-15-relevant work
(16 KB page-size linker flag, `max-page-size=16384`, HEAD commit
2025-08-20). It also contains GMS proxy code (`GmsCore`, `GmsProxy`,
`GoogleAccountManagerProxy`), which is necessary — not sufficient — for
Muse's FCM / Credential Manager / Play billing path.

It is still **NOT proven** for this guest, for these specific reasons:
- Neither fork has any API 35 or 36 code branch. The newest framework branch
  found in `Bcore` code is Android 14 (`UPSIDE_DOWN_CAKE`, plus
  `SDK_INT >= 33` in one place). Android 15/16 framework changes, Samsung
  Knox behaviour, and hidden-API restrictions are therefore unhandled in
  code, whatever the README claims.
- Guest targetSdk handling exists but is generic: `BActivityThread`
  reads `applicationInfo.targetSdkVersion` and passes it to the runtime
  (`setTargetSdkVersion`). That means targetSdk 36 is not rejected
  outright by that path — but passing a number through is not the same as
  handling API-36 behaviour (foreground-service types, photo picker,
  predictive back, FCM token per virtual user, biometric/Health Connect).
- Last commit 2025-08-20 is ~14 months before this prototype (Oct 2026);
  there is no evidence in the repo of a targetSdk-36, arm64, Meta-app test.
- Building it in this VM is blocked anyway: it needs NDK 29.0.13846066
  (not installed here), compileSdk 35 (only platform 34 is installed
  here), and the Gradle daemon client fails in this VM (see BUILD_LOG.md).

## Other candidates — ruled out or weaker

- **ALEX5402/NewBlackbox** (HEAD 2026-07-19, newer commit, but weaker for
  this target): root `build.gradle` sets `compileSdkVersion = 35` but
  `targetSdkVersion = 28`. That old host target is the classic BlackBox
  workaround for hidden-API restrictions, but a target-28 host on
  Android 15/16 faces modern foreground-service, permission and Play
  requirements, and its README build section still contains a
  placeholder (`git clone https://github.com/your-repo/NewBlackbox.git`).
  Its README claims "Android 5.0 to 14.0+" and "Android 15: use the
  latest build" — a claim, not a code branch. Keep as fallback only.
- **FBlackBox/BlackBox (original)**: UNUSABLE. Its `master` HEAD
  (a137343, 2024-04-12) contains only README, .gitignore, issue
  templates and workflows — the engine source has been stripped.
  Do not base work on it.
- **asLody/VirtualApp**: RULED OUT. Open-source code is frozen at
  `compileSdkVersion 26`, `targetSdkVersion 22`; its own description
  says Android 14.0 support is only in the closed business version.
  No usable open engine for a targetSdk-36 guest.

## Muse-specific compatibility risks (all unproven until a real phone test)

1. FCM: each virtual user needs a distinct push registration; BlackBox
   proxies GMS, but per-clone FCM token delivery + notification
   forwarding latency for Muse's approval notifications is untested.
2. Credential Manager / Meta login: tied to the original package +
   signature, which virtualisation preserves (unlike repackaging) — in
   theory. Whether Meta's server-side checks accept a virtualised
   environment is untested.
3. Native agent runtime + 5 dex + arm64-only native libs: no fork
   documents a test with this app; 16 KB page-size handling helps on
   Android 15 but does not prove the native runtime starts.
4. Background keep-alive: Android/Samsung kill background processes;
   a foreground service + battery exemption helps, it does not guarantee
   3–4 clones stay alive (see PERFORMANCE.md).
5. Billing, biometric, Health Connect permissions: Muse requests all of
   them; proxying each through the engine is untested for this app.

## What was NOT done

- The Muse APK was NOT copied into this host, NOT modified, NOT
  re-signed, and NOT bundled into any distributable artifact.
- No BlackBox AAR was bundled into the prototype v1 APK, because
  bundling an unbuilt, unproven engine would fake a working container.
  The host instead has a stub launcher that explicitly refuses to
  launch and says why (see `EngineLauncher`).

## Update 2026-10-27 — toolchain attempt + language stack

Language stack (user-approved, performance-first):
- Host: Kotlin (Gradle project in `app/`). The shipped manual APK
  is framework-only Java only as a VM fallback build; it does not
  change the chosen stack.
- Engine: C++ via NDK, as in BlackBox itself (Bcore/src/main/cpp,
  ndkBuild, Dobby hooks, IO redirection). NOT Flutter/React Native,
  NOT a Rust rewrite. Reusing the BlackBox engine model is the only
  way to keep per-clone overhead close to native instead of adding
  a whole second runtime (Flutter engine, JS bridge) per clone.

Toolchain changes in this run:
- cmdline-tools installed under `~/workspace/tools/android-sdk/
  cmdline-tools/latest/` (157 MB zip, via curl — curl handles the
  VM egress proxy; sdkmanager's Java proxy handling does not).
- sdkmanager install FAILED (verified, not assumed): Java
  `HttpURLConnection.doTunneling` threw
  `java.util.NoSuchElementException` at `StringTokenizer.nextToken`
  while fetching the remote repository, and no `ndk/` dir was
  created. Unsetting the proxy made it worse (no manifests fetched).
- Workaround in progress: direct curl downloads of
  `platform-35_r01.zip` (64 MB, verified 200) and
  `android-ndk-r29-linux.zip` (783 MB, verified 200) into
  `~/workspace/tools/`, then manual unzip into the SDK layout.
  Platform zip note: it extracts a nested `android-35/` folder, so
  the final path must be checked (`platforms/android-35/android.jar`
  must exist, not `platforms/android-35/android-35/android.jar`).
- RAM in this VM at this run: ~316 MB available (7.7 GB total),
  worse than the ~450 MB in the previous run. This alone makes a
  Gradle daemon build fragile; low-memory settings
  (`--no-daemon`, `-Xmx512m`-class heap) are mandatory, and even
  then a daemon-protocol failure (type tag 109, previous run) may
  recur. That failure is a VM toolchain failure, not app code.

Integration code status (this run):
- `app/.../BlackBoxEngineLauncher.kt` written: doAttachBaseContext
  with daemon OFF, doCreate, installPackageAsUser(File, userId),
  launchApk(package, userId), active re-check, shared APK path,
  lazy install-on-launch, no fallback to the installed Muse app.
- NOT wired into MainActivity yet: no Bcore AAR exists in this VM
  until the NDK + platform-35 install finishes and a Gradle
  `:Bcore:assembleDebug` (or `:Bcore:assembleRelease`) succeeds.
  Wiring it in before that point would be a fake integration.

## Update 2026-10-27 (second attempt) — build verdict

- sudami/NewBlackbox-15 remains the most plausible candidate,
  and its C++ core is now PROVEN to compile: ndk-build produced
  `native-build/libs/arm64-v8a/libblackbox.so` (300 KB, ARM64)
  from Bcore/src/main/cpp (BoxCore, hooks, Dobby, xdl, IO
  redirection, 16 KB page-size linker flag).
- Its Java engine is NOT built: Gradle daemon fails in this VM
  for every project, including a trivial hello project
  (Gradle 8.7, type tag 109, see README update). No Bcore AAR
  exists, so `BlackBoxEngineLauncher.kt` is written but not
  wired into MainActivity. ALEX5402/NewBlackbox was not tried
  as a fallback in this run because it shares the same Gradle
  build path and would hit the same VM daemon failure; its
  weaker targetSdk 28 host also remains a reason not to prefer it.
- What would unblock a real build: a PC / CI environment with
  a working Gradle daemon (or a Gradle version whose daemon
  protocol is not broken by this VM's localhost handling),
  NDK r29, SDK platform 35, and the engine's Maven/JitPack
  dependencies resolvable by Gradle. That environment is NOT
  this VM as configured.
- Muse-specific risks are unchanged and still unproven:
  per-clone FCM tokens, Meta server-side acceptance of a
  virtualised environment, native agent runtime start inside
  the engine, background kill at 2-3 active clones on a 6 GB
  Galaxy A23, billing/biometric/Health Connect proxying.
