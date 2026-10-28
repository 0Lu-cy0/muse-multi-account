package top.niunaijun.blackbox.core.system;

import android.os.IBinder;

import java.util.HashMap;
import java.util.Map;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.accounts.BAccountManagerService;
import top.niunaijun.blackbox.core.system.am.BActivityManagerService;
import top.niunaijun.blackbox.core.system.am.BJobManagerService;
import top.niunaijun.blackbox.core.system.location.BLocationManagerService;
import top.niunaijun.blackbox.core.system.notification.BNotificationManagerService;
import top.niunaijun.blackbox.core.system.os.BStorageManagerService;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.core.system.pm.BXposedManagerService;
import top.niunaijun.blackbox.core.system.user.BUserManagerService;

/**
 * updated by alex5402 on 3/31/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * TFNQw5HgWUS33Ke1eNmSFTwoQySGU7XNsK (USDT TRC20)
 */
public class ServiceManager {
    private static ServiceManager sServiceManager = null;
    public static final String ACTIVITY_MANAGER = "activity_manager";
    public static final String JOB_MANAGER = "job_manager";
    public static final String PACKAGE_MANAGER = "package_manager";
    public static final String STORAGE_MANAGER = "storage_manager";
    public static final String USER_MANAGER = "user_manager";
    public static final String XPOSED_MANAGER = "xposed_manager";
    public static final String ACCOUNT_MANAGER = "account_manager";
    public static final String LOCATION_MANAGER = "location_manager";
    public static final String NOTIFICATION_MANAGER = "notification_manager";

    private final Map<String, IBinder> mCaches = new HashMap<>();

    public static ServiceManager get() {
        if (sServiceManager == null) {
            synchronized (ServiceManager.class) {
                if (sServiceManager == null) {
                    sServiceManager = new ServiceManager();
                }
            }
        }
        return sServiceManager;
    }

    public static IBinder getService(String name) {
        return get().getServiceInternal(name);
    }

    private ServiceManager() {
        mCaches.put(ACTIVITY_MANAGER, BActivityManagerService.get());
        mCaches.put(JOB_MANAGER, BJobManagerService.get());
        mCaches.put(PACKAGE_MANAGER, BPackageManagerService.get());
        mCaches.put(STORAGE_MANAGER, BStorageManagerService.get());
        mCaches.put(USER_MANAGER, BUserManagerService.get());
        mCaches.put(XPOSED_MANAGER, BXposedManagerService.get());
        mCaches.put(ACCOUNT_MANAGER, BAccountManagerService.get());
        mCaches.put(LOCATION_MANAGER, BLocationManagerService.get());
        mCaches.put(NOTIFICATION_MANAGER, BNotificationManagerService.get());
    }

    public IBinder getServiceInternal(String name) {
        IBinder cached = mCaches.get(name);
        if (cached != null) {
            return cached;
        }
        // v4l repair (2026-10-28, phone log muse-log-nho-4.txt): on the
        // user's phone the :black ServiceManager map is fully built
        // EXCEPT package_manager, which is null from the first request
        // in every :black process, while all eight other services are
        // healthy. The constructor-time put for PM can only store null
        // if BPackageManagerService.get() was called reentrantly while
        // its class initialisation was still running on the same thread
        // (sService not yet assigned); the map then keeps that null
        // forever, and no amount of client-side polling or waiting
        // (BlackManager's 2s gate, the host's 5x1s poll) can fix it,
        // because every retry reads the same poisoned map entry.
        // Repair here, at the source: when the cached entry is null,
        // construct the service directly from its singleton instead of
        // trusting the constructor-time entry. By the time a VM call
        // arrives, class initialisation has finished, so get() returns
        // the healthy singleton in the reentrancy case. If class
        // initialisation itself is poisoned, get() throws; catch it
        // and return null, preserving the old behaviour exactly.
        // This repair only fires on a null entry; a healthy map is
        // untouched. The result is cached so later calls stay cheap.
        try {
            IBinder repaired = null;
            if (PACKAGE_MANAGER.equals(name)) {
                repaired = BPackageManagerService.get();
            } else if (ACTIVITY_MANAGER.equals(name)) {
                repaired = BActivityManagerService.get();
            } else if (JOB_MANAGER.equals(name)) {
                repaired = BJobManagerService.get();
            } else if (STORAGE_MANAGER.equals(name)) {
                repaired = BStorageManagerService.get();
            } else if (USER_MANAGER.equals(name)) {
                repaired = BUserManagerService.get();
            } else if (XPOSED_MANAGER.equals(name)) {
                repaired = BXposedManagerService.get();
            } else if (ACCOUNT_MANAGER.equals(name)) {
                repaired = BAccountManagerService.get();
            } else if (LOCATION_MANAGER.equals(name)) {
                repaired = BLocationManagerService.get();
            } else if (NOTIFICATION_MANAGER.equals(name)) {
                repaired = BNotificationManagerService.get();
            }
            if (repaired != null) {
                mCaches.put(name, repaired);
                top.niunaijun.blackbox.utils.Slog.d("ServiceManager",
                        "Lazy repair: rebuilt null entry for " + name);
                return repaired;
            }
            top.niunaijun.blackbox.utils.Slog.w("ServiceManager",
                    "Lazy repair: singleton still null for " + name);
        } catch (Throwable t) {
            top.niunaijun.blackbox.utils.Slog.e("ServiceManager",
                    "Lazy repair failed for " + name, t);
        }
        return null;
    }

    public static void initBlackManager() {
        BlackBoxCore.get().getService(ACTIVITY_MANAGER);
        BlackBoxCore.get().getService(JOB_MANAGER);
        BlackBoxCore.get().getService(PACKAGE_MANAGER);
        BlackBoxCore.get().getService(STORAGE_MANAGER);
        BlackBoxCore.get().getService(USER_MANAGER);
        BlackBoxCore.get().getService(XPOSED_MANAGER);
        BlackBoxCore.get().getService(ACCOUNT_MANAGER);
        BlackBoxCore.get().getService(LOCATION_MANAGER);
        BlackBoxCore.get().getService(NOTIFICATION_MANAGER);
    }
}
