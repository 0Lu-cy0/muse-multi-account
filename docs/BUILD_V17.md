# BUILD_V17 — Muse splash freeze: StackOverflowError in getRunningAppProcesses (FIXED)

- Version: versionCode 28, versionName 0.8.7-splashfix, engine bcore-v4n
- Date: 2026-10-28
- Status: built and source-pushed; NOT yet proven on a real phone (see "Must prove on phone").

## 1. What the user saw, and what changed since v16

- v16 fixed the LauncherActivity layout crash. In the 2026-10-28 12:37–12:38
  log (`muse-log-nho-6.txt`, 14,386 lines), the second launch (new host
  process PID 31092, v16) reached `LauncherActivity.onCreate` at 12:38:27.580
  with NO `Resources$NotFoundException`, and `ActivityStack` started
  `com.facebook.aura.main.AuraMainActivity` at 12:38:27.716 — the first time
  since v4k that the guest's own screen appeared at all.
- The user then saw the guest's OWN splash screen (blue "M" logo, "Muse"
  text, white background, screenshot 12:39) and it HUNG there forever, never
  reaching login/main. No `AuraMainActivity RESUMED` line appears in the log.
- The first launch in the same file (host PID 26532, 12:37:54, same
  layout-ID crash at `LauncherActivity.java:95`) was the OLD v15 engine
  still running before the v16 update took effect. It is NOT a v16
  regression and is not investigated again here.

## 2. Log evidence for the new crash

In the `:black` process (`com.example.musecontainer:black`), twice:

- Binder PID 31427 at 12:38:28.919 and binder PID 31468 at 12:38:29.904:
  `FATAL EXCEPTION: binder:XXXX_3`, `java.lang.StackOverflowError: stack
  size 991KB`.
- Repeating stack cycle (dozens of identical frames in each dump):

  ```
  HashMap.get
  -> ClassInvocationStub.invoke (line 119)
  -> IActivityManagerProxy.invoke (line 114)
  -> Proxy.invoke -> $Proxy62.getRunningAppProcesses
  -> ActivityManager.getRunningAppProcesses
  -> BActivityManagerService.getRunningAppProcesses (line 171)
  -> BActivityManager.getRunningAppProcesses (line 387)
  -> IActivityManagerProxy$GetRunningAppProcesses.hook (line 447)
  -> ClassInvocationStub.invoke (line 132)
  -> back to IActivityManagerProxy.invoke ...
  ```

- Immediately after each overflow: a native runtime crash in
  `container:black`, then `Service died: activity_manager` and `Cleared
  service cache for activity_manager`. The guest splash never proceeds
  because every attempt to start its main activity dies in this cycle.

## 3. Root cause (proven from the code + the stack, not guessed)

The recursion is a self-call through the ActivityManager hook:

1. The guest (Muse) calls `ActivityManager.getRunningAppProcesses()` during
   startup. In every virtual process the system `IActivityManager`
   singleton has been replaced by the `IActivityManagerProxy` proxy
   (`ClassInvocationStub`), so the call enters
   `ClassInvocationStub.invoke` and dispatches to the
   `GetRunningAppProcesses` hook.
2. The hook (`engine-build/Bcore/.../fake/service/IActivityManagerProxy.java`,
   `@ProxyMethod("getRunningAppProcesses")`, class at line 443, `hook` at
   ~line 447) calls
   `BActivityManager.get().getRunningAppProcesses(package, userId)`, a
   binder call into the `:black` service
   (`BActivityManager.getRunningAppProcesses`, `fake/frameworks/BActivityManager.java:385-387`
   -> `getService().getRunningAppProcesses(...)`).
3. The `:black` service implementation
   (`core/system/am/BActivityManagerService.java:168-171`, old code) did:

   ```java
   ActivityManager manager = (ActivityManager)
           BlackBoxCore.getContext().getSystemService(Context.ACTIVITY_SERVICE);
   List<ActivityManager.RunningAppProcessInfo> runningAppProcesses =
           manager.getRunningAppProcesses();
   ```

   But `:black`'s own `ActivityManager` is ALSO hooked (its
   `IActivityManager` is the same proxy), so this call re-enters step 1:
   hook -> binder -> service -> hooked `ActivityManager` -> hook ...
   forever, on the same binder thread, until the 991KB stack overflows
   and `:black` dies. The service never returns, the guest's activity
   start never completes, and the splash hangs.

Why the neighbouring `getServices` hook does NOT recurse (comparison
required by the investigation): its service implementation
(`BActivityManagerService.getRunningServices`) reads only BlackBox's
internal state (`userSpace.mActiveServices.getRunningServiceInfo(...)`)
and never calls back into any hooked Android API, so it terminates in
one hop. Likewise `GetContentProvider` invokes the method on `who`
(the unhooked base object), not on the proxy. `GetRunningAppProcesses`
was the odd one out: its service implementation re-entered the very
API being hooked.

Why v4k did not hit this is NOT explained by the current evidence
(the old service code already contained the same system-AM call); the
guest's startup path evidently did not take this route then, or took it
in a process whose AM was not hooked. This is recorded as unknown, not
papered over. The recursion mechanism itself is proven by the stack
matching the code path frame for frame.

## 4. The fix (engine bcore-v4n, one method only)

`BActivityManagerService.getRunningAppProcesses` no longer touches the
system `ActivityManager` at all. It builds the result purely from
BlackBox's own process records:

```java
List<ProcessRecord> packageProcessAsUser =
        BProcessManagerService.get().getPackageProcessAsUser(callerPackage, userId);
RunningAppProcessInfo appProcessInfo = new RunningAppProcessInfo();
for (ProcessRecord processRecord : packageProcessAsUser) {
    if (processRecord.pid <= 0) continue;
    ActivityManager.RunningAppProcessInfo info =
            new ActivityManager.RunningAppProcessInfo();
    info.processName = processRecord.processName;
    info.pid = processRecord.pid;
    info.uid = processRecord.uid;
    info.pkgList = new String[]{callerPackage};
    info.importance = ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    appProcessInfo.mAppProcessInfoList.add(info);
}
```

Why this terminates: the new body calls only
`BProcessManagerService.getPackageProcessAsUser` (internal BlackBox
state, no hooked Android API) and plain object construction, so the
chain is hook -> binder -> service -> return, exactly one hop, with no
possible re-entry. The guest-visible fields are preserved:
`processName`, `pid`, `uid`, `pkgList`, and `importance`
(FOREGROUND is correct for a virtual process that only appears here
while its app is being launched/used). The one deliberate behaviour
change: entries no longer carry the system's `lru`/importance detail
for real host processes — but the old code only ever returned entries
for the caller's own virtual processes anyway (it filtered the system
list by virtual pids), so no virtual-process entry is lost; dead or
not-yet-started virtual processes (pid <= 0) are skipped instead of
being silently dropped by the old pid-map lookup, which is equivalent.

Deliberately NOT changed: the hook itself (still installed, still
returns the service's list, still returns an empty list on null), the
v15 lazy repair, the v16 programmatic `LauncherActivity` splash, the
v12 launch safety rules, the "Đã yêu cầu mở" status wording, and all
host UI (3 tabs, circular logo, VPN UI-only). The fix is mirrored
identically into `engine-blackbox/Bcore/...` (the stale mirror tree)
so the two engine trees do not drift.

Verification of the built engine (not just the source edit):
`javap -c` on `BActivityManagerService` in the built `bcore-v4n.aar`
shows `getRunningAppProcesses` calling only
`BProcessManagerService.getPackageProcessAsUser` and constructing
`RunningAppProcessInfo` objects — no `getSystemService` call and no
`ActivityManager.getRunningAppProcesses` call remains in the method.
The v17 APK dex still contains the three "Lazy repair" strings (the
v15 repair is intact).

## 5. Lazy-repair verdict (SETTLED, do not re-open)

The 12:37–12:38 log contains `Lazy repair: rebuilt null entry for
package_manager` at 12:37:54.640 and again at 12:38:26.206. The
`Lazy repair: singleton still null for package_manager` lines are the
first call on a thread before the rebuild — expected, not a failure.
Conclusion: the v15 lazy repair WORKS (candidate 1 confirmed: the
construction-time map entry was poisoned, and the on-demand rebuild
from the singleton repairs it). The package_manager question is closed;
the remaining work is this splash recursion and whatever the guest
does after it.

## 6. What this build does NOT fix

- Whether the guest passes its splash to login/main after this fix
  (unproven until a phone test; see below).
- The noisy but non-blocking `PackageManager service is null /
  Skipping service creation ... due to recent failure / using
  fallback` lines from `BlackManager` during activity start: they did
  not block the v16 launch (virtual list ok, GMS installed, launchApk
  true in 30 ms) and are untouched.
- Anything about notifications, RAM/battery, multi-clone behaviour, or
  real VPN — all still pending after a working launch.

## 7. Must prove on phone (do not claim before this evidence)

1. Tapping clone 1 passes the Muse splash to the login screen (or, for
   an already-logged-in clone, to its main screen) within a normal few
   seconds.
2. No `StackOverflowError` / `FATAL EXCEPTION: binder` in `:black`
   in the new log, and no `Service died: activity_manager` following
   a clone launch.
3. Clone survival through a v16 -> v17 update install (no uninstall,
   no login loss).
4. Carried-over items: tab switching/state, Back behaviour, update
   checker from the Utilities tab, language switch on the Utilities
   tab, circular logo at 48dp.

## 8. Build record

- Engine: bcore-v4n (v4m source + only this `BActivityManagerService`
  change; v15 lazy repair and v16 programmatic splash intact). The
  v4n AAR binary itself is not in git; the engine source change is.
- Host: byte-identical to v16 except the manifest version
  (versionCode 28, versionName 0.8.7-splashfix); no string, layout,
  or Java changes in the host.
- APK: `muse-container-host-v17-debug.apk`, 1,937,533 bytes, SHA-256
  523e6fabd156034585d73f5b7acb5b52c3140a4704cfc682138bb7dca93e30c1,
  apksigner VERIFY_OK, same signer as v10–v16 (installs over v16
  without uninstalling), package com.example.musecontainer, minSdk 29,
  targetSdk 34.
