# BUILD V22 — 1.1.0 (versionCode 33): per-app auto-naming of clones

HOST-ONLY change. Engine is bcore-v4r from v21, untouched — no engine
rebuild; the v21 engine AAR (`bcore-v4r.aar`) was reused exactly.

## Old behavior (v21 and earlier)

After an app was added, `promptForCloneName()` showed an AlertDialog
"Đặt tên clone" with an empty text field. If the user left it empty,
`createClone()` fell back to `"Clone N"` with N = total clones + 1
(global count, not per-app). Clone names therefore carried no app
identity ("1", "2", "Clone 2", …) and every add required a manual step.

## New behavior (v22)

The name dialog is gone. Both add paths create the clone immediately
with an auto-generated name:

```
"<appLabel> <k>"
```

- `appLabel` = the added app's display label.
- `k` = per-app numbering: starts at (existing clones with the same
  `sourcePackage`) + 1, then increments while `"<label> <k>"` already
  exists among ANY clone's name. This guarantees a unique name even
  after renames and deletions.

Examples: first Muse clone → `Muse 1`; second Muse clone → `Muse 2`;
first Block Blast clone → `Block Blast 1`.

## Call sites changed (MainActivity.java only)

- Installed-app picker path (`onActivityResult`, `REQ_PICK_INSTALLED`):
  was `promptForCloneName()`, now `autoCreateClone()`.
- APK-file path (import copy finish): was `promptForCloneName()`, now
  `autoCreateClone()`.
- `promptForCloneName()` deleted. New methods: `autoCreateClone()`
  (calls existing `createClone(autoName)` with no user input),
  `resolveAppLabel()`, `buildAutoCloneName()`.

## Label loading per path + fallback

The import flow stores no label today (only `importedPackage`,
`importedVersion`, `importedApk`, `importedSplit`), so the label is
resolved at creation time in `resolveAppLabel()`:

- Installed-app path: `getPackageManager().getApplicationInfo(
  importedPackage, 0).loadLabel(pm)` — the package is installed.
- APK-file path: `getPackageManager().getPackageArchiveInfo(
  importedApk.getAbsolutePath(), 0)`, then
  `applicationInfo.sourceDir = applicationInfo.publicSourceDir =
  apk path` before `loadLabel(pm)` (without setting sourceDir the
  label lookup falls back to the package name).
- Any null/empty label or any exception → fall back to
  `importedPackage` itself (or `"Clone"` if even that is null).
  Label resolution never throws out of creation and never shows
  a dialog.

Note: the APK-file import's local `name` variable (a UUID-prefixed
copy filename under `files/imports/`) is NOT the app label and is
not used for the clone name.

## Collision rule

`k` starts at same-package count + 1. If `"<label> <k>"` is already
taken by any clone (e.g. the user renamed another clone to it, or a
same-package clone was deleted and re-added), `k` increments until
the name is free. The empty-name fallback inside `createClone()`
stays as defensive code; it is no longer reachable from the add
flow because the auto name is always non-empty.

## Unchanged

- `createClone()` logic: `virtualUserId` = max existing + 1 (never
  reused), `CloneStore` save, toasts, `refreshList()`.
- Long-press rename: still the user's "edit later" path, untouched.
- `create_name_*` strings stay in host-res (unused now; key-count
  parity across languages matters, so they are not removed).
- Host res is byte-identical to v21; only the manifest version
  changed (versionCode 33, versionName 1.1.0).
- Active/idle toggle, bubbles, fast switch, VPN UI, engine: untouched.

## Verification

- APK: `muse-container-host-v22-debug.apk`, 1,941,629 bytes,
  SHA-256 `c006e5e3c94bbd1ca9a92b98bfe07f5279d1cc3719207fb8177dbc24efd8b5de`.
- aapt2 badging: package `com.example.musecontainer`,
  versionCode 33, versionName 1.1.0, minSdk 29, targetSdk 34.
- apksigner VERIFY_OK, same debug signer as all builds
  (cert SHA-256 `f1fb90f5684f6b17979c9f9079843b6bbcba135db22715029d007c7321e14b70`).
- Diff v21 → v22: host src differs ONLY in `MainActivity.java`;
  host res byte-identical; manifest version only.
- Dex check: `autoCreateClone` / `buildAutoCloneName` /
  `resolveAppLabel` present, `promptForCloneName` absent, v21
  engine markers (`PM host fallback`, `Dropped dead process
  record`, `Created default virtual user 0`) still present.

## What only device testing can prove

- The exact label each real app resolves to (e.g. whether an APK
  file's archive label matches its installed label).
- That a second clone of the same app is named `<label> 2` and a
  renamed-then-re-added clone never collides.
