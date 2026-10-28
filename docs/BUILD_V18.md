# BUILD_V18 — Black screen after the v17 splash fix: PM hook self-recursion

- Version: versionCode 29, versionName 0.8.8-pmrecursion, engine bcore-v4o
- APK: `muse-container-host-v18-debug.apk` — 1,937,533 bytes,
  SHA-256 `6690bf6049df632de951170996b46b41dd132c305c3c55f13353d86daf9154bf`,
  apksigner VERIFY_OK, same signer as v10–v17 (cert SHA-256
  `f1fb90f5684f6b17979c9f9079843b6bbcba135db22715029d007c7321e14b70`),
  package `com.example.musecontainer`, minSdk 29, targetSdk 34.
- Host source is byte-identical to v17 except the manifest version
  (versionCode 29 / 0.8.8-pmrecursion). Host res/strings unchanged.
  Engine v4o differs from v4n in exactly two compiled classes
  (javap/class-diff verified, see §5).

## 1. What the user saw (v17, phone test 2026-10-28 13:05)

- The clone passes the engine splash (white background, Muse icon +
  "Muse" text — that screen is the v16 programmatic LauncherActivity
  splash, NOT the guest's own splash), then the screen goes BLACK and
  stays black. The user then opened "a new tab" which was also black;
  the log file ends at 13:05:42.395, so that later "new tab" event is
  NOT in this log and is not analysed here.
- The v17 AM `getRunningAppProcesses` fix is NOT implicated: no
  `getRunningAppProcesses` cycle appears anywhere in this log.

## 2. Log evidence (muse-log-nho-7.txt, 6,328 lines, 13:05:38–13:05:42)

Timeline, all 2026-10-28 local:

- 13:05:40.970–41.163: `launchApk` returned true in 18 ms,
  `LauncherActivity.onCreate()` for com.facebook.aura, userId 0,
  `ActivityStack: startActivityLocked : ActivityInfo{... AuraMainActivity}`,
  `windowIsTranslucent: false`, "App launch initiated successfully"
  (13:05:41.461). No `LauncherActivity` layout crash — the v16 fix holds.
- 13:05:41.469: `:black` (com.example.musecontainer:black, PID 7326,
  binder thread 7326_3) `FATAL EXCEPTION: binder:7326_3`,
  `java.lang.StackOverflowError: stack size 991KB`, repeating cycle:
  `$Proxy57.getApplicationInfo` → `PackageManager.getApplicationInfoAsUserUncached`
  → `...Cached` → `ApplicationPackageManager.getApplicationInfoAsUser`
  → `getApplicationInfo` → `BPackageManagerService.getApplicationInfo`
  (BPackageManagerService.java:102) → `BPackageManager.getApplicationInfo`
  (BPackageManager.java:307) → `IPackageManagerProxy$GetApplicationInfo.hook`
  (IPackageManagerProxy.java:287) → `ClassInvocationStub.invoke` (line 132)
  → `Proxy.invoke` → `$Proxy57.getApplicationInfo` → … forever.
  The final cycle frame is `IBPackageManagerService$Stub.onTransact`.
- 13:05:42.350–380: `:black` dies — "Service died: activity_manager",
  "Service died: package_manager" (twice), "Service died: storage_manager".
- 13:05:42.350–395, guest process com.facebook.aura (PID 7359):
  `RemoteException in getApplicationInfo for com.example.musecontainer`
  (`DeadObjectException` — the :black PM service is dead),
  then repeated "PackageManager service is null … using fallback /
  No existing APK found for com.facebook.aura, using null path",
  then `BActivityThread.handleBindApplication`:
  "makeApplication returned null, attempting fallback creation",
  "Creating minimal application context as fallback",
  "Failed to create fallback application context",
  `FATAL EXCEPTION: main`: `RuntimeException: Unable to makeApplication
  - all fallback attempts failed` at `handleBindApplication:493` (via
  `bindApplication:336`, `HCallbackProxy:174`), caused by
  `ClassCastException: android.app.ContextImpl cannot be cast to
  android.app.Application` at `handleBindApplication:463`. VM shuts
  down; the guest process is dead, so its window stays black.
- No `AuraMainActivity` RESUMED/Displayed line appears anywhere.

## 3. Root cause — proven from the code, matching the stack exactly

The cycle has one re-entry point, in the :black PM service:

- `BPackageManagerService.getApplicationInfo` (line 98–120) has a
  host-package branch: if `packageName` equals
  `BlackBoxCore.getHostPkg()` (com.example.musecontainer), it calls
  `BlackBoxCore.getPackageManager().getApplicationInfo(packageName, flags)`.
  `BlackBoxCore.getPackageManager()` is just `sContext.getPackageManager()`
  (BlackBoxCore.java:152–153).
- In :black, that `PackageManager` is itself proxied:
  `HookManager` installs `IPackageManagerProxy` in server processes too
  (its `inject()` replaces the ActivityThread `sPackageManager` and the
  `ApplicationPackageManager.mPM` field), and `ClassInvocationStub.invoke`
  passes `mBase` as `who` to hooks — so a call through the proxy
  re-enters `GetApplicationInfo.hook`, which calls
  `BPackageManager.getApplicationInfo`, which binders back to the same
  :black service, which takes the host branch again. Infinite recursion
  on the binder thread until the 991KB stack overflows and :black dies.
- The service method calls the system PM for `getApplicationInfo`
  ONLY in that host branch (grep of the whole service file: the only
  other system-PM calls are `getPackageArchiveInfo`/`loadLabel`/
  `getPackageInfo` in the install path, lines 690/711/722 — see §4).
  The proven cycle is therefore host-package only.
- This is exactly the v17 audit §9.4 suspect (1) — the PM host-package
  branch — which v17 left unfixed as unproven. It is now PROVEN by this
  log. The v17 audit's claim that PM hooks were safe ("implementations
  work on internal package data; no hooked-framework call on the
  guest-launch path") was wrong for the host branch: the guest-launch
  path does reach it (the :black caller at 13:05:41 was asking for the
  host package's info while starting the guest activity).

## 4. The fix (engine bcore-v4o, one file, two hooks)

In `IPackageManagerProxy.java` (mirrored identically into
`engine-blackbox/`), both hooks now bypass to the unhooked original
(`method.invoke(who, args)`, where `who == mBase` per
`ClassInvocationStub.invoke`) when — and only when — BOTH conditions hold:

- `BlackBoxCore.get().isServerProcess()` (the :black server process), AND
- the queried package equals `BlackBoxCore.getHostPkg()`.

Sites:

- `GetApplicationInfo.hook` — bypass at the top of the method. This
  breaks the exact 13:05:41.469 cycle.
- `GetPackageInfo.hook` — bypass after the existing fake-Google-Play
  (`com.android.vending`) branch, which is preserved unchanged.
  `BPackageManagerService.getPackageInfo` (line 273–290) shares the host
  branch verbatim (system PM call at line 277, and again the only
  system-PM call in that method), so it has the identical cycle by code
  symmetry even though the log only shows `getApplicationInfo`. Both
  are fixed; neither is left to be discovered by a later phone log.

Why the bypass is scoped to the host package (not the v17-style blanket
server bypass): :black's own callers asking about a VIRTUAL package must
still reach the service and get virtual info, not the real system
answer — e.g. the install path (service lines 690/711/722) asks the
system PM about the package being installed, and a blanket bypass would
change those answers (real system PM knows nothing about virtual
packages; it would throw `NameNotFoundException` where the service
currently returns virtual info or null). The scoped bypass changes
nothing for virtual packages: they still route to the service, and the
service answers them from internal `mPackages` without touching the
system PM, so no cycle exists there.

Why `isServerProcess()` alone, not `isBlackProcess()` (the v17 trap,
unchanged): `isBlackProcess()` means `ProcessType.BAppClient`, which is
true in GUEST processes too. Bypassing there would hand guests the real
system info instead of virtual package info. Guest processes still go
through the service; the service's host branch then reaches the real
manager via this same bypass on the :black side. The pre-existing
`BindServiceCommon` uses of the combined condition are untouched (they
answer a different question).

Other PM hooks checked, left unchanged, with reasons:

- `GetPackageUid`, `GetProviderInfo`, `GetReceiverInfo`,
  `GetActivityInfo`, `GetServiceInfo`, `ResolveIntent`,
  `ResolveService`, `GetInstalledApplications`, `GetInstalledPackages`,
  `QueryContentProviders`, `QueryBroadcastReceivers`,
  `ResolveContentProvider`, `GetPackagesForUid`, `GetInstallerPackageName`,
  `GetSharedLibraries`, `getComponentEnabledSetting`, and the
  permission/splash/Xiaomi hooks: their service implementations read
  only internal package data (`mPackages`, `ComponentResolver`,
  `getInstalled*ListInternal`) and make no system-PM call — grep of
  `BPackageManagerService.java` finds system-PM calls only at lines
  102, 277, 690, 711, 722. No cycle is possible through them.
- The install-path system-PM calls (lines 690/711/722) are NOT given
  their own bypass: after this fix, any `getApplicationInfo`/
  `getPackageInfo` they trigger inside :black hits the scoped bypass
  only for the host package (correct — that is the real host info they
  want); for a virtual package they route to the service as before.
  `getPackageArchiveInfo` has no hook class in `IPackageManagerProxy`,
  so an unhooked method falls through to `mBase` by
  `ClassInvocationStub.invoke`'s no-hook path — safe without a bypass.
- `getComponentEnabledSetting`'s hook (proxy line ~391) calls
  `BPackageManager.getApplicationInfo` internally: in :black it now
  terminates via the scoped bypass for the host package and via the
  service's internal path for virtual packages — no new cycle.

Untouched, as instructed: v15 lazy repair (dex still contains all
"Lazy repair" strings), v16 programmatic `LauncherActivity` splash,
v17 AM/notification fixes, v12 launch safety rules, "Đã yêu cầu mở"
wording, 3-tab host UI / circular logo / VPN UI-only.

## 5. Build verification

- Engine: `build-bcore-v4o.sh` (from `build-bcore-v4n.sh`, v4n→v4o).
  `javap -c` on the built `bcore-v4o.aar`: both
  `IPackageManagerProxy$GetApplicationInfo` and
  `IPackageManagerProxy$GetPackageInfo` contain `isServerProcess`,
  `getHostPkg`, and `Method.invoke`. Class-by-class diff of v4o vs v4n
  shows EXACTLY those two classes differ — no other engine class
  changed. (The build script's cosmetic echo still says `BCORE_V4N_OK`;
  the produced artifact is `bcore-v4o.aar`, 2.1 MB.)
- Host: `build-host-v18.sh` (from `build-host-v17.sh`, v17→v18,
  bcore-v4n→bcore-v4o), manifest versionCode 29 / 0.8.8-pmrecursion.
- Host dex contains the "Lazy repair" strings (v15 repair intact).

## 6. The bindApplication failure — downstream hypothesis, and how to tell

Working hypothesis: the `ClassCastException` / "Unable to
makeApplication" failure is DOWNSTREAM of :black's death, not an
independent bug — evidence:

- It happens 0.9 s AFTER :black dies, in a guest process whose PM
  service calls all fail with `DeadObjectException` / "service is
  null", so `BActivityThread` cannot build a real `Application` and
  falls back to a minimal context that is not an `Application` instance.
- v4k bound the guest application successfully with the same
  `BActivityThread` code (login screen appeared 2026-10-27), so the
  bind code itself is not newly broken; what changed is that :black
  dies mid-launch in this log.
- The failure is therefore expected to disappear once :black survives
  the PM query.

Discriminator in the NEXT phone log: if the log shows no
`:black` `StackOverflowError` and no "Service died" lines, yet
"Unable to makeApplication" / `ClassCastException` at
`handleBindApplication:463/493` STILL occurs, then it is a SEPARATE,
independent bug (bind-path, not PM-recursion) and must be investigated
as such. If it is gone and the guest draws content, the hypothesis is
confirmed. Record which outcome happened; do not claim the
bindApplication question closed until that log arrives.

## 7. Still unproven on a real phone (must not be claimed)

1. Clone passes the engine splash AND reaches the Muse login/main
   screen with content drawn — not a black window.
2. No `:black` `StackOverflowError` and no "Service died" lines on launch.
3. No "Unable to makeApplication" on launch (see §6 discriminator).
4. Clone survival v17→v18 (no uninstall, no login loss).
5. Carried over from v17: first guest notification with clone-name
   label and tap routing (notification-bypass check).
