# Food.Logger — signed releases verified

Verified 2026-10-28 America/Los_Angeles. Checkout: C:\Users\mrove\Projects\food.logger.
GitHub mrayover/food.logger/main is the source of truth. Release implementation source
82738df; corrected CI/tooling source 5fbd015. Later verification-record commits change
only documentation; APK/source provenance remains exact in each BUILD_INFO.json.

## Ready deliveries

Version 1.0.2 / code 3. Stable release ID: com.foodlogger. Development retains
com.foodlogger.debug and its original debug configuration. Next distributed update
must increase versionCode to >=4 while keeping the SAME release ID and signing key.

- PRIVATE local APK with USDA online search: deliverables/Food.Logger-release.apk.
  Exact package folder/source revision: deliverables/LATEST-RELEASE.txt.
  SHA-256: 78480cc0b9929f0f17c7afb26e282b953a4e9855e8ccb1adf5877e510ad8e4e4.
- PUBLIC credential-free signed APK: Actions run 40822264911, artifact
  Food.Logger-signed-release-2. Download/extract the ZIP in a browser; no Android Studio.
  https://github.com/mrayover/food.logger/actions/runs/40822264911
  Local verified copy: deliverables/actions/40822264911/release/Food.Logger-release.apk.
  SHA-256: 49518aa552826dd7b1c3d62fec89414464228cf77c3e33be0313c5831eb9f82c.

Owner declined publishing an extractable USDA key. The public Actions build contains
NO USDA credentials: offline USDA catalog and Open Food Facts online search/barcodes
work; authenticated USDA online search is disabled. Full USDA-enabled APK stays LOCAL.
Both delivery modes share the permanent certificate and application ID. The owner and
wife can privately install the local configured APK on both phones. Do not upload it
or generated BuildConfig to public artifacts. USDA_API_KEY remains only in the ignored
local secrets.properties; its unused Actions secret was removed.

## Permanent signing and repeatability

Generated ONCE: RSA-4096 PKCS12 release key, reused locally and restored (not generated)
by Actions. Public certificate SHA-256, pinned in config/release-certificate.sha256:
6ed276d4c4e57da5c32811e967a351e1665ce5bf7aa27cc78a9cc5205ba24e84.

Private key/recovery: ignored .signing/; local signing values: release-signing.properties.
Windows permissions restrict these to the owner and SYSTEM. A verified protected copy
exists outside the checkout: C:\Users\mrove\Food.Logger-Release-Key-Backup. This is
still on the same computer: owner must place key AND recovery password in encrypted
off-computer storage. Never generate a replacement key for a failed build.

Four encrypted repository Actions secrets are already configured:
RELEASE_KEYSTORE_BASE64, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD.
Exact secure setup/recovery instructions are in docs/RELEASES.md; no secret values are
in Git, source archives, chat or public logs. No USDA secret is used by public Actions.

Workflow: .github/workflows/release.yml, pinned official actions and Ubuntu 24.04.
Main pushes build/test and upload signed, non-debuggable credential-free APKs with
source ZIP/identity/checksums. Manual Run workflow rebuilds a selected source revision.
PRs run debug checks without signing secrets. Public verification fails if USDA is
configured, signing pin differs, package/version/SDK differs, or catalog checksum fails.
Actions artifacts expire after 90 days; retain trusted copies locally.

Local private publishing: tools/Publish-ReleaseApk.ps1 after successful release build
and source commit. It preserves versioned builds and produces APK, source ZIP, metadata,
test evidence and LATEST-RELEASE.txt under deliverables. Debug publisher remains intact.

## Verification actually completed

- Initial local signed debug/release build passed; final local tests: 48 debug + 48
  release = 96, zero failures/errors/skips. Final build/test command completed in 1m9s.
- Hosted corrected run 40822264911: build SUCCESS; 48 debug + 48 release tests passed.
  Downloaded CI test XML independently checked: 96 total, zero failures/errors/skips.
- Android API 26 (8.0) and API 35 (15) emulator jobs both SUCCESS: fresh installation,
  launch, same-version install -r, unchanged app UID, force-stop and successful relaunch.
- Initial run 40821395420: build and API26 passed. API35 installed/launched but the old
  dumpsys UID field lookup failed. The smoke script now uses portable pm UID listing;
  corrected run passed on both. No app/data change was needed to fix the test.
- Local AND downloaded Actions APK signatures/pinned certificate, package com.foodlogger,
  code3, non-debuggable status, minSdk26/target35, all four native ABIs and unchanged
  7,793-food catalog checksum verified. Both APKs use the SAME permanent certificate.
- Negative guards tested: configured private APK is rejected for public delivery;
  debug-signed APK is rejected for release publishing.
- New independent-store complete JSON backup/restore test preserves exact AppData and
  historical nutrition snapshots; original source store remains unchanged.
- No real-phone installation, camera scan, physical-phone import or higher-code future
  update was performed. Emulator reinstall does not assert preservation of real meals.
- Non-failing CI advisories remain for the third-party Android setup action's Node20
  runtime being forced to Node24 and the existing Gradle8.9 toolchain age. Official
  actions were updated to current pinned releases; no unrelated AGP/Kotlin upgrade.

## Data migration and next owner task

No nutrition/UI/features/database/catalog changes. Room remains version2, migration1->2,
backup schema1. Debug and release IDs are DIFFERENT apps and have separate data stores.
Install release alongside debug; it does not automatically migrate debug private data.

On EACH phone: Settings -> Export complete backup from DEBUG (not dashboard summary),
install release, Import complete backup into the fresh release, compare foods/logs/
daily totals/weights/goals/check-ins. Import REPLACES destination data, does not merge
phones, and omits optional per-food unit preferences. Keep old app/data and JSON backup
until verified. Future releases update com.foodlogger in place using the same key and
higher versionCode; never uninstall/clear data to bypass signature errors.

Next task: perform that phone acceptance on both phones, and secure the off-computer
signing backup. Detailed owner GitHub Desktop/browser workflow, exact secrets, migration,
credential-mode limitations and recovery: docs/RELEASES.md.
