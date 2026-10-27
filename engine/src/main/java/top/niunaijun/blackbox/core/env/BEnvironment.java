package top.niunaijun.blackbox.core.env;

import java.io.File;
import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.FileUtils;

/**
 * updated by alex5402 on 4/22/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * TFNQw5HgWUS33Ke1eNmSFTwoQySGU7XNsK (USDT TRC20)
 */
public class BEnvironment {
    // v4b fix (2026-10-27, phone crash): these were static field
    // initializers that ran at class-load time and called
    // BlackBoxCore.getContext().getCacheDir() unconditionally. In the
    // :black server process the host Application had never run
    // doAttachBaseContext, so getContext() was null and the class
    // failed to initialize with NPE -> ExceptionInInitializerError ->
    // FATAL in SystemCallProvider.onCreate, repeated on every retry.
    // Roots are now lazy: nothing touches the context until load()
    // or a getter runs, and then only after an explicit null check.
    // JUNIT_JAR / EMPTY_JAR are only referenced from commented code;
    // they are set in load() instead of at class-load time, so a
    // read before load() returns null instead of crashing the class.
    private static File sVirtualRoot;
    private static File sExternalVirtualRoot;

    public static File JUNIT_JAR;
    public static File EMPTY_JAR;

    private static synchronized void ensureInit() {
        if (sVirtualRoot != null) return;
        android.content.Context ctx = BlackBoxCore.getContext();
        if (ctx == null) {
            throw new IllegalStateException(
                "BEnvironment: BlackBoxCore context is null. " +
                "Application.attachBaseContext must run doAttachBaseContext " +
                "before BEnvironment is used in this process.");
        }
        java.io.File cacheDir = ctx.getCacheDir();
        if (cacheDir == null || cacheDir.getParent() == null) {
            throw new IllegalStateException("BEnvironment: context has no cache dir parent");
        }
        sVirtualRoot = new File(cacheDir.getParent(), "blackbox");
        sExternalVirtualRoot = ctx.getExternalFilesDir("blackbox");
    }

    public static void load() {
        ensureInit();
        JUNIT_JAR = new File(getCacheDir(), "junit.apk");
        EMPTY_JAR = new File(getCacheDir(), "empty.apk");
        FileUtils.mkdirs(sVirtualRoot);
        FileUtils.mkdirs(sExternalVirtualRoot);
        FileUtils.mkdirs(getSystemDir());
        FileUtils.mkdirs(getCacheDir());
        FileUtils.mkdirs(getProcDir());
    }

    public static File getVirtualRoot() {
        ensureInit();
        return sVirtualRoot;
    }

    public static File getExternalVirtualRoot() {
        ensureInit();
        return sExternalVirtualRoot;
    }

    public static File getSystemDir() {
        ensureInit();
        return new File(sVirtualRoot, "system");
    }

    public static File getProcDir() {
        ensureInit();
        return new File(sVirtualRoot, "proc");
    }

    public static File getCacheDir() {
        ensureInit();
        return new File(sVirtualRoot, "cache");
    }

    public static File getUserInfoConf() {
        return new File(getSystemDir(), "user.conf");
    }

    public static File getAccountsConf() {
        return new File(getSystemDir(), "accounts.conf");
    }

    public static File getUidConf() {
        return new File(getSystemDir(), "uid.conf");
    }

    public static File getSharedUserConf() {
        return new File(getSystemDir(), "shared-user.conf");
    }

    public static File getXPModuleConf() {
        return new File(getSystemDir(), "xposed-module.conf");
    }

    public static File getFakeLocationConf() {
        return new File(getSystemDir(), "fake-location.conf");
    }

    public static File getPackageConf(String packageName) {
        return new File(getAppDir(packageName), "package.conf");
    }

    public static File getExternalUserDir(int userId) {
        ensureInit();
        return new File(sExternalVirtualRoot, String.format(Locale.CHINA, "storage/emulated/%d/", userId));
    }

    public static File getUserDir(int userId) {
        ensureInit();
        return new File(sVirtualRoot, String.format(Locale.CHINA, "data/user/%d", userId));
    }

    public static File getDeDataDir(String packageName, int userId) {
        ensureInit();
        return new File(sVirtualRoot, String.format(Locale.CHINA, "data/user_de/%d/%s", userId, packageName));
    }

    public static File getExternalDataDir(String packageName, int userId) {
        return new File(getExternalUserDir(userId), String.format(Locale.CHINA, "Android/data/%s", packageName));
    }


    public static File getDataDir(String packageName, int userId) {
        ensureInit();
        return new File(sVirtualRoot, String.format(Locale.CHINA, "data/user/%d/%s", userId, packageName));
    }

    public static File getProcDir(int pid) {
        File file = new File(getProcDir(), String.format(Locale.CHINA, "%d", pid));
        FileUtils.mkdirs(file);
        return file;
    }

    public static File getExternalDataFilesDir(String packageName, int userId) {
        return new File(getExternalDataDir(packageName, userId), "files");
    }

    public static File getDataFilesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "files");
    }

    public static File getExternalDataCacheDir(String packageName, int userId) {
        return new File(getExternalDataDir(packageName, userId), "cache");
    }

    public static File getDataCacheDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "cache");
    }

    public static File getDataLibDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "lib");
    }

    public static File getDataDatabasesDir(String packageName, int userId) {
        return new File(getDataDir(packageName, userId), "databases");
    }

    public static File getAppRootDir() {
        return getAppDir("");
    }

    public static File getAppDir(String packageName) {
        ensureInit();
        return new File(sVirtualRoot, "data/app/" + packageName);
    }

    public static File getBaseApkDir(String packageName) {
        ensureInit();
        return new File(sVirtualRoot, "data/app/" + packageName + "/base.apk");
    }

    public static File getAppLibDir(String packageName) {
        return new File(getAppDir(packageName), "lib");
    }

    public static File getXSharedPreferences(String packageName, String prefFileName) {
       return new File(BEnvironment.getDataDir(packageName, BlackBoxCore.getUserId()), "shared_prefs/" + prefFileName + ".xml");
    }
}
