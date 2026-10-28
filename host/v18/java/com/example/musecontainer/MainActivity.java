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
import android.widget.ScrollView;
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
    /** v11, direction B: at most ONE clone row expanded at a time. */
    private String expandedId = null;
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
    /** v13: bottom tabs (no Fragment dependency). Both tab contents
     *  stay alive in the view tree; switching only changes
     *  visibility, so clone list, expanded row and VPN strip state
     *  survive a switch. Saved across recreate (e.g. language
     *  toggle) via onSaveInstanceState. */
    private ScrollView cloneScrollView, notesScrollView, utilsScrollView;
    private Button tabClonesBtn, tabNotesBtn, tabUtilsBtn;
    /** v14: 0 = Clone, 1 = Notes, 2 = Utilities. */
    private int currentTab = 0;
    private File importedApk;
    private String importedPackage, importedVersion;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // v11: language toggle, top of the main screen. v13
        // header: circular logo left, language toggle right only;
        // no app-name text, no header Notes button (Notes is its
        // own tab). The toggle works on all three tabs: tapping it
        // recreates the activity, and the selected tab is restored
        // from the saved instance state, so switching language on
        // the Notes tab does not drop the user back to Clone.
        // Label always shows the OTHER language; tapping
        // saves the choice and recreates so every string refreshes.
        final Button langBtn = findViewById(R.id.btnLanguageToggle);
        if (langBtn != null) {
            langBtn.setText(Lang.toggleLabel(this));
            langBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Lang.setLanguage(MainActivity.this,
                        Lang.isEnglish(MainActivity.this) ? Lang.VI : Lang.EN);
                    recreate();
                }
            });
        }
        statusText = findViewById(R.id.statusText);
        clonesContainer = findViewById(R.id.clonesContainer);
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
        setupVpnStrip();
        // v13: Notes is a SEPARATE TAB, not a card at the bottom.
        // v12 was a half-move (header button, content still at the
        // bottom, tap scrolled down to it) and the user rejected
        // exactly that. Nothing in this file references the old
        // header button, bottom card, or scroll-down behaviour
        // any more: the tab bar below is the only way to Notes.
        cloneScrollView = findViewById(R.id.cloneScrollView);
        notesScrollView = findViewById(R.id.notesScrollView);
        utilsScrollView = findViewById(R.id.utilsScrollView);
        tabClonesBtn = findViewById(R.id.btnTabClones);
        tabNotesBtn = findViewById(R.id.btnTabNotes);
        tabUtilsBtn = findViewById(R.id.btnTabUtils);
        if (savedInstanceState != null) {
            // v14: tab_index is authoritative; fall back to the old
            // boolean so a state saved by v13 still restores Notes.
            currentTab = savedInstanceState.getInt("tab_index",
                savedInstanceState.getBoolean("tab_notes", false) ? 1 : 0);
            if (currentTab < 0 || currentTab > 2) currentTab = 0;
        }
        if (tabClonesBtn != null) {
            tabClonesBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTab(0); }
            });
        }
        if (tabNotesBtn != null) {
            tabNotesBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTab(1); }
            });
        }
        if (tabUtilsBtn != null) {
            tabUtilsBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { switchTab(2); }
            });
        }
        switchTab(currentTab);
        // v11: battery / keep-alive / refresh relocated from the main
        // flow into Notes (tidy rule: gathered, not scattered).
        findViewById(R.id.btnBatteryNotes).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestBatteryExemption(); }
        });
        findViewById(R.id.btnKeepAliveNotes).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startKeepAlive(); }
        });
        findViewById(R.id.btnRefreshNotes).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refreshList(); }
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
        // v11/v13: overlay controls live on the Notes tab, not
        // in the main flow — the Clone tab is the clone list. Only the short
        // overlay status line below is refreshed by
        // updateOverlayFallback; the full explanation already lives
        // in Notes. The Notes tab content is always visible when
        // its tab is selected — no toggle, no collapse.
        final LinearLayout notesContent = findViewById(R.id.notesContent);
        final LinearLayout utilsOverlayContainer = findViewById(R.id.utilsOverlayContainer);
        overlayExplainView = new TextView(this);
        overlayExplainView.setText(R.string.overlay_explain_default);
        overlayExplainView.setTextSize(14f);
        overlayExplainView.setTextColor(Color.parseColor("#5A6E72"));
        overlayExplainView.setPadding(dp(4), dp(4), dp(4), dp(12));
        notesContent.addView(overlayExplainView);
        updateOverlayFallback();
        // v14: overlay (bubble + fast-switch) CONTROL buttons moved
        // to the Utilities tab; only the explanatory line above
        // stays on Notes. The section heading is in the Utils
        // layout now, so no programmatic title is added here.
        Button overlayPermBtn = new Button(this);
        overlayPermBtn.setText(R.string.notes_overlay_perm_btn);
        styleSecondary(overlayPermBtn);
        overlayPermBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, R.string.overlay_already_granted, Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(MainActivity.this, R.string.overlay_why, Toast.LENGTH_LONG).show();
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    statusText.setText(getString(R.string.overlay_intent_failed, e.getMessage()));
                }
            }
        });
        utilsOverlayContainer.addView(overlayPermBtn);
        Button startOverlayBtn = new Button(this);
        startOverlayBtn.setText(R.string.notes_start_overlay);
        stylePrimary(startOverlayBtn);
        startOverlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, R.string.overlay_grant_first, Toast.LENGTH_LONG).show();
                    return;
                }
                Intent i = new Intent(MainActivity.this, BubbleService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
                Toast.makeText(MainActivity.this, R.string.overlay_started, Toast.LENGTH_LONG).show();
            }
        });
        utilsOverlayContainer.addView(startOverlayBtn);
        Button stopOverlayBtn = new Button(this);
        stopOverlayBtn.setText(R.string.btn_stop_overlay);
        styleSecondary(stopOverlayBtn);
        stopOverlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, BubbleService.class);
                i.setAction(BubbleService.ACTION_STOP);
                startService(i);
                Toast.makeText(MainActivity.this, R.string.overlay_stopping, Toast.LENGTH_SHORT).show();
            }
        });
        utilsOverlayContainer.addView(stopOverlayBtn);
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        refreshList();
        updateBatteryStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshList();
        // v12: clear a stale launch failure on return. A launch
        // success already replaces it in launchCloneRow; this covers
        // the "next refresh" case without wiping messages that other
        // flows set after their own refreshList() call.
        if (statusText != null) {
            String cur = statusText.getText() == null ? "" : statusText.getText().toString();
            String failPrefix = getString(R.string.launch_failed_prefix);
            if (!failPrefix.isEmpty() && cur.startsWith(failPrefix)) statusText.setText("");
        }
        updateOverlayFallback();
    }

    /** v14: show one of three tabs, hide the other two; all views
     *  stay alive, so nothing is rebuilt and no state is lost.
     *  The active tab gets the primary (teal) style, the inactive
     *  ones secondary. 0 = Clone, 1 = Notes, 2 = Utilities. */
    private void switchTab(int tab) {
        currentTab = tab;
        if (cloneScrollView != null) {
            cloneScrollView.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        }
        if (notesScrollView != null) {
            notesScrollView.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        }
        if (utilsScrollView != null) {
            utilsScrollView.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        }
        if (tabClonesBtn != null) {
            if (tab == 0) stylePrimary(tabClonesBtn); else styleSecondary(tabClonesBtn);
        }
        if (tabNotesBtn != null) {
            if (tab == 1) stylePrimary(tabNotesBtn); else styleSecondary(tabNotesBtn);
        }
        if (tabUtilsBtn != null) {
            if (tab == 2) stylePrimary(tabUtilsBtn); else styleSecondary(tabUtilsBtn);
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab_index", currentTab);
        outState.putBoolean("tab_notes", currentTab == 1);
    }

    /** v14: back on the Notes or Utilities tab returns to the
     *  Clone tab first; only back on the Clone tab exits. */
    @Override public void onBackPressed() {
        if (currentTab != 0) { switchTab(0); return; }
        super.onBackPressed();
    }

    /** v9, report two-path add flow: the "+" entry opens a choice
     *  dialog. Both paths reuse the exact v6/v8 intents and result
     *  codes, so import, split warning and APK fallback behaviour
     *  are unchanged. */
    private void showAddAppChoice() {
        final CharSequence[] options = new CharSequence[]{
            getString(R.string.add_app_installed), getString(R.string.add_app_apk)};
        new AlertDialog.Builder(this)
            .setTitle(R.string.add_app_title)
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
            .setNegativeButton(R.string.add_app_cancel, null)
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
        versionText.setText(getString(R.string.update_version_prefix,
                AppUpdater.currentVersionLabel(this)));
        final AppUpdater updater = new AppUpdater(this);
        checkBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                updateStatus.setText(R.string.update_checking);
                checkBtn.setEnabled(false);
                updater.checkForUpdate(new AppUpdater.CheckCallback() {
                    @Override public void onStatus(String text) {
                        updateStatus.setText(text);
                    }
                    @Override public void onUpToDate(String currentLabel) {
                        checkBtn.setEnabled(true);
                        updateStatus.setText(getString(R.string.update_uptodate, currentLabel));
                    }
                    @Override public void onUpdateAvailable(final AppUpdater.Info info) {
                        checkBtn.setEnabled(true);
                        String msg = getString(R.string.update_available_msg, info.versionName,
                                info.versionCode,
                                AppUpdater.currentVersionLabel(MainActivity.this),
                                (info.changelog != null && !info.changelog.isEmpty()
                                        ? info.changelog
                                        : getString(R.string.update_no_changelog)));
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.update_available_title)
                            .setMessage(msg)
                            .setPositiveButton(R.string.update_download_btn,
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface d, int w) {
                                        startUpdateDownload(updater, info, updateStatus, checkBtn);
                                    }
                                })
                            .setNegativeButton(R.string.update_later, null)
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
            updateStatus.setText(R.string.update_unknown_sources);
            Toast.makeText(this, R.string.update_unknown_sources_toast, Toast.LENGTH_LONG).show();
            try {
                startActivity(updater.unknownSourcesSettingsIntent());
            } catch (Exception e) {
                updateStatus.setText(getString(R.string.update_settings_failed, e.getMessage()));
            }
            return;
        }
        updateStatus.setText(R.string.update_downloading);
        checkBtn.setEnabled(false);
        updater.downloadUpdate(info, new AppUpdater.DownloadCallback() {
            @Override public void onProgress(int percent) {
                if (percent >= 0) {
                    updateStatus.setText(getString(R.string.update_downloading_pct, percent));
                }
            }
            @Override public void onDownloaded(java.io.File apk) {
                checkBtn.setEnabled(true);
                updateStatus.setText(R.string.update_downloaded_ok);
                try {
                    startActivity(updater.installIntent());
                } catch (Exception e) {
                    updateStatus.setText(getString(R.string.update_installer_failed, e.getMessage()));
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
            overlayExplainView.setText(R.string.overlay_granted_note);
        } else {
            overlayExplainView.setText(R.string.overlay_denied_note);
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
                Toast.makeText(MainActivity.this, R.string.vpn_toast_no_server, Toast.LENGTH_LONG).show();
            }
        });
        Spinner countrySpinner = findViewById(R.id.vpnCountrySpinner);
        final String[] countries = getResources().getStringArray(R.array.vpn_countries);
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
        final boolean expanded = prefs.getBoolean("stripExpanded", false);
        vpnStripDetails.setVisibility(expanded ? View.VISIBLE : View.GONE);
        vpnCollapseBtn.setText(expanded ? getString(R.string.vpn_collapse) : getString(R.string.vpn_expand));
        vpnCollapseBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean nowExpanded = vpnStripDetails.getVisibility() != View.VISIBLE;
                vpnStripDetails.setVisibility(nowExpanded ? View.VISIBLE : View.GONE);
                vpnCollapseBtn.setText(nowExpanded ? getString(R.string.vpn_collapse) : getString(R.string.vpn_expand));
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
        String summary = getString(R.string.vpn_summary, country);
        vpnStripSummary.setText(summary);
        vpnIpText.setText(R.string.vpn_ip);
        vpnStateText.setText(R.string.vpn_state);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // v6, direction P1: result from the installed-app picker.
        if (requestCode == REQ_PICK_INSTALLED) {
            if (resultCode != RESULT_OK || data == null) {
                String msg = data != null ? data.getStringExtra("error") : null;
                if (msg == null || msg.isEmpty()) {
                    msg = getString(R.string.picker_no_pick);
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
            String msg = getString(R.string.import_installed_msg, importedPackage, importedVersion);
            if (importedSplit) {
                msg += getString(R.string.import_split_warn);
            }
            Log.i(TAG_IMPORT, "Installed-app import OK: package=" + importedPackage + " version=" + importedVersion + " split=" + importedSplit + " sha256=" + sha);
            FileLogger.log(this, TAG_IMPORT, "Installed-app import OK: package=" + importedPackage + " version=" + importedVersion + " split=" + importedSplit);
            statusText.setText(msg);
            Toast.makeText(this, getString(R.string.import_installed_toast, importedPackage, importedSplit ? getString(R.string.import_split_suffix) : ""), Toast.LENGTH_LONG).show();
            promptForCloneName();
            return;
        }
        // v4d: previously this early return was completely silent, so a
        // cancelled picker (or a picker returning no data) looked exactly
        // like a successful import that showed nothing. Log + show it now.
        if (requestCode != REQ_PICK_APK) { Log.d(TAG_IMPORT, "onActivityResult ignored: requestCode=" + requestCode + " (not PICK)"); return; }
        Log.i(TAG_IMPORT, "onActivityResult: resultCode=" + resultCode + " data=" + (data == null ? "null" : "present") + " uri=" + (data == null || data.getData() == null ? "null" : data.getData().toString()));
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            String msg = getString(R.string.pick_cancelled, resultCode);
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
                String msg = getString(R.string.import_failed_not_apk);
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
            statusText.setText(getString(R.string.imported_msg, name, importedPackage,
                importedVersion, (int) (dest.length() / 1024 / 1024)));
            Toast.makeText(this, getString(R.string.imported_toast, importedPackage, importedVersion, (int) (dest.length() / 1024 / 1024)), Toast.LENGTH_LONG).show();
            promptForCloneName();
        } catch (Exception e) {
            Log.e(TAG_IMPORT, "Import failed with exception", e);
            FileLogger.log(this, TAG_IMPORT, "Import failed with exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            statusText.setText(getString(R.string.import_failed, e.getMessage()));
            Toast.makeText(this, getString(R.string.import_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    /** v11: clone creation now follows the add flow — the user
     *  picks an app with "+", then this name prompt appears. The
     *  old main-flow name field and "Create named clone" button are
     *  gone; the create logic itself is unchanged. */
    private void promptForCloneName() {
        if (importedApk == null) return;
        final EditText input = new EditText(this);
        input.setHint(R.string.create_name_hint);
        String pkg = importedPackage != null ? importedPackage : "?";
        String ver = importedVersion != null ? importedVersion : "";
        new AlertDialog.Builder(this)
            .setTitle(R.string.create_name_title)
            .setMessage(getString(R.string.create_name_msg, pkg, ver))
            .setView(input)
            .setPositiveButton(R.string.create_name_create, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    createClone(input.getText().toString().trim());
                }
            })
            .setNegativeButton(R.string.create_name_cancel, null)
            .show();
    }

    private void createClone(String name) {
        Log.i(TAG_IMPORT, "Create clone pressed: importedApk=" + (importedApk == null ? "null" : importedApk.getAbsolutePath()) + " package=" + importedPackage + " version=" + importedVersion);
        if (importedApk == null) { Toast.makeText(this, R.string.pick_apk_first, Toast.LENGTH_SHORT).show(); return; }
        try {
            List<CloneEntry> clones = CloneStore.load(this);
            if (name == null || name.isEmpty()) name = "Clone " + (clones.size() + 1);
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
            Toast.makeText(this, getString(R.string.clone_created, name), Toast.LENGTH_SHORT).show();
            refreshList();
        } catch (Exception e) {
            statusText.setText(getString(R.string.create_clone_failed, e.getMessage()));
        }
    }

    /** v11, sample row behaviour: tapping the whole row launches.
     *  Engine launch still runs off the UI thread (v4a ANR lesson);
     *  the row is disabled while launching, like the old Launch
     *  button was. */
    private void launchCloneRow(final CloneEntry c, final View row) {
        row.setEnabled(false);
        statusText.setText(getString(R.string.launching, c.name));
        new Thread(new Runnable() {
            @Override public void run() {
                final EngineLauncher.Result r = EngineLauncher.launchClone(c, MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        row.setEnabled(true);
                        statusText.setText((r.launched ? getString(R.string.launched_prefix) : getString(R.string.launch_failed_prefix)) + r.detail);
                    }
                });
            }
        }).start();
    }

    /** v11, sample row behaviour: long-press opens Rename /
     *  Set active-or-idle / Delete. No other row menu exists. */
    private void showRowMenu(final CloneEntry c) {
        final CharSequence[] options = new CharSequence[]{
            getString(R.string.row_menu_rename),
            getString(c.active ? R.string.row_menu_set_idle : R.string.row_menu_set_active),
            getString(R.string.row_menu_delete)};
        new AlertDialog.Builder(this)
            .setTitle(getString(R.string.row_menu_title, c.name))
            .setItems(options, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    if (which == 0) showRenameDialog(c);
                    else if (which == 1) setActiveState(c, !c.active);
                    else deleteCloneEntry(c);
                }
            })
            .setNegativeButton(R.string.row_menu_cancel, null)
            .show();
    }

    private void showRenameDialog(final CloneEntry c) {
        final EditText input = new EditText(this);
        input.setText(c.name);
        input.setHint(R.string.rename_hint);
        new AlertDialog.Builder(this)
            .setTitle(R.string.rename_title)
            .setView(input)
            .setPositiveButton(R.string.rename_save, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(MainActivity.this, R.string.rename_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        List<CloneEntry> current = CloneStore.load(MainActivity.this);
                        List<CloneEntry> updated = new ArrayList<>();
                        for (CloneEntry e : current) {
                            updated.add(e.id.equals(c.id) ? e.withName(newName) : e);
                        }
                        CloneStore.save(MainActivity.this, updated);
                        Toast.makeText(MainActivity.this, getString(R.string.renamed_msg, newName), Toast.LENGTH_SHORT).show();
                        refreshList();
                    } catch (Exception ex) {
                        statusText.setText(getString(R.string.toggle_failed, ex.getMessage()));
                    }
                }
            })
            .setNegativeButton(R.string.rename_cancel, null)
            .show();
    }

    /** v11: active/idle (the sample's freeze equivalent) moved from
     *  a details button into the long-press menu. Cap logic is
     *  unchanged: at most MAX_ACTIVE_CLONES active at once. */
    private void setActiveState(final CloneEntry c, boolean makeActive) {
        try {
            List<CloneEntry> current = CloneStore.load(MainActivity.this);
            if (makeActive) {
                int activeCount = 0;
                for (CloneEntry e : current) if (e.active) activeCount++;
                if (activeCount >= MAX_ACTIVE_CLONES) {
                    String msg = getString(R.string.active_cap_msg, MAX_ACTIVE_CLONES);
                    statusText.setText(msg);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    return;
                }
            }
            List<CloneEntry> updated = new ArrayList<>();
            for (CloneEntry e : current) {
                updated.add(e.id.equals(c.id) ? e.withActive(makeActive) : e);
            }
            CloneStore.save(MainActivity.this, updated);
            refreshList();
        } catch (Exception ex) {
            statusText.setText(getString(R.string.toggle_failed, ex.getMessage()));
        }
    }

    /** v11: delete moved from a details button into the long-press
     *  menu. Engine/user deletion and the virtualUserId safeguard
     *  are unchanged from v10. */
    private void deleteCloneEntry(final CloneEntry c) {
        try {
            EngineLauncher.Result r = EngineLauncher.deleteClone(c, MainActivity.this);
            List<CloneEntry> current = CloneStore.load(MainActivity.this);
            List<CloneEntry> updated = new ArrayList<>();
            for (CloneEntry e : current) if (!e.id.equals(c.id)) updated.add(e);
            CloneStore.save(MainActivity.this, updated);
            File dir = CloneStore.dataDir(MainActivity.this, c.id);
            deleteRecursive(dir);
            if (c.id.equals(expandedId)) expandedId = null;
            statusText.setText(getString(R.string.deleted_msg, c.name, r.detail));
            refreshList();
        } catch (Exception ex) {
            statusText.setText(getString(R.string.delete_failed, ex.getMessage()));
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
            err.setText(getString(R.string.clone_store_error, e.getMessage()));
            clonesContainer.addView(err);
            statusText.setText(getString(R.string.clone_store_error, e.getMessage()));
            return;
        }
        int activeCount = 0;
        for (CloneEntry c : clones) if (c.active) activeCount++;
        // v11: list header is ONE short, accurate count line only.
        TextView header = new TextView(this);
        header.setText(getString(R.string.clone_list_header, clones.size(), activeCount));
        header.setTextSize(14f);
        header.setTextColor(Color.parseColor("#5A6E72"));
        clonesContainer.addView(header);
        for (final CloneEntry c : clones) {
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            // v8: clone card, not a flat grey row: white card, rounded
            // corners (drawable), padding, clear gap to the next card.
            row.setBackgroundResource(R.drawable.card_bg);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(12);
            row.setLayoutParams(rowLp);
            // v11, sample row: icon + name/status + chevron. The
            // whole row launches; the chevron only expands details.
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
            LinearLayout nameCol = new LinearLayout(this);
            nameCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nameCol.setLayoutParams(np);
            TextView nameTv = new TextView(this);
            nameTv.setText(c.name);
            nameTv.setTextSize(16f);
            nameTv.setTypeface(null, android.graphics.Typeface.BOLD);
            nameTv.setTextColor(Color.parseColor("#12333A"));
            nameCol.addView(nameTv);
            TextView subTv = new TextView(this);
            String splitNote = c.splitApk ? getString(R.string.clone_split_note) : "";
            subTv.setText(c.sourcePackage + " " + c.sourceVersionName + splitNote);
            subTv.setTextSize(13f);
            subTv.setTextColor(Color.parseColor("#5A6E72"));
            nameCol.addView(subTv);
            LinearLayout statusLine = new LinearLayout(this);
            statusLine.setOrientation(LinearLayout.HORIZONTAL);
            statusLine.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView dot = new TextView(this);
            dot.setText("\u25cf ");
            dot.setTextSize(14f);
            dot.setTextColor(Color.parseColor(c.active ? "#2E7D32" : "#9AA5A8"));
            statusLine.addView(dot);
            TextView statusTv = new TextView(this);
            statusTv.setText(c.active ? getString(R.string.clone_status_active) : getString(R.string.clone_status_idle));
            statusTv.setTextSize(14f);
            statusTv.setTextColor(Color.parseColor("#5A6E72"));
            statusLine.addView(statusTv);
            nameCol.addView(statusLine);
            headerRow.addView(nameCol);
            // v12: an unambiguous launch cue. This TextView is NOT
            // clickable, so tapping it still launches via the row's
            // own click handler; it only labels what a row tap does.
            // It is NOT another stacked button and does not touch
            // the expand chevron, which keeps its own click handler.
            TextView openCue = new TextView(this);
            openCue.setText(R.string.row_open_label);
            openCue.setTextSize(14f);
            openCue.setTextColor(Color.parseColor("#004D5C"));
            openCue.setPadding(dp(8), dp(4), dp(4), dp(4));
            headerRow.addView(openCue);
            final LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(0, dp(8), 0, 0);
            final boolean startExpanded = c.id.equals(expandedId);
            details.setVisibility(startExpanded ? View.VISIBLE : View.GONE);
            final Button expandBtn = new Button(this);
            expandBtn.setText(startExpanded ? getString(R.string.collapse) : getString(R.string.expand));
            styleAccent(expandBtn);
            expandBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    expandedId = (details.getVisibility() != View.VISIBLE) ? c.id : null;
                    refreshList();
                }
            });
            headerRow.addView(expandBtn);
            row.addView(headerRow);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launchCloneRow(c, row); }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { showRowMenu(c); return true; }
            });
            Button updateBtn = new Button(this);
            updateBtn.setText(R.string.btn_update_apk);
            styleSecondary(updateBtn);
            updateBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (importedApk == null) {
                        Toast.makeText(MainActivity.this, R.string.pick_new_apk_first, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        EngineLauncher.Result r = EngineLauncher.prepareApkUpdate(c, MainActivity.this);
                        if (!r.launched) { statusText.setText(getString(R.string.update_failed_prefix) + r.detail); return; }
                        List<CloneEntry> current = CloneStore.load(MainActivity.this);
                        List<CloneEntry> updated = new ArrayList<>();
                        for (CloneEntry e : current) {
                            updated.add(e.id.equals(c.id) ? e.withApk(importedApk.getAbsolutePath(), importedVersion, CloneStore.sha256(importedApk)) : e);
                        }
                        CloneStore.save(MainActivity.this, updated);
                        statusText.setText(getString(R.string.updated_msg, c.name, importedVersion, r.detail));
                        refreshList();
                    } catch (Exception ex) {
                        statusText.setText(getString(R.string.update_failed, ex.getMessage()));
                    }
                }
            });
            details.addView(updateBtn);
            // v5: per-clone overlay toggles, default OFF. Bubbles are
            // shown only for ACTIVE clones by BubbleService; toggling
            // here only saves the preference and refreshes the overlay
            // if it is already running (it never starts the overlay
            // on its own, so a toggle cannot surprise the user with
            // floating icons).
            Button bubbleBtn = new Button(this);
            bubbleBtn.setText(c.bubbleEnabled ? getString(R.string.bubble_on) : getString(R.string.bubble_off));
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
                            Toast.makeText(MainActivity.this, R.string.bubble_inactive_toast, Toast.LENGTH_LONG).show();
                        }
                        refreshOverlay();
                        refreshList();
                    } catch (Exception ex) {
                        statusText.setText(getString(R.string.bubble_toggle_failed, ex.getMessage()));
                    }
                }
            });
            Button switchBtn = new Button(this);
            switchBtn.setText(c.switchEnabled ? getString(R.string.switch_on) : getString(R.string.switch_off));
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
                        statusText.setText(getString(R.string.switch_toggle_failed, ex.getMessage()));
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
            vpnSwitch.setText(R.string.vpn_per_clone);
            vpnSwitch.setChecked(false);
            vpnSwitch.setEnabled(false);
            details.addView(vpnSwitch);
            TextView vpnNote = new TextView(this);
            vpnNote.setText(R.string.vpn_per_clone_note);
            vpnNote.setTextSize(13f);
            vpnNote.setTextColor(Color.parseColor("#5A6E72"));
            details.addView(vpnNote);
            row.addView(details);
            clonesContainer.addView(row);
        }
        if (clones.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.no_clones);
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
            Toast.makeText(this, R.string.battery_already_exempt, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            statusText.setText(getString(R.string.battery_intent_failed, e.getMessage()));
        }
    }

    /** v9: the status line is ONE short, accurate line — never the
     *  stale v8 default ("no APK imported yet") which stayed on
     *  screen even with clones present. A clone-store error, if any,
     *  is left untouched so refreshList's error is not overwritten. */
    private void updateBatteryStatus() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        String cur = statusText.getText() == null ? "" : statusText.getText().toString();
        if (cur.contains(getString(R.string.clone_store_error, ""))) return; // v11: error prefix is language-dependent
        statusText.setText(getString(R.string.battery_status, pm.isIgnoringBatteryOptimizations(getPackageName())
            ? getString(R.string.battery_exempt) : getString(R.string.battery_not_exempt)));
    }

    private void startKeepAlive() {
        Intent i = new Intent(this, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        Toast.makeText(this, R.string.keepalive_started, Toast.LENGTH_SHORT).show();
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
