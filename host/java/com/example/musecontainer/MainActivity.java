package com.example.musecontainer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {
    /** Hard performance cap (user requirement, 2026-10-27): at most 4 clones
     *  may be ACTIVE (online, notifications on) at once. Unlimited clones may
     *  be created, but beyond this cap they stay dormant: no process, no
     *  background sync, no polling. This cap is enforced in the toggle below,
     *  not just documented. */
    public static final int MAX_ACTIVE_CLONES = 4;
    private static final int REQ_PICK_APK = 1001;
    private static final int REQ_NOTIF = 1002;
    /** v4d import logging: every Pick/import step logs here so a logcat
     *  filtered for MuseImport proves whether the APK really entered the
     *  app, where it was copied, and why it failed if it did not. */
    private static final String TAG_IMPORT = "MuseImport";
    private TextView statusText;
    private LinearLayout clonesContainer;
    private EditText nameInput;
    private File importedApk;
    private String importedPackage, importedVersion;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        statusText = findViewById(R.id.statusText);
        clonesContainer = findViewById(R.id.clonesContainer);
        nameInput = findViewById(R.id.cloneNameInput);
        findViewById(R.id.btnPickApk).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_PICK_APK);
            }
        });
        findViewById(R.id.btnCreateClone).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { createClone(); }
        });
        findViewById(R.id.btnBattery).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestBatteryExemption(); }
        });
        findViewById(R.id.btnKeepAlive).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startKeepAlive(); }
        });
        findViewById(R.id.btnRefresh).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refreshList(); }
        });
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        refreshList();
        updateBatteryStatus();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // v4d: previously this early return was completely silent, so a
        // cancelled picker (or a picker returning no data) looked exactly
        // like a successful import that showed nothing. Log + show it now.
        if (requestCode != REQ_PICK_APK) { Log.d(TAG_IMPORT, "onActivityResult ignored: requestCode=" + requestCode + " (not PICK)"); return; }
        Log.i(TAG_IMPORT, "onActivityResult: resultCode=" + resultCode + " data=" + (data == null ? "null" : "present") + " uri=" + (data == null || data.getData() == null ? "null" : data.getData().toString()));
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            String msg = "Pick cancelled or no file returned (resultCode=" + resultCode + "). No APK was imported.";
            Log.w(TAG_IMPORT, msg);
            FileLogger.log(this, TAG_IMPORT, msg);
            statusText.setText(msg);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }
        Uri uri = data.getData();
        Log.i(TAG_IMPORT, "Picked uri=" + uri.toString());
        try {
            // v3 fix: unique import filename. v2 used only the picked
            // file's name, so a second import with the same filename
            // silently overwrote the first import and could make old
            // clones run a different APK (SHA check at launch would
            // then refuse, but the file was already lost).
            String raw = uri.getLastPathSegment();
            if (raw == null || raw.isEmpty()) raw = "imported.apk";
            raw = raw.replaceAll("[^A-Za-z0-9._-]", "_");
            if (raw.length() > 60) raw = raw.substring(raw.length() - 60);
            String name = UUID.randomUUID().toString().substring(0, 8) + "_" + raw;
            File dest = new File(new File(getFilesDir(), "imports"), name);
            dest.getParentFile().mkdirs();
            Log.i(TAG_IMPORT, "Copying picked file to app-private dest=" + dest.getAbsolutePath());
            InputStream in = getContentResolver().openInputStream(uri);
            OutputStream out = new java.io.FileOutputStream(dest);
            byte[] buf = new byte[8192]; int n; long copied = 0;
            while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); copied += n; }
            in.close(); out.close();
            Log.i(TAG_IMPORT, "Copy finished: bytesCopied=" + copied + " destLength=" + dest.length() + " destExists=" + dest.exists());
            PackageInfo info = getPackageManager().getPackageArchiveInfo(dest.getAbsolutePath(), 0);
            Log.i(TAG_IMPORT, "PackageArchiveInfo: " + (info == null ? "null" : ("package=" + info.packageName + " version=" + info.versionName)));
            if (info == null || info.packageName == null) {
                dest.delete();
                importedApk = null;
                String msg = "Import failed: picked file is not a readable APK (PackageInfo is null). File was deleted, not kept.";
                Log.e(TAG_IMPORT, msg + " dest=" + dest.getAbsolutePath());
                statusText.setText(msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                return;
            }
            importedApk = dest;
            importedPackage = info.packageName;
            importedVersion = info.versionName != null ? info.versionName : "";
            Log.i(TAG_IMPORT, "IMPORTED OK: name=" + name + " package=" + importedPackage + " version=" + importedVersion + " sizeMB=" + (dest.length() / 1024 / 1024) + " dest=" + dest.getAbsolutePath());
            FileLogger.log(this, TAG_IMPORT, "IMPORTED OK: name=" + name + " package=" + importedPackage + " version=" + importedVersion + " sizeMB=" + (dest.length() / 1024 / 1024) + " dest=" + dest.getAbsolutePath());
            statusText.setText("Imported: " + name + "\npackage=" + importedPackage +
                " version=" + importedVersion + " size=" + (dest.length() / 1024 / 1024) + " MB\n" +
                "NOTE: This APK was supplied by you. It is stored in app-private storage, " +
                "not bundled into this host app, and it was NOT modified or re-signed.");
            Toast.makeText(this, "Da nap: " + importedPackage + " " + importedVersion + " (" + (dest.length() / 1024 / 1024) + " MB)", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG_IMPORT, "Import failed with exception", e);
            FileLogger.log(this, TAG_IMPORT, "Import failed with exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            statusText.setText("Import failed: " + e.getMessage());
            Toast.makeText(this, "Nap APK that bai: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void createClone() {
        Log.i(TAG_IMPORT, "Create clone pressed: importedApk=" + (importedApk == null ? "null" : importedApk.getAbsolutePath()) + " package=" + importedPackage + " version=" + importedVersion);
        if (importedApk == null) { Toast.makeText(this, "Pick an APK first", Toast.LENGTH_SHORT).show(); return; }
        try {
            List<CloneEntry> clones = CloneStore.load(this);
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) name = "Clone " + (clones.size() + 1);
            String id = UUID.randomUUID().toString();
            // v3: stable virtualUserId = max existing + 1, never reused.
            int maxUid = -1;
            for (CloneEntry e : clones) if (e.virtualUserId > maxUid) maxUid = e.virtualUserId;
            CloneEntry entry = new CloneEntry(id, name, importedPackage, importedVersion,
                importedApk.getAbsolutePath(), CloneStore.sha256(importedApk), false, System.currentTimeMillis(), maxUid + 1);
            File dir = CloneStore.dataDir(this, id);
            java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, "README.txt"));
            String txt = "Host-side data dir for clone '" + name + "' (" + id + ").\nSource package: " + entry.sourcePackage +
                "\nReal guest isolation requires a virtual engine (see ENGINE_EVALUATION.md).\n";
            out.write(txt.getBytes("UTF-8")); out.close();
            clones.add(entry);
            CloneStore.save(this, clones);
            Log.i(TAG_IMPORT, "Clone created: name=" + name + " id=" + id + " virtualUserId=" + (maxUid + 1) + " sourceApkPath=" + importedApk.getAbsolutePath());
            FileLogger.log(this, TAG_IMPORT, "Clone created: name=" + name + " virtualUserId=" + (maxUid + 1) + " sourceApkPath=" + importedApk.getAbsolutePath());
            nameInput.setText("");
            Toast.makeText(this, "Created " + name, Toast.LENGTH_SHORT).show();
            refreshList();
        } catch (Exception e) {
            statusText.setText("Create clone failed: " + e.getMessage());
        }
    }

    private void refreshList() {
        clonesContainer.removeAllViews();
        List<CloneEntry> clones;
        try {
            clones = CloneStore.load(this);
        } catch (Exception e) {
            // v3: CloneStore no longer silently returns empty on corrupt
            // data. Show the error and the preserved backup name, and
            // do not present an empty list as if the clones were gone.
            TextView err = new TextView(this);
            err.setText("Clone store error: " + e.getMessage());
            clonesContainer.addView(err);
            statusText.setText("Clone store error: " + e.getMessage());
            return;
        }
        int activeCount = 0;
        for (CloneEntry c : clones) if (c.active) activeCount++;
        TextView header = new TextView(this);
        header.setText("Clones: " + clones.size() + " total, " + activeCount + " active\n" +
            "Target for simultaneous online: 3-4. Active here means you marked it active — " +
            "it does NOT mean a guest Muse process is running yet.\n" +
            "Notifications: guest notifications are posted through the engine's " +
            "notification proxy, not reposted by this host. The Keep-alive button " +
            "only keeps a host foreground service alive; it does not by itself " +
            "deliver or label per-clone notifications. Verify delay per clone on a real phone.");
        header.setTextSize(14f);
        clonesContainer.addView(header);
        for (final CloneEntry c : clones) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            TextView tv = new TextView(this);
            tv.setText(c.name + " — " + c.sourcePackage + " " + c.sourceVersionName +
                " [" + (c.active ? "ACTIVE" : "idle") + "]\ndata: clones/" + c.id + "/");
            row.addView(tv);
            Button launchBtn = new Button(this);
            launchBtn.setText("Launch clone");
            launchBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    // v4b: launchClone -> doCreate waits for the :black
                    // process (up to ~10s+ retries). Running it on the
                    // UI thread froze MainActivity in v4a and produced
                    // "Input dispatching timed out" ANR. Run it on a
                    // background thread and post the result back.
                    final Button btn = (Button) v;
                    btn.setEnabled(false);
                    statusText.setText("Launching '" + c.name + "'... (engine init runs off the UI thread; if this takes >30s, check logcat for the :black process)");
                    new Thread(new Runnable() {
                        @Override public void run() {
                            final EngineLauncher.Result r = EngineLauncher.launchClone(c, MainActivity.this);
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    btn.setEnabled(true);
                                    statusText.setText((r.launched ? "Launched: " : "Launch failed:\n") + r.detail);
                                }
                            });
                        }
                    }).start();
                }
            });
            Button toggleBtn = new Button(this);
            toggleBtn.setText(c.active ? "Set idle" : "Set active");
            toggleBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        List<CloneEntry> current = CloneStore.load(MainActivity.this);
                        // Enforce the hard cap BEFORE activating: dormant clones
                        // must never be started, synced or polled.
                        if (!c.active) {
                            int activeCount = 0;
                            for (CloneEntry e : current) if (e.active) activeCount++;
                            if (activeCount >= MAX_ACTIVE_CLONES) {
                                String msg = "Active cap reached (" + MAX_ACTIVE_CLONES + "). " +
                                    "Set another clone to idle first — clones beyond the cap stay " +
                                    "dormant (no process, no sync, no polling).";
                                statusText.setText(msg);
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                                return;
                            }
                        }
                        List<CloneEntry> updated = new ArrayList<>();
                        for (CloneEntry e : current) {
                            updated.add(e.id.equals(c.id) ? e.withActive(!e.active) : e);
                        }
                        CloneStore.save(MainActivity.this, updated);
                        refreshList();
                    } catch (Exception ex) {
                        statusText.setText("Toggle failed: " + ex.getMessage());
                    }
                }
            });
            Button updateBtn = new Button(this);
            updateBtn.setText("Update APK (use picked APK)");
            updateBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (importedApk == null) {
                        Toast.makeText(MainActivity.this, "Pick a new APK first", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        EngineLauncher.Result r = EngineLauncher.prepareApkUpdate(c, MainActivity.this);
                        if (!r.launched) { statusText.setText("Update failed:\n" + r.detail); return; }
                        List<CloneEntry> current = CloneStore.load(MainActivity.this);
                        List<CloneEntry> updated = new ArrayList<>();
                        for (CloneEntry e : current) {
                            updated.add(e.id.equals(c.id) ? e.withApk(importedApk.getAbsolutePath(), importedVersion, CloneStore.sha256(importedApk)) : e);
                        }
                        CloneStore.save(MainActivity.this, updated);
                        statusText.setText("Updated " + c.name + " to " + importedVersion + ". " + r.detail);
                        refreshList();
                    } catch (Exception ex) {
                        statusText.setText("Update failed: " + ex.getMessage());
                    }
                }
            });
            Button deleteBtn = new Button(this);
            deleteBtn.setText("Delete clone");
            deleteBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        EngineLauncher.Result r = EngineLauncher.deleteClone(c, MainActivity.this);
                        List<CloneEntry> current = CloneStore.load(MainActivity.this);
                        List<CloneEntry> updated = new ArrayList<>();
                        for (CloneEntry e : current) if (!e.id.equals(c.id)) updated.add(e);
                        CloneStore.save(MainActivity.this, updated);
                        // Host data dir is removed; engine virtual user
                        // deletion was attempted in deleteClone (best effort).
                        File dir = CloneStore.dataDir(MainActivity.this, c.id);
                        deleteRecursive(dir);
                        statusText.setText("Deleted " + c.name + ". " + r.detail + " Other clones keep their virtualUserId, so their guest data is not remapped.");
                        refreshList();
                    } catch (Exception ex) {
                        statusText.setText("Delete failed: " + ex.getMessage());
                    }
                }
            });
            row.addView(launchBtn);
            row.addView(toggleBtn);
            row.addView(updateBtn);
            row.addView(deleteBtn);
            clonesContainer.addView(row);
        }
        if (clones.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No clones yet. Pick an APK, then Create clone.");
            clonesContainer.addView(tv);
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        f.delete();
    }

    private void requestBatteryExemption() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "Already exempt from battery optimisation", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            statusText.setText("Battery-exemption intent failed: " + e.getMessage() +
                ". Open Settings > Apps > Muse Container > Battery > Unrestricted manually.");
        }
    }

    private void updateBatteryStatus() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        statusText.setText(statusText.getText().toString() +
            "\nBattery optimisation exempt: " + pm.isIgnoringBatteryOptimizations(getPackageName()));
    }

    private void startKeepAlive() {
        Intent i = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "Keep-alive service started (prototype only)", Toast.LENGTH_SHORT).show();
    }
}
