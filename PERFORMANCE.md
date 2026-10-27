# Performance — requirement, design, and what is (not) measured

User requirement (2026-10-27, explicit): do NOT let the phone lag.

## Hard rules implemented in prototype v1

1. **Hard active cap: max 4.** `MainActivity.MAX_ACTIVE_CLONES = 4`
   (Java manual build; the Kotlin/Gradle copy enforces the same count).
   Activating a 5th clone is refused in the UI with an explanation.
   Unlimited clones may be *created*, but beyond the cap they are
   dormant records only.
2. **Dormant = truly dormant.** A dormant clone has no process, no
   background sync, no polling, and is never preloaded at host startup.
   There is no timer, `Handler.postDelayed`, polling loop, ads SDK, or
   Firebase/polling dependency anywhere in the host source
   (verified by source grep: no matches for poll/Timer/postDelayed/ads).
3. **Shared APK code.** `createClone()` records the *path* to the single
   user-imported APK file and its SHA-256; it does NOT copy the APK
   per clone. Only per-clone data lives separately, under
   `filesDir/clones/<cloneId>/`. The 41.4 MB Muse APK is therefore
   stored once, not N times.
4. **Lazy-load.** Host startup only lists clone records from
   `clones.json`. A clone is launched only when the user opens it or
   marks it active. In v1, launch is still blocked by the engine stub
   (see ENGINE_EVALUATION.md), so lazy-load is a code property, not
   yet a runtime guest-launch property.
5. **Push, not polling, for notifications.** The design for active
   clones is FCM forwarding via the virtual engine. The host itself
   must never poll Muse servers for notifications. This is unproven
   until the engine is integrated — see below.
6. **Minimal host.** The manual-build host is framework-only Java:
   no AndroidX, no Kotlin runtime, no ads SDK, no heavy library.
   The foreground service (`KeepAliveService`) posts one low-importance
   notification and does nothing else: no loop, no clone iteration,
   no preloading. The Gradle project copy still lists AppCompat/
   core-ktx, but the shipped manual APK does not include them.
7. **Lightweight UI.** One Activity, one ScrollView/LinearLayout list,
   no RecyclerView, no images, no animations.

## What was measured in this VM (host only)

- Host debug APK: 21 KB, `com.example.musecontainer`, versionCode 1,
  versionName `0.1.0-prototype`, minSdk 29, targetSdk 34, compileSdk 34,
  apksigner VERIFY_OK, debug-signed (throwaway key, never the Muse key).
- Host APK contents: only `AndroidManifest.xml`, one layout,
  `resources.arsc`, `classes.dex`, META-INF. No `com.facebook.aura`
  content, no bundled Muse APK (verified by archive listing).
- Host source grep: no polling/timer/ads code; clone creation shares
  one APK path (see rule 3).
- Build time for the manual build: ~12 s once the toolchain was fixed
  (not a phone performance metric).

## What was NOT measured — do NOT claim the prototype is lag-free

Because there is no Android device/emulator with Play Services in
this VM and no virtual engine is integrated yet, the following are
all **UNPROVEN** and must be measured on the real Samsung phone:

- RAM per active Muse clone (guest + engine overhead, not host RAM).
- Guest launch time (cold start of a virtual Muse clone).
- Notification delay per active clone vs. the normally installed app
  (especially approval notifications, which are time-sensitive).
- Battery drain with 3 and with 4 clones online simultaneously.
- Whether Android/Samsung kills background clones despite the
  foreground service + battery exemption, and after how long.
- Frame/UI smoothness of the guest inside the container vs. native.
- Storage growth per clone over time (chat/media/cache).

## Required phone test (see README.md for the full checklist)

On the Samsung phone, with the host APK installed: import the
user-supplied Muse APK, create Clone 1, grant battery exemption,
start keep-alive, then — once the engine is integrated — launch
1 clone, log in, send a Muse task that triggers an approval
notification, measure its delay; repeat with 3, then 4 active
clones; record RAM (Settings / developer options or `adb shell
dumpsys meminfo` if available), battery %, and any kill/restart.
If notification delay or kills are unacceptable at 4, drop the
active cap to 3 in the host before any wider use. Do not raise
the cap above 4 without new measurements.

## Update 2026-10-27 — deploy-first, performance-only scope

User direction: deploy it, UI is not needed, performance is what
matters. Consequences applied in this run:

- No UI work was done beyond the bare list already in v1. Any
  future UI change must be justified by a performance or launch
  need, not by appearance.
- Language stack and why it is the performance-optimal choice:
  Kotlin host (no extra runtime per clone beyond the Android
  runtime) + C++ engine core via NDK (native hooks/IO redirection,
  the BlackBox model). Flutter would add a Flutter engine per
  process, React Native a JS bridge, and a Rust rewrite would
  discard the only tested native hook code — all three would cost
  RAM and time for no performance gain on this phone.
- Test order is now stepwise by user approval: 2 active clones
  first, then 3, then 4 only if 3 passes. On the Galaxy A23
  (6 GB RAM, ~1.5-2 GB free in normal use) the practical cap is
  expected to be 2-3, not 4. The code cap stays 4, but the test
  cap and the reported recommendation must follow measurements,
  not the code cap.
- Measurements still NOT available (no device, no engine AAR yet):
  RAM per active clone, guest cold-launch time, notification delay
  vs. native per clone, battery drain at 2 and at 3 active, and
  background-kill time. The phone test in README.md (this run's
  update) is the exact checklist that must produce those numbers.
  Do NOT report the prototype as lag-free, fast, or deployable
  for 3-4 simultaneous clones until at least the 2-clone stage
  of that checklist has passed on the Galaxy A23.

## Update 2026-10-27 (second attempt) — measurements: none, and why

No performance measurements exist for Muse clones, and none are
claimed. Exact reasons, in order:

1. No integrated engine: the Java engine (Bcore AAR) could not
   be built in this VM because the Gradle daemon protocol fails
   for every project (see README/ENGINE updates). Only the C++
   .so built, which alone runs no guest app.
2. No Android device/emulator with Play Services in this VM, so
   even a built engine could not have produced RAM, launch-time,
   notification-delay, battery, or background-kill numbers here.
3. UI was not touched in this run, per the deploy-first
   direction: the host UI is already a bare list, and no time
   was spent on themes, animations, or presentation. UI
   completeness is therefore NOT reported as progress.

What IS in place for measurement once an engine build exists
on a working Gradle machine and the host is installed on the
Galaxy A23: the stepwise checklist in README.md (2 active
first, then 3, then 4 only if 3 passes), measuring free RAM,
per-clone launch time, notification delay vs. native per
clone, battery over 1 hour online, and background kill after
1 hour idle. Until at least the 2-clone stage passes there,
the practical cap for this phone must be treated as unknown,
with 2-3 expected and 4 at high risk — not as a proven cap.
