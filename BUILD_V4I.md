# BUILD_V4I — do not corrupt "call" method names, do not wrap BlackBox providers

- Host: versionCode 13 / 0.4.9-call, same debug signer, VERIFY_OK.
  File: muse-container-host-v4i-debug.apk, 1,690,431 bytes,
  SHA-256 205dcaa17a81257cc38bf1a764c92f1c4049ab7fa06707b20291a06c9a5b22f6.
- Engine: bcore-v4i.aar (host sources unchanged from v4f).
- Why v4h regressed: the 18:24 and 18:48 phone tests both died at
  bindApplication (services unavailable, makeApplication null,
  ClassCastException fallback) before reaching AuraMainActivity.
  Cause: v4h wrapped BlackBox's own VM service provider, and
  ContentProviderStub replaced the "call" method name ("VM") with the
  host package, so every service lookup returned null.
- Changes (engine only, v4g/v4h fixes kept):
  - ContentProviderStub: for "call", only args[0] may be replaced
    (old callingPkg signature); method name and arg are preserved.
  - GetContentProvider wrapping skips the BlackBox bind provider
    and proxy_content_provider_* providers.
- Not yet phone-tested. Next test must show the guest passing
  bindApplication again (AuraMainActivity, then Settings path).
