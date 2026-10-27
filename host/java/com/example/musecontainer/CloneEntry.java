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
    public CloneEntry(String id, String name, String sourcePackage, String sourceVersionName,
                      String sourceApkPath, String sourceSha256, boolean active, long createdAtMs) {
        this(id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256, active, createdAtMs, -1);
    }
    public CloneEntry(String id, String name, String sourcePackage, String sourceVersionName,
                      String sourceApkPath, String sourceSha256, boolean active, long createdAtMs, int virtualUserId) {
        this.id = id; this.name = name; this.sourcePackage = sourcePackage;
        this.sourceVersionName = sourceVersionName; this.sourceApkPath = sourceApkPath;
        this.sourceSha256 = sourceSha256; this.active = active; this.createdAtMs = createdAtMs;
        this.virtualUserId = virtualUserId;
    }
    public CloneEntry withActive(boolean a) {
        return new CloneEntry(id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256, a, createdAtMs, virtualUserId);
    }
    public CloneEntry withApk(String apkPath, String versionName, String sha256) {
        return new CloneEntry(id, name, sourcePackage, versionName, apkPath, sha256, active, createdAtMs, virtualUserId);
    }
}
