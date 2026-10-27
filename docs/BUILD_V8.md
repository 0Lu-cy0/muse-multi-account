# BUILD_V8 — full report build: all functions + styled UI + generic icon

- APK: `muse-container-host-v8-debug.apk`, 1,765,085 bytes,
  SHA-256 `8f13e72af5fa02eef4ab76b3fa76f363ee62c2a032efcc5e86fa21f330bb2a3d`,
  versionCode 19, versionName `0.7.0-full`, package `com.example.musecontainer`,
  minSdk 29 / targetSdk 34, arm64-v8a, apksigner verify OK, signer SHA-256
  `f1fb90f5684f6b17979c9f9079843b6bbcba135db22715029d007c7321e14b70`
  (same shared debug signer as v2–v7, so `adb install -r` updates in place).
- Base: v6 sources copied to `manual-aar/host-src-v8/` (v7 was icon-only;
  v7 APK is untouched at 1,756,373 bytes, SHA `024be673…`). Engine
  `bcore-v4k.aar` reused as-is. Guest APK never bundled/modified/re-signed.
- Icon (from v7, included here): generic mark, NOT Muse/Meta — deep teal
  field, two overlapping amber rounded windows, two person silhouettes, a
  plus sign, no text. Master 512px: `icon-assets/ic_launcher_master_512.png`
  (copy also in `manual-aar/host-src-v8/icon-master.png`); densities
  mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192 in
  `app/src/main/res/mipmap-*/ic_launcher.png`; manifest
  `android:icon` + `android:roundIcon` = `@mipmap/ic_launcher` (verified in
  the merged manifest and in the APK listing).

## What v8 adds over v6 (functionality unchanged otherwise)
- Main layout A, styled: screen background pale teal; clone rows are white
  cards (rounded corners, stroke, 12dp padding, 12dp gap) instead of flat
  grey rows; clone name bold dark teal 16sp; letter tile is a teal oval;
  Launch = teal button, expand = amber button, secondary controls = pale
  teal buttons, Delete = red button, all with padding. VPN strip is its
  own pale-teal card on top, still collapsible to its one-line summary.
- Per-clone VPN switch: a DISABLED, unchecked Switch inside each expanded
  clone row plus an explanation line (UI only — see honest limits).
- Bubbles A1 + overflow A2: individual bubbles unchanged (drag, tap to
  open, drag-to-trash turns toggle OFF). NEW: with more than 3 bubble-enabled
  active clones, the bubbles collapse into ONE teal stack bubble showing
  the count (threshold 3 is this build's choice; the report names no
  number). Tap the stack to expand; a small amber "–" bubble re-stacks.
  Dragging the stack to trash does NOT turn any toggle off.
- Fallback A4: MainActivity's overlay section is refreshed in onResume and
  now states explicitly when the overlay permission is denied — no bubbles
  and no ⇄ button will appear, and switching from the host list (Launch)
  is the baseline path. Nothing fails silently.
- Fast switch B1: floating ⇄ button + compact overlay list unchanged in
  behaviour; panel now highlights active clones (●), states the launch-path
  limit in the panel itself, and uses coloured buttons.
- Picker P1: already complete in v6 and verified again here (full-screen
  single-tap list, search by name/package, user apps first then system,
  host excluded, empty-list explanation + APK-file fallback, split warning
  on the row, in the import result, and on the clone row). v8 only styles
  picker rows as cards with dark names / muted subtitles.
- Kept from v5/v6: keep-alive/battery buttons, bubble/fast-switch toggles,
  clone create/delete/update, launch, active cap, CloneStore compatibility
  (no new stored fields; per-clone VPN switch stores nothing).

## Honest limits — NOT implemented, and why
- Real VPN connect: NO backend and NO WireGuard configuration exist yet.
  The shared Connect button stays disabled (grey state-list drawable) and
  the per-clone switches stay disabled; the strip never shows Connected
  and never shows an IP (always "—"). Country choice only stores a choice.
- Bring-to-front restoration: the engine exposes NO such API, so switching
  still reuses the launchApk path. Login/guest data are not lost, but the
  exact foreground screen/scroll is NOT guaranteed to be restored.
- Split-APK clones: the engine installs only a single APK, so a split clone
  may be incomplete. Split rows/results/clone rows all warn; a split clone
  is never presented as complete.

## NOT verified (no phone on the VM)
Card colours/spacing, VPN card collapse, per-clone switch disabled state,
stack bubble expand/collapse/drag, overlay-denied text, switch panel both
directions, picker on-device list/search/import, Samsung launcher icon
rendering and icon-cache behaviour, RAM cost, and that existing
clones/logins survive the update. VM checks passed: javac OK, apksigner
verify OK, merged manifest keeps the icon, APK contains all mipmap
densities and all new drawables.
