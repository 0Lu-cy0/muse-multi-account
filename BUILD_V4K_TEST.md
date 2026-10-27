# v4k phone test 2026-10-27 19:16 — guest launched and survived

- Launch 19:16:02, install + GMS success, launchApk true.
- VM_CALL_SERVER: all services binderNull=false, callingUid=12922,
  myUid=12922 — the v4j host-uid fix is confirmed working.
- AuraMainActivity created 19:16:08.965, resumed 19:16:09.306.
- No Settings NPE, no guest main-thread crash in the log.
- Only FATALs are in other processes (GMS lowpool 19:16:13,
  vending background 19:16:19) — secondary, not the guest.
- Still open: login inside the clone, 2nd clone, notification
  labelling/routing, RAM/battery/background-kill, 3 then 4 active.
