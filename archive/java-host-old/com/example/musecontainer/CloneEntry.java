package com.example.musecontainer;

public class CloneEntry {
    public final String id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256;
    public final boolean active;
    public final long createdAtMs;
    public CloneEntry(String id, String name, String sourcePackage, String sourceVersionName,
                      String sourceApkPath, String sourceSha256, boolean active, long createdAtMs) {
        this.id = id; this.name = name; this.sourcePackage = sourcePackage;
        this.sourceVersionName = sourceVersionName; this.sourceApkPath = sourceApkPath;
        this.sourceSha256 = sourceSha256; this.active = active; this.createdAtMs = createdAtMs;
    }
    public CloneEntry withActive(boolean a) {
        return new CloneEntry(id, name, sourcePackage, sourceVersionName, sourceApkPath, sourceSha256, a, createdAtMs);
    }
}
