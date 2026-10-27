# BUILD_V9 — tidy main screen + report-faithful "+" add entry (0.7.1-tidy)

- APK: `muse-container-host-v9-debug.apk`, 1,765,085 bytes,
  SHA-256 `4485e0b6090bfd74345bec1dfeeeb30e891dd685dc420ecf367a29b07928f8e7`,
  versionCode 20, versionName `0.7.1-tidy`, package `com.example.musecontainer`,
  minSdk 29 / targetSdk 34, arm64-v8a, apksigner verify OK, signer SHA-256
  `f1fb90f5684f6b17979c9f9079843b6bbcba135db22715029d007c7321e14b70`
  (same shared debug signer as v2–v8, so `adb install -r` updates in place).
- Base: v8 sources COPIED to `manual-aar/host-src-v9/` (Java) and
  `manual-aar/host-res-v9/` (resources, copied from `app/src/main/res` so
  the shared v8 layout is untouched). No v8 file was edited: v8 sources
  keep their old mtimes and the v8 APK still hashes to `8f13e72a…`.
  Build: `manual-aar/build-host-v9.sh`, manifest `host-manifest-v9-base.xml`,
  merge script `merge-manifest-v9.py`. Engine `bcore-v4k.aar` reused as-is.
  Guest APK never bundled/modified/re-signed. Icon unchanged from v8.

## What changed (report fidelity + tidy-up)

1. "+" add entry (report section 09 two-path flow, Parallel Space "+"
   pattern): the old numbered buttons "1. Pick / import user-supplied
   APK" and "1b. Pick installed app" are REMOVED from the layout and
   from code. One prominent teal button "+ Thêm app mới" (18sp) sits
   right after the VPN strip card. Tapping it opens a choice dialog
   ("Thêm app mới") with two items: "App đã cài trên điện thoại"
   (InstalledAppPickerActivity, P1, same intent/result code as v6/v8)
   and "File APK" (ACTION_OPEN_DOCUMENT, same as before). Both
   underlying functions, onActivityResult handling, split warning and
   APK fallback are unchanged; only the entry point changed.
2. Notes section: ONE collapsible "Ghi chú ▾" card at the bottom of the
   main screen (after the clone list), collapsed by default
   (`notesContent` GONE, toggle switches to "Thu gọn Ghi chú ▴").
   Moved there, meaning preserved, nothing deleted outright:
   - "Về app này": the generic description paragraph from the top.
   - "VPN": the full v8 VPN-strip explanation; the card now shows only
     "Chưa có server VPN — xem Ghi chú."
   - "Bong bóng và chuyển nhanh": the full bubbles/fast-switch bullets
     (shortcut-only, toggles default OFF, drag-to-trash, no
     bring-to-front API). Main flow keeps only a one-line overlay
     status (granted / denied + "Bấm nút 5" + "xem Ghi chú").
   - "Thông báo": notifications-proxy sentence + verify-delay sentence.
   - "Clone đang hoạt động": target 3–4 + "active means marked active".
   - "Pin và giữ chạy nền": Why-battery paragraph + keep-alive sentence
     (the keep-alive sentence was previously inside the notifications
     paragraph; it is preserved here, split noted).
   Kept in place (not scattered main-flow text): per-clone split warning
   in the card header and per-clone VPN note inside the expanded
   details row (contextual to those controls), plus transient operation
   results (import/launch/error) in the status line.
3. Stale status line fixed: the XML default "Status: no APK imported
   yet." is gone (empty initial text). The list header in code is now
   one short accurate line ("N clone, M đang hoạt động"), and the
   status line on open is one short battery line ("Pin: đã/chưa miễn
   tối ưu hóa"); a clone-store error is never overwritten by it.
4. Background colour, verified in the compiled APK (aapt2 dump):
   main ScrollView background resolves to `color/screen_bg`. v8's
   value #EAF2F2 was nearly indistinguishable from white in the user's
   phone screenshots, so v9 deepens it to #D9E9EC (still pale teal,
   now visibly not white). Clone cards stay white (#FFFFFF, stroke
   #C8D8DB) for contrast on that background, VPN card #D6E9EC with a
   teal stroke, buttons teal #004D5C / amber #F5A623 / pale-teal
   #DCE9EB. Real gap found and fixed: the installed-app picker screen
   had NO background set (theme default white); it now uses
   `screen_bg` with a teal title. Picker fallback wordings updated to
   the new entry point ("bấm + Thêm app mới rồi chọn File APK");
   back button is now just "Quay lại".

## Direction-A audit (checked in layout/source, not just claimed)
- Header/title: present, teal_dark bold 22sp. OK.
- Collapsible VPN strip card: present, own card, summary always
  visible, details collapse, Connect disabled, never Connected/IP.
  OK. Note: details default to expanded on fresh install (saved pref
  from v8 stays expanded for the existing user); details are now short,
  so the "+" button still sits near the top with no long text above it.
- Minimal clone list: present, white cards, short count header. OK.
- "+" add entry: NEW, see (1). OK.
- Per-clone expanded details: unchanged (Set active/idle, Update APK,
  Delete, Bubble/Fast-switch toggles, disabled per-clone VPN switch +
  note). OK.
- Remaining deviation, stated openly: the functional buttons
  battery/keep-alive/refresh and overlay buttons 5/6/Stop still sit in
  the main flow between Create and the list, because they are
  functional controls, not explanatory text; hiding them inside the
  collapsed Notes section would hide functionality. Only the
  paragraphs around them were moved.

## Honest limits — NOT changed
- Real VPN connect: still disabled (no backend, no WireGuard config);
  shared Connect and per-clone switches stay disabled, never Connected,
  never a fake IP.
- Bring-to-front restoration: engine exposes no such API; switching
  still reuses the launch path, login/data not lost, exact screen not
  guaranteed.
- Split-APK clones: engine installs only a single APK; split warnings
  unchanged (row, import result, clone row).

## NOT verified (no phone on the VM)
Main-screen tidiness and background visibility on the Samsung phone,
Notes expand/collapse, "+" dialog both paths end-to-end on device,
picker list/search/import on device, bubbles/switch both directions,
and that existing clones/logins survive the update. VM checks passed:
javac 0 errors (one pre-existing deprecation note in BubbleService),
apksigner verify OK, badging versionCode 20 / 0.7.1-tidy, compiled
layout dump confirms "+ Thêm app mới", "Ghi chú ▾", all Notes headings
and the short VPN line, and no old numbered-button text remains.
