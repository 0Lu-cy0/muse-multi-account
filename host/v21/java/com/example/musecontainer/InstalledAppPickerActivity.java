package com.example.musecontainer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * v6, installed-app picker (direction P1): a full-screen single-tap
 * list of launchable installed apps, in addition to the APK-file flow
 * (which stays as the fallback). User apps first, then system apps,
 * each alphabetical inside its group. A search box filters by app
 * name or package name. Tapping one app imports it exactly like a
 * file import: the APK is copied to app-private imports storage with
 * a unique filename, its package/version/SHA-256 are recorded, and
 * the result is returned to MainActivity, which creates the clone
 * from it through the existing launch flow.
 *
 * Permission route: the manifest declares the narrow launcher-intent
 * <queries> (MAIN + LAUNCHER), so queryIntentActivities returns
 * launchable apps WITHOUT QUERY_ALL_PACKAGES. If the system still
 * returns an empty list (e.g. the query was not honoured), this
 * screen explains why instead of showing a blank list, and offers
 * the APK-file fallback — an empty list with no explanation is the
 * failure mode the report warns about.
 *
 * Split APKs (honesty, do not soften): an installed app may consist
 * of a base APK plus splitSourceDirs. This screen copies the base
 * AND every split file into imports storage (so nothing is silently
 * dropped at import time), but the engine install path
 * (BlackBoxCore.installPackageAsUser(File)) only installs ONE APK
 * file, and launchClone passes it only the base path. A split clone
 * may therefore be INCOMPLETE (missing resources, language, or
 * native libraries). Split rows carry a visible warning, the import
 * result carries split=true, and CloneEntry.splitApk persists it so
 * the clone row warns too. Do not present a split clone as whole.
 *
 * The host's own package is excluded: cloning the container itself
 * is nonsense and would only confuse the list.
 */
public class InstalledAppPickerActivity extends Activity {
    private static final String TAG = "MusePicker";
    private static final String TAG_IMPORT = "MuseImport";

    private static class Item {
        String label, packageName, versionName, sourceDir;
        String[] splitDirs;
        Drawable icon;
        boolean userApp;
    }

    private LinearLayout listContainer;
    private TextView statusText;
    private EditText searchInput;
    private final List<Item> allItems = new ArrayList<>();
    private boolean importing = false;

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        // v9: the picker used to render on the theme default (white)
        // because no background was set. Match the main screen's
        // pale-teal background and teal title colour.
        scroll.setBackgroundResource(R.color.screen_bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(R.string.picker_title);
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#004D5C"));
        root.addView(title);
        TextView explain = new TextView(this);
explain.setText(R.string.picker_explain);
        explain.setTextSize(14f);
        root.addView(explain);
        searchInput = new EditText(this);
        searchInput.setHint(R.string.picker_search_hint);
        root.addView(searchInput);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { renderList(); }
        });
        statusText = new TextView(this);
        statusText.setText(R.string.picker_loading);
        statusText.setTextSize(14f);
        root.addView(statusText);
        Button backBtn = new Button(this);
        backBtn.setText(R.string.picker_back);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        root.addView(backBtn);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        loadAppsInBackground();
    }

    private void loadAppsInBackground() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Item> items = new ArrayList<>();
                try {
                    PackageManager pm = getPackageManager();
                    Intent launcher = new Intent(Intent.ACTION_MAIN);
                    launcher.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> resolved = pm.queryIntentActivities(launcher, 0);
                    Log.i(TAG, "queryIntentActivities returned " + (resolved == null ? "null" : resolved.size()) + " launchable entries");
                    if (resolved != null) {
                        for (ResolveInfo r : resolved) {
                            if (r.activityInfo == null || r.activityInfo.applicationInfo == null) continue;
                            ApplicationInfo ai = r.activityInfo.applicationInfo;
                            if (getPackageName().equals(ai.packageName)) continue; // never clone the host itself
                            Item it = new Item();
                            it.packageName = ai.packageName;
                            it.sourceDir = ai.sourceDir;
                            it.splitDirs = ai.splitSourceDirs;
                            it.userApp = (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                                || (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
                            try {
                                CharSequence label = r.loadLabel(pm);
                                it.label = label != null ? label.toString() : ai.packageName;
                            } catch (Exception e) {
                                it.label = ai.packageName;
                            }
                            try {
                                PackageInfo pi = pm.getPackageInfo(ai.packageName, 0);
                                it.versionName = pi.versionName != null ? pi.versionName : "";
                            } catch (Exception e) {
                                it.versionName = "";
                            }
                            try {
                                it.icon = r.loadIcon(pm);
                            } catch (Exception ignored) {}
                            items.add(it);
                        }
                    }
                    // De-duplicate by package (one app can expose several
                    // launcher activities) and sort: user apps first,
                    // then alphabetical by label inside each group.
                    java.util.Map<String, Item> byPkg = new java.util.HashMap<>();
                    for (Item it : items) {
                        if (!byPkg.containsKey(it.packageName)) byPkg.put(it.packageName, it);
                    }
                    items.clear();
                    items.addAll(byPkg.values());
                    Collections.sort(items, new Comparator<Item>() {
                        @Override public int compare(Item a, Item b) {
                            if (a.userApp != b.userApp) return a.userApp ? -1 : 1;
                            return a.label.compareToIgnoreCase(b.label);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Loading installed apps failed", e);
                }
                final List<Item> result = items;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        allItems.clear();
                        allItems.addAll(result);
                        if (result.isEmpty()) {
statusText.setText(R.string.picker_empty);
                            Log.w(TAG, "Installed-app list came back EMPTY; showing explanation + APK-file fallback, not a blank list");
                        } else {
                            statusText.setText(getString(R.string.picker_found, result.size()));
                        }
                        renderList();
                    }
                });
            }
        }).start();
    }

    private void renderList() {
        listContainer.removeAllViews();
        String q = searchInput.getText().toString().trim().toLowerCase();
        int shown = 0;
        for (final Item it : allItems) {
            if (!q.isEmpty()
                && !it.label.toLowerCase().contains(q)
                && !it.packageName.toLowerCase().contains(q)) {
                continue;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackgroundResource(R.drawable.card_bg);
            row.setPadding(8, 16, 8, 16);
            if (it.icon != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(it.icon);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(96, 96);
                iv.setLayoutParams(ip);
                row.addView(iv);
            }
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            texts.setLayoutParams(tp);
            TextView name = new TextView(this);
            name.setText(it.label);
            name.setTextSize(16f);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setTextColor(Color.parseColor("#12333A"));
            texts.addView(name);
            TextView sub = new TextView(this);
            String subText = it.packageName + (it.versionName.isEmpty() ? "" : " " + it.versionName)
                + (it.userApp ? "" : getString(R.string.picker_system_app));
            sub.setTextColor(Color.parseColor("#5A6E72"));
            texts.addView(sub);
            boolean split = it.splitDirs != null && it.splitDirs.length > 0;
            if (split) {
                TextView warn = new TextView(this);
                warn.setText(getString(R.string.picker_split_warn, it.splitDirs.length));
                warn.setTextSize(13f);
                texts.addView(warn);
            }
            sub.setText(subText);
            row.addView(texts);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { importItem(it); }
            });
            listContainer.addView(row);
            shown++;
        }
        if (!allItems.isEmpty() && shown == 0) {
            TextView none = new TextView(this);
            none.setText(R.string.picker_no_match);
            listContainer.addView(none);
        }
    }

    private void importItem(final Item it) {
        if (importing) return;
        importing = true;
        statusText.setText(getString(R.string.picker_importing, it.label, it.packageName));
        Log.i(TAG_IMPORT, "Importing installed app: label=" + it.label + " package=" + it.packageName + " sourceDir=" + it.sourceDir + " splits=" + (it.splitDirs == null ? 0 : it.splitDirs.length));
        new Thread(new Runnable() {
            @Override public void run() {
                final List<File> copied = new ArrayList<>();
                try {
                    if (it.sourceDir == null) {
                        throw new IllegalStateException(getString(R.string.picker_no_source));
                    }
                    File importsDir = new File(getFilesDir(), "imports");
                    importsDir.mkdirs();
                    String prefix = UUID.randomUUID().toString().substring(0, 8) + "_";
                    File baseDest = copyFile(new File(it.sourceDir),
                        new File(importsDir, prefix + "base.apk"));
                    copied.add(baseDest);
                    if (it.splitDirs != null) {
                        for (int i = 0; i < it.splitDirs.length; i++) {
                            File splitDest = copyFile(new File(it.splitDirs[i]),
                                new File(importsDir, prefix + "split" + i + ".apk"));
                            copied.add(splitDest);
                        }
                    }
                    PackageInfo info = getPackageManager().getPackageArchiveInfo(baseDest.getAbsolutePath(), 0);
                    if (info == null || info.packageName == null) {
                        throw new IllegalStateException(getString(R.string.picker_unreadable));
                    }
                    if (!info.packageName.equals(it.packageName)) {
                        Log.w(TAG_IMPORT, "Archive package " + info.packageName + " differs from listed package " + it.packageName + "; trusting the archive");
                    }
                    String sha = CloneStore.sha256(baseDest);
                    boolean split = it.splitDirs != null && it.splitDirs.length > 0;
                    Log.i(TAG_IMPORT, "Installed-app import OK: package=" + info.packageName + " version=" + info.versionName + " sizeMB=" + (baseDest.length() / 1024 / 1024) + " split=" + split);
                    FileLogger.log(InstalledAppPickerActivity.this, TAG_IMPORT,
                        "Installed-app import OK: package=" + info.packageName + " version=" + info.versionName + " split=" + split);
                    Intent result = new Intent();
                    result.putExtra("apkPath", baseDest.getAbsolutePath());
                    result.putExtra("package", info.packageName);
                    result.putExtra("version", info.versionName != null ? info.versionName : "");
                    result.putExtra("sha256", sha);
                    result.putExtra("split", split);
                    setResult(RESULT_OK, result);
                    finish();
                } catch (final Exception e) {
                    for (File f : copied) { try { f.delete(); } catch (Exception ignored) {} }
                    Log.e(TAG_IMPORT, "Installed-app import failed for " + it.packageName, e);
                    FileLogger.log(InstalledAppPickerActivity.this, TAG_IMPORT,
                        "Installed-app import failed for " + it.packageName + ": " + e.getMessage());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            importing = false;
statusText.setText(getString(R.string.picker_import_failed, e.getMessage()));
                            Toast.makeText(InstalledAppPickerActivity.this,
                                getString(R.string.import_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private static File copyFile(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int n;
        long copied = 0;
        while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); copied += n; }
        in.close();
        out.close();
        Log.i(TAG_IMPORT, "Copied " + src.getAbsolutePath() + " -> " + dst.getAbsolutePath() + " bytes=" + copied);
        return dst;
    }
}
