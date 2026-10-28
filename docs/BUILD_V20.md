# BUILD V20 — PM guard + :black classification fix (versionCode 31, 0.8.10-pmguard)

## Log evidence: log-8 vs log-9

- Log-8 (v18 run, 2026-10-28 13:33): NO StackOverflowError, NO "Service died",
  PM binder healthy (`binderNull=false`, BinderProxy, services initialised).
  The guest then died at bind with an NPE (null `PackageInfo`), which v19
  addressed with bind recovery. Log-8's clean PM window was taken as proof
  that the v18 bypass worked. That inference was WRONG, as log-9 proves.
- Log-9 (v19 run, 2026-10-28 13:50:15–13:50:18, 6,061 lines):
  `launchApk` true in 14 ms, `startActivityLocked` AuraMainActivity
  13:50:17.101, `windowIsTranslucent: false` 13:50:17.385, then at
  13:50:17.407 `:black` (PID 16732, binder thread 16732_2) FATAL
  `StackOverflowError: stack size 991KB`, repeating cycle:
  `$Proxy57.getApplicationInfo` → system
  `PackageManager.getApplicationInfoAsUser*` →
  `BPackageManagerService.getApplicationInfo` (line 102) →
  `BPackageManager.getApplicationInfo` (line 307) →
  `IPackageManagerProxy$GetApplicationInfo.hook` (line 347 — moved from 287
  in log-7 by the v18 bypass code, and BActivityThread frames moved from
  463/493 to 495/545/552 by the v19 helper code) →
  `ClassInvocationStub.invoke` (line 132) → proxy → … — the SAME cycle as
  log-7, back again. This IS the v19 engine, not an old build: the line
  shifts prove the v18 bypass code and the v19 helper code are present.
- After `:black` died (`Service died: activity_manager`, `package_manager`
  ×2 at 13:50:18.308, `storage_manager` at 13:50:18.335), the guest bind
  failed downstream exactly as in log-7: `handleBindApplication:495`
  exception, "makeApplication returned null, attempting fallback creation",
  "Failed to create fallback application context", `ClassCastException:
  ContextImpl cannot be cast to Application` at `:545`, `RuntimeException:
  "Unable to makeApplication - all fallback attempts failed"` at `:552`,
  FATAL main, VM shutdown, black window.
- NO v19 bind-recovery lines appear anywhere in log-9 (no
  `getPackageInfo null` / `flags=0 succeeded` / `userId=0 succeeded` /
  `ALL steps failed` / synthesis lines) — because `:black` died BEFORE the
  guest bind could use the recovery path. Log-9 therefore says NOTHING
  about whether the v19 recovery works. It is kept intact, untouched.
- No `AuraMainActivity` RESUMED/Displayed line appears in log-9.

## Why the v18 bypass never fired: the :black classification is broken

The v18 bypass condition is `isServerProcess() AND package == host
package`. `isServerProcess()` is `mProcessType == ProcessType.Server`,
and `mProcessType` is set once in `BlackBoxCore.doAttachBaseContext`
(`BlackBoxCore.java:900-909`):

```java
String processName = getProcessName(getContext());
if (processName.equals(BlackBoxCore.getHostPkg())) {
    mProcessType = ProcessType.Main;
} else if (processName.endsWith(getContext().getString(R.string.black_box_service_name))) {
    mProcessType = ProcessType.Server;
} else {
    mProcessType = ProcessType.BAppClient;
}
```

The `R.string.black_box_service_name` reference is a `public static final
int`, so javac inlines its AAR-build-time value into
`BlackBoxCore.class`. Disassembly (`javap -c`) of both `bcore-v4k.aar`
and `bcore-v4p.aar` shows the identical baked constant `2131427356`
= `0x7f0b001c`:

- In the v4k APK, `0x7f0b001c` really is
  `string/black_box_service_name` (verified with `aapt2 dump
  resources`), so `:black` classified as Server and everything that
  depends on it worked.
- Since v11, when `array vpn_countries` shifted aapt2 type IDs, the
  merged APK has `0x7f0b001c` = `layout/activity_launcher` and
  `string/black_box_service_name` = `0x7f0d0027` (verified in the v19
  APK). So in every v11+ build, `getString(0x7f0b001c)` in `:black`
  returns the layout file path, not `":black"`; the `endsWith` test
  fails; and `:black` is misclassified as `BAppClient`.

This is the SAME v11 resource-shift root cause as the v16 layout crash,
one level deeper. Its consequences in :black:

1. `isServerProcess()` is false in `:black`, so the v18 PM bypass
   (`isServerProcess() && host package`) and the v17 notification
   bypasses (`isServerProcess()`-only) NEVER fire there. Log-8's clean
   PM window cannot be credited to the bypass: the triggering
   host-branch query simply did not execute in log-8's run before the
   guest died at bind (different failure order/timing), while in log-9
   it executed during `AuraMainActivity` start.
2. `MethodHook.isEnable()` defaults to `isBlackProcess()` — which is
   TRUE for misclassified `:black`. So hooks that v4k had DISABLED in
   `:black` (PM, AM, notification, …) became ENABLED there in v11+.
   That is what made the whole hook-recursion family possible in the
   first place: in v4k, `:black`'s system-PM calls were unhooked, so no
   cycle could form at all.
3. Supporting (not decisive alone): no `DaemonService` start line
   appears in any of logs 6–9, although DaemonService start is gated on
   `isServerProcess()` — consistent with it being false in `:black`
   throughout v11+.

The three candidate explanations from the task resolve as follows:
(a) the overflowing query's package is NOT the host package — REJECTED:
the service calls the system PM at line 102 only in the host-package
branch, and the cycle in the stack passes through exactly that line;
(b) `isServerProcess()` is false on the overflowing thread — CONFIRMED,
but not for a thread reason: it is false in the WHOLE `:black` process
because the process classification itself is broken (same value on
every thread, since `mProcessType` is a process-wide field set once at
attach); (c) bypass position bug — REJECTED: the bypass is the first
thing after the package-name read in both hooks, but it can never be
reached with a true condition while (b) holds.

## The fix (engine bcore-v4q, two parts)

### Part 1 — root fix: classify :black by literal, never by baked R

`BlackBoxCore.doAttachBaseContext` now tests
`processName.endsWith(":black")` with a comment recording the above.
`":black"` is what the manifest process name ends with
(`android:process="@string/black_box_service_name"` resolves to
`":black"` at install time from the MERGED resources), so this
comparison is immune to any future R shift. This restores v4k
semantics: `:black` is Server again, default hooks are disabled there
(as in v4k), and the v18/v17 bypasses become live again. Guest
processes (`:p0`–`:p5`) and the main process are unaffected by the
change (they match neither the host package nor `":black"` / the host
package respectively, exactly as before). `BlackBoxCore.java` is the
only other baked-`R` use in that file; the remaining known baked-R
risks (`R.drawable.ic_dialog_info` in ProxyVpnService/DaemonService,
`R.id.*` in the old LauncherActivity layout) were already neutralised
by the v16 programmatic splash or are unreachable UI paths, and are
unchanged here.

### Part 2 — backstop: per-thread re-entrancy guard in the PM hooks

`IPackageManagerProxy.GetApplicationInfo` and `.GetPackageInfo` now
start with a guard (`sPMHookDepth` ThreadLocal + `sPMGuardLogged`):

- If the current thread is already inside one of these hooks (depth >
  0), the call is routed straight to the unhooked base
  (`method.invoke(who, args)`, `who == mBase`) instead of the service,
  and ONE diagnostic line is logged per process (tag `BlackManager`,
  already in the user's filter):
  `PM guard tripped: re-entered <method> for <package> at depth <n>,
  bypassing to unhooked base`.
- Otherwise the depth is incremented, the existing hook body
  (including the v18 host-package bypass) runs inside `try`, and the
  depth is restored in `finally`. The `finally` is load-bearing:
  binder threads are pooled, and a stale flag would wrongly bypass
  later, unrelated calls on the same thread.

The guard is valid because the `:black` cycle is single-threaded:
`BlackManager.getService()` in `:black` returns the LOCAL service
object (log-9: `getService: activity_manager,
BActivityManagerService@df9e772`), not a BinderProxy, so the
service-method → hooked-PM → hook → service loop runs on one `:black`
thread until the stack overflows. The guard terminates it after one
extra hop, for EVERY package and EVERY process classification — it does
not depend on the v18 bypass, the package name, or `isServerProcess()`.
The v18 bypass is kept unchanged underneath the guard.

Scope notes: the guard covers exactly the two hooks whose service
methods call the system PM (`getApplicationInfo` line 102,
`getPackageInfo` line 277, plus the install path's `getPackageInfo`
line ~711 and `loadLabel` PM use, which funnel through the same two
hooks). Other PM hooks were re-checked against the v18 audit: their
service methods read only internal package data, so no cycle is
possible through them, and they are unchanged.

## Verification (build-time, not phone)

- `bcore-v4q.aar` built with `build-bcore-v4q.sh` (`BCORE_V4Q_OK`).
  `javap -c` on its `BlackBoxCore.class`: the classification now uses
  `ldc String ":black"`, with ZERO `getString` calls in the
  `doAttachBaseContext` segment. Guard string `PM guard tripped`
  present in `IPackageManagerProxy`.
- Host APK `muse-container-host-v20-debug.apk`: versionCode 31,
  versionName 0.8.10-pmguard, package `com.example.musecontainer`,
  minSdk 29, targetSdk 34, size 1,937,533 bytes, SHA-256
  f6b03d9e428fcc23f2a8df2e3b939f4fa097a448b021ad02746730c22b376188,
  apksigner VERIFY_OK, signer cert SHA-256
  f1fb90f5684f6b17979c9f9079843b6bbcba135db22715029d007c7321e14b70
  (same as v10–v19, installs over v19 without uninstalling).
- Dex checks on the built APK: `PM guard tripped` present; all v19 bind
  recovery lines present (`getPackageInfo null`, `flags=0 succeeded`,
  `userId=0 succeeded`, `getApplicationInfo succeeded`, `ALL steps
  failed`); all three `Lazy repair` strings present (v15 repair
  intact); host strings still contain `Đã yêu cầu mở` (wording
  unchanged); host src/res byte-identical to v19 except the manifest
  version.
- Engine change mirrored identically into `engine-blackbox/` for both
  changed files (verified by diff; the only remaining
  `BlackBoxCore.java` diff between the trees is the pre-existing v4e
  PendingIntent comment block, untouched).

## Untouched, as instructed

v15 lazy repair, v16 programmatic LauncherActivity splash, v17
AM/notification fixes, v18 PM bypass (kept, now live again via Part 1),
v19 bind recovery (kept intact — never exercised in log-9), v12 launch
safety rules, "Đã yêu cầu mở" wording, 3-tab host UI / circular logo /
VPN UI-only.

## Still unproven on a real phone (must not be claimed)

1. Clone passes the engine splash AND reaches the Muse login/main
   screen with content drawn — not a black window.
2. No `:black` StackOverflowError and no "Service died" lines on
   launch; the guard line `PM guard tripped` in the log would prove
   the guard fired (its absence with a clean launch proves the
   classification fix alone sufficed).
3. EITHER no bind failure, OR the v19 recovery lines appearing and
   succeeding (log-9 never exercised them, so they remain unproven).
4. Clone survival v19→v20 (no uninstall, no login loss).
5. Carried over from v17: first guest notification with clone-name
   label and tap routing (the notification bypasses are now live again
   via Part 1, but still unproven on a phone).
