# BUILD_V15 — package_manager service repair (0.8.5-pmfix, versionCode 26)

## Log evidence (muse-log-nho-4.txt, user's phone, 2026-10-28, 1044 lines)

- The v11 raw NullPointerException dump is GONE. The v12 poll runs
  (virtual-list attempt 1/5 through 5/5, 1s apart) and then refuses
  install with a short message ("virtual list never became ready;
  refusing install", IllegalStateException: package manager service
  null, logged only). The remaining bug is that opening a clone
  still fails because the service never becomes ready.
- The :black engine process IS running: "Black process is running -
  SystemCallProvider accessible". Only package_manager is null, every
  time: "Failed to get binder for service: package_manager",
  "getService: package_manager, null", "No fallback available for
  service: package_manager". All eight other services return healthy
  binders in the same process, in the same init loop
  (11:35:26.901-903 and 11:53:07.710-711, :black pids 20429 and 22939:
  activity_manager, storage_manager, user_manager, job_manager,
  xposed_manager, account_manager, location_manager,
  notification_manager — all non-null, package_manager null).
- The same pattern repeats in every :black process since 10:50
  (pids 14768, 19195, 20429, 22939), from the FIRST request in each
  process. It is deterministic, not a slow start.
- There is NO host-app FATAL crash for com.example.musecontainer in
  the log (the AndroidRuntime lines are an unrelated Shopee process).
  The guest APK files exist for both clones. The fallback
  isInstalled check returns true, but that is the SYSTEM
  PackageManager (true because the real Muse is installed on the
  phone) and must NOT be trusted as proof the virtual user has the
  guest (v4f lesson).
- Context: the same engine binary (bcore-v4k.aar, built 2026-10-27
  19:09) worked on 2026-10-27 (first working clone, login
  succeeded). The host Application class is byte-identical between
  v4k and v14. What persists across the updates on the phone is the
  virtual data directory, not the engine binary.

## What is NOT the cause (verified, not assumed)

- The BlackManager 2-second gate is NOT permanently latched and is
  NOT the cause. BlackManager.java (engine-build/Bcore/.../fake/
  frameworks/BlackManager.java:19) has RETRY_TIMEOUT_MS = 2000, and
  the flag resets after the timeout (lines 34-45). The log shows
  fresh provider attempts at 1s+ intervals still returning null,
  and the v12 5x1s poll spans ~4s, so later attempts are real
  provider attempts, not gate-suppressed ones. BUILD_V12's notes
  that the gate is 2 seconds and that BlackManager.getService() is
  public are CORRECT and were re-verified against the code; what
  was wrong was only the implication that waiting longer would fix
  it — it cannot, because the server answers null every time.
- Longer polling alone cannot fix this. The server-side answer is
  null on every attempt, in fresh processes.

## Root-cause analysis — and its honest limit

Server side, SystemCallProvider.call (engine-build/Bcore/.../core/
system/SystemCallProvider.java:84-93) returns binderNull=true for
package_manager only, because ServiceManager.getService() returns
null for that name only. ServiceManager (engine-build/Bcore/.../
core/system/ServiceManager.java) builds its map once, in its
constructor: AM, JOB, PM, STORAGE, USER, XPOSED, ACCOUNT, LOCATION,
NOTIFICATION — each via that service's static get(). The AAR's
ServiceManager and BPackageManagerService bytecode were verified
identical to the analysed source, so the shipped engine really is
this code.

In this bytecode, a constructor-time put can store null for PM
only if BPackageManagerService.get() returned null at that moment,
and get() returns the static sService field, which is assigned in
<classinit> as `sService = new BPackageManagerService()`. A get()
call can therefore only observe null if it runs reentrantly on the
same thread while <clinit> is still running (sService not yet
assigned). Once that happens, the map keeps the null entry forever
— ServiceManager is a singleton that is never rebuilt — while every
other service works. That mechanism exactly matches the observed
state: PM null from the first request, all other services healthy,
deterministic across fresh processes.

What was NOT found, after checking the full constructor graph, is
the concrete reentrant call chain: BPackageManagerService's
constructor path is Settings() (file IO, all failures caught),
ComponentResolver() (empty), and registerReceiver(); AM's field
mPms = BPackageManagerService.get() initialises PM cleanly before
ServiceManager's PM put; BEnvironment/SharedUserSetting contain no
service or startup calls; the client asks for exactly
"package_manager" (BPackageManager.getServiceName() returns
ServiceManager.PACKAGE_MANAGER); the host APK has a single dex and
a single ServiceManager copy. So the root cause is NOT proven to
the code level. The two leading candidates are:

1. (Mechanism above) A same-thread reentrant get() during PM class
   initialisation poisoned the ServiceManager map entry at
   construction time. The reentrant trigger was not located in
   static analysis.
2. PM class initialisation itself is poisoned in the :black process
   on this phone (e.g. its constructor's registerReceiver or a
   static-init step failing in a way that leaves the class
   unusable), with the map entry being a downstream symptom rather
   than the cause. This is inconsistent with AM working only if
   the poisoning is PM-specific, which is possible because PM is
   the only service whose constructor does file-backed Settings
   loading plus a receiver registration.

The single check that distinguishes them is in the next phone log:
v15's repair logs "Lazy repair: rebuilt null entry for
package_manager" (candidate 1: repair fires, launch then works) or
"Lazy repair failed for package_manager" with a
NoClassDefFoundError / initialisation exception (candidate 2: the
singleton itself cannot be created). The parent must ask for that
log line after the user tests v15.

## The fix (v15) — targeted repair, not a guessed rewrite

Engine, ServiceManager.getServiceInternal() (new bcore-v4l.aar,
built from engine-build/Bcore with only this change):

- If the cached entry for a name is null, do NOT return it
  blindly. Reconstruct that service directly from its static
  singleton (PM via BPackageManagerService.get(), likewise for the
  other eight services) and cache the result. By the time a VM call
  arrives, class initialisation has finished, so in candidate 1
  get() returns the healthy singleton and the launch path works
  again. If candidate 2 holds, get() throws; the repair catches
  Throwable and returns null, preserving the old behaviour
  exactly — strictly non-regressing for this failure mode.
- The repair only fires on a null entry; a healthy map is
  untouched. It logs its outcome under the ServiceManager tag so
  the next phone log proves which candidate holds.

Host v15 is otherwise byte-identical to v14 (verified by diff of
host-src and host-res): same 3-tab layout (Clone / Notes /
Utilities), circular header logo, launch path with the v12 safety
rules intact (NEVER install when the virtual list is not provably
ready; NEVER show a raw Java exception on the main screen — short
VI/EN message only, detail to logs), same engine behaviour apart
from the repair, VPN UI-only, updater and clone data format
unchanged. Only the manifest version changed (26 / 0.8.5-pmfix)
and the host build now consumes bcore-v4l.aar.

## What v15 does NOT fix / does NOT prove

- It does not prove candidate 1. If the next phone log shows the
  repair failing, the fix did not address the true cause and the
  next step is to chase the PM class-initialisation failure
  itself (its constructor's registerReceiver/context path on this
  device), not to poll longer.
- It does not change virtual package data, does not reinstall any
  guest, and does not touch clone logins.

## Phone testing that must prove it (do not claim as tested)

- After a fresh open, tapping clone 1 AND clone 2 launches Muse,
  without reinstalling and without losing logins.
- The next filtered log contains either "Lazy repair: rebuilt null
  entry for package_manager" (repair fired) or "Lazy repair failed
  for package_manager" (candidate 2) — report that line.
- Tab switching, Back behaviour, update checker, language switch,
  and clone survival through a v14 -> v15 update install (same
  signer, install over, never uninstall first).

## Sources

- engine-build/Bcore/.../core/system/ServiceManager.java (patched
  getServiceInternal; the ONLY engine source change)
- bcore-v4l.aar (rebuilt from engine-build/Bcore; patch verified
  in the AAR by strings: "Lazy repair: rebuilt null entry for")
- manual-aar/host-src-v15/ + host-res-v15/ (identical to v14)
- manual-aar/build-bcore-v4l.sh, manual-aar/build-host-v15.sh
  (host build consumes bcore-v4l.aar)
- manual-aar/host-manifest-v15-base.xml (versionCode 26,
  versionName 0.8.5-pmfix)
