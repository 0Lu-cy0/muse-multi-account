package com.example.musecontainer

/** Result of a launch attempt. Deliberately honest: until a virtual engine
 *  (BlackBox/NewBlackbox) is actually integrated AND proven on a real phone,
 *  launch must report NotIntegrated, never silently launch the normally
 *  installed Muse app — that would look like a clone working when it is not. */
sealed class EngineResult {
    data class NotIntegrated(val reason: String) : EngineResult()
    data class Launched(val detail: String) : EngineResult()
    data class Failed(val detail: String) : EngineResult()
}

interface EngineLauncher {
    fun launchClone(clone: CloneEntry): EngineResult
}

/** v1 stub. Replace only this implementation when a BlackBox/NewBlackbox AAR
 *  is integrated (BlackBoxCore.installPackageAsUser + launchApk per userId),
 *  and only claim Launched after a real-device test. */
class StubEngineLauncher : EngineLauncher {
    override fun launchClone(clone: CloneEntry): EngineResult =
        EngineResult.NotIntegrated(
            "Virtual engine not integrated in prototype v1. " +
            "Clone '${clone.name}' (${clone.sourcePackage}) is recorded with " +
            "isolated host-side data dir, but no guest process was started. " +
            "See ENGINE_EVALUATION.md for why no engine was bundled yet."
        )
}
