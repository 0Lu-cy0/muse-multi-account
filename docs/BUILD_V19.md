# BUILD_V19 — Guest bind NPE after the v18 PM-recursion fix: black screen, new cause

- Version: versionCode 30, versionName 0.8.9-bindfix, engine bcore-v4p
- APK: `muse-container-host-v19-debug.apk` — 1,937,533 bytes,
  SHA-256 `c82829f1f939ec03d6e4a74c7f6f353f457991f92d1995b77ae0283e712befb8`,
  apksigner VERIFY_OK, same signer as v10–v18 (cert SHA-256
  `f1fb90f5684f6b17979c9f9079843b6bbcba135db22715029d007c7321e14b70`),
  package `com.example.musecontainer`, minSdk 29, targetSdk 34.
- Host source and res are byte-identical to v18 except the manifest version
  (versionCode 30 / 0.8.9-bindfix). Engine v4p differs from v4o in exactly
  two compiled classes (class-diff verified, see §5): BActivityThread
  (plus its renumbered anonymous inner classes) and PackageManagerCompat.

## 1. What the user saw (v18, phone test 2026-10-28 13:32–13:33)

- "Vẫn vậy này" — still black. The clone passes the engine splash, then the
  screen stays black. Same visible symptom as v17, different cause one
  step later (see §2). The v18 discriminator from BUILD_V18 §6 resolves
  as follows: the log-7 bind failure (ClassCastException downstream of
  :black's death) is GONE, and the bind failure that remains is a
  DIFFERENT, now-isolated bug. The v18 PM recursion is NOT re-fixed here.

## 2. Log evidence (muse-log-nho-8.txt, 807 lines, 13:32:59–13:33:01)

Timeline, all 2026-10-28 local:

- 13:32:59.957: host virtual-list attempt 1/5 ok, size=1, installed=true;
  launchClone clone=1, com.facebook.aura, userId=0, virtual install SKIPPED.
- 13:33:01.194: installGms success=true.
- 13:33:01.218: launchApk returned=true in 22 ms.
- 13:33:01.249: LauncherActivity.onCreate() for com.facebook.aura, userId 0.
  No layout crash — the v16 fix holds.
- 13:33:01.402: ActivityStack startActivityLocked :
  ActivityInfo{... com.facebook.aura.main.AuraMainActivity},
  windowIsTranslucent: false. "App launch initiated successfully"
  (13:33:01.702).
- 13:33:01.660: guest-side service lookup reports package_manager
  binderNull=false with a real BinderProxy; "Services initialized
  successfully" follows. NO StackOverflowError and NO "Service died"
  lines anywhere in this log — the v18 fix holds.
- 13:33:01.665–666, guest proxy process com.example.musecontainer:p0
  (PID 12544): BlackManager "Creating service for:
  IBPackageManagerService$Stub" / "Successfully created service:
  package_manager" (a binder wrapper to :black's service, not a local
  registry — BlackManager.getService() wraps the provider binder).
- 13:33:01.729, :p0: "Error creating bind application data",
  NullPointerException "Attempt to read from field 'ApplicationInfo
  PackageInfo.applicationInfo' on a null object reference" at
  BActivityThread.createBindApplicationData (line 347), via
  bindApplication (line 335) via HCallbackProxy.handleLaunchActivity
  (line 174). This NPE is caught inside createBindApplicationData and
  only logged; its return value is discarded by bindApplication.
- 13:33:01.739, :p0: FATAL EXCEPTION: main, the same NPE at
  BActivityThread.handleBindApplication (line 376) via bindApplication
  (line 336). VM shuts down; the guest process dies at bind time, so
  its window stays black. No AuraMainActivity RESUMED/Displayed line
  appears anywhere.
- Earlier in the same window, on the :black side (PID 12519), there are
  still "PackageManager service is null, attempting reinitialization /
  Failed to reinitialize / using fallback / Creating fallback
  ApplicationInfo for com.example.musecontainer / No existing APK found
  for com.example.musecontainer, using null path" lines — but those
  precede the healthy binder at 13:33:01.660, did NOT kill :black this
  time, and concern the HOST package's fallback, not the guest query.
  Treated as startup noise.

## 3. The null-PackageInfo chain — what is proven, what is not

The crashing dereferences, both in BActivityThread.java (identical in
engine-build/ and engine-blackbox/):

- createBindApplicationData (line 346–347):
  `PackageInfo packageInfo = BlackBoxCore.getBPackageManager()
  .getPackageInfo(packageName, PackageManager.GET_PROVIDERS, getUserId());`
  `ApplicationInfo applicationInfo = packageInfo.applicationInfo;`
  with no null check (the NPE is caught and logged, return value unused).
- handleBindApplication (line 375–376): the identical query and
  dereference, with NO catch around it — this is the fatal one.
- The caller chain: HCallbackProxy.handleLaunchActivity (line 174)
  calls `bindApplication(activityInfo.packageName, activityInfo.processName)`
  where activityInfo is the TARGET activity info from the stub record
  (ProxyActivityRecord.create reads "_B_|_activity_info_" — put there by
  ActivityStack.getStartStubActivityIntentInner from the resolved
  AuraMainActivity info), so packageName is com.facebook.aura; and
  getUserId() is BActivityThread.getUserId() = mAppConfig.userId (or 0
  if mAppConfig is null), where mAppConfig arrives via
  ProxyContentProvider init_process from ProcessRecord.getClientConfig(),
  whose userId comes from ActivityStack.startActivityProcess →
  BProcessManagerService.startProcessLocked(packageName, processName,
  userId, ...) with the stack's userId (0 for clone 1, per the
  LauncherActivity.onCreate userId: 0 line).
- The service side, BPackageManagerService.getPackageInfo (lines 273–295),
  returns null only if: (a) !sUserManager.exists(userId); (b) the
  package is the host package and the system PM throws
  NameNotFoundException (not applicable — package is aura); (c)
  mPackages.get(packageName) == null; or (d) generatePackageInfo
  returns null — which happens if ps.pkg == null, if
  checkUseInstalledOrHidden fails (!state.installed || state.hidden
  for that userId, or a black-listed package), or if the inner
  generatePackageInfo throws (swallowed by the outer catch in
  generatePackageInfo(BPackageSettings), which then returns null).
  The client-side fallbacks do NOT apply here: BPackageManager.
  getPackageInfo only falls back (createFallbackPackageInfo, non-null)
  when the service itself is null or the binder call throws — a clean
  null return from the service passes straight through.
- PROVEN by elimination within this window: the null is specific to
  the bind query's exact call, not to a missing package or a dead
  service. Evidence: 0.33 s earlier (13:33:01.402),
  BProcessManagerService.startProcessLocked called
  BPackageManagerService.getApplicationInfo(aura, flags=0, userId) in
  :black and got a NON-null result (otherwise it returns null and no
  :p0 process would exist); and at 13:32:59.957 the host virtual list
  (getInstalledPackagesAsUser, which reads mPackages directly and
  checks ps.getInstalled(userId)) reported aura installed for user 0.
  So at bind time the package WAS in the registry and installed for
  user 0, and the service was alive and reachable.
- NOT PROVEN to a single mechanism — the filtered log does not record
  the bind query's arguments or the service's internal branch, and the
  PM service's own Slog lines are not in the user's filter. The two
  leading mechanisms, in order:
  - (a) A component-generation throw inside generatePackageInfo's
    GET_PROVIDERS branch (or another flag branch). The bind query is
    the only query in this window that uses GET_PROVIDERS; the
    startProcessLocked query used flags=0 and therefore never executed
    any component branch. A single throwing provider/activity/service
    used to propagate out of its branch into the outer catch and null
    the WHOLE PackageInfo. (BPackage's own lists are initialised to
    empty and re-initialised in the parcel constructor, so a null
    list itself is not the suspect — a throwing item is.)
  - (b) A userId/packageName difference in :p0's query versus the
    startProcessLocked query (e.g. mAppConfig carrying a different
    userId than the stack's), which the filtered log cannot show
    because neither the AppConfig nor the query arguments are logged.
    If userId != 0, readUserState(userId) has no entry, installed
    defaults to false, and checkUseInstalledOrHidden fails → null.
- What v4k tells us: v4k bound the guest successfully with the same
  BActivityThread code, so the bind code itself is not newly broken;
  what differs is state (the lazy-repair service path, the re-imported
  guest APK, and the accumulated host/engine changes since v4k), not
  the bind call shape. No v4k-vs-now code difference in this path was
  found that explains the null.

## 4. The fix (engine bcore-v4p, two layers)

Layer 1 — bind recovery in BActivityThread.java (both engine trees,
verified identical): new private helper getBindPackageInfo(packageName,
userId), used by BOTH createBindApplicationData and
handleBindApplication, recovering in order and logging every step
under tag BActivityThread (in the user's log filter):

1. getPackageInfo(pkg, GET_PROVIDERS, userId) — the original call.
2. If null: getPackageInfo(pkg, 0, userId) — the flags=0 call proven
   to work in this exact window (startProcessLocked).
3. If null and userId != 0: getPackageInfo(pkg, GET_PROVIDERS, 0),
   then getPackageInfo(pkg, 0, 0) — covers mechanism (b).
4. If null: getApplicationInfo(pkg, 0, userId); if non-null, synthesise
   a PackageInfo (applicationInfo set, providers = empty array).
   Documented limitation: providers synthesised this way are empty,
   so guest ContentProviders would not be installed from this path;
   this step exists only so bind can proceed to a drawn main screen
   when the provider query itself is the poison, and Layer 2 is meant
   to make step 1 succeed so step 4 is rarely used.
5. If all fail: log packageName, userId, and the installed-packages
   list for that user (guarded), and return null. handleBindApplication
   then throws a RuntimeException carrying packageName/userId instead
   of a bare NPE — it NEVER continues with a null ApplicationInfo.
   createBindApplicationData throws the same informative error instead
   of returning a null-info fallback object (its return value was
   already discarded by bindApplication, so this changes no behaviour
   except the log).

Layer 2 — generator hardening in PackageManagerCompat.
generatePackageInfo(BPackage, ...): in the GET_ACTIVITIES,
GET_RECEIVERS, GET_SERVICES, GET_PROVIDERS, and GET_INSTRUMENTATION
branches, each item's generate* call is now wrapped in try/catch
(Throwable) and a throw or a null result skips just that item,
matching the pre-existing null-skip behaviour of the providers
branch; a null component list is treated as empty instead of
throwing on .size(). This removes mechanism (a) at its source: one
bad component can no longer null the whole PackageInfo.

Untouched, as instructed: v15 lazy repair (dex still contains all
"Lazy repair" strings — verified in the built APK dex), v16
programmatic LauncherActivity splash, v17 AM/notification fixes,
v18 PM GetApplicationInfo/GetPackageInfo bypass (the v18 bypass is
NOT changed: the bind query is for a virtual package, not the host
package, so it never takes the bypass path), v12 launch safety rules,
"Đã yêu cầu mở" wording, 3-tab host UI / circular logo / VPN UI-only.

## 5. Build verification

- Engine: `build-bcore-v4p.sh` (from `build-bcore-v4o.sh`, v4o→v4p).
  Class-by-class diff of bcore-v4p.aar vs bcore-v4o.aar shows EXACTLY
  these classes differ: BActivityThread (plus its renumbered anonymous
  inner classes $2–$7 and AppBindData — renumbering is expected because
  the new helper adds anonymous classes) and PackageManagerCompat.
  No other engine class changed. javap confirms the v19 recovery
  strings are present in BActivityThread.
- Host: `build-host-v19.sh` (from `build-host-v18.sh`, v18→v19,
  bcore-v4o→bcore-v4p), manifest versionCode 30 / 0.8.9-bindfix.
  Host src and res diff-verified byte-identical to v18.
- Host dex contains the "Lazy repair" strings (v15 repair intact).

## 6. How the next phone log distinguishes the mechanisms

The v19 BActivityThread log lines (tag BActivityThread, in the filter)
state which recovery step succeeded:

- Step 1 succeeds (no recovery lines, or only the Layer-2 path):
  mechanism (a) is fixed by the generator hardening (or was transient).
- "flags=0 succeeded" line: the GET_PROVIDERS (or another flag)
  branch was the poison; Layer 2 did not fully cover it — inspect
  which component branch still throws.
- "userId=0 succeeded" line: mechanism (b) — :p0's AppConfig userId
  differs from the stack's; fix the AppConfig/userId plumbing next.
- "ALL steps failed" line with the installed-packages list: the
  package is genuinely absent/not-installed for that user in the
  answering registry — a registry/install-state bug, not a query bug.
- If bind still fails with a different exception at a later line,
  that is a new, later failure, not this one.

## 7. Still unproven on a real phone (must not be claimed)

1. Clone passes the engine splash AND reaches the Muse login/main
   screen with content drawn — not a black window.
2. No :black StackOverflowError and no "Service died" lines on launch
   (v18 result, must still hold).
3. No bindApplication NPE/ClassCastException on launch; the v19
   recovery lines above identify the mechanism.
4. Clone survival v18→v19.
5. Carried over from v17: first guest notification with clone-name
   label and tap routing.
