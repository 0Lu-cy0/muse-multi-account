package com.example.musecontainer;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

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
                out.add(new CloneEntry(o.getString("id"), o.getString("name"),
                    o.getString("sourcePackage"), o.optString("sourceVersionName", ""),
                    o.getString("sourceApkPath"), o.optString("sourceSha256", ""),
                    o.optBoolean("active", false), o.optLong("createdAtMs", 0L)));
            }
        } catch (Exception ignored) {}
        return out;
    }
    public static void save(Context ctx, List<CloneEntry> clones) throws Exception {
        JSONArray arr = new JSONArray();
        for (CloneEntry c : clones) {
            JSONObject o = new JSONObject();
            o.put("id", c.id); o.put("name", c.name); o.put("sourcePackage", c.sourcePackage);
            o.put("sourceVersionName", c.sourceVersionName); o.put("sourceApkPath", c.sourceApkPath);
            o.put("sourceSha256", c.sourceSha256); o.put("active", c.active); o.put("createdAtMs", c.createdAtMs);
            arr.put(o);
        }
        write(file(ctx), arr.toString());
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
    private static String read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()]; int n = in.read(b);
            return new String(b, 0, n < 0 ? 0 : n, "UTF-8");
        }
    }
    private static void write(File f, String s) throws Exception {
        java.io.FileOutputStream out = new java.io.FileOutputStream(f);
        out.write(s.getBytes("UTF-8")); out.close();
    }
}
