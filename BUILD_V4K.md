# BUILD_V4K — diagnostic build, behaviour unchanged except logging

- Host: versionCode 15 / 0.4.11-diag, same debug signer, VERIFY_OK.
  File: muse-container-host-v4k-debug.apk, 1,690,431 bytes,
  SHA-256 0dd55277d027f5bab88f26aa6793e455689bdb5518615cfb1b52575bdb78bb35.
- Engine: bcore-v4k.aar (host sources unchanged from v4f; v4j UID fix kept).
- Why: v4i disproved the method-name hypothesis, but the old logs
  could not say which hop of the VM service call failed — ProviderCall
  swallowed exceptions silently, and the server-side SystemCallProvider
  tag was never in the phone filter.
- New diagnostic lines (all land in the PC full logcat file, and in
  the filtered file when their tags are in the filter):
  - VM_STUB_ENTRY (ContentProviderStub) — call reached the stub.
  - VM_CALL_SERVER (BlackBoxCore) — call reached :black, with
    service name, binderNull, callingUid, myUid.
  - VM_CLIENT_NULL (BlackBoxCore) — provider client could not be
    acquired at all.
  - VM_CALL_EXCEPTION (BlackBoxCore) — provider call threw.
- Interpretation for the next phone run:
  - VM_STUB_ENTRY present + VM_CALL_SERVER absent + UID mismatch
    line = stub rejected the call (UID or other).
  - VM_CLIENT_NULL = :black provider not reachable at all.
  - VM_CALL_SERVER with binderNull=true = :black runs but the
    named service is not registered there.
  - VM_CALL_SERVER with binderNull=false + guest still dies =
    the failure is after service lookup, not in it.
