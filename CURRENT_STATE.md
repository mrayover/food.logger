# Food.Logger — verified compiling baseline

Verified 2026-10-11 (America/Los_Angeles; test report timestamp 2026-10-12 06:20:33 UTC).
The sole source of truth is `mrayover/food.logger`, branch `main`.

## Baseline and scope

This work started from repository commit `e8740d7cdd7a731a8234273d63d743184654055f` (the recovered baseline). Its previous preservation report correctly warned that the earlier successful build fixes were absent. The fixes below have now been restored in the repository and verified against this source. The project was not recreated or implemented again from the specification.

No features, UI redesign, architecture refactoring, or cosmetic work were undertaken.

## Restored fixes

- Explicit domain `Target` imports in data, UI and test files resolve the collision with Kotlin's `Target` annotation.
- Explicit `com.foodlogger.R` import in `FoodApp` resolves resource references.
- Named `pathEffect` argument in the chart's `drawLine` call fixes the stroke-cap argument mismatch.
- Explicit `Unit` return type on `PersistenceTest.snapshotsSurviveEditingDeletionAndRelaunch` meets JUnit's void-return requirement.
- Bounded buffered backup reading replaces `readNBytes`, retaining the 20 MiB limit without relying on that newer Android API.
- Serializable recommendation draft with a nullable JSON-backed Compose saver, plus a saved correction ID resolved from loaded records, restores the documented activity-recreation state fix. The initial saver nullability compile error was corrected before the successful build.
- Preserved the Room-generated version 2 schema under `app/schemas/`; no database version or migration change was made.
- Ignored the generated `.kotlin/` cache alongside existing build/cache exclusions.

## Verification actually completed

The final Gradle invocation ran `assembleDebug testDebugUnitTest assembleDebugAndroidTest --no-daemon --max-workers=2 --console=plain` and exited 0 with **BUILD SUCCESSFUL** (74 actionable tasks: 26 executed, 48 up-to-date).

| Check | Verified result |
| --- | --- |
| `:app:assembleDebug` | Passed; debug APK produced |
| `:app:testDebugUnitTest` | Passed; all 25 existing `EngineTest` tests ran |
| Unit-test XML totals | 25 tests, 0 failures, 0 errors, 0 skipped |
| `:app:assembleDebugAndroidTest` | Passed; existing instrumentation tests compiled into a test APK |
| `git diff --check` | Passed before commit |

The unit-test counts were read from `app/build/test-results/testDebugUnitTest/TEST-com.foodlogger.EngineTest.xml`, not inferred from an earlier conversation.

Generated APK SHA-256 values from this build:

- `app/build/outputs/apk/debug/app-debug.apk`: `3a8e16beb4ba641383daa33f32dc30efe0fed893a2047cd3a71f2543ee24cf37`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`: `94e69d887ef0e7acfad113a4722e63fea30b1f860fe0716392fff7e3922eeca3`

APKs, raw build reports, SDKs and caches remain generated local outputs and are not source-controlled. These hashes identify this verification run; debug signing and environment details may change hashes on another machine.

## Build environment and reproduction

Used Gradle 8.9 (the version pinned by the existing wrapper), full Temurin JDK 17.0.20.1, Android platform 35 and Build Tools 34.0.0. Existing AGP 8.7.3 and Kotlin 2.0.21 configuration was retained.

The environment initially lacked Gradle, the Android SDK and a full JDK with `jlink`. Tools were installed outside the repository. An inherited obsolete Java/Gradle proxy setting also blocked dependency resolution; local proxy/trust-store configuration was corrected without changing project repositories or dependencies. The successful run used the installed Gradle 8.9 executable with those environment settings.

On a configured machine with JDK 17 and Android SDK 35, run from the repository root: `bash gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest` (Windows: `gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest`). Configure the SDK through `ANDROID_HOME` or an untracked `local.properties`.

## Not verified and known limitations

- No remaining compile or existing unit-test failures were observed in the final run.
- The three persistence instrumentation tests and existing Compose UI test were compiled but **not executed on a device or emulator**. App launch and end-to-end runtime workflows are not verified by this session.
- Activity recreation, backup import on older Android, live food-provider requests, barcode/camera/OCR, and persistence after process restart remain unverified on-device.
- Previously mentioned `CoreWorkflowTest` and `RestartTest` were absent from the recovered repository and were not recreated.
- The previously documented unused orange alcohol threshold in `Engine.statuses` remains unchanged and outside this build-baseline task.
- Release builds/signing, lint, device compatibility and production readiness were not evaluated.

## Publication blocker

The verified source was committed locally, but publication to GitHub was blocked: command-line Git had no HTTPS write credential, and the connected GitHub integration returned HTTP 403 (`Resource not accessible by integration`) when creating the commit tree. GitHub `main` has not been updated. Repository write access must be restored before this task meets its full definition of done. A Git bundle preserves the commits for subsequent import and push; it is a recovery transport, not a replacement source of truth.
