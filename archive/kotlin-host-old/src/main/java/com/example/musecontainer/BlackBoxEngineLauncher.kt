package com.example.musecontainer

import android.content.Context
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration
import java.io.File

/**
 * Real engine launcher — replaces StubEngineLauncher ONLY when the
 * sudami/NewBlackbox-15 Bcore library is actually built and linked.
 *
 * Language stack (user-approved, performance-first):
 * - Host: Kotlin (this file, MainActivity, CloneStore) — no Flutter,
 *   no React Native.
 * - Engine core: C++ via NDK (Bcore/src/main/cpp, ndkBuild, Dobby
 *   hooks + IO redirection) — NOT a Rust rewrite of the engine.
 *
 * Performance rules enforced here, not just documented:
 * - One virtual userId per clone. Prototype mapping: index of the
 *   clone in CloneStore order (no delete in v1, so the index is
 *   stable). Do NOT reuse a userId for two clones.
 * - Shared APK code: installPackageAsUser is given the SAME single
 *   imported APK file path for every clone. The engine stores
 *   per-user data separately; the host never copies the APK per clone.
 * - Lazy: install + launch happen only when the user taps Launch
 *   for an ACTIVE clone. Nothing is installed or started at host
 *   startup, and dormant clones get no process, no sync, no polling.
 * - No polling anywhere in this launcher. Notifications must come
 *   via the engine's FCM/GMS proxy, not via a host poll loop.
 * - This launcher NEVER falls back to launching the normally
 *   installed Muse app. If the engine is absent or install/launch
 *   fails, it returns Failed with the exact error — a fake launch
 *   would be worse than an honest failure.
 *
 * Integration status: this file compiles ONLY against a built
 * Bcore AAR/module (see engine-blackbox/Bcore). Until that AAR
 * exists, the shipped host keeps StubEngineLauncher and this file
 * must NOT be wired into MainActivity.
 */
class BlackBoxEngineLauncher(private val context: Context) : EngineLauncher {

    @Volatile
    private var attached = false

    private fun ensureAttached() {
        if (attached) return
        synchronized(this) {
            if (attached) return
            BlackBoxCore.get().doAttachBaseContext(
                context,
                object : ClientConfiguration() {
                    override fun getHostPackageName(): String = context.packageName
                    override fun isHideRoot(): Boolean = false
                    override fun isHideXposed(): Boolean = false
                    // Daemon off: a background daemon per clone would defeat
                    // the active-cap / battery goal on a 6 GB phone.
                    override fun isEnableDaemonService(): Boolean = false
                    override fun isEnableLauncherActivity(): Boolean = true
                }
            )
            BlackBoxCore.get().doCreate()
            attached = true
        }
    }

    private fun userIdFor(clone: CloneEntry): Int {
        val clones = CloneStore.load(context)
        val idx = clones.indexOfFirst { it.id == clone.id }
        return if (idx >= 0) idx else 0
    }

    override fun launchClone(clone: CloneEntry): EngineResult {
        // Active-cap is enforced by MainActivity before this is called,
        // but re-check here so a direct call cannot bypass it.
        if (!clone.active) {
            return EngineResult.Failed(
                "Clone '${clone.name}' is not active. Set it active first " +
                    "(max 4 active; dormant clones stay frozen)."
            )
        }
        return try {
            ensureAttached()
            val userId = userIdFor(clone)
            val apk = File(clone.sourceApkPath)
            if (!apk.exists()) {
                return EngineResult.Failed("Imported APK not found: ${apk.absolutePath}")
            }
            if (!BlackBoxCore.get().isInstalled(clone.sourcePackage, userId)) {
                val result = BlackBoxCore.get().installPackageAsUser(apk, userId)
                if (!result.success) {
                    return EngineResult.Failed(
                        "Engine install failed for user $userId: ${result.msg}"
                    )
                }
            }
            val t0 = System.currentTimeMillis()
            val ok = BlackBoxCore.get().launchApk(clone.sourcePackage, userId)
            if (!ok) {
                EngineResult.Failed(
                    "Engine launchApk returned false for ${clone.sourcePackage} " +
                        "user $userId (no launch intent — engine or guest failed to start)."
                )
            } else {
                EngineResult.Launched(
                    "Virtual user $userId, package ${clone.sourcePackage}, " +
                        "launch requested in ${System.currentTimeMillis() - t0} ms. " +
                        "Login, notification delay, RAM and battery must still be " +
                        "measured on a real phone — launch alone proves none of those."
                )
            }
        } catch (e: Exception) {
            EngineResult.Failed("Engine launch threw: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
