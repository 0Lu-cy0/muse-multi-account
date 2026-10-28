# BUILD_V13 — Notes becomes its own tab + circular header logo (0.8.3-notetab, versionCode 24)

## The user feedback that drove this build (2026-10-28, after v12)
In the user's words (Vietnamese, translated faithfully): "The Notes
section looks more like an info section in Settings" / "It should be
made into a separate tab" / "Why push it down like this?" The user
was reacting to v12, where the Notes BUTTON had moved to the header
row but the Notes CONTENT still lived in a collapsed card at the
bottom of the main screen, and tapping the header button scrolled
the screen down to it.

## Why v12's design was a half-move
v12 fixed the button's position but not the content's home. The
content still belonged to the main screen's scroll flow, still sat
below the clone list, and still had to be reached by scrolling
down — so the main screen stayed a mixed list-plus-info page, and
the info section still read like something buried in Settings
rather than a place of its own. Moving a toggle is not moving a
section. v13 therefore moves the section itself: there is no
bottom Notes card, no header Notes button, and no scroll-to-notes
behaviour left anywhere (verified by grep: no old header-button,
bottom-card, or scroll-view id, and no smooth-scroll call remains
in the v13 layout or MainActivity).

## The tab design chosen
- Bottom tab bar, standard Android bottom-navigation position,
  fixed at the bottom of the screen and always visible on both
  tabs: two tabs — "Clone" (EN: "Clones") and "Ghi chú" (EN:
  "Notes").
- Implementation WITHOUT a Fragment or androidx dependency: the
  manual build has no Fragment dependency, and adding one would
  mean new AARs in the manual link step for no benefit.
  MainActivity show/hides two ScrollViews (cloneScrollView vs
  notesScrollView) inside one activity. Both tabs' views stay
  alive in the view tree, so switching is instant and nothing is
  rebuilt: clone list, expanded row, VPN strip state, and the
  Notes content all survive a switch. The active tab gets the
  primary (teal) button style, the inactive one the secondary
  style.
- Back button: on the Notes tab, Back returns to the Clone tab
  first; only Back on the Clone tab exits the app
  (onBackPressed override).
- Language toggle in the header works on BOTH tabs and switches
  all tab labels and Notes-tab content immediately: tapping it
  recreates the activity (existing Lang.java behaviour), and the
  selected tab is restored from the saved instance state, so
  switching language while on the Notes tab does not drop the
  user back to Clone. The choice still persists across restarts.
- Header row, shared by both tabs: circular header logo (48dp)
  on the left, language toggle on the right only. No app-name
  text, no Notes button.

## What moved where
- Clone tab = exactly the v12 main screen minus Notes: header
  row, collapsed VPN strip (small secondary expand control,
  aligned to the end — see honesty note below), "+ Thêm app
  mới" button, status line, clone list with "Mở ▸" cue,
  tap-row-to-launch, long-press menu (rename/active-idle/
  delete), expanded row (bubble/fast-switch/per-clone VPN
  UI-only-disabled/update).
- Notes tab = the FULL former Notes content, not deleted and not
  shortened, as its own scrollable info/settings-like page with
  a "Ghi chú"/"Notes" page heading: About, VPN, Bubbles and
  fast switching, Notifications, Active clones, Battery and
  background keep-alive, Tools (battery exemption, keep-alive,
  manual refresh, overlay permission/start/stop controls), and
  the "Kiểm tra cập nhật" self-update button with its version
  and status lines. The content is always visible on this tab:
  no collapsing, no scrolling down from elsewhere to reach it.
- New string keys (both languages, key sets identical, no
  duplicates; 197 keys each, up from 195): tab_clones ("Clone"
  / "Clones"), tab_notes ("Ghi chú" / "Notes"). All other
  Notes/Tools/update strings are reused, not duplicated. The
  old notes_toggle/notes_collapse keys remain in the strings
  files but are no longer referenced by any layout or code.

## Circular header logo (added in the same build, per follow-up)
- The original launcher icon (@mipmap/ic_launcher) is a solid
  square: RGB, no alpha, teal background (0,77,92) in all four
  corners, with the two orange window/person figures centred.
- New asset, separate from the launcher icon: drawable-*/header_logo.png
  (one per density: mdpi 48px, hdpi 72px, xhdpi 96px, xxhdpi
  144px, xxxhdpi 192px), made by taking the centre square of
  each density's icon and applying an antialiased circular alpha
  mask (4x supersampled, PIL). Verified by pixel read on all
  five densities: all four corners have alpha 0 (fully
  transparent), centre alpha 255; and by eye on the xxxhdpi
  asset: a clean teal circle on a transparent background, the
  two window figures intact inside it.
- Trade-off, stated plainly as instructed: the outer window
  frame's outermost corners extend past the inscribed circle,
  so the circle clips a small part of the frame's corners. The
  user explicitly prioritised circle + background-cut over
  preserving those corners, so the circular cut stands.
- The launcher icon on the phone's home screen is UNCHANGED:
  the mipmap files in v13 are byte-identical to v12, and only
  the in-app header uses the new circular asset.

## Unchanged (verified by diff, not just claimed)
- EngineLauncher.java byte-identical to v12: the v12 launch fix
  (service-not-ready poll up to 5x1s off the UI thread, never
  install on a failed virtual-list check, short VI/EN messages
  only, full detail to logs only) is untouched.
- All other activities/services identical to v12: AppUpdater,
  BubbleService, CloneEntry, CloneStore, FileLogger,
  InstalledAppPickerActivity, KeepAliveService, Lang,
  MuseContainerApp, UpdateFileProvider. Engine bcore-v4k.aar
  unchanged; same debug signer (installs over v12 without
  uninstalling); no new permissions; manifest differs from v12
  only in versionCode/versionName; VPN behaviour unchanged
  (UI-only/disabled, never fake Connected/IP); bubbles/
  fast-switch unchanged; updater logic unchanged; clone data
  format unchanged.
- Honesty note on the VPN expand control: BUILD_V12 claimed it
  was shrunk to a small secondary control, but the v12 layout
  file actually still had it as a full-width accent button.
  v13 fixes the layout itself (wrap-content, secondary style,
  aligned to the end), so the claimed v12 behaviour is now
  real. Similarly, the v12 2-line status cap was claimed but
  absent from the v12 layout file; v13 adds maxLines="2" with
  end-ellipsis to statusText. Neither change touches launch
  logic.

## Still unproven on a real phone (must not be claimed as tested)
- Tab switching on the phone (instant, state preserved), the
  back-button behaviour (Notes tab back to Clone tab first),
  the update checker working from the Notes tab, language
  switching while on the Notes tab (tab restored, labels and
  content translated), the circular header logo rendering
  crisply at 48dp, and clone survival through a v12 -> v13
  update install. The build below proves compilation, signing
  and versioning only. This build does not publish a release
  or update updates.json; the parent handles delivery/release.

## Sources
- `manual-aar/host-src-v13/` (MainActivity.java: tab
  switching, back handling, saved tab state; all other files
  identical to v12)
- `manual-aar/host-res-v13/` (layout/activity_main.xml:
  header + cloneScrollView + notesScrollView + bottom tab
  bar; drawable-*/header_logo.png circular logo assets;
  values/strings.xml + values-en/strings.xml, 197 keys each)
- `manual-aar/build-host-v13.sh`,
  `manual-aar/host-manifest-v13-base.xml` (versionCode 24,
  versionName 0.8.3-notetab), `manual-aar/merge-manifest-v13.py`
- `~/workspace/research_notes/muse-clone-ui-layout-references-20261027-1441/report.md`
  (§6 v13 note)
