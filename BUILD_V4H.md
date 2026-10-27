# BUILD_V4H — wrap lazily acquired providers, host uid for system providers

- Host: versionCode 12 / 0.4.8-provider, same debug signer, VERIFY_OK.
  File: muse-container-host-v4h-debug.apk, 1,690,431 bytes,
  SHA-256 066a31cceb5bf34b2dcbeece7bedd8e43806a69a27efa5bed1d4c65e234f956e.
- Engine: bcore-v4h.aar (host sources unchanged from v4f).
- Why v4g failed: the Settings crash recurred in the 18:12 phone test
  even though v4g code was present. The crashing Settings$Config path
  goes through ContentProvider.call, and providers acquired lazily
  after BlackBox init were never wrapped (ContentProviderDelegate
  .update() was dead code), so they hit system_server raw with
  AttributionSource uid = virtual BUid (10015) vs Binder uid (12922).
- Changes (engine only, v4g empty-cursor fix kept):
  - IActivityManagerProxy.GetContentProvider now wraps every newly
    acquired provider via ContentProviderDelegate.update().
  - ContentProviderStub: for system providers, all String args for
    "call" are preserved (method name is not a package name) and the
    AttributionSource uid is set to the real host uid.
- Not yet phone-tested. Next test must show AuraMainActivity surviving
  past attach (login screen) or the next crash.
