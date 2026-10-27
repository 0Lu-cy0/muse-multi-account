# BUILD_V4G — Settings$Config null-cursor fix

- Host: versionCode 11 / 0.4.7-settings, same debug signer, VERIFY_OK.
  File: muse-container-host-v4g-debug.apk, 1,690,431 bytes,
  SHA-256 9f0f204834d701e55449080ddfab926466dda96a213dba9d7ece18fe942ee67c.
- Engine: bcore-v4g.aar (host sources unchanged from v4f).
- Change (engine only):
  - ContentProviderStub: a provider query that fails with a UID/permission
    error now returns an empty MatrixCursor (name/value, 0 rows) instead
    of null.
  - SystemProviderStub: same empty-cursor fallback for query on UID /
    Permission Denial.
- Why: v4f phone test (2026-10-27 17:55-17:56) — guest really started
  (ProxyActivity + AuraMainActivity onResume) but died 3x with NPE at
  Settings$NameValueCache.getStringsForPrefix, via Settings$Config ->
  DeviceConfig.getBoolean -> AutofillFeatureFlags -> AutofillManager init
  -> AndroidComposeView attach. The Settings$Config query failed on UID
  mismatch and the null cursor left the framework cache map null.
  An empty cursor lets DeviceConfig fall back to defaults instead.
- Not yet phone-tested. Next test must show AuraMainActivity surviving
  past attach (login screen) or the next crash, and whether the
  "returning empty cursor" line appears in the log.
