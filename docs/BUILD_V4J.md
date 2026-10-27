# BUILD_V4J — AttributionSource uid must be the real host uid

- Host: versionCode 14 / 0.4.10-uid, same debug signer, VERIFY_OK.
  File: muse-container-host-v4j-debug.apk, 1,690,431 bytes,
  SHA-256 1d2adba596dbc4e662861e058a36ba7a3773d2c4c1c14cabf508b902c4ec4ad6.
- Engine: bcore-v4j.aar (host sources unchanged from v4f).
- Why v4i still failed: the 19:00 phone test died at makeApplication
  exactly like v4h, with ContentProviderStub UID mismatch showing
  source uid 10000 — the virtual BUid before app config loads.
  Every real provider server enforces AttributionSource uid ==
  Binder uid (host 12922), so any BUid is rejected, including for
  BlackBox's own VM service channel (hence all services null).
- Change (engine only, v4g/v4h/v4i fixes kept): AttributionSource
  uid is now set to BlackBoxCore.getHostUid() everywhere it was
  set to BUid (ContentProviderStub, ContextCompat.fix,
  AttributionSourceUtils, IAttributionSourceProxy).
- Not yet phone-tested. Next test must show services resolving
  (no "Failed to get binder") and the guest passing bindApplication.
