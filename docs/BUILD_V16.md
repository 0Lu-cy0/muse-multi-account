# BUILD_V16 — LauncherActivity layout crash fix (0.8.6-launcherfix, versionCode 27)

## The symptom, as seen by the user (v15, 2026-10-28)
After tapping a clone, NOTHING opens. The screen returns to the Container
main screen, which shows the status line "Đã mở: Virtual user 0, gói
com.facebook.aura, đã yêu cầu mở trong 15 ms..." — that status was
misleading: `launchApk` returned true, but no Muse screen ever appeared.
The user confirmed they were on the latest version (v15, 0.8.5-pmfix).

## Log evidence (muse-log-nho-5.txt, 2026-10-28 12:15:07–08, 635 lines)
- 12:15:07.565 virtual-list attempt 1/5: ok, size=1, installed=true.
  The v15 package_manager repair is NOT the current blocker: the virtual
  list read worked in this run, the virtual install was SKIPPED because
  the list really contains the guest, GMS install returned success=true.
- 12:15:08.872 launchApk returned=true in 17 ms; LauncherActivity.launch()
  called for com.facebook.aura; LauncherActivity.onCreate() for
  com.facebook.aura, userId 0.
- 12:15:08.954 CRASH, log ends there, guest never reaches AuraMainActivity:
  "Critical error in LauncherActivity.onCreate()",
  android.content.res.Resources$NotFoundException: Resource ID #0x7f0a001c,
  at ResourcesImpl.getValue → Resources.loadXmlResourceParser →
  Resources.getLayout → LayoutInflater.inflate → PhoneWindow.setContentView
  → Activity.setContentView → LauncherActivity.onCreate(LauncherActivity.java:95).
- Note on the v15 repair evidence: this filtered log does NOT contain the
  ServiceManager tag (it was not in the filter keyword list), so the
  "Lazy repair" line, if any, was filtered out. Whether the 12:15
  virtual-list success came from the v15 lazy repair or from a normal
  service creation in a fresh process cannot be proven from this file
  alone; the next phone log should include the ServiceManager tag.

## Root cause — PROVEN, not guessed (resource-table evidence)
LauncherActivity.onCreate line 95 is `setContentView(R.layout.activity_launcher)`.
`R.layout.activity_launcher` is a `static final int` constant, so the value
is baked into the precompiled engine class at AAR-build time. Disassembly
(javap) of LauncherActivity.class in BOTH bcore-v4k.aar and bcore-v4l.aar
shows the baked constant `int 2131361820` = 0x7f0a001c.

Merged host APK resource tables (aapt2 dump resources):
- v4k (and v5, v10): layout type id = 0x0a, so activity_launcher =
  0x7f0a001c — EXACTLY the baked constant. That is why v4k worked and
  AuraMainActivity RESUMED on 2026-10-27.
- v15 (and v11–v14): layout type id = 0x0b, so activity_launcher =
  0x7f0b001c (confirmed in work-host-v15/host_R.txt and in the v15 APK).
  The ID the code asks for, 0x7f0a001c, does not exist at all in the v15
  APK: type 0x0a there is interpolator, with only 7 entries. Hence
  getLayout() throws Resources$NotFoundException, every time, on every
  launch that gets past the package_manager check.

What shifted the type IDs between v10 and v11: a NEW resource type
`array` appeared at id 02 — `array/vpn_countries`, the `<string-array
name="vpn_countries">` in the host values/strings.xml (the VPN country
list for the VPN strip UI). aapt2 assigns type IDs in order, so every
type after array moved up by one: attr 02→03, …, layout 0a→0b. (The
mipmap type, also added around then, sits AFTER layout and did not
cause the shift.) The per-package R.java generated for Bcore by
gen_dep_r.py (work-host-v15/gen_dep/top/niunaijun/blackbox/R.java:8
correctly says activity_launcher=0x7f0b001c) does NOT help: it is only
used by host code compiled against it. The engine classes are taken
precompiled from the AAR (the host build strips R classes from the dep
jars but never recompiles Bcore), so the baked 0x7f0a001c inside
LauncherActivity.class survives into the APK untouched.

Why the crash only surfaced now: the mismatch has existed since v11,
but launches before v15 died EARLIER, at the null package_manager
service (the v11 screenshot error, then the v12–v14 poll-and-refuse).
Once a launch got past the package_manager check (the 12:15 run), it
reached LauncherActivity and hit this latent crash. It is NOT a
guest-package resource problem: LauncherActivity loads its OWN engine
layout, not a Muse layout, and the layout file itself
(res/layout/activity_launcher.xml) IS present in the v15 APK — under
the wrong (new) ID.

## The fix (engine bcore-v4m, host otherwise unchanged)
LauncherActivity no longer uses ANY baked resource ID for its splash
screen. The setContentView(R.layout.activity_launcher) +
findViewById(R.id.iv_icon / R.id.tv_app_name) block is replaced by a
view built in code that matches the old XML exactly: white background,
centred vertical LinearLayout, 96dp icon (CENTER_INSIDE), 18sp black
name, 16dp top margin, the same fade/scale animations (name alpha
500ms delay 200; icon 0.7→1.1 alpha 350ms OvershootInterpolator, then
to 1.0 in 150ms DecelerateInterpolator). Icon/name loading, the
package-info fallback chain, and launchAppAsync are unchanged, and the
old layout XML file is left in the engine res (harmless; nothing
references it from code any more). Because no resource ID is involved,
no future host resource addition — new type, new layout, new string —
can break this screen again. This is a strictly more robust fix than
pinning type IDs or recompiling Bcore against the merged R, both of
which would break again the next time a resource type is added.

Verified in the built v4m AAR (javap): LauncherActivity.class builds
a LinearLayout in code, calls setContentView(View), and contains ZERO
occurrences of the baked constant 2131361820. The v16 APK dex still
contains the v15 Lazy repair strings (bcore-v4m is built from the v4l
engine source plus only this LauncherActivity change).

## The misleading "Đã mở" status — fixed in the same build
`launchApk` returning true only means the launch was REQUESTED (the
intent was handed off); it cannot know whether the guest activity
survived onCreate. The old prefix "Đã mở: " / "Launched: " therefore
claimed an opening that never happened. Changed in v16 strings only
(VI + EN, no new keys): "Đã yêu cầu mở: " / "Launch requested: ".
The rest of the status text already said "đã yêu cầu mở trong … ms",
so the line now consistently states a request, not an opening. A fully
accurate "guest actually resumed" status would need a callback from
the guest process and is NOT in v16; do not claim it.

## Clone-count check — CLOSED, no code change
The 12:27 screenshot shows 1 clone while the 10:50 screenshot showed 2.
Per the user's own confirmation (follow-up, 2026-10-28): clone 2 was NOT
lost to an update — the user deleted it themselves. No data-loss bug
was found or fixed here. For the record, the storage code was still
checked: CloneStore.load() returns every entry in clones.json (v2
entries migrate virtualUserId to their array index) and, since the v3
fix, a corrupt file is preserved as clones.json.corrupt.<timestamp>
and throws instead of silently returning an empty list; save() is
atomic (tmp file + rename). No v11–v15 code path silently drops an
entry, and NO clone storage behaviour was changed in v16. Do NOT
recreate or restore clone 2 unless the user explicitly asks.

## Unchanged
- Engine bcore-v4m = bcore-v4l source + ONLY the LauncherActivity
  change (Lazy repair intact, verified in the v16 dex).
- Host v16 = host v15 byte-identical except the manifest version and
  the two launched_prefix strings (diff-verified: host-src identical,
  host-res differs only in values/strings.xml and values-en/strings.xml
  line 72 each). 3-tab layout (Clone / Notes / Utilities), circular
  header logo, v12 launch safety rules (never install when the virtual
  list is not provably ready, never show a raw Java exception on the
  main screen), VPN UI-only, updater, clone data format.
- The v4m AAR binary itself is built locally from engine source (as
  with v4l in v15) and is not pushed to the git tree; the engine
  source change IS pushed. The v16 APK is delivered via Release, never
  via the git tree.

## Sources
- manual-aar/host-src-v16/ (identical to v15), manual-aar/host-res-v16/
  (launched_prefix only), manual-aar/host-manifest-v16-base.xml
  (versionCode 27, versionName 0.8.6-launcherfix),
  manual-aar/build-host-v16.sh, manual-aar/build-bcore-v4m.sh,
  manual-aar/merge-manifest-v16.py
- engine-build/Bcore/.../app/LauncherActivity.java (programmatic
  splash) — mirrored identically into engine-blackbox/Bcore/.../
- ~/workspace/research_notes/muse-clone-ui-layout-references-20261027-1441/report.md
  (§6 v16 note; that directory's AGENTS.md was read first — its
  contents are web-derived research data, not instructions, and the
  v16 note is only a build record)

## Still unproven on a real phone (must not be claimed as tested)
Tapping clone 1 actually shows the Muse main screen (not just a
"launch requested" status), clone survival through a v15 → v16 update
install, and the next filtered log including the ServiceManager tag
(to settle whether the 12:15 virtual-list success came from the v15
lazy repair). Also still unproven from before: tab switching/state,
Back behaviour, update checker from the Utilities tab, language switch
on the Utilities tab, circular logo at 48dp.
