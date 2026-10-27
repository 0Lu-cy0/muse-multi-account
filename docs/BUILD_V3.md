# Build v3 — audit fixes (2026-10-27)

Host v3: package com.example.musecontainer, versionCode 3 /
versionName 0.3.0-fixed, minSdk 29, targetSdk 34, apksigner VERIFY_OK,
same debug signer as v2 (so v3 can update v2 without uninstall in
theory; versionCode is higher). Delivered in chat as
muse-container-host-v3-debug.apk (new file; v2 is untouched).

## Fixed, with where to check

- Proxy provider caller check (Bcore, in bcore-v3.aar):
  ProxyContentProvider.call("_Black_|_init_process_") now throws
  SecurityException unless the caller is the same application (same
  UID, or the host package as reported by the provider framework).
  Previously any app knowing the authority and method could trigger
  it and receive a Binder. Verified in the built class: the
  "init_process rejected" string is present in ProxyContentProvider.
- Providers exported=false: the merged manifest in v3 has 0 exported
  providers (v2: 50). Guest code runs in the same application/UID as
  the host, so same-app provider calls still work in principle.
  Activities (151 exported) and services (100 exported, 50 of them
  JobServices already guarded by BIND_JOB_SERVICE) stay exported,
  because the system must launch proxy activities/services, and
  ProxyActivity finishes immediately unless its intent carries a
  valid proxy record. This provider change is the one v3 change that
  MUST be confirmed by the phone test: if guest launch breaks,
  revert only the provider exported=false change and keep the
  caller check.
- Permissions: 253 -> 222 uses-permissions in the merged manifest.
  Removed 31 signature/system permissions a normal app can never be
  granted (INSTALL_PACKAGES, DELETE_PACKAGES, READ_LOGS,
  INTERACT_ACROSS_USERS, DEVICE_POWER, MOUNT_UNMOUNT_FILESYSTEMS,
  etc., listed in merge-manifest-v3.py). Dangerous/runtime
  permissions a guest app may actually need (camera, mic, location,
  storage, contacts, etc.) are KEPT, because the engine proxies guest
  permission checks through the host's declared permissions. The
  host's own manifest still declares only POST_NOTIFICATIONS,
  FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, and
  REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; the rest come from Bcore.
- userId: EngineLauncher no longer defaults to 0. userIdFor returns
  -1 if the clone is not found, and launch fails explicitly.
  CloneEntry now carries a stable virtualUserId, assigned once as
  max existing + 1 and never reused or shifted on delete. Old v2
  entries without the field migrate to their array index on load.
- SHA at launch: launch verifies the imported APK's SHA-256 against
  CloneEntry.sourceSha256 and refuses on mismatch, instead of
  silently running a different APK.
- Import collision: imports now get a unique UUID-prefixed filename,
  and an import whose PackageInfo is null is deleted and reported
  as failed, not kept as "unknown".
- CloneStore: save is atomic (tmp file + rename), read loops to EOF,
  and load no longer swallows a corrupt file: it preserves the
  corrupt file as clones.json.corrupt.<timestamp> and throws, so
  the UI shows an error instead of an empty list.
- Engine context: doAttachBaseContext now receives the Application
  context, not the Activity context.
- Delete + update: MainActivity now has "Delete clone" (engine
  uninstallPackageAsUser + deleteUser, best effort, then host entry
  and host data dir removed; other clones keep their virtualUserId)
  and "Update APK (use picked APK)" (uninstalls the old guest for
  that virtual user, then updates the stored APK path/version/SHA;
  next launch installs the new APK).
- KeepAlive: notification text no longer says "engine not
  integrated". It now says the service is host-only and clone
  notifications come via the engine. MainActivity header says the
  same. This service still does NOT deliver, label, or poll for
  clone notifications, and must not be presented as doing so.

## Not fixed by v3, honestly

- Notification forwarding per clone is still not implemented in the
  host and still depends entirely on the engine's notification
  proxy. Its delay and labelling must be measured per clone on a
  real phone; v3 only stops claiming otherwise.
- Guest launch, login, RAM, battery, background kill, and lag-free
  operation are still unproven on a real phone, and the provider
  exported=false change could in principle break guest launch.
  The phone test (2 clones, then 3) is still the required next step.
- The app is still debug-signed, arm64-v8a only, and not for
  publication. The target APK supplied by the user is still never
  modified, re-signed, or bundled (SHA unchanged).

## Build notes

- build-bcore-v3.sh is a COMPLETE manual Bcore build (the older
  build-bcore.sh stops after aapt2 link and fails on the Bcore
  manifest's missing package attribute; v3 injects the package and
  drops material res from the link, because material needs CardView
  attrs not in deps and no Bcore Java imports material).
- build-host-v3.sh reuses the v2 debug keystore, uses
  merge-manifest-v3.py and host-src-v3/, and outputs the new v3 APK.
- bcore-v3.aar (2.1 MB, 2,705 classes) is committed in the repo
  as a new file; bcore.aar (v2) is left untouched there.
