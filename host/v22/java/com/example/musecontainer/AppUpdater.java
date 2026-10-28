package com.example.musecontainer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v10: in-app self-update, MANUAL ONLY. No background polling, no
 * auto-download, no auto-install (performance-first requirement, and
 * Android forbids silent self-install anyway). Every step is started
 * by the user tapping "Kiểm tra cập nhật" in the Notes section.
 *
 * Sources, in order:
 *  1. https://raw.githubusercontent.com/0Lu-cy0/muse-multi-account/main/updates.json
 *     {"versionCode": int, "versionName": string, "apkUrl": string,
 *      "sha256": hex-or-empty, "changelog": string}
 *  2. Fallback: https://api.github.com/repos/0Lu-cy0/muse-multi-account/releases/latest
 *     (tag_name + first .apk asset + versionCode parsed from the body,
 *      if present; without a versionCode the fallback cannot compare
 *      safely and reports an error instead of guessing.)
 *
 * Safety: the downloaded APK's SHA-256 is verified against the
 * manifest before the installer is ever triggered. On mismatch the
 * file is deleted and no install intent is sent. Installation always
 * goes through the system confirmation dialog; there is no silent
 * install path. Data is preserved by the system update itself (same
 * package, same signer) — this class never touches clone data.
 * All network work runs on a background thread; failures only
 * produce a short Vietnamese message, never a crash.
 */
public class AppUpdater {
    public static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/0Lu-cy0/muse-multi-account/main/updates.json";
    public static final String RELEASES_URL =
            "https://api.github.com/repos/0Lu-cy0/muse-multi-account/releases/latest";

    public static class Info {
        public int versionCode;
        public String versionName;
        public String apkUrl;
        public String sha256;
        public String changelog;
    }

    public interface CheckCallback {
        void onStatus(String text);
        void onUpToDate(String currentLabel);
        void onUpdateAvailable(Info info);
        void onError(String message);
    }

    public interface DownloadCallback {
        void onProgress(int percent); // -1 when total size is unknown
        void onDownloaded(File apk);
        void onError(String message);
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());

    public AppUpdater(Context ctx) {
        // v11: keep the caller's context, NOT getApplicationContext():
        // MainActivity passes its locale-wrapped activity context, so
        // getString() below resolves in the user's chosen language.
        this.ctx = ctx;
    }

    public static int currentVersionCode(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    public static String currentVersionLabel(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0);
            return ctx.getString(R.string.updater_version_label, pi.versionName, pi.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            return ctx.getString(R.string.updater_unknown);
        }
    }

    private void post(final Runnable r) { main.post(r); }

    public void checkForUpdate(final CheckCallback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Info info = fetchManifest();
                    if (info == null) info = fetchReleasesFallback();
                    if (info == null) {
                        fail(cb, ctx.getString(R.string.updater_fetch_failed));
                        return;
                    }
                    final Info found = info;
                    final int current = currentVersionCode(ctx);
                    if (found.versionCode <= 0) {
                        fail(cb, ctx.getString(R.string.updater_no_version));
                        return;
                    }
                    if (found.versionCode <= current) {
                        post(new Runnable() {
                            @Override public void run() {
                                cb.onUpToDate(currentVersionLabel(ctx));
                            }
                        });
                    } else {
                        post(new Runnable() {
                            @Override public void run() {
                                cb.onUpdateAvailable(found);
                            }
                        });
                    }
                } catch (final Exception e) {
                    fail(cb, ctx.getString(R.string.updater_check_error, safe(e)));
                }
            }
        }).start();
    }

    private void fail(final CheckCallback cb, final String msg) {
        post(new Runnable() {
            @Override public void run() { cb.onError(msg); }
        });
    }

    private String fetchText(String urlStr) throws Exception {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "MuseContainer-Updater");
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) {
                throw new Exception("HTTP " + c.getResponseCode());
            }
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private Info fetchManifest() {
        try {
            JSONObject o = new JSONObject(fetchText(MANIFEST_URL));
            if (!o.has("versionCode") || !o.has("apkUrl")) return null;
            Info info = new Info();
            info.versionCode = o.getInt("versionCode");
            info.versionName = o.optString("versionName", ctx.getString(R.string.updater_manifest_version_default, info.versionCode));
            info.apkUrl = o.getString("apkUrl");
            info.sha256 = o.optString("sha256", "");
            info.changelog = o.optString("changelog", "");
            if (info.apkUrl == null || !info.apkUrl.startsWith("https://")) return null;
            return info;
        } catch (Exception e) {
            return null; // caller falls back to Releases, then reports
        }
    }

    private Info fetchReleasesFallback() {
        try {
            JSONObject o = new JSONObject(fetchText(RELEASES_URL));
            String body = o.optString("body", "");
            Matcher m = Pattern.compile("versionCode[^0-9]*([0-9]+)")
                    .matcher(body);
            if (!m.find()) return null; // cannot compare safely without a code
            JSONArray assets = o.optJSONArray("assets");
            if (assets == null) return null;
            String apkUrl = null;
            for (int i = 0; i < assets.length(); i++) {
                String u = assets.getJSONObject(i).optString("browser_download_url", "");
                if (u.endsWith(".apk")) { apkUrl = u; break; }
            }
            if (apkUrl == null) return null;
            Info info = new Info();
            info.versionCode = Integer.parseInt(m.group(1));
            String tag = o.optString("tag_name", "");
            info.versionName = tag.startsWith("v") ? tag.substring(1) : tag;
            info.apkUrl = apkUrl;
            info.sha256 = "";
            info.changelog = o.optString("name", "");
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    public void downloadUpdate(final Info info, final DownloadCallback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                HttpURLConnection c = null;
                try {
                    File out = UpdateFileProvider.updateFile(ctx);
                    File dir = out.getParentFile();
                    if (dir != null && !dir.exists()) dir.mkdirs();
                    if (out.exists()) out.delete();
                    URL url = new URL(info.apkUrl);
                    c = (HttpURLConnection) url.openConnection();
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(30000);
                    c.setRequestProperty("User-Agent", "MuseContainer-Updater");
                    c.setInstanceFollowRedirects(true);
                    if (c.getResponseCode() != 200) {
                        failDownload(cb, ctx.getString(R.string.updater_download_http, c.getResponseCode()));
                        return;
                    }
                    long total = c.getContentLengthLong();
                    InputStream in = c.getInputStream();
                    FileOutputStream fos = new FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    long done = 0;
                    int n;
                    int lastPercent = -1;
                    while ((n = in.read(buf)) != -1) {
                        fos.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            int p = (int) (done * 100 / total);
                            if (p != lastPercent) {
                                lastPercent = p;
                                final int fp = p;
                                post(new Runnable() {
                                    @Override public void run() { cb.onProgress(fp); }
                                });
                            }
                        }
                    }
                    fos.close();
                    in.close();
                    if (info.sha256 != null && !info.sha256.trim().isEmpty()) {
                        String actual = sha256Of(out);
                        if (!actual.equalsIgnoreCase(info.sha256.trim())) {
                            out.delete();
                            failDownload(cb, ctx.getString(R.string.updater_hash_mismatch));
                            return;
                        }
                    }
                    final File apk = out;
                    post(new Runnable() {
                        @Override public void run() { cb.onDownloaded(apk); }
                    });
                } catch (final Exception e) {
                    failDownload(cb, ctx.getString(R.string.updater_download_error, safe(e)));
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }).start();
    }

    private void failDownload(final DownloadCallback cb, final String msg) {
        post(new Runnable() {
            @Override public void run() { cb.onError(msg); }
        });
    }

    private static String sha256Of(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        in.close();
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }

    /** True when the system will let this app trigger its own update
     *  install. On API < 26 there is no such gate. */
    public boolean canInstallUnknownApps() {
        try {
            return ctx.getPackageManager().canRequestPackageInstalls();
        } catch (Exception e) {
            return true;
        }
    }

    /** Opens Settings for this app's "install unknown apps" toggle.
     *  The user must allow it once, then come back and retry. */
    public Intent unknownSourcesSettingsIntent() {
        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        i.setData(Uri.parse("package:" + ctx.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** Triggers the SYSTEM installer. This is not silent: Android
     *  always shows its own confirmation dialog for the update. */
    public Intent installIntent() {
        Uri uri = UpdateFileProvider.updateUri(ctx);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    private static String safe(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) return e.getClass().getSimpleName();
        return m.length() > 120 ? m.substring(0, 120) : m;
    }
}
