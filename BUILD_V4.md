# Build v4 — hybrid per-clone notification labelling (2026-10-27)

Host v4: package com.example.musecontainer, versionCode 4 /
versionName 0.4.0-hybrid, minSdk 29, targetSdk 34, apksigner
VERIFY_OK, same debug signer as v2/v3, 1.7 MB, permissions 222,
providers exported 0 (as in v3). Delivered as
muse-container-host-v4-debug.apk (new file; v3 untouched).

## What changed, and only this

- Host: CloneStore.save() now also writes clone_names.json into
  the host filesDir, a flat map virtualUserId -> clone name,
  atomically (tmp + rename), on every save (create, toggle,
  update, delete). Deleted clones disappear from the map
  automatically. This file is the only bridge that gives the
  engine the real clone name; the engine otherwise only knows a
  numeric userId.
- Engine: BNotificationManagerService (in bcore-v4.aar) reads
  clone_names.json lazily, re-reading only when its
  last-modified time changes (no polling, no extra process).
  On enqueueNotificationWithTag, before posting to the real
  NotificationManager, it prefixes the notification title with
  "[<clone name>] ". If the notification carries a conversation
  title (MessagingStyle), it prefixes that instead, and only
  falls back to the plain title otherwise, to avoid a double
  prefix in the shade. If the file is missing, unparseable, or
  has no entry for that userId, the notification is posted
  unchanged; labelling never blocks or drops a notification.
  The object modified is a parcel copy in the engine service,
  not the guest's own copy.
- Channel names: createNotificationChannel also prefixes the
  real channel name with "[<clone name>] " on first creation,
  then restores the original name before storing the channel in
  the guest-visible record. Android does not rename an
  already-existing channel, so for channels already created
  under v2/v3 the title prefix is the primary label; the channel
  prefix only takes effect for newly created channels.

## What v4 does NOT claim

- Tap routing: contentIntent is left to the engine's existing
  PendingIntent proxy (BActivityManagerService intent-sender
  records per userSpace). Whether tapping a labelled
  notification opens the correct clone must be verified on a
  real phone; if it opens the wrong clone, the next fix is to
  replace contentIntent with a host intent that launches the
  correct virtual user, in the same engine hook.
- Delay: labelling does not change delivery delay. Delay still
  depends on GMS per virtual user, Doze, battery exemption, and
  whether the guest maintains its own connection. Measure delay
  per clone on the phone, separately from labelling.
- MessagingStyle edge cases: if Muse uses a custom notification
  view that does not read EXTRA_TITLE / EXTRA_CONVERSATION_TITLE
  from extras, the prefix may not show. That is a phone-test
  finding, not something static checking can prove.
- Still debug-signed, arm64-v8a only, not for publication.
  The user-supplied target APK is still never modified,
  re-signed, or bundled (SHA unchanged).

## Phone test for v4 (after installing over v3, same signer)

1. Create / keep 2 clones with different names, set both active.
2. From another account, send a message to each clone's account.
3. Check: each notification title starts with the correct
   "[<clone name>]", notifications do not overwrite each other,
   and tapping each opens the correct clone.
4. If a channel was already created under v3, its name in
   Settings will still be the old name; only new channels get
   the prefixed name. That is expected Android behaviour, not a
   v4 bug.
