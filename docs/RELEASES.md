# Signed releases for both phones

## Two delivery types

The owner declined publishing an extractable USDA key from this public repository.
GitHub Actions therefore builds a signed **credential-free** release: automatic offline
USDA catalog and Open Food Facts online search/barcodes work; authenticated USDA online
search is disabled. The **local private release** includes the owner's USDA key and must
stay local; never upload that APK or its generated BuildConfig to GitHub artifacts.
Both use com.foodlogger, the same release certificate, and the checked-in version.
The client integration remains intact; no server or new credential-entry feature was added.

## Install on each phone and preserve current records

Android requires Android 8.0/API 26 or later. The APK supports ARM32/ARM64/x86/x86_64;
camera hardware is optional. The release is not debuggable. It uses com.foodlogger,
while earlier development APKs use com.foodlogger.debug. Android treats these as separate
apps: release installation does not import or overwrite the debug app's private data.

On EACH phone separately:

1. Open the existing debug app. Settings -> **Export complete backup**. Save the JSON
   somewhere durable outside the app. **Export dashboard summary cannot restore data.**
2. Install the signed release APK alongside the debug app. Permit installation from the
   browser/file manager when Android requests it. Keep the debug app and its data.
3. In the new release app, Settings -> **Import complete backup**; choose that phone's
   exported JSON. Confirm only after checking the destination has no data you need.
   Import replaces the release app's current records; it does not merge two phones.
4. Compare foods, logs/daily totals, weights, goals, check-ins and historical snapshots.
   Confirm offline search. Keep the debug app until satisfied; do not clear/uninstall
   it to solve a signature mismatch. Both launchers currently have the same app label.
5. Back up each phone independently. No accounts, cloud synchronization or automatic
   sharing between you and your wife is provided. Optional per-food unit preferences
   are outside JSON; food quantities and main settings are included.

Future release updates: install the newer release APK OVER the existing release app.
Never change com.foodlogger or the permanent key. Increment versionCode for every
new distributed version; first release is 1.0.2/code 3, next must be code 4 or higher.
Change versionName as a human-readable label too. Do not reinstall an old code over a
newer one or uninstall/clear data to bypass installation errors. App data remains local;
Room schema/version 2 and explicit migration 1->2 are unchanged by this packaging work.

## GitHub Desktop / browser release workflow (no Android Studio)

1. Fetch/pull main in GitHub Desktop before making changes. Review CURRENT_STATE.md.
2. For the next update, edit app/build.gradle.kts versionCode/versionName, commit source
   in Desktop, and **Push origin** to main. The Signed Android release workflow runs.
   Alternatively open Actions -> Signed Android release -> Run workflow -> main to
   rebuild the existing version (a rebuild is not a new version).
3. Open https://github.com/mrayover/food.logger/actions/workflows/release.yml . Wait for
   build/test and Android installation checks. Open the run and its **Artifacts** section.
4. Download **Food.Logger-signed-release-<run number>**. Extract the ZIP, then transfer
   Food.Logger-release.apk to either phone. BUILD_INFO.json identifies package, version,
   certificate, APK SHA-256, source commit, supported architectures and credential mode.
   Install/update using the steps above. Test results are separate artifacts.
5. Artifacts expire after 90 days. Save trusted APKs/metadata in your own backups before
   expiry. GitHub Desktop manages source; downloading artifacts uses your web browser.
   Pull requests run debug checks without exposing signing secrets. Only main pushes
   and explicit manual runs build signed artifacts; action dependencies are commit-pinned.

The same-version smoke test checks fresh install, launch, install -r, unchanged app UID
and relaunch on API 26 and 35 emulators. It is not a test of real meals or either owner's
phone. JSON migration is separately tested between independent Room stores. Physical
phone acceptance and a real next-version update remain required checks.

## Permanent key and secure backup

Created once for Food.Logger; never generated in CI. Public certificate SHA-256:

6ed276d4c4e57da5c32811e967a351e1665ce5bf7aa27cc78a9cc5205ba24e84

This public pin lives in config/release-certificate.sha256; every release is rejected
if its certificate differs. It is safe to commit the public fingerprint, not private keys.

Private local key: .signing/food.logger-release.p12. Private recovery instructions/password
are in .signing/RECOVERY-PRIVATE.txt. Local Gradle values are in release-signing.properties.
Their Windows access is restricted to the owner and SYSTEM. All are Git-ignored.
A verified, access-restricted backup exists OUTSIDE the checkout at
C:\Users\mrove\Food.Logger-Release-Key-Backup. This is a same-computer backup: copy its
keystore and recovery information into your encrypted password manager/offline secure
storage as well. Losing the key/password prevents ordinary same-ID updates.
Keep passwords out of chat, screenshots, issue comments, commits, shared folders and logs.

## Exact Actions repository secrets

Already configured for this repository, but these are the exact four required names.
Browser: repository -> Settings -> Secrets and variables -> Actions -> New repository secret.

| Name | Secure value source |
| --- | --- |
| RELEASE_KEYSTORE_BASE64 | Base64 text of the EXISTING .signing/food.logger-release.p12 |
| RELEASE_STORE_PASSWORD | Existing password from the private recovery file |
| RELEASE_KEY_ALIAS | foodlogger-release |
| RELEASE_KEY_PASSWORD | Existing key password (same as store password for this PKCS12) |

To populate the binary secret, use a local PowerShell session and paste ONLY into the
GitHub repository-secret form (never into chat):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path '.signing/food.logger-release.p12'))) | Set-Clipboard
```

Clear the clipboard after saving: `Set-Clipboard -Value ''`. Base64 is an encoding, not
encryption. Repository secrets provide encrypted storage. To restore after a laptop loss,
use the SAME backed-up keystore and passwords. Never create a new key to fix a CI error.

USDA_API_KEY is intentionally NOT an Actions secret in the public repository following
the owner's decision. Keep it in ignored local secrets.properties as USDA_API_KEY=<value>.
The earlier unused Actions copy was removed. Public CI explicitly builds with an empty
USDA_API_KEY and -PallowMissingUsdaKey=true; verification refuses to upload a configured
APK. Do not reintroduce that secret into a public artifact workflow without owner consent.

## Local private release build

With JDK 17, Gradle wrapper, Android SDK 35 / Build Tools 34.0.0 configured:

```powershell
.\gradlew.bat testDebugUnitTest testReleaseUnitTest assembleRelease
.\tools\Publish-ReleaseApk.ps1 -PythonExe <python.exe path>
```

The existing local release-signing.properties and secrets.properties are reused. The
publisher verifies the certificate, SDK/package/version, private credential mode and
catalog checksum, then copies a versioned APK, metadata, tracked-source ZIP and test
results under deliverables/releases, preserving older builds. Latest pointer:
deliverables/LATEST-RELEASE.txt; convenience copy: deliverables/Food.Logger-release.apk.
Never upload the private release folder to public Actions artifacts. Local secrets and
build tools are not needed to DOWNLOAD/install the credential-free Actions APK.

Official references: Android signing https://developer.android.com/studio/publish/app-signing ;
update identity https://developer.android.com/google/play/app-updates ;
Actions secrets https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets .
