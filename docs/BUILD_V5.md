# BUILD_V5 — chat bubbles + fast switching (host only, Java native)

## What changed (v5, versionCode 16 / 0.5.0-bubbles)

- Engine UNCHANGED: bcore-v4k.aar (v4j UID fix) reused as-is. No Flutter,
  no React Native, no Gradle (manual aapt2 + javac + d8 + zipalign +
  apksigner, build-host-v5.sh). Main-screen layout XML untouched; the
  main UI layout direction (A/B/C/D) is still undecided and was NOT
  redesigned — only controls were added.
- New: `BubbleService` (foreground, specialUse, no loops/polling/timers).
  One TextView bubble per ACTIVE clone with its bubble toggle ON
  (default OFF, per clone, stored in clones.json). Drag to move (position
  persisted per clone); drag to the red bottom strip to hide, which
  turns that clone's bubble toggle OFF persistently. Tap a bubble to
  launch that clone full-screen (background thread, same launch path).
  A bubble is a SHORTCUT ICON ONLY: it does NOT render conversation
  content (guest Bubbles API support is unproven).
- New: one floating "⇄" switch button, shown when at least one ACTIVE
  clone has its fast-switch toggle ON. Tap opens a compact overlay list
  of ACTIVE clones; tapping one jumps to it without the host home screen.
- `CloneEntry`/`CloneStore`: added `bubbleEnabled`/`switchEnabled`
  (optBoolean, default false — old clones.json entries read OFF, and
  withActive/withApk preserve the toggles).
- `MainActivity`: per-clone "Bubble: ON/OFF" and "Fast switch: ON/OFF"
  buttons per row; overlay-permission button (explains SYSTEM_ALERT_WINDOW
  and opens Settings), Start/Stop overlay buttons. Toggles only refresh
  the overlay if it is already running — they never start it by surprise.
- Manifest: SYSTEM_ALERT_WINDOW declared explicitly; BubbleService
  declared (exported=false, foregroundServiceType specialUse).

## Bring-to-front honesty (read before testing)

The engine exposes NO bring-to-front API. `BlackBoxCore.launchApk()`
only builds the guest launch intent and starts it with
FLAG_ACTIVITY_NEW_TASK (LauncherActivity). Fast switching therefore
reuses exactly the same launchApk path as the host Launch button.
Switching does NOT lose the source clone's login or state (guest data
lives in the virtual user's storage and the launch reuses it), but the
exact foreground screen/scroll/chat of a previously opened clone is NOT
guaranteed to be restored — the guest main activity may be recreated.
That is a fallback to verify on the phone, NOT faked instant switching.

## Build (verified on VM)

- APK: `muse-container-host-v5-debug.apk`, 1,698,623 bytes,
  SHA-256 e7488685802220e6a86172636b99807a60b42cdf8778cbb8831d90a4bf782e41
- versionCode 16, versionName 0.5.0-bubbles, package
  com.example.musecontainer, minSdk 29, targetSdk 34, arm64-v8a.
- apksigner verify OK; signer SHA-256
  f1fb90f5684f6b17979c9f9079843b6bbcba135db22715029d007c7321e14b70
  (same shared debug signer as v2–v4k, so `adb install -r` updates
  without uninstall). v4k APK and bcore-v4k.aar untouched.

## NOT verified (phone test required)

- Overlay permission grant flow, bubble drag/tap/trash, switch panel,
  switch both directions, whether a switched-to clone restores its
  foreground screen or recreates main activity, RAM cost of the overlay
  service, Samsung background-kill behaviour on the overlay, and that
  existing clones/logins survive the update. Nothing above was tested
  on a phone; the VM has no device.

## Phone test (same workflow as before)

1. On the PC, verify the file FIRST: size 1,698,623 bytes and SHA-256
   e7488685802220e6a86172636b99807a60b42cdf8778cbb8831d90a4bf782e41
   (certutil). Do NOT install if they differ.
2. `adb install -r` the APK. NEVER uninstall first — clones, imported
   APK and logins would be lost.
3. Force-stop the host app, then `adb logcat -c`, then capture a full
   logcat to a file.
4. In the host app: button 5 (overlay permission) → allow "Display over
   other apps" for Muse Container → come back. Set 2 clones ACTIVE.
   For each: turn its Bubble toggle ON and its Fast-switch toggle ON.
   Button 6 (Start bubbles & fast switch).
5. Inside clone 1: drag its bubble to another corner (must move, must
   stay after release); tap the bubble (must open clone 1 full-screen);
   tap the ⇄ button, tap clone 2 in the list (must jump to clone 2
   without the host home screen); from clone 2, tap ⇄ and jump back to
   clone 1. Note whether each clone reopens on its previous screen or
   on the guest main screen, and whether login is intact both ways.
6. Drag one bubble to the red bottom strip: it must disappear and stay
   gone after stopping/restarting the overlay (toggle saved OFF).
7. Stop the capture (Ctrl+C), THEN filter afterwards:
   `findstr /i "MuseBubble MuseLaunch" <full-log> > muse-log-nho.txt`
   and send the filtered log. Include: bubble drag/tap/hide results,
   switch both-directions results, screen-restore vs recreate notes,
   and any crash/ANR lines.
