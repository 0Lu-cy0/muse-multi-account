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
        if (requestCode != REQ_PICK_APK || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            String name = uri.getLastPathSegment();
            if (name == null || name.isEmpty()) name = "imported.apk";
            if (name.length() > 80) name = name.substring(name.length() - 80);
            File dest = new File(new File(getFilesDir(), "imports"), name);
            dest.getParentFile().mkdirs();
            InputStream in = getContentResolver().openInputStream(uri);
            OutputStream out = new java.io.FileOutputStream(dest);
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close(); out.close();
            PackageInfo info = getPackageManager().getPackageArchiveInfo(dest.getAbsolutePath(), 0);
            importedApk = dest;
            importedPackage = info != null ? info.packageName : "unknown";
            importedVersion = info != null && info.versionName != null ? info.versionName : "";
            statusText.setText("Imported: " + name + "\npackage=" + importedPackage +
                " version=" + importedVersion + " size=" + (dest.length() / 1024 / 1024) + " MB\n" +
                "NOTE: This APK was supplied by you. It is stored in app-private storage, " +
                "not bundled into this host app, and it was NOT modified or re-signed.");
        } catch (Exception e) {
            statusText.setText("Import failed: " + e.getMessage());
        }
    }

    private void createClone() {
        if (importedApk == null) { Toast.makeText(this, "Pick an APK first", Toast.LENGTH_SHORT).show(); return; }
        try {
            List<CloneEntry> clones = CloneStore.load(this);
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) name = "Clone " + (clones.size() + 1);
            String id = UUID.randomUUID().toString();
            CloneEntry entry = new CloneEntry(id, name, importedPackage, importedVersion,
                importedApk.getAbsolutePath(), CloneStore.sha256(importedApk), false, System.currentTimeMillis());
            File dir = CloneStore.dataDir(this, id);
            java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, "README.txt"));
            String txt = "Host-side data dir for clone '" + name + "' (" + id + ").\nSource package: " + entry.sourcePackage +
                "\nReal guest isolation requires a virtual engine (see ENGINE_EVALUATION.md).\n";
            out.write(txt.getBytes("UTF-8")); out.close();
            clones.add(entry);
            CloneStore.save(this, clones);
            nameInput.setText("");
            Toast.makeText(this, "Created " + name, Toast.LENGTH_SHORT).show();
            refreshList();
        } catch (Exception e) {
            statusText.setText("Create clone failed: " + e.getMessage());
        }
    }

    private void refreshList() {
        clonesContainer.removeAllViews();
        List<CloneEntry> clones = CloneStore.load(this);
        int activeCount = 0;
        for (CloneEntry c : clones) if (c.active) activeCount++;
        TextView header = new TextView(this);
        header.setText("Clones: " + clones.size() + " total, " + activeCount + " active\n" +
            "Target for simultaneous online: 3-4. Active here means you marked it active — " +
            "it does NOT mean a guest Muse process is running yet.");
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
                    EngineLauncher.Result r = EngineLauncher.launchClone(c);
                    statusText.setText((r.launched ? "Launched: " : "LAUNCH BLOCKED (honest stub):\n") + r.detail);
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
            row.addView(launchBtn);
            row.addView(toggleBtn);
            clonesContainer.addView(row);
        }
        if (clones.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No clones yet. Pick an APK, then Create clone.");
            clonesContainer.addView(tv);
        }
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
