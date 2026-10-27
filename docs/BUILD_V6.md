# BUILD_V6 — UI redesign per approved combined report (main A, bubbles A1, fast switch B1, picker P1)

## Build (verified on VM)

- APK: `muse-container-host-v6-debug.apk`, 1,710,911 bytes,
  SHA-256 `33162978526b1e7f765edc0a99ad4e51c8cfdec696c1033bd4e74714ef2007dc`
- versionCode 17, versionName `0.6.0-ui`, package `com.example.musecontainer`,
  minSdk 29 / targetSdk 34, arm64-v8a.
- apksigner verify OK; signer SHA-256
  `f1fb90f5684f6b17979c9f9079843b6bbcba135db22715029d007c7321e14b70`
  (same shared debug signer as v2–v5, so `adb install -r` updates
  without uninstall). v5 APK untouched (SHA still e7488685…).
- Engine UNCHANGED: bcore-v4k.aar (v4j UID fix) reused as-is. No Flutter,
  no React Native, no Gradle (manual aapt2 + javac + d8 + zipalign +
  apksigner, build-host-v6.sh). Guest APK never bundled, modified,
  re-signed, or redistributed.

## What changed (all under `~/workspace/muse-clone-prototype/`)

- `manual-aar/host-src-v6/` (copied from v5, then edited):
  - `MainActivity.java`: direction A main screen — the clone list is
    now the whole main screen. Each row header shows the target app's
    icon (from the real installed app when available, else a letter
    tile), clone name, package/version, ACTIVE/idle status, and the
    Launch button. All other per-clone controls (Set active/idle,
    Update APK, Delete, Bubble, Fast switch) live in a details
    container below the header, collapsed by default and expanded
    with a "Mở rộng ▾ / Thu gọn ▴" button (expanded rows stay
    expanded across list refreshes in the same session).
  - VPN strip (direction A, top of main screen): one-line summary
    always visible; detail block (country Spinner, IP text, state
    text, Connect button, explanation) collapses to that one line
    with "Thu gọn / Mở rộng dải VPN" (state persisted).
  - `InstalledAppPickerActivity.java` (new, direction P1): full-screen
    single-tap list of launchable installed apps, search box filtering
    by name or package, user apps first then system apps, each
    alphabetical inside its group, host package excluded. Tapping one
    imports it exactly like a file import (copy to app-private
    imports/ with a unique filename, record package/version/SHA-256,
    reuse the existing launch flow).
  - `CloneEntry.java` / `CloneStore.java`: new `splitApk` boolean
    (optBoolean, default false — old clones.json entries read false,
    so existing clones are unaffected; `withApk` clears it because
    file imports are always single-file).
  - `BubbleService.java`: UNCHANGED from v5. Bubbles already stack
    vertically (each bubble's default Y is offset by its index), which
    is the A2 overflow behaviour in its simplest form; no collapsible
    group bubble was added, to avoid complicating the build.
- `manual-aar/host-manifest-v6-base.xml`: versionCode 17 /
  0.6.0-ui; `InstalledAppPickerActivity` declared (exported=false);
  narrow launcher-intent `<queries>` (MAIN + LAUNCHER) for the picker.
- `app/src/main/res/layout/activity_main.xml`: header retitled to
  the generic container (not Muse-only), VPN strip views added at
  the top, "1b. Pick installed app (new)" button added after the
  Pick APK button. (This shared file is only consumed by the v6
  build; the already-built v5 APK is untouched.)

## Honesty sections (read before testing)

- **VPN strip is UI ONLY (critical).** There is no working VPN
  backend in this build (the engine ProxyVpnService forwards
  nothing) and no WireGuard configuration exists yet. The strip
  therefore NEVER shows Connected and NEVER shows an IP (IP text is
  always "— (chưa kết nối)", state is always "Tắt — chưa có server
  VPN"). The country Spinner only stores a choice for later use; it
  changes nothing on the network. The Connect button is DISABLED and
  labelled as disabled for lack of a server. Any Connected state or
  IP shown by this strip would be fake, and this build contains none.
- **Split APKs.** The picker copies the base APK AND every split
  file into imports storage, but the engine install path
  (`BlackBoxCore.installPackageAsUser(File)`) only installs ONE APK
  file, and `launchClone` passes it only the base path. A split clone
  may therefore be INCOMPLETE (missing resources, language, or
  native libraries). Split rows in the picker carry a visible warning,
  the import result carries split=true, `CloneEntry.splitApk`
  persists it, and the clone row warns too. A split clone is never
  presented as whole. This limitation is NOT fixed in v6.
- **Fast switching (unchanged from v5).** The engine exposes NO
  bring-to-front API; switching reuses the launchApk path. Login and
  guest data are not lost (per-virtual-user storage), but the exact
  foreground screen/scroll/chat of a previously opened clone is NOT
  guaranteed to be restored — the guest main activity may be
  recreated. That is a fallback to verify on the phone, NOT faked
  instant switching.
- **Bubbles (unchanged from v5).** Shortcut icons only; they do NOT
  render conversation content. Per-clone toggles default OFF.

## NOT verified (phone test required)

- Main list rendering, icon loading, row expand/collapse, VPN strip
  collapse, country selection persistence, disabled Connect button.
- Picker: list contents (launchable apps present, host excluded,
  user-first ordering), search filtering, empty-list explanation +
  APK fallback, tap-to-import for a monolithic installed app and for
  a split app, copy failures for unreadable APKs.
- Bubble drag/tap/hide, switch both directions, screen-restore vs
  recreate, RAM cost, Samsung background-kill, and that existing
  clones/logins survive the update. Nothing above was tested on a
  phone; the VM has no device.

## Phone test (same workflow as before)

1. On the PC, verify FIRST: size 1,710,911 bytes and SHA-256
   `33162978526b1e7f765edc0a99ad4e51c8cfdec696c1033bd4e74714ef2007dc`
   (certutil). Do NOT install if they differ.
2. `adb install -r` the APK. NEVER uninstall first — clones,
   imported APK and logins would be lost.
3. Force-stop the host app, then `adb logcat -c`, then capture a
   full logcat to a file.
4. Main screen: confirm the clone list is the main screen, each row
   shows icon + name + status + Launch, "Mở rộng" reveals the
   per-clone controls and "Thu gọn" hides them again.
5. VPN strip: confirm the one-line summary stays visible, the detail
   block collapses/expands, the country Spinner changes only the
   stored country, IP stays "—", state stays Off, and the Connect
   button is disabled (never shows Connected or a fake IP).
6. Bubbles + fast switch (v5 behaviour, unchanged): overlay
   permission already granted from v5 testing (re-grant if needed),
   set 2 clones ACTIVE with Bubble + Fast-switch ON, Start bubbles,
   drag a bubble, tap it, use ⇄ to jump both directions, drag one
   bubble to the red strip (must stay hidden after restart).
7. Picker: tap "1b. Pick installed app" — confirm installed apps
   appear (user apps first, search filters by name/package, host
   app itself absent). Tap one monolithic installed app, create a
   clone from it with a different name, launch it, confirm login
   screen/state. If any installed app shows the SPLIT warning, note
   it and check whether its clone launches completely or is missing
   parts. If the picker ever shows an empty list, confirm it shows
   the explanation + APK-file fallback instead of a blank screen.
8. Fallback: confirm "1. Pick / import APK file" still works and a
   clone created from it launches as before.
9. Stop capture (Ctrl+C), THEN filter afterwards:
   `findstr /i "MuseBubble MuseLaunch MuseImport MusePicker" <full-log> > muse-log-nho.txt`,
   and send the filtered log plus: main-list/strip results, bubble
   and switch results (both directions, screen-restore vs recreate),
   picker results (search, import, launch, split warnings if any),
   fallback result, and any crash/ANR lines.
