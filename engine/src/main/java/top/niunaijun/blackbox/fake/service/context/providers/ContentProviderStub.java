package top.niunaijun.blackbox.fake.service.context.providers;

import android.database.MatrixCursor;
import android.os.IInterface;

import java.lang.reflect.Method;

import black.android.content.BRAttributionSource;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.utils.compat.ContextCompat;
import top.niunaijun.blackbox.utils.Slog;
import android.os.Bundle;

/**
 * updated by alex5402 on 4/8/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * TFNQw5HgWUS33Ke1eNmSFTwoQySGU7XNsK (USDT TRC20)
 */
public class ContentProviderStub extends ClassInvocationStub implements BContentProvider {
    public static final String TAG = "ContentProviderStub";
    private IInterface mBase;
    private String mAppPkg;

    public IInterface wrapper(final IInterface contentProviderProxy, final String appPkg) {
        mBase = contentProviderProxy;
        mAppPkg = appPkg;
        injectHook();
        return (IInterface) getProxyInvocation();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    protected void onBindMethod() {

    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("asBinder".equals(method.getName())) {
            return method.invoke(mBase, args);
        }
        // v4k diagnostic: for the VM service channel, log entry and
        // result so the phone run shows whether the call even reached
        // the stub and what it returned, instead of just "null".
        if ("call".equals(method.getName()) && args != null && args.length > 1
                && args[1] instanceof String
                && ((String) args[1]).contains("blackbox.SystemCallProvider")) {
            Slog.d(TAG, "VM_STUB_ENTRY method=" + (args.length > 2 ? args[2] : "?")
                    + " arg=" + (args.length > 3 ? args[3] : "?"));
        }
        // Which provider is this call for? For "call" the authority is
        // args[1]; for query/insert/... it is the Uri in args[1].
        // System providers (settings, media, telephony, ...) are served by
        // the real system_server, which validates the AttributionSource uid
        // in the parcel against the real Binder calling uid (the host uid).
        // Faking it to the virtual BUid guarantees a UID-mismatch rejection,
        // which is what killed the guest in v4g: Settings$Config calls
        // (DeviceConfig -> AutofillManager init) failed, and
        // NameValueCache.getStringsForPrefix then NPE'd on a null map.
        // So for system providers: keep every String argument intact
        // (the "call" method name and its arg are NOT package names) and
        // set the AttributionSource uid to the host uid, so the call can
        // actually succeed instead of needing a safe default.
        String callAuthority = null;
        if (args != null && args.length > 1) {
            if ("call".equals(method.getName()) && args[1] instanceof String) {
                callAuthority = (String) args[1];
            } else if (args[1] instanceof android.net.Uri) {
                callAuthority = ((android.net.Uri) args[1]).getAuthority();
            }
        }
        boolean isSystemProvider = callAuthority != null
                && isSystemProviderAuthority(callAuthority);

        // Fix AttributionSource and package name issues
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof String) {
                    if (isSystemProvider) {
                        // Preserve method name / arg for system providers.
                        continue;
                    }
                    // NEW (v4i): for "call", only args[0] can be a package
                    // name (old signature: call(callingPkg, method, ...)).
                    // In the current signature args are (AttributionSource,
                    // authority, method, arg, extras) — the method name
                    // (e.g. BlackBox's own "VM" service channel) and the
                    // arg are NOT package names. Replacing them broke v4h:
                    // the VM call arrived with a wrong method, returned
                    // null, and every BlackBox service lookup failed,
                    // so the guest died at makeApplication.
                    if ("call".equals(method.getName()) && i != 0) {
                        continue;
                    }
                    String strArg = (String) arg;
                    // Don't replace system provider authorities
                    if (!isSystemProviderAuthority(strArg)) {
                        // Replace package name with the correct one
                        args[i] = mAppPkg;
                    }
                } else if (arg != null && arg.getClass().getName().equals(BRAttributionSource.getRealClass().getName())) {
                    // Fix AttributionSource UID
                    try {
                        ContextCompat.fixAttributionSourceState(arg, BlackBoxCore.getHostUid());
                    } catch (Exception e) {
                        // If fixing AttributionSource fails, try to create a new one or skip
                        Slog.w(TAG, "Failed to fix AttributionSource, continuing with original");
                    }
                } else if (arg != null && arg.getClass().getName().contains("AttributionSource")) {
                    // Handle any AttributionSource-like objects that might cause UID issues
                    try {
                        // Try to fix UID using reflection
                        fixAttributionSourceUid(arg);
                    } catch (Exception e) {
                        Slog.w(TAG, "Failed to fix AttributionSource-like object: " + e.getMessage());
                    }
                }
            }
            if (isSystemProvider) {
                // Override the BUid set above: system_server will only
                // accept the real host uid for system providers.
                for (Object arg : args) {
                    if (arg != null && arg.getClass().getName().contains("AttributionSource")) {
                        try {
                            ContextCompat.fixAttributionSourceState(arg, BlackBoxCore.getHostUid());
                        } catch (Exception e) {
                            Slog.w(TAG, "Failed to fix AttributionSource to host uid: " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        // Pre-validate the call to prevent system-level SecurityException
        String methodName = method.getName();
        if (methodName.equals("query") || methodName.equals("insert") || 
            methodName.equals("update") || methodName.equals("delete") || 
            methodName.equals("bulkInsert") || methodName.equals("call")) {
            
            // Check if this is likely to cause a UID mismatch
            try {
                return method.invoke(mBase, args);
            } catch (Throwable e) {
                // Handle SecurityException and other UID-related errors
                Throwable cause = e.getCause();
                if (isUidMismatchError(cause)) {
                    Slog.w(TAG, "UID mismatch in ContentProvider call, returning safe default: " + cause.getMessage());
                    return getSafeDefaultValue(methodName, method.getReturnType());
                } else if (cause instanceof RuntimeException) {
                    String message = cause.getMessage();
                    if (message != null && (message.contains("uid") || message.contains("permission"))) {
                        Slog.w(TAG, "Permission/UID error in ContentProvider call, returning safe default: " + message);
                        return getSafeDefaultValue(methodName, method.getReturnType());
                    }
                }
                
                // For call method specifically, always return safe default on any error
                if (methodName.equals("call")) {
                    Slog.w(TAG, "Error in call method, returning safe default: " + e.getMessage());
                    return getSafeDefaultValue(methodName, method.getReturnType());
                }
                
                throw e.getCause();
            }
        }
        
        // For other methods, proceed normally
        try {
            return method.invoke(mBase, args);
        } catch (Throwable e) {
            // Handle SecurityException for UID mismatch in any method
            Throwable cause = e.getCause();
            if (isUidMismatchError(cause)) {
                Slog.w(TAG, "UID mismatch in " + methodName + ", returning safe default: " + cause.getMessage());
                return getSafeDefaultValue(methodName, method.getReturnType());
            }
            throw e.getCause();
        }
    }
    
    private Object getSafeDefaultValue(String methodName) {
        switch (methodName) {
            case "query":
                return null; // Return null cursor
            case "insert":
                return null; // Return null URI
            case "update":
            case "delete":
                return 0; // Return 0 rows affected
            case "bulkInsert":
                return 0; // Return 0 rows inserted
            case "call":
                return null; // Return null for call method
            case "getType":
                return null; // Return null MIME type
            case "openFile":
                return null; // Return null ParcelFileDescriptor
            case "openAssetFile":
                return null; // Return null AssetFileDescriptor
            default:
                return null; // Default fallback
        }
    }

    private boolean isSystemProviderAuthority(String authority) {
        if (authority == null) return false;
        
        // Check for system provider authorities that need special handling
        return authority.equals("settings") || 
               authority.equals("settings_global") || 
               authority.equals("settings_system") || 
               authority.equals("settings_secure") ||
               authority.equals("media") ||
               authority.equals("telephony") ||
               authority.startsWith("android.provider.Settings");
    }
    
    /**
     * Enhanced UID mismatch detection and handling
     */
    private boolean isUidMismatchError(Throwable error) {
        if (error == null) return false;
        
        String message = error.getMessage();
        if (message == null) return false;
        
        // Check for UID mismatch patterns
        return message.contains("Calling uid") && 
               message.contains("doesn't match source uid") ||
               message.contains("uid") && 
               message.contains("permission") ||
               message.contains("SecurityException") ||
               message.contains("UID mismatch");
    }
    
    /**
     * Get safe default value based on method name and return type
     */
    private Object getSafeDefaultValue(String methodName, Class<?> returnType) {
        if (returnType == null) {
            return getSafeDefaultValue(methodName);
        }
        
        // Return type-specific safe defaults
        if (returnType == String.class) {
            return "true"; // Safe default for strings
        } else if (returnType == int.class || returnType == Integer.class) {
            return 1; // Safe default for integers
        } else if (returnType == long.class || returnType == Long.class) {
            return 1L; // Safe default for longs
        } else if (returnType == float.class || returnType == Float.class) {
            return 1.0f; // Safe default for floats
        } else if (returnType == boolean.class || returnType == Boolean.class) {
            return true; // Safe default for booleans
        } else if (returnType == Bundle.class) {
            return new Bundle(); // Safe default for bundles
        }

        // A null cursor crashes framework callers that iterate it without a
        // null check. Known case (2026-10-27): Settings$NameValueCache
        // .getStringsForPrefix() calls Map.entrySet() on the map built from
        // the Settings$Config query; when the query fails with a UID
        // mismatch and we return null, the map stays null and the guest
        // dies with NPE in AutofillManager init on AuraMainActivity attach.
        // An empty cursor means "no rows" and lets the caller fall back to
        // its defaults (e.g. DeviceConfig.getBoolean -> false) instead.
        if (methodName.equals("query")) {
            Slog.w(TAG, "UID/permission error in query, returning empty cursor instead of null");
            return new MatrixCursor(new String[]{"name", "value"});
        }

        // Fallback to method-specific defaults
        return getSafeDefaultValue(methodName);
    }

    /**
     * Fix AttributionSource UID to prevent crashes on Android 12+
     */
    private void fixAttributionSourceUid(Object attributionSource) {
        try {
            if (attributionSource == null) return;
            
            Class<?> attributionSourceClass = attributionSource.getClass();
            
            // Try to find and set the UID field
            try {
                java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField("mUid");
                uidField.setAccessible(true);
                uidField.set(attributionSource, BlackBoxCore.getHostUid());
                Slog.d(TAG, "Fixed AttributionSource UID via field access");
            } catch (NoSuchFieldException e) {
                // Try alternative field names
                try {
                    java.lang.reflect.Field uidField = attributionSourceClass.getDeclaredField("uid");
                    uidField.setAccessible(true);
                    uidField.set(attributionSource, BlackBoxCore.getHostUid());
                    Slog.d(TAG, "Fixed AttributionSource UID via alternative field");
                } catch (NoSuchFieldException e2) {
                    // Try using setter method
                    try {
                        java.lang.reflect.Method setUidMethod = attributionSourceClass.getDeclaredMethod("setUid", int.class);
                        setUidMethod.setAccessible(true);
                        setUidMethod.invoke(attributionSource, BlackBoxCore.getHostUid());
                        Slog.d(TAG, "Fixed AttributionSource UID via setter method");
                    } catch (Exception e3) {
                        Slog.w(TAG, "Could not fix AttributionSource UID: " + e3.getMessage());
                    }
                }
            }
            
            // Also try to fix package name
            try {
                java.lang.reflect.Field packageField = attributionSourceClass.getDeclaredField("mPackageName");
                packageField.setAccessible(true);
                packageField.set(attributionSource, mAppPkg);
                Slog.d(TAG, "Fixed AttributionSource package name");
            } catch (Exception e) {
                // Ignore package name fixing errors
            }
            
        } catch (Exception e) {
            Slog.w(TAG, "Error fixing AttributionSource UID: " + e.getMessage());
        }
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }
}
