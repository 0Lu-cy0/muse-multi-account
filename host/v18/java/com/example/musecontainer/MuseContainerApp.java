package com.example.musecontainer;

import android.app.Application;
import android.content.Context;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;

/**
 * Host Application, v4b fix (2026-10-27, phone crash).
 *
 * v4a had NO Application class: the engine was only attached from
 * MainActivity, in the main process, when the user pressed Launch.
 * Android runs a ContentProvider in each process BEFORE any Activity,
 * so when the :black server process started, its SystemCallProvider
 * ran BlackBoxSystem.startup() -> BEnvironment class init with
 * BlackBoxCore.getContext() == null -> NPE -> FATAL, repeated on
 * every retry, and the main process ANR'd waiting for the provider.
 *
 * This Application runs in EVERY process of this app (main, :black,
 * and guest-client processes), and attachBaseContext runs BEFORE
 * any ContentProvider.onCreate in that process. So sContext is set
 * before SystemCallProvider can touch BEnvironment, in every process.
 *
 * onCreate intentionally does NOT call doCreate(): that is the
 * main-process launch path (EngineLauncher.ensureAttached), which
 * blocks waiting for the black process. Calling it here in the
 * :black process itself would be a self-wait; in the main process
 * it would run at app startup even before the user presses Launch.
 */
public class MuseContainerApp extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            // 'this' IS the Application object here, so it is already
            // the application context; do not call getApplicationContext()
            // during attach, when its result is not yet guaranteed.
            final Context appContext = MuseContainerApp.this;
            BlackBoxCore.get().doAttachBaseContext(appContext, new ClientConfiguration() {
                @Override public String getHostPackageName() { return appContext.getPackageName(); }
                @Override public boolean isHideRoot() { return false; }
                @Override public boolean isHideXposed() { return false; }
                @Override public boolean isEnableDaemonService() { return false; }
                @Override public boolean isEnableLauncherActivity() { return true; }
            });
        } catch (Throwable t) {
            // Never let engine attach crash app startup; the provider
            // fallback (SystemCallProvider v4b) and EngineLauncher will
            // retry attachment with a logged failure instead.
            android.util.Log.w("MuseContainerApp", "doAttachBaseContext failed: " + t.getMessage());
        }
    }
}
