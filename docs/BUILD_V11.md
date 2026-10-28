# BUILD_V11 — rebuilt main screen to match the report sample + language toggle (0.8.1-language, versionCode 22)

## The problem, owned honestly (v10 -> v11)
v10 was checked element-by-element (VPN strip present, "+" present,
Notes present) and called "following the report", while as a whole
it did not resemble §5 direction A of the UI layout reference
report. v10's main screen was a stack of numbered buttons
("2. Create named clone", "3. Request battery",
"4. Start keep-alive", "Refresh clone list") plus status plus list
plus Notes, and each clone row was a stack of separate buttons,
with no tap-to-launch, no long-press menu, no freeze/rename on the
row, no real app icon (letter only), no status dot. v11 rebuilds
the screen so the resemblance is checked as a whole. The language
toggle from the held v11 work is kept and finalised in the same
build; the translation strings are layout-independent, so the
rebuild did not require re-translation.

## What changed (v10 -> v11)
- Main screen order, top to bottom: title row (title + language
  toggle) -> compact collapsible VPN strip -> single prominent "+"
  add entry -> small clone-count header -> clone list (the main
  content) -> collapsed Notes section at the bottom.
- VPN strip: collapsed to its one-line summary by default on a
  fresh install (previously it started expanded). Expanding shows
  country / IP / state only. Still UI-only, connect DISABLED,
  never a fake Connected state or fake IP.
- Clone row redesign (direction A + B): the WHOLE row is tappable
  and launches that clone (engine launch still runs off the UI
  thread; the row is disabled while launching, as the old Launch
  button was). Row shows app icon when the package is installed,
  otherwise the existing letter tile as fallback; renamed clone
  label (bold); source package + version in small text; a status
  dot (green = active, grey = idle) + active/idle text. A chevron
  expands the row — one row expanded at a time — revealing Bubble
  toggle, Fast-switch toggle, per-clone VPN switch (still UI-only
  / disabled, with its honest note), and Update APK.
- Long-press on a row (new): a menu with Rename, Set active /
  Set idle, Delete. Rename edits the name in place via dialog and
  saves through CloneStore (new `CloneEntry.withName`; existing
  clones load unchanged, including the virtualUserId mapping).
  Set active / Set idle is the sample's freeze equivalent, with
  the max-4-active cap and its message unchanged. Delete is the
  existing flow, with the virtualUserId safeguard unchanged.
- Relocated, NOT deleted: (a) clone-name input + create now
  happens in a name dialog after picking an app ("Đặt tên clone"
  / "Name the clone", showing the picked package + version,
  default "Clone N"); (b) battery-exemption, keep-alive and
  manual refresh buttons moved into Notes under "Tiện ích" /
  "Tools"; (c) bubble/fast-switch permission, start and stop
  controls moved into Notes under their own heading (they were
  previously inserted into the main flow after the list). The
  list refreshes automatically in onResume and after every
  create/delete/toggle/rename mutation.
- Removed from the main flow: the numbered button stack
  ("2. Create named clone", "3. Battery", "4. Keep-alive",
  "Refresh clone list"), the main-flow name field, the per-row
  Launch button (row tap replaces it), and the per-row Set
  active/idle and Delete buttons (long-press menu replaces
  them). No function was deleted outright; each one has a new
  home listed above.
- Language toggle (from the held v11 work, finalised here):
  button at the top next to the title; label always shows the
  OTHER language ("English" in Vietnamese UI, "Tiếng Việt" in
  English UI); tapping saves the choice (SharedPreferences
  `app_settings`, key `app_language`, values vi|en, default vi)
  and recreates the activity so every string refreshes; the
  choice survives restarts. 170 original keys plus 24 new keys
  (name dialog, long-press menu, rename dialog, Notes tools and
  overlay buttons, updated empty-state and overlay texts) in
  both `values/strings.xml` (Vietnamese) and
  `values-en/strings.xml` (English) — 195 keys each, verified
  to match with no duplicates. The guest app is untouched.
- Reference reports updated (not rewritten): the main layout
  report gained §6 (language-toggle specification + rebuilt
  screen as built, with VI/EN sample text) and a v11 as-built
  note in the §5 direction-A row; the installed-app picker
  report gained a v11 as-built note in §1.5 (name dialog after
  picking). The bubble report needed no change: bubble toggles
  and labels are unchanged and still live in the expanded row.

## What matches the sample, and what honestly still differs
- Matches: clone list as the main content; tap row to launch;
  long-press menu with rename / active-idle (freeze) / delete;
  status dot + label; chevron-expanded row with per-clone
  controls; collapsible VPN strip on top; "+" add entry;
  language toggle always visible at the top.
- Still differs, stated so the next check is not fooled again:
  the "+" is a full-width top button, NOT a FAB (the manual
  build has no FAB dependency to pull in); rows use a letter
  tile when the app is not installed, because no installed icon
  exists to show; "freeze" is active/idle, not a separate freeze
  state; the long-press action is a dialog menu, not a system
  context menu. A FAB, a real icon for file-imported apps, and
  a separate freeze state would each need new work, not a
  relabel.

## Unchanged
- Engine bcore-v4k.aar as-is; same shared debug signer
  (certificate SHA-256 matches v10, so the APK installs over
  v10 without uninstalling); no new permissions (v11 manifest
  base differs from v10 only in versionCode/versionName);
  VPN behaviour (UI-only, disabled); bubbles/fast-switch
  behaviour; updater logic (manual check inside Notes only);
  clone data format except the name field, which already
  existed; clone create/delete/update engine paths.

## Important limitations, stated honestly
Device verification is still UNPROVEN and must not be claimed
as tested: tap-to-launch on a real row, long-press menu,
rename persistence across restarts, language switching after
the rebuild, list auto-refresh on return, and clone survival
through an update install from v10 to v11. The build below
proves compilation, signing and versioning only. The v10
updater limitation also still applies: a real download-and-
install test through the in-app updater needs a published
v11 release + correct updates.json, which the parent handles
at delivery time (this build does not publish a release or
update updates.json).

## Sources
- `manual-aar/host-src-v11/` (MainActivity.java rebuilt,
  CloneEntry.java +withName, Lang.java unchanged from the
  held v11 work, other activities unchanged except string
  keys)
- `manual-aar/host-res-v11/` (layout/activity_main.xml
  rebuilt, values/strings.xml + values-en/strings.xml)
- `manual-aar/build-host-v11.sh`,
  `manual-aar/host-manifest-v11-base.xml` (versionCode 22,
  versionName 0.8.1-language), `manual-aar/merge-manifest-v11.py`
- `~/workspace/research_notes/muse-clone-ui-layout-references-20261027-1441/report.md`
  (§6 added, §5 direction-A note)
- `~/workspace/research_notes/muse-installed-app-picker-ui-references-20261027-1630/report.md`
  (§1.5 v11 note)
