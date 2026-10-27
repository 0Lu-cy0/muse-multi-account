package top.niunaijun.blackbox.utils;

import android.util.Log;

/**
 * v4e fix for the Muse guest crash (2026-10-27, muse-log-4.txt):
 * In virtual, Firebase cannot find com.google.android.gms, so it falls
 * back to its old local IID implementation, which calls
 * PendingIntent.getBroadcast(..., flags=0). On Android S+ (API 31+) the
 * framework throws IllegalArgumentException "Targeting S+ requires
 * FLAG_IMMUTABLE or FLAG_MUTABLE" on a worker thread (firebase-iid-
 * executor), and the process dies and restarts in a loop, even though
 * AuraMainActivity already reached onResume.
 *
 * This fix does NOT modify the guest APK. It installs, LAST in the
 * BlackBoxCore static init chain (after CrashMonitor / NativeCrash-
 * Prevention, which otherwise delegate to the system kill handler),
 * a default uncaught-exception handler that swallows ONLY this
 * specific PendingIntent-flag error and delegates everything else to
 * the previous handler. The worker thread dies, the process and the
 * UI survive; Firebase simply gets no push token from that attempt.
 * Push notifications are a later test anyway; login must come first.
 */
public class PendingIntentCrashFix {
    private static final String TAG = "PendingIntentFix";
    private static boolean sInstalled = false;

    public static void install() {
        if (sInstalled) return;
        sInstalled = true;
        try {
            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable throwable) {
                    if (isPendingIntentFlagCrash(throwable)) {
                        Log.w(TAG, "Swallowed PendingIntent FLAG_IMMUTABLE/FLAG_MUTABLE crash on thread "
                            + thread.getName() + " (Firebase IID fallback, non-fatal for UI): " + throwable.getMessage());
                        // Do NOT delegate: returning here keeps the process alive.
                        return;
                    }
                    if (previous != null) {
                        previous.uncaughtException(thread, throwable);
                    }
                }
            });
            Log.i(TAG, "PendingIntent flag crash fix installed (last handler in chain)");
        } catch (Exception e) {
            Log.w(TAG, "Failed to install PendingIntent crash fix: " + e.getMessage());
        }
    }

    private static boolean isPendingIntentFlagCrash(Throwable throwable) {
        if (throwable == null) return false;
        if (!(throwable instanceof IllegalArgumentException)) return false;
        String msg = throwable.getMessage();
        if (msg != null && msg.contains("FLAG_IMMUTABLE") && msg.contains("FLAG_MUTABLE")) {
            return true;
        }
        StackTraceElement[] stack = throwable.getStackTrace();
        if (stack != null) {
            for (StackTraceElement el : stack) {
                if ("android.app.PendingIntent".equals(el.getClassName())
                    && "checkPendingIntent".equals(el.getMethodName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
