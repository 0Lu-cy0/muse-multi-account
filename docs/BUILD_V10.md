# BUILD_V10 — in-app self-update (0.8.0-updater, versionCode 21)

## What changed (v9 -> v10, nothing else in behaviour)
- Manifest (`manual-aar/host-manifest-v10-base.xml`): versionCode 21,
  versionName 0.8.0-updater; added `REQUEST_INSTALL_PACKAGES`; added a
  non-exported `UpdateFileProvider` (authority
  `com.example.musecontainer.updateprovider`, serves ONLY
  `cache/updates/update.apk` to the system installer).
- New `AppUpdater.java`: manual-only update check (no background
  polling, no auto-download, no auto-install). Fetches
  `https://raw.githubusercontent.com/0Lu-cy0/muse-multi-account/main/updates.json`
  first, falls back to
  `https://api.github.com/repos/0Lu-cy0/muse-multi-account/releases/latest`
  (tag + first .apk asset + versionCode parsed from the release body;
  without a versionCode the fallback reports an error instead of
  guessing). Compares manifest versionCode against the installed
  PackageInfo versionCode. Downloads on a background thread with
  progress, verifies SHA-256 against the manifest when present, and
  DELETES the file + never triggers the installer on mismatch.
  Installation always goes through the system confirmation dialog
  (ACTION_VIEW, application/vnd.android.package-archive, FileProvider
  URI + FLAG_GRANT_READ_URI_PERMISSION). If
  `canRequestPackageInstalls()` is false, it opens
  Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES for this package and
  explains in Vietnamese that the user must allow it once, then retry.
  Network failures only produce a short Vietnamese message, never a crash.
- New `UpdateFileProvider.java`: minimal ContentProvider serving only
  the update file, read-only, path-validated.
- UI: a "Cập nhật ứng dụng" block INSIDE the existing collapsed
  "Ghi chú" (Notes) section in `activity_main.xml` — version text
  ("Phiên bản đang cài: <name> (mã <code>)"), a "Kiểm tra cập nhật"
  button, and a status line — wired by `MainActivity.setupUpdater()`.
  The tidy main screen is otherwise untouched.
- Unchanged: tidy main screen, "+" add entry with two-path choice,
  Notes gathering, VPN strip + per-clone VPN switches DISABLED (never
  Connected, never a fake IP), bubbles A1 + stack overflow, fast
  switch, picker P1, icon, clone create/delete/update/launch, engine
  bcore-v4k.aar as-is, same shared debug signer (required: Android
  rejects an update signed with a different key).

## updates.json format (what the parent must publish at release time)
```json
{
  "versionCode": 22,
  "versionName": "0.8.1",
  "apkUrl": "https://github.com/0Lu-cy0/muse-multi-account/releases/download/<tag>/<apk-file>",
  "sha256": "<lowercase hex SHA-256 of the published APK>",
  "changelog": "<short Vietnamese changelog>"
}
```
Template: `updates.json.template` (same directory as this file).
Release-time checklist for the parent (v10 itself does NOT do this):
1. Build the new version, push its source to git (one commit per
   version, source only, no APKs in git — standing rule).
2. Publish the APK as a GitHub Release asset (browser route; API
   asset upload is blocked by the credential host limit).
3. Write `updates.json` to the repo root on main with that version's
   versionCode, versionName, release APK URL, SHA-256 of the
   PUBLISHED APK, and changelog. The updater reads the raw file from
   main, so the file must be on main, not only in a branch.
4. Put the versionCode in the release body too (e.g. a line
   "versionCode: 22") so the Releases fallback can compare.

## Important limitation, stated honestly
v10's updater will report "already on the latest version" against a
manifest pointing at v10 itself. A real download-and-install test can
only be proven once a LATER version (v11+) is published with a
correct updates.json + release APK. Until then, the download/install
path, the unknown-sources permission flow, and clone survival through
a self-update are UNPROVEN on a device and must not be claimed as
tested. The v10 install itself is still via `adb install -r` (v9 has
no updater) — v10 is the LAST adb install needed; from v10 onward,
updates go through the in-app button.

## Sources
- `manual-aar/host-src-v10/` (copied from v9, edited only here)
- `manual-aar/host-res-v10/` (copied from v9, Notes block added)
- `manual-aar/build-host-v10.sh`, `manual-aar/host-manifest-v10-base.xml`,
  `manual-aar/merge-manifest-v10.py`
