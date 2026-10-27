package com.example.musecontainer;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * CloneStore, v3 fixes (audit 2026-10-27):
 * - load() no longer swallows a corrupt clones.json and returns an
 *   empty list. If the file exists but cannot be parsed, the corrupt
 *   file is preserved as clones.json.corrupt.<timestamp> and an
 *   IllegalStateException is thrown, so the UI shows an error
 *   instead of silently "losing" every clone.
 * - read() loops until EOF instead of trusting a single read() call.
 * - save() is atomic: write to clones.json.tmp in the same directory,
 *   then rename over clones.json, so a crash mid-write cannot leave
 *   a half-written store.
 * v4 (hybrid notifications): save() also writes clone_names.json,
 * a tiny map virtualUserId -> clone name, in the same filesDir.
 * The engine (BNotificationManagerService) reads this file when it
 * posts a notification and prefixes the notification title with the
 * clone name. The engine only knows a numeric userId, so without
 * this file it could only label "Clone 2". This file is the only
 * bridge that gives the engine the real name, and it is updated on
 * every save (create, toggle, update, delete), so deleted clones
 * disappear from the map automatically.
 */
public class CloneStore {
    private static File file(Context ctx) { return new File(ctx.getFilesDir(), "clones.json"); }
    public static List<CloneEntry> load(Context ctx) {
        List<CloneEntry> out = new ArrayList<>();
        File f = file(ctx);
        if (!f.exists()) return out;
        try {
            JSONArray arr = new JSONArray(read(f));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                // v2 entries have no virtualUserId: migrate to array index.
                int uid = o.has("virtualUserId") ? o.optInt("virtualUserId", i) : i;
                out.add(new CloneEntry(o.getString("id"), o.getString("name"),
                    o.getString("sourcePackage"), o.optString("sourceVersionName", ""),
                    o.getString("sourceApkPath"), o.optString("sourceSha256", ""),
                    o.optBoolean("active", false), o.optLong("createdAtMs", 0L), uid,
                    o.optBoolean("bubbleEnabled", false), o.optBoolean("switchEnabled", false),
                    o.optBoolean("splitApk", false)));
            }
            return out;
        } catch (Exception e) {
            try {
                File backup = new File(ctx.getFilesDir(),
                    "clones.json.corrupt." + System.currentTimeMillis());
                copy(f, backup);
            } catch (Exception ignored) {}
            throw new IllegalStateException(
                "clones.json is corrupt and was preserved as clones.json.corrupt.*. " +
                "No clones were loaded, to avoid silently losing them. Cause: " + e.getMessage(), e);
        }
    }
    public static void save(Context ctx, List<CloneEntry> clones) throws Exception {
        JSONArray arr = new JSONArray();
        for (CloneEntry c : clones) {
            JSONObject o = new JSONObject();
            o.put("id", c.id); o.put("name", c.name); o.put("sourcePackage", c.sourcePackage);
            o.put("sourceVersionName", c.sourceVersionName); o.put("sourceApkPath", c.sourceApkPath);
            o.put("sourceSha256", c.sourceSha256); o.put("active", c.active); o.put("createdAtMs", c.createdAtMs); o.put("virtualUserId", c.virtualUserId);
            o.put("bubbleEnabled", c.bubbleEnabled); o.put("switchEnabled", c.switchEnabled);
            o.put("splitApk", c.splitApk);
            arr.put(o);
        }
        File target = file(ctx);
        File tmp = new File(ctx.getFilesDir(), "clones.json.tmp");
        write(tmp, arr.toString());
        if (!tmp.renameTo(target)) {
            write(target, arr.toString());
        }
        // v4: notification name map for the engine. Best effort: a
        // failure here must not fail the clone save itself.
        try {
            JSONObject names = new JSONObject();
            for (CloneEntry c : clones) {
                if (c.virtualUserId >= 0 && c.name != null) {
                    names.put(String.valueOf(c.virtualUserId), c.name);
                }
            }
            File nameTarget = new File(ctx.getFilesDir(), "clone_names.json");
            File nameTmp = new File(ctx.getFilesDir(), "clone_names.json.tmp");
            write(nameTmp, names.toString());
            if (!nameTmp.renameTo(nameTarget)) {
                write(nameTarget, names.toString());
            }
        } catch (Exception ignored) {}
    }
    public static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }
    public static File dataDir(Context ctx, String cloneId) {
        File d = new File(ctx.getFilesDir(), "clones/" + cloneId);
        d.mkdirs();
        return d;
    }
    private static void copy(File src, File dst) throws Exception {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
    private static String read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) >= 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        }
    }
    private static void write(File f, String s) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes("UTF-8"));
        }
    }
}
