# BUILD V21 — stale-record liveness + PM host fallback + default virtual user 0 (versionCode 32, 1.0.1)

Engine: bcore-v4r. Host: v21 tree (byte-identical to v20 except the
manifest version). This is a bug-fix release under Semantic Versioning:
1.0.0 was the first stable release, so this is 1.0.1 — and unlike the
1.0.0 release (whose APK still badged 0.8.10-pmguard), this APK's badging
carries the semver name: versionCode 32, versionName 1.0.1 (verified with
`aapt2 dump badging`, see Verification below).

## Log evidence: day-4/5/6 stall, then day-7

- Day-4 (2026-10-28 14:31:35) and day-5 (14:35:51): after the user cleared
  the Container app's data and re-added Muse, tapping the fresh clone
  (clone 1, userId 2) produced a silent stall — `launchApk` true in
  20–24 ms, engine splash started, proxy activity started and focused,
  but NO guest process, NO AuraMainActivity, NO crash, NO error of any
  kind in windows of 11 s and 70 s. Distinct from every earlier black
  screen, all of which had a crash.
- Day-6 (14:53:00, host PID 6073, `:black` PID 6151) reproduced the stall
  identically (proxy P0 started 14:53:01.138, focused, never drew). It
  did NOT prove a fresh `:black`: `:black` 6151's first line in the file
  is the 14:53:01 ActivityStack line, so its start time is unknown
  (before the file window) — a host restart does not necessarily kill
  `:black`. The stale-record hypothesis therefore stayed open, not
  rejected.
- Day-7 (15:26:25, after a VERIFIED force-stop: `adb shell pidof
  com.example.musecontainer` printed nothing) settled it: fresh host
  20708 + fresh `:black` 20777 RAN the init path — `init bUid = 210011,
  bPid = 0` and `initProcess: com.facebook.aura` at 15:26:25.914 — and
  the system started `:p0` (PID 20799) for `ProxyContentProvider$P0`.
  Conclusion: the day-4/5/6 stall WAS a stale `ProcessRecord` in the old
  `:black` (a record whose guest process had died, returned blindly
  because `bActivityThread != null`). Fix 1 below removes that blind
  trust.
- Day-7 then revealed the NEXT layer: `:p0` died at 15:26:26.170 in
  SYSTEM `ActivityThread.handleBindApplication` — NPE reading
  `PackageItemInfo.metaData` on a null object — during the FIRST (host)
  bind, BEFORE any guest bind. So the v19 recovery never ran (it only
  runs on the guest LAUNCH_ACTIVITY bind), and `ProxyActivity$P0`
  (started 15:26:26.182) launched into a dead process → black window
  again. Same symptom, new cause — the masked-layer pattern once more.

## Fix 1 — stale-record liveness (BProcessManagerService)

File: `engine/.../core/system/BProcessManagerService.java`,
`startProcessLocked`, in the `bpid == -1` early-return branch only.

Before returning an existing record whose `bActivityThread` is non-null,
the binder is now validated:

```java
boolean alive = false;
try {
    alive = app.bActivityThread.asBinder().isBinderAlive();
} catch (Throwable ignored) {
}
if (alive) {
    return app;
}
Slog.d(TAG, "Dropped dead process record: " + processName
    + " userId=" + userId + ", re-initializing");
bProcess.remove(processName);
mPidsSelfLocked.remove(app);
```

A dead record is dropped and the normal `initAppProcessL` path runs
instead. The tag is `BProcessManager`, already in the user's log filter.
This fix is needed regardless of the other two: without it, any guest
process death leaves `:black` wedged until a force-stop.

## Fix 2 — PM host fallback (IPackageManagerProxy)

Files: `engine/.../fake/service/IPackageManagerProxy.java`,
`GetApplicationInfo.hook` and `GetPackageInfo.hook` (symmetry).

Root cause of the day-7 bind crash, from code: `BPackageManagerService`
`.getApplicationInfo` gates FIRST on `sUserManager.exists(userId)` and
returns null when the user does not exist. In a fresh guest process,
before any guest init has run, `BActivityThread.getUserId()` is 0
(`mAppConfig == null` → 0, `BActivityThread.java:177-179`). After the
user's app-data clear the only virtual user is 2, so the query's
userId 0 fails the gate and the service returns null — for the HOST
package, which AOSP 14's tail-of-bind block queries with
`GET_META_DATA` and then dereferences. Hence the null-`metaData` NPE in
system code, before BlackBox's own guest bind ever runs.

The fix, placed AFTER the service call and the `isOpenPackage`
fallback, BEFORE the `return null`, in both hooks:

```java
if (BlackBoxCore.getHostPkg().equals(packageName)) {
    Slog.d(TAG, "PM host fallback: service null for " + packageName
        + " user=" + BlackBoxCore.getUserId()
        + ", returning real PM info");
    return method.invoke(who, args);
}
```

Scoped to the host package only: a virtual package that the service
cannot answer must still return null, not the real system answer. The
v18 `isServerProcess() && hostPkg` bypass and the v20 per-thread
re-entrancy guard sit ABOVE this change and are untouched — the
fallback only fires on the path that used to return null, so it cannot
re-enter the service. NOTE for the log filter: this line's tag is the
proxy's tag `PackageManagerStub`, which is NOT in the user's current
findstr list — add `PackageManagerStub` to the filter to see it, or
grep the full dump.

## Fix 3 — default virtual user 0 (BUserManagerService)

File: `engine/.../core/system/user/BUserManagerService.java`,
`systemReady()`.

After `scanUserL()`, if the user table is empty or missing (fresh
install, or a wiped data dir), virtual user 0 is created and persisted:

```java
synchronized (mUserLock) {
    if (mUsers.isEmpty()) {
        createUserLocked(0);
        Slog.d(TAG, "Created default virtual user 0 (user table was empty or missing)");
    }
}
```

Tag `BUserManager` (new). This is structural protection for EVERY
`exists()`-gated service call that uses the pre-init userId 0, not just
`getApplicationInfo`. Deliberate scope limit: an existing table that
merely LACKS user 0 (e.g. only user 2 after an app-data clear) is left
untouched — that case is covered by Fix 2, not by this. Creating user 0
unconditionally would change the numbering of later clones; the
empty-table case is the only one where no clone numbering exists yet
to disturb.

## NOT touched (explicitly)

- v19 bind recovery in `BActivityThread.getBindPackageInfo` (still the
  safety net for a null bind query on the guest path).
- v15 lazy repair in `ServiceManager`.
- v16 programmatic `LauncherActivity` splash (no baked resource IDs).
- v17 AM/notification bypasses — `isServerProcess()` ALONE, never
  `isBlackProcess() || isServerProcess()` for the labelling bypass.
- v18 PM bypass, v20 `:black` literal classification, v20 PM
  re-entrancy guard.
- Host UI strings: `host-src-v21` and `host-res-v21` are byte-identical
  to v20 (verified with `diff -rq`); only the manifest version changed.
  The status prefix is still "Đã yêu cầu mở" / "Launch requested".

## Verification (this build)

- AAR: `bcore-v4r.aar` built from `engine-build/Bcore` with
  `manual-aar/build-bcore-v4r.sh` (javac OK, 2,708 classes); the three
  new log strings verified present in the AAR classes.
- APK: `muse-container-host-v21-debug.apk`, built with
  `manual-aar/build-host-v21.sh` from the v21 host tree + v4r AAR.
- `aapt2 dump badging`: package `com.example.musecontainer`,
  versionCode 32, versionName **1.0.1**, sdkVersion 29, targetSdk 34.
- `apksigner verify`: VERIFY_OK; signer cert SHA-256
  `f1fb90f5684f6b17979c9f9079843b6bbcba135db22715029d007c7321e14b70`
  — the same debug signer as every build (install with `-r`, never
  uninstall, or clones/logins are lost).
- Size: 1,941,629 bytes; SHA-256:
  `69d0b6ff2697087e827832bff093a585807a1f9769f74ddaf8e554a63a5db13d`.
- Dex/string check on the built APK: `PM host fallback`, `Dropped dead
  process record`, `Created default virtual user 0`, v19 recovery lines
  (`getPackageInfo null`, `flags=0 succeeded`, `userId=0 succeeded`,
  `ALL steps failed`), `Lazy repair`, `PM guard tripped`, `:black`
  literal — all present. `Đã yêu cầu mở` / `Launch requested` live in
  `resources.arsc` (string resources, not dex) — verified present there
  and in `host-res-v21/values/strings.xml`.
- Source pushed to `0Lu-cy0/muse-multi-account` (one commit, source
  only, no APKs/logs/guest APK); Release v1.0.1 created via API with a
  `versionCode: 32` line in the body (the in-app updater parses it as
  fallback). The APK asset upload and the `updates.json` bump are the
  parent's steps, not part of this build.

## Discriminator lines for the next phone run

After updating (install with `-r`, no uninstall, no data clear), force
a clean slate ONLY if the old stall reproduces — otherwise tap the
Muse clone once and look for, in order:

1. `initProcess: com.facebook.aura` (tag `BProcessManager`) — the init
   path runs instead of returning a stale record. If the old `:black`
   state is still wedged, expect instead:
   `Dropped dead process record: com.facebook.aura userId=2,
   re-initializing` followed by a fresh `initProcess:` line.
2. `PM host fallback: service null for com.example.musecontainer
   user=0, returning real PM info` (tag `PackageManagerStub` — ADD this
   tag to the findstr filter) — only if the pre-init userId-0 query
   still reaches the hook; on a healthy run with user 0 existing it may
   legitimately NOT appear.
3. `Created default virtual user 0 (user table was empty or missing)`
   (tag `BUserManager`, at `:black` startup) — only on a fresh/empty
   user table; it must NOT appear when clones already exist.
4. Success criteria, unchanged: `AuraMainActivity onActivityCreated`
   then `onActivityResumed`, NO `FATAL EXCEPTION` in `:p0`, NO
   `StackOverflowError`, NO `Service died`, and the clone opens (fresh
   clone → login screen, since the data clear wiped the old login).
5. If `:p0` STILL dies in system `handleBindApplication` with the same
   null-`metaData` NPE and NO `PM host fallback` line precedes it, the
   null did not come through this hook — send that log, it re-opens
   the bind-crash investigation with the hook path ruled out.
