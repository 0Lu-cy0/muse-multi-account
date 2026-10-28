package com.example.musecontainer;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.util.List;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
import top.niunaijun.blackbox.entity.pm.InstallResult;

/**
 * Real engine launcher, v3 fixes (audit 2026-10-27):
 * - Application context, not Activity context, is passed to
 *   doAttachBaseContext, so a recreated Activity cannot leak or
 *   invalidate the attached engine.
 * - userIdFor returns -1 when the clone is not in CloneStore, and
 *   launchClone fails explicitly. It NEVER defaults to 0, which
 *   previously could have opened another clone's virtual user.
 * - Launch verifies the imported APK's SHA-256 against the SHA
 *   recorded in CloneEntry. A mismatch (e.g. the import file was
 *   overwritten by a later import with the same filename) fails
 *   the launch instead of silently running a different APK.
 * - Shared APK path, lazy install on Launch of an ACTIVE clone only,
 *   daemon service OFF, and NEVER falls back to the normally
 *   installed target app.
 */
public class EngineLauncher {
    public static class Result {
        public final boolean launched; public final String detail;
        public Result(boolean launched, String detail) { this.launched = launched; this.detail = detail; }
    }

    private static volatile boolean attached = false;

    private static synchronized void ensureAttached(final Context context) {
        if (attached) return;
        final Context appContext = context.getApplicationContext();
        BlackBoxCore.get().doAttachBaseContext(appContext, new ClientConfiguration() {
            @Override public String getHostPackageName() { return appContext.getPackageName(); }
            @Override public boolean isHideRoot() { return false; }
            @Override public boolean isHideXposed() { return false; }
            @Override public boolean isEnableDaemonService() { return false; }
            @Override public boolean isEnableLauncherActivity() { return true; }
        });
        BlackBoxCore.get().doCreate();
        attached = true;
    }

    private static int userIdFor(Context context, CloneEntry clone) {
        // v3: stable virtualUserId stored on the clone, not its list index.
        // If the stored id is missing (-1), fall back to finding it in the
        // store (v2 migration already fills it on load); if it is still
        // not found, return -1 and let the caller fail, never 0.
        if (clone.virtualUserId >= 0) return clone.virtualUserId;
        List<CloneEntry> clones = CloneStore.load(context);
        for (int i = 0; i < clones.size(); i++) {
            if (clones.get(i).id.equals(clone.id)) return clones.get(i).virtualUserId;
        }
        return -1;
    }

    /** Delete a clone's virtual user and uninstall its guest package.
     *  Best effort on the engine side: even if the engine delete throws,
     *  the caller should still remove the host entry, so the user is
     *  never stuck with an undeletable clone. Orphaned engine data, if
     *  any, is reported in the returned detail string. */
    public static Result deleteClone(CloneEntry clone, Context context) {
        int userId = -1;
        try { userId = userIdFor(context, clone); } catch (Exception ignored) {}
        // v11: same visible outcome as v10 (success always reported
        // as the best-effort wording, because the original string
        // comparison could never reach the plain "deleted" branch),
        // only now localised via resources instead of literals.
        String engineNote = context.getString(R.string.engine_delete_skipped);
        if (userId >= 0) {
            try {
                ensureAttached(context);
                try { BlackBoxCore.get().uninstallPackageAsUser(clone.sourcePackage, userId); } catch (Exception ignored) {}
                boolean threw = false;
                try { BlackBoxCore.get().deleteUser(userId); } catch (Exception e) { engineNote = context.getString(R.string.engine_delete_threw, e.getMessage()); threw = true; }
                if (!threw) engineNote = context.getString(R.string.engine_user_deleted_best, userId);
            } catch (Exception e) {
                engineNote = context.getString(R.string.engine_delete_failed, e.getMessage());
            }
        }
        return new Result(true, engineNote);
    }

    /** Update a clone to a newly imported APK: uninstall the old guest
     *  package for its stable virtual user, so the next launch installs
     *  the new APK instead of reusing the old installed guest. */
    public static Result prepareApkUpdate(CloneEntry clone, Context context) {
        int userId = userIdFor(context, clone);
        if (userId < 0) return new Result(false, context.getString(R.string.engine_update_not_found));
        try {
            ensureAttached(context);
            try { BlackBoxCore.get().uninstallPackageAsUser(clone.sourcePackage, userId); } catch (Exception ignored) {}
            return new Result(true, context.getString(R.string.engine_update_prepared, userId));
        } catch (Exception e) {
            return new Result(false, context.getString(R.string.engine_update_threw, e.getMessage()));
        }
    }

    public static Result launchClone(CloneEntry clone, Context context) {
        if (!clone.active) {
            return new Result(false, context.getString(R.string.engine_not_active, clone.name));
        }
        try {
            ensureAttached(context);
            int userId = userIdFor(context, clone);
            if (userId < 0) {
                return new Result(false, context.getString(R.string.engine_not_in_store, clone.name));
            }
            File apk = new File(clone.sourceApkPath);
            if (!apk.exists()) {
                return new Result(false, context.getString(R.string.engine_apk_missing, apk.getAbsolutePath()));
            }
            if (clone.sourceSha256 != null && !clone.sourceSha256.isEmpty()) {
                String actual = CloneStore.sha256(apk);
                if (!clone.sourceSha256.equals(actual)) {
                    return new Result(false, context.getString(R.string.engine_sha_mismatch, clone.name));
                }
            }
            // v4f: DO NOT trust isInstalled() alone. In v4c and again in
            // v4e, the engine fell into fallback mode during :black init
            // (failureCount >= 2 from service test pings), and then
            // isInstalledFallback() asked the SYSTEM PackageManager, which
            // returns true because the real Muse is installed on the
            // phone, even though the virtual user has no guest at all.
            // getInstalledPackagesAsUser() has NO fallback: it returns the
            // real virtual list, or empty on service failure, so it is
            // the only trustworthy check. If the list does not contain
            // the guest, install it, even if isInstalled() says true.
            boolean installedFallback = BlackBoxCore.get().isInstalled(clone.sourcePackage, userId);
            boolean installedVirtual = false;
            int virtualListSize = -1;
            try {
                java.util.List<top.niunaijun.blackbox.entity.pm.InstalledPackage> pkgs =
                    BlackBoxCore.getBPackageManager().getInstalledPackagesAsUser(userId);
                virtualListSize = pkgs == null ? -1 : pkgs.size();
                if (pkgs != null) {
                    for (top.niunaijun.blackbox.entity.pm.InstalledPackage p : pkgs) {
                        if (clone.sourcePackage.equals(p.packageName)) { installedVirtual = true; break; }
                    }
                }
            } catch (Exception listEx) {
                Log.w("MuseLaunch", "getInstalledPackagesAsUser threw, treating as not installed: " + listEx.getMessage());
            }
            Log.i("MuseLaunch", "launchClone: clone=" + clone.name + " package=" + clone.sourcePackage + " userId=" + userId + " apk=" + apk.getAbsolutePath() + " apkExists=" + apk.exists() + " isInstalled(fallback-prone)=" + installedFallback + " virtualListInstalled=" + installedVirtual + " virtualListSize=" + virtualListSize);
            FileLogger.log(context, "MuseLaunch", "launchClone: clone=" + clone.name + " userId=" + userId + " isInstalled=" + installedFallback + " virtualListInstalled=" + installedVirtual + " virtualListSize=" + virtualListSize);
            if (!installedVirtual) {
                InstallResult result = BlackBoxCore.get().installPackageAsUser(apk, userId);
                Log.i("MuseLaunch", "installPackageAsUser: success=" + result.success + " msg=" + result.msg);
                FileLogger.log(context, "MuseLaunch", "installPackageAsUser: success=" + result.success + " msg=" + result.msg);
                if (!result.success) {
                    return new Result(false, context.getString(R.string.engine_install_failed, userId, result.msg));
                }
            } else {
                Log.i("MuseLaunch", "Virtual install SKIPPED because virtual list really contains the guest");
            }
            // v4e: best-effort GApps install, NEVER blocks launch. In v4d,
            // Firebase fell back to its old local IID code because virtual
            // had no com.google.android.gms, and that code crashed on
            // PendingIntent flags. Installing the real system GMS/G SF/
            // Vending packages into the virtual user (same principle as
            // the user-supplied Muse APK: system APKs, not bundled) may
            // stop the fallback entirely. If it fails, launch continues
            // and the engine PendingIntentCrashFix is the safety net.
            try {
                // v4f: same fallback trap applies to GMS, so check the
                // virtual list directly for com.google.android.gms too.
                boolean gmsInstalled = BlackBoxCore.get().isInstallGms(userId);
                boolean gmsInVirtualList = false;
                try {
                    java.util.List<top.niunaijun.blackbox.entity.pm.InstalledPackage> pkgs2 =
                        BlackBoxCore.getBPackageManager().getInstalledPackagesAsUser(userId);
                    if (pkgs2 != null) for (top.niunaijun.blackbox.entity.pm.InstalledPackage p : pkgs2)
                        if ("com.google.android.gms".equals(p.packageName)) { gmsInVirtualList = true; break; }
                } catch (Exception ignored) {}
                Log.i("MuseLaunch", "GMS check: isSupportGms=" + BlackBoxCore.get().isSupportGms() + " virtualIsInstallGms(fallback-prone)=" + gmsInstalled + " gmsInVirtualList=" + gmsInVirtualList);
                FileLogger.log(context, "MuseLaunch", "GMS check: virtualIsInstallGms=" + gmsInstalled + " gmsInVirtualList=" + gmsInVirtualList);
                if (!gmsInVirtualList) {
                    InstallResult gmsResult = BlackBoxCore.get().installGms(userId);
                    Log.i("MuseLaunch", "installGms: success=" + gmsResult.success + " msg=" + gmsResult.msg);
                    FileLogger.log(context, "MuseLaunch", "installGms: success=" + gmsResult.success + " msg=" + gmsResult.msg);
                }
            } catch (Exception gmsEx) {
                Log.w("MuseLaunch", "installGms threw, continuing to launch anyway: " + gmsEx.getMessage());
                FileLogger.log(context, "MuseLaunch", "installGms threw, continuing: " + gmsEx.getMessage());
            }
            long t0 = System.currentTimeMillis();
            boolean ok = BlackBoxCore.get().launchApk(clone.sourcePackage, userId);
            Log.i("MuseLaunch", "launchApk returned=" + ok + " in " + (System.currentTimeMillis() - t0) + " ms");
            FileLogger.log(context, "MuseLaunch", "launchApk returned=" + ok + " in " + (System.currentTimeMillis() - t0) + " ms");
            if (!ok) {
                return new Result(false, context.getString(R.string.engine_launch_false, clone.sourcePackage, userId));
            }
            return new Result(true, context.getString(R.string.engine_launch_ok, userId, clone.sourcePackage, (System.currentTimeMillis() - t0)));
        } catch (Exception e) {
            return new Result(false, context.getString(R.string.engine_launch_threw, e.getClass().getSimpleName(), e.getMessage()));
        }
    }
}
