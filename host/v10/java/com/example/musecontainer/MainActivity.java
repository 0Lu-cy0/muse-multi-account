package com.example.musecontainer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.graphics.Color;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    /** v6: installed-app picker (direction P1) result code. */
    private static final int REQ_PICK_INSTALLED = 1003;
    /** v6: whether the currently imported APK came from a split-APK
     *  installed app (base + splits). File imports always clear this. */
    private boolean importedSplit = false;
    /** v6, direction A: clone rows whose detail controls are expanded.
     *  Kept in memory only (not persisted): refreshList rebuilds rows,
     *  and this set keeps expanded rows expanded across rebuilds. */
    private final Set<String> expandedIds = new HashSet<>();
    /** v6: VPN strip views (direction A). UI ONLY — see setupVpnStrip. */
    private LinearLayout vpnStripDetails;
    private TextView vpnStripSummary, vpnIpText, vpnStateText;
    private Button vpnCollapseBtn;
    /** v8: overlay explanation view, refreshed in onResume (fallback A4). */
    private TextView overlayExplainView;
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
        // v9, report section 09 two-path add flow: ONE prominent "+"
        // entry replaces the old numbered buttons "1. Pick / import
        // user-supplied APK" and "1b. Pick installed app". Tapping it
        // opens the choice between the installed-app picker (P1) and
        // the APK-file import. Both underlying functions are unchanged
        // (same intents, same result codes, same onActivityResult
        // handling); only the entry point changed to match the report.
        findViewById(R.id.btnAddApp).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAddAppChoice(); }
        });
        findViewById(R.id.btnCreateClone).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { createClone(); }
        });
        setupVpnStrip();
        findViewById(R.id.btnBattery).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestBatteryExemption(); }
        });
        findViewById(R.id.btnKeepAlive).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startKeepAlive(); }
        });
        findViewById(R.id.btnRefresh).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refreshList(); }
        });
        // v9 tidy-up: ONE collapsible Notes section at the bottom of
        // the main screen, collapsed by default. All long explanatory
        // paragraphs moved there; the toggle only shows/hides them.
        final LinearLayout notesContent = findViewById(R.id.notesContent);
        final Button notesToggleBtn = findViewById(R.id.notesToggleBtn);
        notesToggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean nowVisible = notesContent.getVisibility() != View.VISIBLE;
                notesContent.setVisibility(nowVisible ? View.VISIBLE : View.GONE);
                notesToggleBtn.setText(nowVisible ? "Thu gọn Ghi chú ▴" : "Ghi chú ▾");
            }
        });
        // v10: in-app self-update, inside the Notes section only (the
        // tidy main screen stays tidy). Manual only: tapping the
        // button checks updates.json, then Releases as fallback. No
        // background polling, no auto-download, no silent install.
        setupUpdater();
        // v5: bubbles + fast switch controls, added programmatically so
        // the shared activity_main.xml layout (and the main-screen
        // layout direction, still undecided) is NOT redesigned here.
        // Overlay permission is a special Settings permission, not a
        // runtime grant: the button explains it, then opens Settings.
        LinearLayout parent = (LinearLayout) clonesContainer.getParent();
        int insertAt = parent.indexOfChild(clonesContainer);
        // v9: only a SHORT overlay status line lives in the main flow
        // (updateOverlayFallback fills it in right below). The full
        // bubbles/fast-switch explanation moved to the Notes section.
        overlayExplainView = new TextView(this);
        overlayExplainView.setText("Bong bóng & chuyển nhanh — xem Ghi chú để biết cách dùng.");
        overlayExplainView.setTextSize(14f);
        overlayExplainView.setTextColor(Color.parseColor("#5A6E72"));
        overlayExplainView.setPadding(dp(4), dp(4), dp(4), dp(12));
        parent.addView(overlayExplainView, insertAt++);
        updateOverlayFallback();
        Button overlayPermBtn = new Button(this);
        overlayPermBtn.setText("5. Overlay permission (bubbles & fast switch)");
        styleSecondary(overlayPermBtn);
        overlayPermBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, "Overlay permission already granted", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(MainActivity.this,
                    "Why this permission: bubbles and the fast-switch button must float above other apps, including inside a clone. " +
                    "Without it Android will not show them at all. Allow 'Display over other apps' for Muse Container, then come back here.",
                    Toast.LENGTH_LONG).show();
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    statusText.setText("Overlay-permission intent failed: " + e.getMessage() +
                        ". Open Settings > Apps > Muse Container > Display over other apps manually.");
                }
            }
        });
        parent.addView(overlayPermBtn, insertAt++);
        Button startOverlayBtn = new Button(this);
        startOverlayBtn.setText("6. Start bubbles & fast switch");
        stylePrimary(startOverlayBtn);
        startOverlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, "Grant the overlay permission first (button 5)", Toast.LENGTH_LONG).show();
                    return;
                }
                Intent i = new Intent(MainActivity.this, BubbleService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                Toast.makeText(MainActivity.this, "Bubbles & fast switch started (only clones with toggles ON appear)", Toast.LENGTH_LONG).show();
            }
        });
        parent.addView(startOverlayBtn, insertAt++);
        Button stopOverlayBtn = new Button(this);
        stopOverlayBtn.setText("Stop bubbles & fast switch");
        styleSecondary(stopOverlayBtn);
        stopOverlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, BubbleService.class);
                i.setAction(BubbleService.ACTION_STOP);
                startService(i);
                Toast.makeText(MainActivity.this, "Bubbles & fast switch stopping", Toast.LENGTH_SHORT).show();
            }
        });
        parent.addView(stopOverlayBtn, insertAt++);
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        refreshList();
        updateBatteryStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        updateOverlayFallback();
    }

    /** v9, report two-path add flow: the "+" entry opens a choice
     *  dialog. Both paths reuse the exact v6/v8 intents and result
     *  codes, so import, split warning and APK fallback behaviour
     *  are unchanged. */
    private void showAddAppChoice() {
        final CharSequence[] options = new CharSequence[]{
            "App đã cài trên điện thoại", "File APK"};
        new AlertDialog.Builder(this)
            .setTitle("Thêm app mới")
            .setItems(options, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        Intent i = new Intent(MainActivity.this, InstalledAppPickerActivity.class);
                        startActivityForResult(i, REQ_PICK_INSTALLED);
                    } else {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        startActivityForResult(i, REQ_PICK_APK);
                    }
                }
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    /** v10: in-app self-update wiring. The button lives inside the
     *  Notes section (see activity_main.xml) so the tidy main screen
     *  is untouched. Flow: tap -> check manifest (Releases fallback)
     *  -> if newer, a dialog shows version + changelog with
     *  Download/Cancel -> download with SHA-256 verification ->
     *  unknown-sources gate -> SYSTEM installer dialog (never
     *  silent). Every failure shows a short Vietnamese message and
     *  leaves the app usable; nothing here touches clone data. */
    private void setupUpdater() {
        final TextView versionText = findViewById(R.id.updateVersionText);
        final TextView updateStatus = findViewById(R.id.updateStatusText);
        final Button checkBtn = findViewById(R.id.btnCheckUpdate);
        if (versionText == null || checkBtn == null || updateStatus == null) return;
        versionText.setText("Phiên bản đang cài: "
                + AppUpdater.currentVersionLabel(this));
        final AppUpdater updater = new AppUpdater(this);
        checkBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                updateStatus.setText("Đang kiểm tra cập nhật…");
                checkBtn.setEnabled(false);
                updater.checkForUpdate(new AppUpdater.CheckCallback() {
                    @Override public void onStatus(String text) {
                        updateStatus.setText(text);
                    }
                    @Override public void onUpToDate(String currentLabel) {
                        checkBtn.setEnabled(true);
                        updateStatus.setText("Bạn đang dùng bản mới nhất ("
                                + currentLabel + ").");
                    }
                    @Override public void onUpdateAvailable(final AppUpdater.Info info) {
                        checkBtn.setEnabled(true);
                        String msg = "Có bản mới: " + info.versionName
                                + " (mã " + info.versionCode + ").\n"
                                + "Bản đang cài: "
                                + AppUpdater.currentVersionLabel(MainActivity.this)
                                + ".\n"
                                + (info.changelog != null && !info.changelog.isEmpty()
                                        ? info.changelog
                                        : "Không có ghi chú thay đổi.");
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Có bản cập nhật mới")
                            .setMessage(msg)
                            .setPositiveButton("Tải và cài đặt",
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        startUpdateDownload(updater, info, updateStatus, checkBtn);
                                    }
                                })
                            .setNegativeButton("Để sau", null)
                            .show();
                    }
                    @Override public void onError(String message) {
                        checkBtn.setEnabled(true);
                        updateStatus.setText(message);
                    }
                });
            }
        });
    }

    private void startUpdateDownload(final AppUpdater updater,
            final AppUpdater.Info info, final TextView updateStatus,
            final Button checkBtn) {
        if (!updater.canInstallUnknownApps()) {
            updateStatus.setText("Cần bật quyền “cài đặt app từ nguồn này” "
                    + "cho app chủ một lần trong Cài đặt, rồi bấm "
                    + "“Kiểm tra cập nhật” lại.");
            Toast.makeText(this,
                    "Bật “Cho phép cài đặt từ nguồn này” cho Muse Container, "
                    + "rồi quay lại bấm Kiểm tra cập nhật.",
                    Toast.LENGTH_LONG).show();
            try {
                startActivity(updater.unknownSourcesSettingsIntent());
            } catch (Exception e) {
                updateStatus.setText("Không mở được Cài đặt: " + e.getMessage()
                        + ". Mở Cài đặt > Ứng dụng > Muse Container > "
                        + "Cài đặt app không xác định rồi bật lên.");
            }
            return;
        }
        updateStatus.setText("Đang tải bản cập nhật…");
        checkBtn.setEnabled(false);
        updater.downloadUpdate(info, new AppUpdater.DownloadCallback() {
            @Override public void onProgress(int percent) {
                if (percent >= 0) {
                    updateStatus.setText("Đang tải bản cập nhật… " + percent + "%");
                }
            }
            @Override public void onDownloaded(java.io.File apk) {
                checkBtn.setEnabled(true);
                updateStatus.setText("Đã tải xong và kiểm tra mã khớp. "
                        + "Đang mở trình cài đặt của hệ thống…");
                try {
                    startActivity(updater.installIntent());
                } catch (Exception e) {
                    updateStatus.setText("Không mở được trình cài đặt: "
                            + e.getMessage());
                }
            }
            @Override public void onError(String message) {
                checkBtn.setEnabled(true);
                updateStatus.setText(message);
            }
        });
    }

    /** v8, fallback A4, shortened in v9: when the overlay permission
     *  is denied, the host must SAY SO instead of silently showing
     *  no bubbles — but only as ONE short line in the main flow.
     *  The full bubbles/fast-switch explanation now lives in the
     *  Notes section. Switching from this list (each clone's Launch
     *  button) stays the baseline path whether the overlay is
     *  granted or not. */
    private void updateOverlayFallback() {
        if (overlayExplainView == null) return;
        if (Settings.canDrawOverlays(this)) {
            overlayExplainView.setText("Bong bóng & chuyển nhanh: đã cấp quyền lớp phủ — xem Ghi chú để biết cách dùng.");
        } else {
            overlayExplainView.setText("\u26a0 Chưa cấp quyền lớp phủ: bong bóng và nút \u21c4 sẽ không hiện. " +
                "Bấm nút 5 để cấp; nếu không, chuyển clone bằng nút Mở trong danh sách. Chi tiết xem Ghi chú.");
        }
    }

    /** v8 styling helpers: coloured buttons with comfortable padding,
     *  replacing the flat grey default buttons the user criticised. */
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void stylePrimary(Button b) { b.setBackgroundResource(R.drawable.btn_primary); b.setTextColor(Color.WHITE); b.setPadding(dp(12), dp(10), dp(12), dp(10)); }
    private void styleSecondary(Button b) { b.setBackgroundResource(R.drawable.btn_secondary); b.setTextColor(Color.parseColor("#12333A")); b.setPadding(dp(12), dp(10), dp(12), dp(10)); }
    private void styleAccent(Button b) { b.setBackgroundResource(R.drawable.btn_accent); b.setTextColor(Color.parseColor("#12333A")); b.setPadding(dp(12), dp(10), dp(12), dp(10)); }
    private void styleDanger(Button b) { b.setBackgroundResource(R.drawable.btn_danger); b.setTextColor(Color.WHITE); b.setPadding(dp(12), dp(10), dp(12), dp(10)); }

    /** v6, direction A + VPN honesty (CRITICAL): the VPN strip is UI
     *  ONLY in this build. There is no working VPN backend (the engine
     *  ProxyVpnService forwards nothing) and no WireGuard configuration
     *  exists yet, so this code must NEVER show a fake Connected state
     *  or a fake IP, and the connect button stays disabled. Country
     *  selection is stored for later use only; it changes nothing on
     *  the network. The strip collapses to its one-line summary. */
    private void setupVpnStrip() {
        vpnStripDetails = findViewById(R.id.vpnStripDetails);
        vpnStripSummary = findViewById(R.id.vpnStripSummary);
        vpnIpText = findViewById(R.id.vpnIpText);
        vpnStateText = findViewById(R.id.vpnStateText);
        vpnCollapseBtn = findViewById(R.id.vpnCollapseBtn);
        Button connectBtn = findViewById(R.id.vpnConnectBtn);
        // Disabled, not hidden: hiding it would look like connecting is
        // possible elsewhere. The label itself says why it is off.
        connectBtn.setEnabled(false);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(MainActivity.this,
                    "VPN chưa có server để kết nối. Dải này hiện chỉ là giao diện — cần có cấu hình WireGuard trước.",
                    Toast.LENGTH_LONG).show();
            }
        });
        Spinner countrySpinner = findViewById(R.id.vpnCountrySpinner);
        final String[] countries = new String[]{
            "— Chưa chọn —", "Singapore", "Nhật Bản (Japan)", "Hà Lan (Netherlands)",
            "Hoa Kỳ (United States)", "Đức (Germany)", "Anh (United Kingdom)",
            "Hàn Quốc (South Korea)", "Úc (Australia)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, countries);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        countrySpinner.setAdapter(adapter);
        final android.content.SharedPreferences prefs =
            getSharedPreferences("vpn_prefs", MODE_PRIVATE);
        String savedCountry = prefs.getString("country", countries[0]);
        int savedIndex = 0;
        for (int i = 0; i < countries.length; i++) {
            if (countries[i].equals(savedCountry)) { savedIndex = i; break; }
        }
        countrySpinner.setSelection(savedIndex);
        final boolean expanded = prefs.getBoolean("stripExpanded", true);
        vpnStripDetails.setVisibility(expanded ? View.VISIBLE : View.GONE);
        vpnCollapseBtn.setText(expanded ? "Thu gọn dải VPN" : "Mở rộng dải VPN");
        vpnCollapseBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean nowExpanded = vpnStripDetails.getVisibility() != View.VISIBLE;
                vpnStripDetails.setVisibility(nowExpanded ? View.VISIBLE : View.GONE);
                vpnCollapseBtn.setText(nowExpanded ? "Thu gọn dải VPN" : "Mở rộng dải VPN");
                prefs.edit().putBoolean("stripExpanded", nowExpanded).apply();
            }
        });
        countrySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putString("country", countries[position]).apply();
                updateVpnSummary(countries[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        updateVpnSummary(countries[savedIndex]);
    }

    /** The strip NEVER reports connected and NEVER shows an IP: there
     *  is no backend to report from. Only the chosen country (for later
     *  use) and the honest Off state are shown. */
    private void updateVpnSummary(String country) {
        String summary = "VPN chung: Tắt — chưa kết nối | Quốc gia: " + country + " | IP: —";
        vpnStripSummary.setText(summary);
        vpnIpText.setText("IP hiện tại: — (chưa kết nối)");
        vpnStateText.setText("Trạng thái: Tắt — chưa có server VPN");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // v6, direction P1: result from the installed-app picker.
        if (requestCode == REQ_PICK_INSTALLED) {
            if (resultCode != RESULT_OK || data == null) {
                String msg = data != null ? data.getStringExtra("error") : null;
                if (msg == null || msg.isEmpty()) {
                    msg = "No installed app was picked. Use 'Pick installed app' again, or 'Pick APK file' instead.";
                }
                Log.i(TAG_IMPORT, "Installed-app picker: " + msg);
                statusText.setText(msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                return;
            }
            String apkPath = data.getStringExtra("apkPath");
            importedPackage = data.getStringExtra("package");
            importedVersion = data.getStringExtra("version");
            String sha = data.getStringExtra("sha256");
            importedSplit = data.getBooleanExtra("split", false);
            importedApk = apkPath == null ? null : new File(apkPath);
            String msg = "Imported from installed app: package=" + importedPackage +
                " version=" + importedVersion + "\nSource: installed app on this phone (copied to app-private imports storage, NOT bundled into this host app, NOT modified or re-signed).";
            if (importedSplit) {
                msg += "\n⚠ SPLIT APK WARNING: this app was installed as a base APK plus split APKs. " +
                    "The engine install path only installs a single APK file, so this clone may be INCOMPLETE " +
                    "(missing resources, language, or native libraries). Launch it to check — do not assume it is whole.";
            }
            Log.i(TAG_IMPORT, "Installed-app import OK: package=" + importedPackage + " version=" + importedVersion + " split=" + importedSplit + " sha256=" + sha);
            FileLogger.log(this, TAG_IMPORT, "Installed-app import OK: package=" + importedPackage + " version=" + importedVersion + " split=" + importedSplit);
            statusText.setText(msg);
            Toast.makeText(this, "Đã nhập: " + importedPackage + (importedSplit ? " (SPLIT — có thể không đầy đủ)" : ""), Toast.LENGTH_LONG).show();
            return;
        }
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
            importedSplit = false;
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
                importedApk.getAbsolutePath(), CloneStore.sha256(importedApk), false, System.currentTimeMillis(), maxUid + 1,
                false, false, importedSplit);
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
        // v9: list header is ONE short, accurate count line only. The
        // target-online and notifications paragraphs moved to Notes.
        TextView header = new TextView(this);
        header.setText(clones.size() + " clone, " + activeCount + " đang hoạt động");
        header.setTextSize(14f);
        header.setTextColor(Color.parseColor("#5A6E72"));
        clonesContainer.addView(header);
        for (final CloneEntry c : clones) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            // v8: clone card, not a flat grey row: white card, rounded
            // corners (drawable), padding, clear gap to the next card.
            row.setBackgroundResource(R.drawable.card_bg);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(12);
            row.setLayoutParams(rowLp);
            // v6, direction A: header row = icon + name/status + Launch.
            // All other per-clone controls live in a collapsible
            // details container below the header (expanded rows only).
            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            headerRow.setPadding(0, 0, 0, dp(8));
            try {
                android.graphics.drawable.Drawable icon =
                    getPackageManager().getApplicationIcon(c.sourcePackage);
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(icon);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp48(), dp48());
                iv.setLayoutParams(ip);
                headerRow.addView(iv);
            } catch (Exception ignored) {
                // No real app with this package installed (e.g. the APK
                // was imported as a file and never installed normally):
                // fall back to a letter tile, same as the bubbles.
                TextView letter = new TextView(this);
                letter.setText((c.name != null && !c.name.isEmpty())
                    ? c.name.substring(0, 1).toUpperCase() : "?");
                letter.setTextSize(22f);
                letter.setGravity(android.view.Gravity.CENTER);
                android.graphics.drawable.GradientDrawable ld = new android.graphics.drawable.GradientDrawable();
                ld.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                ld.setColor(Color.parseColor("#004D5C"));
                letter.setBackground(ld);
                letter.setTextColor(Color.WHITE);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp48(), dp48());
                letter.setLayoutParams(lp);
                headerRow.addView(letter);
            }
            TextView tv = new TextView(this);
            String splitNote = c.splitApk
                ? "\n⚠ Split APK — bản clone có thể không đầy đủ (engine chỉ cài file APK chính)."
                : "";
            tv.setText(c.name + " — " + c.sourcePackage + " " + c.sourceVersionName +
                " [" + (c.active ? "ACTIVE" : "idle") + "]" + splitNote +
                "\ndata: clones/" + c.id + "/");
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(tp);
            tv.setTextSize(16f);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setTextColor(Color.parseColor("#12333A"));
            headerRow.addView(tv);
            final LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(0, dp(8), 0, 0);
            final Button expandBtn = new Button(this);
            final boolean startExpanded = expandedIds.contains(c.id);
            details.setVisibility(startExpanded ? View.VISIBLE : View.GONE);
            expandBtn.setText(startExpanded ? "Thu gọn ▴" : "Mở rộng ▾");
            styleAccent(expandBtn);
            expandBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean nowExpanded = details.getVisibility() != View.VISIBLE;
                    details.setVisibility(nowExpanded ? View.VISIBLE : View.GONE);
                    expandBtn.setText(nowExpanded ? "Thu gọn ▴" : "Mở rộng ▾");
                    if (nowExpanded) expandedIds.add(c.id); else expandedIds.remove(c.id);
                }
            });
            headerRow.addView(expandBtn);
            row.addView(headerRow);
            Button launchBtn = new Button(this);
            launchBtn.setText("Launch clone");
            stylePrimary(launchBtn);
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
            styleSecondary(toggleBtn);
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
            styleSecondary(updateBtn);
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
            styleDanger(deleteBtn);
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
            headerRow.addView(launchBtn);
            details.addView(toggleBtn);
            details.addView(updateBtn);
            details.addView(deleteBtn);
            // v5: per-clone overlay toggles, default OFF. Bubbles are
            // shown only for ACTIVE clones by BubbleService; toggling
            // here only saves the preference and refreshes the overlay
            // if it is already running (it never starts the overlay
            // on its own, so a toggle cannot surprise the user with
            // floating icons).
            Button bubbleBtn = new Button(this);
            bubbleBtn.setText(c.bubbleEnabled ? "Bubble: ON (tap to turn OFF)" : "Bubble: OFF (tap to turn ON)");
            styleSecondary(bubbleBtn);
            bubbleBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        List<CloneEntry> current = CloneStore.load(MainActivity.this);
                        List<CloneEntry> updated = new ArrayList<>();
                        for (CloneEntry e : current) {
                            updated.add(e.id.equals(c.id) ? e.withBubble(!e.bubbleEnabled) : e);
                        }
                        CloneStore.save(MainActivity.this, updated);
                        if (!c.active) {
                            Toast.makeText(MainActivity.this,
                                "Bubble preference saved, but bubbles appear only for ACTIVE clones. Set this clone active and it will appear when the overlay is running.",
                                Toast.LENGTH_LONG).show();
                        }
                        refreshOverlay();
                        refreshList();
                    } catch (Exception ex) {
                        statusText.setText("Bubble toggle failed: " + ex.getMessage());
                    }
                }
            });
            Button switchBtn = new Button(this);
            switchBtn.setText(c.switchEnabled ? "Fast switch: ON (tap to turn OFF)" : "Fast switch: OFF (tap to turn ON)");
            styleSecondary(switchBtn);
            switchBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        List<CloneEntry> current = CloneStore.load(MainActivity.this);
                        List<CloneEntry> updated = new ArrayList<>();
                        for (CloneEntry e : current) {
                            updated.add(e.id.equals(c.id) ? e.withSwitch(!e.switchEnabled) : e);
                        }
                        CloneStore.save(MainActivity.this, updated);
                        refreshOverlay();
                        refreshList();
                    } catch (Exception ex) {
                        statusText.setText("Fast-switch toggle failed: " + ex.getMessage());
                    }
                }
            });
            details.addView(bubbleBtn);
            details.addView(switchBtn);
            // v8, per-clone VPN switch (direction A): UI ONLY, like
            // the shared strip. No backend, no WireGuard config, so
            // this switch stays DISABLED and unchecked; it must
            // never imply this clone is on a VPN. Country choice
            // in the strip is stored for later use only.
            Switch vpnSwitch = new Switch(this);
            vpnSwitch.setText("VPN riêng cho clone này — Tắt (chưa có server VPN)");
            vpnSwitch.setChecked(false);
            vpnSwitch.setEnabled(false);
            details.addView(vpnSwitch);
            TextView vpnNote = new TextView(this);
            vpnNote.setText("VPN của clone này hiện chỉ là giao diện, chưa kết nối được — cần có cấu hình server trước. Không hiển thị Đã kết nối hay IP giả.");
            vpnNote.setTextSize(13f);
            vpnNote.setTextColor(Color.parseColor("#5A6E72"));
            details.addView(vpnNote);
            row.addView(details);
            clonesContainer.addView(row);
        }
        if (clones.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No clones yet. Pick an APK, then Create clone.");
            clonesContainer.addView(tv);
        }
    }

    private int dp48() {
        return (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
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

    /** v9: the status line is ONE short, accurate line — never the
     *  stale v8 default ("no APK imported yet") which stayed on
     *  screen even with clones present. A clone-store error, if any,
     *  is left untouched so refreshList's error is not overwritten. */
    private void updateBatteryStatus() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        String cur = statusText.getText() == null ? "" : statusText.getText().toString();
        if (cur.contains("Clone store error")) return;
        statusText.setText("Pin: " + (pm.isIgnoringBatteryOptimizations(getPackageName())
            ? "đã miễn tối ưu hóa" : "chưa miễn tối ưu hóa"));
    }

    private void startKeepAlive() {
        Intent i = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, "Keep-alive service started (prototype only)", Toast.LENGTH_SHORT).show();
    }

    /** v5: ask the overlay to rebuild its views from CloneStore, but
     *  ONLY if it is already running. A toggle must never start the
     *  overlay by itself. */
    private void refreshOverlay() {
        if (BubbleService.isRunning()) {
            Intent i = new Intent(this, BubbleService.class);
            i.setAction(BubbleService.ACTION_REFRESH);
            startService(i);
        }
    }
}
