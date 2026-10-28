# BUILD_V14 — separate Utilities tab (0.8.4-utiltab, versionCode 25)

## The user decision that drove this build (2026-10-28, after v13)
In the user's words (Vietnamese, translated faithfully): "The
bubble/fast-switch and control part should be split out into
Utilities." When asked whether that meant a third tab or a
separate section inside the Notes tab, the user tapped the
option: "Làm tab Tiện ích riêng" — make a SEPARATE Utilities
tab. So v14 does not add a section; it adds a tab.

## Why the split was needed
In v13 the Notes tab held two different kinds of content at
once: explanatory text (what bubbles, VPN, notifications,
battery are and how they behave) and actionable controls
(permission buttons, service start/stop, battery exemption,
keep-alive, refresh, update check). Reading about a feature
and operating it are different jobs, and mixing them made the
Notes tab read like a settings page with buttons scattered
through the prose — the same complaint that drove v13's tab
split in the first place, one level down. v14 finishes the
separation: information stays information, controls live
together in one place built for operating them.

## The three-tab design
- Bottom tab bar, fixed at the bottom, always visible on all
  three tabs: "Clone" (EN: "Clones"), "Ghi chú" (EN: "Notes"),
  "Tiện ích" (EN: "Tools").
- Implementation unchanged from v13: NO Fragment or androidx
  dependency. MainActivity show/hides three ScrollViews
  (cloneScrollView / notesScrollView / utilsScrollView) in
  one activity, so all three tabs' views stay alive, switching
  is instant, and nothing is rebuilt: clone list, expanded
  row, VPN strip state, and both info and utility contents
  survive a switch. The active tab gets the primary (teal)
  button style, the inactive ones secondary.
- Back button: on the Notes tab OR the Utilities tab, Back
  returns to the Clone tab first; only Back on the Clone tab
  exits the app.
- Language toggle in the header works on ALL three tabs and
  switches tab labels and all tab content immediately: the
  selected tab is saved as an integer tab_index in
  onSaveInstanceState (with a fallback to the old v13 boolean
  for states saved by v13), so switching language while on
  the Utilities tab does not drop the user back to Clone. The
  choice still persists across restarts (Lang.java unchanged).

## What moved from Notes to Utilities (exact list)
Every actionable control that was on the Notes tab moved to
the Utilities tab, behaviour unchanged — same listeners, same
intents, same strings:
- Overlay permission button (opens the overlay-permission
  Settings page, or reports already granted)
- Start overlay button (starts BubbleService — the bubble +
  fast-switch service)
- Stop overlay button (stops BubbleService)
- Battery-exemption button (requestBatteryExemption)
- Keep-alive button (startKeepAlive)
- "Kiểm tra cập nhật" self-update button, with its version
  line and status line (updater logic unchanged: manual only,
  updates.json first, Releases fallback, SHA-256 check, system
  installer confirmation, no silent install)
- Refresh-clone-list button (refreshList)

Utilities tab grouping (headings reused from existing
strings, no new heading keys): "Bong bóng và chuyển nhanh —
điều khiển" for the three overlay buttons; "Pin và giữ chạy
nền" for battery + keep-alive; "Cập nhật ứng dụng" for the
version line + update button + status line. The refresh
button belongs to no section above, so it sits at the bottom
of the Utilities tab as a standalone action; its own label
("Làm mới danh sách clone") says what it does.

## What stayed on Notes (info-only text)
The Notes tab now contains NO buttons, toggles, or
permission/start/stop controls at all (verified: no button
id remains in the Notes section of the layout, and the only
programmatic addView into notesContent is the overlay status
line). What remains, unchanged and not shortened:
- "Về app này" text, "VPN" text, "Bong bóng và chuyển nhanh"
  text, "Thông báo" text, "Clone đang hoạt động" text, "Pin
  và giữ chạy nền" text
- The overlay status line (overlayExplainView), refreshed in
  onResume — a status line, not a control

## The per-clone row toggle exception (stated explicitly)
The per-clone Bubble toggle and per-clone Fast-switch toggle
inside an EXPANDED CLONE ROW on the Clone tab STAY where they
are. They are per-clone row controls (they save a preference
for that one clone and refresh the overlay if it is running),
not the global bubble/fast-switch control section the user
asked to move, and moving them to Utilities would have
separated a clone's switches from the clone itself. The
per-clone VPN switch (UI-only, disabled) in the same expanded
row is likewise untouched.

## Unchanged (verified by diff, not just claimed)
- Header: circular transparent header_logo (48dp) left,
  language toggle right only, no app-name text, no header
  Notes button. Launcher icon on the home screen unchanged.
- Clone tab: collapsed VPN strip with the small secondary
  expand control, "+ Thêm app mới", 2-line-capped status
  line, clone list with "Mở ▸" cue, tap-row-to-launch,
  long-press menu (rename/active-idle/delete), expanded row
  (per-clone bubble/fast-switch toggles, per-clone VPN
  UI-only-disabled, update).
- EngineLauncher.java byte-identical to v13: the v12 launch
  fix (service-not-ready poll up to 5x1s off the UI thread,
  never install on a failed virtual-list check, short VI/EN
  messages only, full detail to logs only) is untouched.
- All other activities/services identical to v13. Engine
  bcore-v4k.aar unchanged; same debug signer (installs over
  v13 without uninstalling); no new permissions; manifest
  differs from v13 only in versionCode/versionName; VPN
  behaviour unchanged (UI-only/disabled, never fake
  Connected/IP); updater logic unchanged; clone data format
  unchanged.
- String keys: exactly ONE new key in both languages —
  tab_tools ("Tiện ích" / "Tools"). Both files now have 199
  keys, key sets identical, no duplicates. All control and
  heading strings are reused, not duplicated. One string DID
  become unreferenced by this move, noted here rather than
  silently deleted: notes_tools_title (the old "Tiện ích"
  heading inside the Notes tab) is no longer referenced by
  any layout or code in v14, because the Utilities tab's
  sections reuse notes_overlay_title / notes_battery_title /
  notes_update_title as their headings. It was left in the
  files, like the two notes_toggle/notes_collapse keys from
  v13 that remain unreferenced as before.

## Still unproven on a real phone (must not be claimed as tested)
- Three-tab switching on the phone (instant, state
  preserved across all three tabs), the back-button behaviour
  from the Utilities tab (back to Clone first), the update
  checker working from the Utilities tab, language switching
  while on the Utilities tab (tab restored, labels and
  content translated), and clone survival through a
  v13 -> v14 update install. The build below proves
  compilation, signing and versioning only. This build does
  not publish a release or update updates.json; the parent
  handles delivery/release.

## Sources
- `manual-aar/host-src-v14/` (MainActivity.java: three-tab
  switching, back handling, saved tab index, overlay buttons
  moved to utilsOverlayContainer; all other files identical
  to v13)
- `manual-aar/host-res-v14/` (layout/activity_main.xml:
  header + cloneScrollView + notesScrollView + utilsScrollView
  + three-button tab bar; values/strings.xml +
  values-en/strings.xml, 199 keys each)
- `manual-aar/build-host-v14.sh`,
  `manual-aar/host-manifest-v14-base.xml` (versionCode 25,
  versionName 0.8.4-utiltab), `manual-aar/merge-manifest-v14.py`
- `~/workspace/research_notes/muse-clone-ui-layout-references-20261027-1441/report.md`
  (§6 v14 note)
