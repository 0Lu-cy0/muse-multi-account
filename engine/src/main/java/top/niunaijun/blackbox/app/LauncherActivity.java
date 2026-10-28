package top.niunaijun.blackbox.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.Nullable;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.utils.Slog;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.animation.OvershootInterpolator;

/**
 * LauncherActivity - Handles the launch of virtual apps
 * This activity serves as a bridge between the host app and virtual apps
 */
public class LauncherActivity extends Activity {
    public static final String TAG = "SplashScreen";

    public static final String KEY_INTENT = "launch_intent";
    public static final String KEY_PKG = "launch_pkg";
    public static final String KEY_USER_ID = "launch_user_id";
    private boolean isRunning = false;

    public static void launch(Intent intent, int userId) {
        try {
            Intent splash = new Intent();
            splash.setClass(BlackBoxCore.getContext(), LauncherActivity.class);
            // Only use FLAG_ACTIVITY_NEW_TASK
            splash.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            splash.putExtra(LauncherActivity.KEY_INTENT, intent);
            splash.putExtra(LauncherActivity.KEY_PKG, intent.getPackage());
            splash.putExtra(LauncherActivity.KEY_USER_ID, userId);
            BlackBoxCore.getContext().startActivity(splash);
            Slog.d(TAG, "LauncherActivity.launch() called for package: " + intent.getPackage());
        } catch (Exception e) {
            Slog.e(TAG, "Error in LauncherActivity.launch()", e);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            
            Intent intent = getIntent();
            if (intent == null) {
                Slog.w(TAG, "Intent is null, finishing activity");
                finish();
                return;
            }
            
            Intent launchIntent = intent.getParcelableExtra(KEY_INTENT);
            String packageName = intent.getStringExtra(KEY_PKG);
            int userId = intent.getIntExtra(KEY_USER_ID, 0);

            if (launchIntent == null || packageName == null) {
                Slog.w(TAG, "Missing launch intent or package name, finishing activity");
                finish();
                return;
            }

            Slog.d(TAG, "LauncherActivity.onCreate() for package: " + packageName + ", userId: " + userId);

            // Get package info with enhanced error handling
            PackageInfo packageInfo = getPackageInfoWithFallback(packageName, userId);
            
            if (packageInfo == null) {
                Slog.w(TAG, "Package info not available for " + packageName + ", but proceeding with launch");
                // Don't fail immediately - try to proceed anyway
            } else {
                Slog.d(TAG, "Successfully retrieved package info for " + packageName);
            }
            
            // Properly load the app icon and app name
            Drawable drawable = null;
            String appName = packageName;
            try {
                if (packageInfo != null && packageInfo.applicationInfo != null) {
                    PackageManager pm = getPackageManager();
                    drawable = pm.getApplicationIcon(packageInfo.applicationInfo);
                    CharSequence label = pm.getApplicationLabel(packageInfo.applicationInfo);
                    if (label != null) appName = label.toString();
                }
            } catch (Exception e) {
                Slog.w(TAG, "Failed to load app icon or name for " + packageName + ": " + e.getMessage());
            }
            // v16 fix (2026-10-28): build the splash view in code, do NOT
            // call setContentView(R.layout.activity_launcher). The R
            // constants baked into this precompiled engine class at
            // AAR-build time (activity_launcher = 0x7f0a001c) stopped
            // matching the host link's merged resource table in v11,
            // when a new "array" resource type (vpn_countries) pushed
            // the layout type from 0x0a to 0x0b, so the layout became
            // 0x7f0b001c and the baked ID no longer exists (type 0x0a
            // is now interpolator, 7 entries) -> Resources$
            // NotFoundException at onCreate, every launch, from v11 on
            // (masked until v15 because launches died earlier at the
            // null package_manager service). A programmatic view uses
            // no baked resource IDs at all, so no future resource
            // addition can break this screen again. Layout matches
            // the old activity_launcher.xml exactly: white background,
            // centred vertical column, 96dp icon, 18sp name, 16dp gap.
            android.widget.LinearLayout root =
                new android.widget.LinearLayout(this);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);
            root.setGravity(android.view.Gravity.CENTER);
            root.setBackgroundColor(android.graphics.Color.WHITE);
            root.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT));
            float density = getResources().getDisplayMetrics().density;
            ImageView iconView = new ImageView(this);
            android.widget.LinearLayout.LayoutParams iconParams =
                new android.widget.LinearLayout.LayoutParams(
                    Math.round(96 * density), Math.round(96 * density));
            iconView.setLayoutParams(iconParams);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconView.setContentDescription(appName);
            root.addView(iconView);
            TextView nameView = new TextView(this);
            android.widget.LinearLayout.LayoutParams nameParams =
                new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = Math.round(16 * density);
            nameView.setLayoutParams(nameParams);
            nameView.setTextSize(18);
            nameView.setTextColor(android.graphics.Color.BLACK);
            root.addView(nameView);
            setContentView(root);
            nameView.setText(appName);
            nameView.setAlpha(0f);
            nameView.animate()
                .alpha(1f)
                .setDuration(500)
                .setStartDelay(200)
                .start();
            if (drawable != null) {
                iconView.setImageDrawable(drawable);
                iconView.setScaleX(0.7f);
                iconView.setScaleY(0.7f);
                iconView.setAlpha(0f);
                iconView.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .alpha(1f)
                    .setDuration(350)
                    .setInterpolator(new OvershootInterpolator())
                    .withEndAction(() -> iconView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start())
                    .start();
            }
            
            // Launch the app in a separate thread to avoid blocking the UI
            launchAppAsync(launchIntent, userId);
            
        } catch (Exception e) {
            Slog.e(TAG, "Critical error in LauncherActivity.onCreate()", e);
            finish();
        }
    }

    /**
     * Get package info with enhanced error handling and fallback mechanisms
     */
    private PackageInfo getPackageInfoWithFallback(String packageName, int userId) {
        try {
            // First attempt: Try to get package info normally
            return BlackBoxCore.getBPackageManager().getPackageInfo(packageName, 0, userId);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to get package info for " + packageName + " (attempt 1): " + e.getMessage());
            
            try {
                // Second attempt: Try with different flags
                return BlackBoxCore.getBPackageManager().getPackageInfo(packageName, 
                    android.content.pm.PackageManager.GET_META_DATA, userId);
            } catch (Exception e2) {
                Slog.w(TAG, "Failed to get package info for " + packageName + " (attempt 2): " + e2.getMessage());
                
                try {
                    // Third attempt: Try to get application info instead
                    android.content.pm.ApplicationInfo appInfo = BlackBoxCore.getBPackageManager()
                        .getApplicationInfo(packageName, 0, userId);
                    
                    if (appInfo != null) {
                        // Create a minimal PackageInfo from ApplicationInfo
                        PackageInfo fallbackInfo = new PackageInfo();
                        fallbackInfo.packageName = packageName;
                        fallbackInfo.applicationInfo = appInfo;
                        fallbackInfo.versionCode = 1;
                        fallbackInfo.versionName = "1.0";
                        fallbackInfo.firstInstallTime = System.currentTimeMillis();
                        fallbackInfo.lastUpdateTime = System.currentTimeMillis();
                        
                        Slog.d(TAG, "Created fallback PackageInfo for " + packageName);
                        return fallbackInfo;
                    }
                } catch (Exception e3) {
                    Slog.w(TAG, "Failed to get application info for " + packageName + ": " + e3.getMessage());
                }
            }
        }
        
        return null;
    }

    /**
     * Launch the app asynchronously to avoid blocking the UI thread
     */
    private void launchAppAsync(final Intent launchIntent, final int userId) {
        new Thread(() -> {
            try {
                Slog.d(TAG, "Starting app launch in background thread");
                
                // Add a small delay to ensure the launcher activity is properly displayed
                Thread.sleep(100);
                
                // Launch the app
                BlackBoxCore.getBActivityManager().startActivity(launchIntent, userId);
                
                Slog.d(TAG, "App launch initiated successfully");
            } catch (Exception e) {
                Slog.e(TAG, "Error launching app", e);
                
                // Try to show an error message to the user
                runOnUiThread(() -> {
                    try {
                        // You could show a toast or dialog here
                        Slog.e(TAG, "Failed to launch app: " + e.getMessage());
                    } catch (Exception uiException) {
                        Slog.e(TAG, "Error showing error message", uiException);
                    }
                });
            }
        }, "AppLaunchThread").start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRunning) {
            finish();
        }
    }
}
