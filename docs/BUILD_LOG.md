# Build log — host prototype v1

## Gradle build: FAILED in this VM (VM toolchain blocker, not app code)

- Toolchain: Gradle 8.5 (downloaded to `~/workspace/tools/gradle-8.5`),
  JDK 17.0.21+9 (`~/workspace/tools/jdk-17.0.21+9`),
  Android SDK `~/workspace/tools/android-sdk` (build-tools 34.0.0,
  platform android-34 only; no cmdline-tools, no NDK, no platform 35/36).
- Symptom, twice:
  ```
  FAILURE: Build failed with an exception.
  * What went wrong: Could not receive a message from the daemon.
  Caused by: java.lang.IllegalArgumentException: Unexpected type tag 109 found.
  ```
- Daemon-side evidence (`/root/.gradle/daemon/8.5/daemon-*.out.log`):
  the daemon started and listened on localhost/127.0.0.1, then stopped
  ~10 s later "after the daemon was no longer found in the daemon
  registry" — i.e. the client socket died first. Reducing the heap
  from `-Xmx2048m` to `-Xmx1024m` and using `--no-daemon` did not help.
  The VM also had only ~450 MB free RAM at the time, which may have
  contributed, but the type-tag error is a daemon-protocol failure,
  not a compilation error: no Kotlin/Java error was ever reported.
- Consequence: the Gradle project (`settings.gradle`, `build.gradle`,
  `app/build.gradle`, Kotlin sources) is kept for a normal Android
  Studio / PC build, but it was NOT the build that produced the
  shipped APK in this VM.

## Manual build: SUCCEEDED

- Script: `build-manual.sh` — framework-only Java sources in
  `java-src/`, layout shared from `app/src/main/res/layout/`,
  `aapt2 compile` + `aapt2 link` + `javac` + `d8` + `zipalign` +
  `apksigner` (throwaway debug key).
- Fixes needed along the way (all in the script, not in app logic):
  1. `javac` encoding: added `-encoding UTF-8` (em-dash characters).
  2. R-class order: compile the aapt2-generated `R.java` before the
     app sources, with the R classes on the classpath.
  3. `d8`: raw classes dir rejected ("Unsupported source file type")
     and a `.dex` output file rejected ("Output must be a .zip or
     .jar archive or an existing directory") — fixed by jarring the
     classes first and using a dex output *directory*.
- Final result: `muse-container-host-debug.apk`, 21 KB, apksigner
  VERIFY_OK, versionCode 1 / versionName `0.1.0-prototype`,
  `com.example.musecontainer`, minSdk 29, targetSdk 34.
- Rebuilt after the performance-cap change (see PERFORMANCE.md);
  the cap is in both the Java and Kotlin sources.

## Engine integration build: NOT ATTEMPTED to completion (blocked)

- No NDK installed (BlackBox forks require NDK 29.0.13846066).
- Only platform android-34 installed; the forks need compileSdk 35.
- Gradle daemon failure above would also block their Gradle builds.
- No Android device/emulator with Play Services in this VM, so even
  a successful engine build could not have proven a Muse launch here.
- Therefore no BlackBox AAR was built or bundled. This is a verified
  stopping point, not a silent omission: see ENGINE_EVALUATION.md.
