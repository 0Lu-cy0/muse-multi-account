package com.example.musecontainer;

/** Honest launch contract: v1 has no virtual engine, so launch must report
 *  NOT_INTEGRATED and must never silently launch the normally installed Muse
 *  app (that would fake a successful clone). Replace StubEngineLauncher only
 *  when a BlackBox/NewBlackbox AAR is integrated AND proven on a real phone. */
public class EngineLauncher {
    public static class Result {
        public final boolean launched; public final String detail;
        public Result(boolean launched, String detail) { this.launched = launched; this.detail = detail; }
    }
    public static Result launchClone(CloneEntry clone) {
        return new Result(false,
            "Virtual engine not integrated in prototype v1. Clone '" + clone.name + "' (" +
            clone.sourcePackage + ") is recorded with an isolated host-side data dir, " +
            "but no guest process was started. See ENGINE_EVALUATION.md.");
    }
}
