# Food.Logger — USDA online search enabled

Verified 2026-10-28 America/Los_Angeles in C:\Users\mrove\Projects\food.logger,
branch serving-barcode-improvements. Previous serving/barcode baseline: e75ca73.

## Current app and delivery

- Version 1.0.1 / versionCode 2, package com.foodlogger.debug.
- The owner's USDA_API_KEY is configured in ignored secrets.properties. The existing
  Gradle/provider integration reads it; no nutrition provider rewrite was necessary.
  The compiled key was checked against the local file without displaying its value.
- One authenticated live USDA search for banana succeeded and returned 3 foods.
- APK: 66,673,859 bytes; SHA-256:
  f670ab8cad33b360d33af2fa4525577cbbd5385bbd8109de14392fb8ff0e08c6.
- Signing certificate verified equal to the previous delivered APK. The original key
  is in the owner's default .android profile. An alternate tool profile initially
  produced a mismatched candidate; it was caught before publication and repackaged
  using the original key. No signing keys were replaced and no user data was cleared.
- Latest APK: deliverables/Food.Logger-debug.apk. Exact versioned build and source
  revision: deliverables/LATEST.txt and its folder's BUILD_INFO.txt.

## Actual verification

- gradlew.bat testDebugUnitTest assembleDebug --no-daemon --max-workers=2 --console=plain:
  BUILD SUCCESSFUL in 53s; all 47 unit tests passed, zero failures/errors/skips.
- EngineTest 25, OfflineCatalogTest 6, ServingTest 13, ServingPersistenceTest 3.
- Signing-only assembleDebug rebuild: BUILD SUCCESSFUL in 14s; APK signature and
  certificate continuity verified. No code changed after the successful test run.
- Packaged offline catalog verified unchanged: 7,793 foods and 11,371,229-byte asset.
- Delivery script syntax checked and publication verifies package/signature/copy hash.
- Existing offline SecurityManager test emits its known JDK deprecation warning.
- Logs: deliverables/usda-build-final.log and deliverables/usda-signing-build.log.
- No phone installation, UI taps, camera scans or on-device USDA requests were tested.

## Established completion practice

AGENTS.md requires a new APK in deliverables on completion of each app/build change,
preserved previous builds, version increments, signing continuity, factual verification
and an early usable build when usage is limited. After a successful build and source
commit, run tools/Publish-LocalApk.ps1 with JAVA_HOME and ANDROID_HOME configured. Use
the original signing profile; do not substitute a new ANDROID_USER_HOME or signing key.
The script creates deliverables/builds/<version>-code<code>-<timestamp>/ with versioned
APK, tracked-source ZIP, source commit/checksum/certificate record and test XML. It
preserves old latest APK/ZIP copies and updates deliverables/LATEST.txt and latest files.
Credentials, APKs, generated files and machine-specific configuration stay out of Git.
Source ZIPs exclude secrets.properties. Configured APKs contain the build-time API key;
keep those deliveries private. The local key itself is not committed or displayed.

## Preserved behavior and next manual check

Serving/barcode changes remain intact; older saved foods retain their definitions and
historical logs retain nutrition snapshots. Room version 2/MIGRATION_1_2 and the offline
USDA catalog are unchanged. No data reset or migration was performed.

Next task: update over the existing app using the new APK. Open Add Food, enter a food
name and tap Search databases; confirm USDA results, select a food, review quantity,
log it and check daily totals. Offline searching remains automatic; online searching
uses the existing explicit button. See docs/SERVINGS_AND_BARCODES.md for the full
serving/barcode phone checklist. Actual phone acceptance remains pending.
