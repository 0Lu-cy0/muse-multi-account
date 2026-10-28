# BUILD_V12 — launch-crash fix + screenshot UI fixes (0.8.2-launchfix, versionCode 23)

## The error, as seen by the user (v11, 2026-10-28 10:50, after updating to v11)
The main screen's status line showed, wrapped over five lines and
pushing the clone list down:
"Mở thất bại: Engine launch báo lỗi: NullPointerException:
Attempt to invoke interface method
'top.niunaijun.blackbox.entity.pm.InstallResult
top.niunaijun.blackbox.core.system.pm.IBPackageManagerService.
installPackageAsUser(java.lang.String,
top.niunaijun.blackbox.entity.pm.InstallOption, int)' on a null
object reference" — with 2 clones, both active, com.facebook.aura
12.0.0.14.169. Those clones already existed and worked in v10,
so this was not a missing guest.

## Verified root-cause chain (read in code, not guessed)
1. `EngineLauncher.launchClone` (v11) checked the virtual list
   with `BlackBoxCore.getBPackageManager().getInstalledPackagesAsUser(userId)`.
   On any exception it caught it, logged "treating as not
   installed", and continued as if the guest were not installed.
2. It then called `BlackBoxCore.get().installPackageAsUser(apk, userId)`.
   Inside the engine, `BPackageManager.installPackageAsUser`
   calls `getService().installPackageAsUser(...)`; when the
   engine package-manager service is unavailable, `getService()`
   returns null, so the call is made on a null object — the NPE
   in the screenshot.
3. The outer catch in `launchClone` then surfaced the exception
   verbatim via the `engine_launch_threw` string (class name +
   message, including the full method signature), and
   `MainActivity` prefixed it with "Mở thất bại:\n" into an
   uncapped status line — hence the five-line dump.
4. Engine-side detail that makes the retry design work: in
   `BPackageManager` (engine-build), `getInstalledPackagesAsUser`
   catches only `RemoteException` and returns an empty list on
   it, so a RemoteException alone looks exactly like an empty
   virtual user. And in `BlackManager`, `getService()` is public
   and returns null while the binder is unavailable or a recent
   service creation failed (its internal retry gate is 2 seconds).
   There is NO dedicated `isReady()` API in the engine — that
   absence is stated plainly here, it is why the host polls.

## The fix (launchClone, host layer only — engine bcore-v4k.aar unchanged)
- A virtual-list check failure (exception, or a null list) is
  now SERVICE UNAVAILABLE, never "not installed". No install
  is attempted in that case, so the v11 NPE path is closed.
- After `ensureAttached`, the host polls for readiness, off
  the UI thread (launch already runs on a worker thread in
  `MainActivity.launchCloneRow`, verified and kept that way):
  up to 5 attempts, 1 second apart, each attempt probing
  `getBPackageManager().getService()` first and only trusting
  a list obtained without an exception while the probe was
  non-null. The 1-second spacing matters because of the
  engine's 2-second creation-failure gate: polls retry service
  creation at about 2s and 4s. Every attempt is logged to
  FileLogger/logcat.
- Only if a list is obtained AND does not contain the guest
  does the code call `installPackageAsUser`, and that call is
  now itself wrapped: a throw, or a non-success result, returns
  a short message (below) with the full detail logged only.
- The GMS check no longer installs GMS off a failed second
  list: if the second virtual-list call throws, GMS install is
  skipped and launch continues (GMS was already best-effort).
- Short messages, both languages, full detail to logs only:
  service-not-ready: "Không mở được clone — dịch vụ engine
  chưa sẵn sàng, thử lại sau vài giây" / "Could not open
  this clone — the engine service is not ready yet, please
  try again in a few seconds"; generic launch failure:
  "Không mở được clone, thử lại sau vài giây" / "Could not
  open this clone, please try again in a few seconds";
  install failure: "Không cài được clone vào engine, thử
  lại sau vài giây" / "Could not install this clone into
  the engine, please try again in a few seconds". Delete and
  update engine paths got the same treatment (short UI
  message, full exception logged). The old exception-bearing
  string keys (`engine_launch_threw`, `engine_install_failed`,
  `engine_delete_threw`, `engine_delete_failed`,
  `engine_update_threw`) are removed; 6 new keys added
  (the 3 above + delete/update short keys + the row-open
  label), string-key sets verified identical in Vietnamese
  and English with no duplicates.
- Status-line cap, defence in depth: `statusText` is now
  `maxLines="2"` with end-ellipsis in the layout, so no
  failure can ever again push the clone list down with a
  wrapped dump. A launch failure is also cleared on the next
  return to the app (`onResume` clears a stale
  launch-failure status after refresh); a launch success
  already replaces it in `launchCloneRow`. Scope note, stated
  honestly: the launch/install/delete/update engine paths
  are sanitized; other handlers (import, create, toggle) still
  append an exception *message* (not a class/signature) to
  the status line, and the 2-line cap is what bounds them —
  they were not in the reported bug and were not rewritten
  in v12.

## UI fixes from the same screenshot (same build, no new features)
a. VPN strip expand control: was a huge full-width orange
   (`btn_accent`) block dominating the strip. Now a small
   secondary-style control (`btn_secondary`, wrap-content,
   aligned to the end, smaller padding, no all-caps), so the
   one-line summary is the strip's main content again. The
   strip still collapses to one line by default and is still
   UI-only/disabled.
b. Language toggle: was clipped/pale and detached at the
   top-right. Now explicit padding (12dp sides, 6dp vertical),
   an 8dp start margin, `minHeight`/`minWidth` 0, 14sp,
   `textAllCaps="false"` (so it shows "English" /
   "Tiếng Việt" as labelled, not a squeezed all-caps block),
   and the title is single-line ellipsized so it can never
   squeeze or clip the button.
c. Row launch cue: nothing on a card said tapping it
   launches. Each row now carries a small "Mở ▸" /
   "Open ▸" label (teal, 14sp) next to the expand chevron.
   It is NOT clickable and NOT another stacked button: taps
   on it still launch via the row's own click handler, and
   the chevron keeps its separate expand handler.

## Unchanged
- Engine bcore-v4k.aar as-is; same shared debug signer
  (certificate SHA-256 matches v11, so the APK installs over
  v11 without uninstalling); no new permissions (v12 manifest
  base differs from v11 only in versionCode/versionName);
  main-screen order (title + language toggle, collapsed VPN
  strip, + add, clone-count header, clone list, collapsed
  Notes); tap-row-to-launch; long-press menu
  (rename/active-idle/delete); expanded row (bubble /
  fast-switch / per-clone VPN UI-only-disabled / update);
  language toggle and strings; VPN behaviour (UI-only,
  disabled, never fake Connected/IP); bubbles/fast-switch
  behaviour; updater logic; clone data format (rename via
  `CloneEntry.withName` stays).

## Important limitations, stated honestly
Device verification is still UNPROVEN and must not be claimed
as tested: whether a tap-to-launch now succeeds on the user's
phone after a fresh open (the actual v11 crash scenario),
whether a retry after a service-not-ready failure succeeds
without reinstalling, whether the 5x1s poll is long enough
on a slow first launch (if the service takes longer than
~4s to become ready, the user still gets the short
service-not-ready message and must tap again — that is the
designed, honest outcome, not a silent install attempt),
rename persistence and language switching after this
rebuild, and clone survival through a v11 -> v12 update
install. The build below proves compilation, signing and
versioning only. This build does not publish a release or
update updates.json; the parent handles delivery/release.

## Sources
- `manual-aar/host-src-v12/` (EngineLauncher.java rewritten
  launch path, MainActivity.java row cue + onResume status
  clearing, other activities unchanged except string keys)
- `manual-aar/host-res-v12/` (layout/activity_main.xml:
  VPN collapse control, language toggle, status cap;
  values/strings.xml + values-en/strings.xml)
- `manual-aar/build-host-v12.sh`,
  `manual-aar/host-manifest-v12-base.xml` (versionCode 23,
  versionName 0.8.2-launchfix), `manual-aar/merge-manifest-v12.py`
- `~/workspace/research_notes/muse-clone-ui-layout-references-20261027-1441/report.md`
  (§6 v12 note)
