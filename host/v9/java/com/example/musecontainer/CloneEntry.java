package com.example.musecontainer;

/**
 * v3: virtualUserId is stable for the life of a clone. It is assigned
 * once at creation as (max existing virtualUserId + 1) and is NEVER
 * reused or shifted when another clone is deleted. This fixes the v2
 * index-based mapping, where deleting a clone in the middle would
 * have remapped every later clone to a different virtual user and
 * opened the wrong guest data. Old entries without the field are
 * migrated on load to their array index, matching v2 behaviour.
 */
public class CloneEntry {
    public final String id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256;
    public final boolean active;
    public final long createdAtMs;
    public final int virtualUserId;
    /** v5: per-clone overlay toggles. Default OFF (see CloneStore load:
     *  old entries without these fields read false), so enabling the
     *  overlay never floods the screen with bubbles for existing clones. */
    public final boolean bubbleEnabled;
    public final boolean switchEnabled;
    /** v6: true when the imported app was installed as split APKs
     *  (base APK + splitSourceDirs). The engine install path only takes
     *  a single APK file, so a split clone may be incomplete — the UI
     *  must warn about this instead of pretending the clone is whole.
     *  Default false for old entries (optBoolean in CloneStore). */
    public final boolean splitApk;
    public CloneEntry(String id, String name, String sourcePackage, String sourceVersionName,
                      String sourceApkPath, String sourceSha256, boolean active, long createdAtMs) {
        this(id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256, active, createdAtMs, -1, false, false, false);
    }
    public CloneEntry(String id, String name, String sourcePackage, String sourceVersionName,
                      String sourceApkPath, String sourceSha256, boolean active, long createdAtMs, int virtualUserId) {
        this(id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256, active, createdAtMs, virtualUserId, false, false, false);
    }
    public CloneEntry(String id, String name, String sourcePackage, String sourceVersionName,
                      String sourceApkPath, String sourceSha256, boolean active, long createdAtMs, int virtualUserId,
                      boolean bubbleEnabled, boolean switchEnabled) {
        this(id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256, active, createdAtMs, virtualUserId, bubbleEnabled, switchEnabled, false);
    }
    public CloneEntry(String id, String name, String sourcePackage, String sourceVersionName,
                      String sourceApkPath, String sourceSha256, boolean active, long createdAtMs, int virtualUserId,
                      boolean bubbleEnabled, boolean switchEnabled, boolean splitApk) {
        this.id = id; this.name = name; this.sourcePackage = sourcePackage;
        this.sourceVersionName = sourceVersionName; this.sourceApkPath = sourceApkPath;
        this.sourceSha256 = sourceSha256; this.active = active; this.createdAtMs = createdAtMs;
        this.virtualUserId = virtualUserId;
        this.bubbleEnabled = bubbleEnabled; this.switchEnabled = switchEnabled;
        this.splitApk = splitApk;
    }
    public CloneEntry withActive(boolean a) {
        return new CloneEntry(id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256, a, createdAtMs, virtualUserId, bubbleEnabled, switchEnabled, splitApk);
    }
    public CloneEntry withApk(String apkPath, String versionName, String sha256) {
        // File imports are always a single APK file, so an APK update
        // via the file picker clears the split flag (the old split
        // warning no longer applies to the new single-file import).
        return new CloneEntry(id, name, sourcePackage, versionName, apkPath, sha256, active, createdAtMs, virtualUserId, bubbleEnabled, switchEnabled, false);
    }
    public CloneEntry withBubble(boolean b) {
        return new CloneEntry(id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256, active, createdAtMs, virtualUserId, b, switchEnabled, splitApk);
    }
    public CloneEntry withSwitch(boolean s) {
        return new CloneEntry(id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256, active, createdAtMs, virtualUserId, bubbleEnabled, s, splitApk);
    }
}
