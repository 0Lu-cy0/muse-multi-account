# BUILD_V4C — fix for the v4b splash crash (2026-10-27)

Host: `muse-container-host-v4c-debug.apk`, versionCode 7 / 0.4.3,
same debug signer as v2/v3/v4/v4a/v4b, so `adb install -r` updates
v4b without uninstall (clones + imported APK kept).
Size 1,690,431 bytes (coincidentally the same byte count as v4b;
check the hash, not just the size), SHA-256
`9f47f8a88082adbcd1a7139b03971dcee93b3c9ecf83d5717790fd9b1abcb8dc`.
VERIFY_OK. Permissions 222, 1 LAUNCHER activity, Application
`.MuseContainerApp` in the merged manifest. All v4b fixes are kept.

## What v4b proved, and what broke next

v4b fixed the :black crash: black process started, SystemCallProvider
initialized successfully, all engine services were provided, and the
guest install of com.facebook.aura succeeded (CreateUser /
CreatePackage / Copy / onPackageInstalled, userId 0). Then:

- 16:14:04.169 `LauncherActivity.launch()` for com.facebook.aura.
- 16:14:04.190 `LauncherActivity.onCreate()`, package info retrieved.
- 16:14:04.222 Critical error in onCreate: `InflateException` on
  `layout/activity_launcher`, caused by `NoClassDefFoundError`:
  `androidx/constraintlayout/core/widgets/ConstraintWidgetContainer`
  (ClassNotFoundException), at `ConstraintLayout.<init>:504`.
- The splash activity caught the exception and finished, so the
  phone fell back to the Samsung home screen at 16:14:06 and the
  guest never started — even though MainActivity had already
  reported Launched, because launchApk only starts the splash.

Root cause: `activity_launcher.xml` was a ConstraintLayout, but
`constraintlayout-2.1.4.aar` only ships the widget classes. The
core classes (`androidx/constraintlayout/core/widgets/*`) are a
separate transitive artifact (constraintlayout-core) that the
manual toolchain never downloaded. No other Bcore layout or Java
file uses ConstraintLayout, so this was the only place it could
bite — and it was hidden until v4b, because v4a never got far
enough to run the splash activity.

## Fix in v4c (one file)

`engine-build/Bcore/.../res/layout/activity_launcher.xml` is now a
plain vertical `LinearLayout` with `gravity=center`: same white
background, same `iv_icon` (96dp) and `tv_app_name` (18sp, black,
16dp top margin), same ids, so `LauncherActivity.java` is unchanged.
The splash now needs no constraintlayout-core at all. Verified in
the built APK: `aapt2 dump xmltree ... res/layout/activity_launcher.xml`
shows `LinearLayout` as root, with the same ImageView/TextView.

## What v4c does NOT yet prove

Guest Muse actually opening, login in a clone, labelled
notifications, tap routing, delay, RAM/battery/kill/lag — all still
unproven until v4c is tested on the phone. v4c only removes this
one splash blocker; the next step after the splash is
`launchAppAsync` -> `BActivityManager.startActivity`, which has
never run yet.

## Phone test for v4c

1. `adb install -r muse-container-host-v4c-debug.apk` (update, do NOT
   uninstall first).
2. Verify hash `9f47f8a8...` before installing if downloaded via chat.
3. Open Muse Container, Refresh, Set active, Launch the existing clone
   (guest is already installed from v4b, so Launch should skip the
   install step and go straight to the splash).
4. Expected: a white splash with the Muse icon + name for a moment,
   then Muse itself opening in the clone. If it instead returns to
   the home screen again, send the status text + the short logcat
   around the Launch press, filtered for SplashScreen / LauncherActivity.
