# Food.Logger permanent workflow

- Use this checkout: C:\Users\mrove\Projects\food.logger. Read CURRENT_STATE.md first; inspect local changes and reuse the existing app.
- Keep work focused on the owner's request. Prioritize an installable build before optional investigation, especially when usage is limited. Run targeted checks, then required build/tests; do not repeat successful checks without a concrete reason.
- At completion of every app/build change, publish the new APK into this repository's deliverables folder. Use tools/Publish-LocalApk.ps1 after a successful build and source commit. Preserve previous builds, update deliverables/LATEST.txt, and report the exact artifact path, source commit, tests and limitations.
- Increment versionCode for a distributed update and use a distinct versionName. Keep com.foodlogger.debug for debug updates and verify the signing certificate matches the previous delivered APK. Never replace signing keys or uninstall/clear user data to make an update install.
- Keep secrets.properties, credentials, signing keys, SDKs and generated outputs out of Git. USDA_API_KEY is read from ignored secrets.properties or the environment by the existing Gradle configuration. Do not print keys in logs or chat. Source ZIPs must exclude local secrets; configured APKs contain the build-time key and are private local deliveries.
- Preserve saved foods, offline operation, historical nutrition snapshots and explicit Room migrations. Do not recreate the app or reset the database.
- Update CURRENT_STATE.md with verified results and the next manual check. Commit/push when authorized and verify the remote revision. Distinguish automated tests, live HTTP checks and actual phone/camera testing.
