# Food.Logger — permanent signed release setup

Release packaging source baseline: deaa767 on serving-barcode-improvements.
Working checkout: C:\Users\mrove\Projects\food.logger. GitHub mrayover/food.logger
remains the source of truth. Release source/verification publication is in progress;
final run results and actual remote revisions will be recorded after Actions completes.

## Identity and delivery

- First release: version 1.0.2/code 3; stable application ID com.foodlogger.
- Debug development configuration remains com.foodlogger.debug with its original key.
- Permanent release RSA-4096 PKCS12 key generated once; CI only restores that existing key.
- Public certificate SHA-256 (committed pin):
  6ed276d4c4e57da5c32811e967a351e1665ce5bf7aa27cc78a9cc5205ba24e84.
- Private key/recovery files are ignored and Windows-access restricted. An identical
  protected backup exists outside the checkout at C:\Users\mrove\Food.Logger-Release-Key-Backup.
  Owner still needs an encrypted off-computer copy of the key AND recovery password.
- Four encrypted Actions signing secrets are configured. No private signing values are
  committed. See docs/RELEASES.md for exact names and secure setup/recovery instructions.
- Owner declined public artifacts containing an extractable USDA key. The USDA Actions
  secret was removed. Public CI forces an empty USDA key, verifies it, and uploads only
  the credential-free signed release. Offline USDA and Open Food Facts continue working.
  Authenticated USDA online search remains available in the private LOCAL release only.
- Local private APK: deliverables/Food.Logger-release.apk. Versioned delivery and source
  provenance will be recorded by tools/Publish-ReleaseApk.ps1 and LATEST-RELEASE.txt.
- Future updates MUST retain this key/application ID and increase versionCode (next >=4).

## Implementation and checks so far

Gradle release uses protected local properties or Actions environment signing values,
non-debuggable release configuration, and fails closed without signing credentials.
Debug builds/tests still work without release credentials. Public CI must explicitly
opt into a missing USDA key; it never receives that key. Action dependencies are pinned.
No functionality/UI/database/catalog refactoring was performed.

Initial local command: testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease;
BUILD SUCCESSFUL in 2m45s, 47 tests passed per variant (94 total), zero failures/errors.
A new independent-store JSON backup-transfer test is included in final verification.
Local release package/signature/v2 signing/API26-35 manifest/native ABIs/catalog checksum
verified. API26/35 emulator installation checks are configured in Actions, not yet run.
No phone installation or camera testing claimed. Room remains version 2, migration 1->2;
backup schema remains 1. Existing data is never cleared as part of release installation.

## Owner workflow and next acceptance

Read docs/RELEASES.md. GitHub Desktop: pull main, make scoped updates and increment app
versionCode/versionName, commit and Push origin. Browser: Actions -> Signed Android
release -> completed run -> Artifacts -> Food.Logger-signed-release-<run number>.
Download/extract APK; no Android Studio required. Artifacts expire after 90 days.

The release and previous debug app install side by side. On EACH phone, export Settings
-> Export complete backup from the debug app; import that JSON into the fresh release
app. Import replaces destination records, does not merge phones, and does not transfer
optional per-food unit preferences. Keep the old app/data and backup until verified.
Next owner task: install the release, import each phone's own backup, compare records
and daily totals, then verify a future higher-version release installs over it intact.
See docs/RELEASES.md for detailed steps, credential-mode limits and secure key recovery.

Initial Actions run 40821395420 built and tested the signed credential-free APK successfully. API 26 installation/reinstall passed. API 35 installed and launched successfully but its smoke UID lookup failed; the verifier now uses the portable package-manager UID listing. Official Actions dependencies and Ubuntu image are updated/pinned following concrete deprecation notices. Corrected workflow verification is pending.
