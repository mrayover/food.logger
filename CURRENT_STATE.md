# Food.Logger — preservation status

Preserved 2026-10-11 (America/Los_Angeles) from `/workspace/scratch/02e5266510e4/FoodLogger`.
Requested archive name: `Food.Logger-Post-Build-Fixes.zip`.

## Critical snapshot discrepancy

The files available at packaging time do **not** contain the latest build-fix work shown earlier in this conversation. All 38 existing non-generated project files match the earlier Food.Logger-Current-State.zip byte for byte. The latest code fixes, added CoreWorkflowTest and RestartTest, exported Room schema, APK and verification reports are absent.

Despite its user-requested filename, this ZIP preserves the **currently available earlier snapshot**, not the successfully built post-fix revision. No application code was reconstructed, restored or modified for this request. No build, debugging or application tests were resumed.

## Compilation and fixes recorded in the preceding work

Compilation **was successfully restored during the preceding work**: the recorded Gradle output showed assembleDebug, testDebugUnitTest and assembleDebugAndroidTest completing with BUILD SUCCESSFUL. A debug APK and test APK were produced then. Those results must not be attributed to the source bytes in this archive.

The preceding work applied these source fixes, which are now absent:

- Explicit domain Target imports to resolve Kotlin's Target annotation collision.
- Explicit application R import in FoodApp.
- Named pathEffect argument for the chart's drawLine call.
- Explicit Unit return type on the persistence test for JUnit compatibility.
- Bounded stream reading replacing readNBytes for older Android compatibility.
- Serializable/saveable recommendation draft and saved correction identity for activity recreation.

Build-environment blockers addressed then included missing SDK/Gradle installations, malformed SDK tools layout, a Java runtime lacking jlink, and certificate trust for dependency downloads. Temporary tooling is no longer present.

## Tests actually completed

- The preceding run completed all **25 EngineTest unit tests**, with **0 failures, 0 errors and 0 skipped**, according to the XML output recorded in this conversation.
- The instrumentation test APK compiled successfully; that does not constitute a device test pass.
- No builds or application tests ran for this preservation request. Earlier raw reports are unavailable in the current directory and have not been recreated as evidence.

## Tests not completed

- The three persistence instrumentation tests and existing Compose UI test were not completed during the preceding build-fix run.
- The new comprehensive workflow and process-restart tests were not executed to completion; their source files are also absent now.
- App launch, target editing, food search/logging, quantity changes, daily macro totals, weight entry, recommendation decisions, reopening check-ins and persistence after restart have no completed device verification from that run.
- Live provider requests, barcode/camera/OCR flows and older-device backup import remain unverified.

## Known issues remaining

- This snapshot still passes PathEffect in the stroke-cap argument position in ProgressScreens.kt and retains conflicting wildcard Target imports. The earlier compile fixes are missing.
- PersistenceTest.snapshotsSurviveEditingDeletionAndRelaunch still infers a Boolean return, incompatible with JUnit's void-return requirement.
- Recommendation draft/correction objects still use remember rather than saved state; activity recreation can lose the draft while retaining its screen.
- Backup import still calls readNBytes without an older-Android fallback.
- The saved orange alcohol threshold is not used by Engine.statuses.
- No new application runtime failure was established through device execution. Testing was blocked by emulator startup failure: missing super/vbmeta partitions were reported. An older emulator package was downloaded, but no successful subsequent boot or test execution was recorded.

## Contents and preservation

The existing implementation includes Compose screens, Room models/DAOs/repository and migration code, food providers, logging and nutrition calculations, targets, weight/check-in logic, backup/restore, resources, original SVGs, fonts/license, tests, Gradle configuration and wrapper, and the implementation-specification DOCX.

All existing non-generated project files are included unchanged. CURRENT_STATE.md is the only added project document. Excluded: .gradle/, app/build/ and local.properties (generated caches/build outputs and machine-specific SDK configuration). No work was recreated from the specification.

The exact post-fix revision cannot be supplied from the files currently available. This archive preserves what remains and distinguishes prior observed results from the source actually packaged.
