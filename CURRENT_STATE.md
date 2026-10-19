# Food.Logger — bundled offline catalog verified

Verified 2026-10-18 America/Los_Angeles (test XML timestamps 2026-10-19 04:55:38–43 UTC).
Source of truth: `mrayover/food.logger`, branch `main`. This task started at
`95113e5` and retained the previously restored build baseline from `7c5de8c`.
The project was extended in place, not recreated.

## Implemented

- Bundled **7,793 USDA FoodData Central SR Legacy foods**, all validated for calories,
  protein, carbohydrate and fat. No records excluded for missing required macros.
- Preserved **644,125 source nutrient rows**, **14,449 source portions**, FDC/NDB IDs,
  nutrient units, derivations, data-point counts, source dictionaries and footnotes.
- Mapped eight existing app nutrients: calories, protein, carbs, fat, fiber, sugar,
  sodium and ethanol. Other secondary nutrients remain in source tables. Missing
  secondary values stay NULL in SQLite; existing app totals use zero with explicit
  missing-value disclosure in food notes. No nutritional values or density conversions
  were invented.
- Separate read-only SQLite catalog; FTS4 case-insensitive token-prefix search, bound
  parameters, indexed food/portion lookups, and a 50-result limit. Lazy streaming
  installation and queries on Dispatchers.IO; no full-catalog startup load.
- Automatic offline search in Add Food. Personal/recent/favorite foods retain priority;
  personal multiword matches no longer require adjacent words. Existing optional online
  search remains button-driven. A query change cancels stale online work.
- Catalog/online USDA share deterministic FDC-based identities. Personal copies win
  on selection and duplicate rows are suppressed across result sections.
- Existing quantity editor uses the authoritative 100 g base; source household portion
  gram weights are retained in personal food notes. Existing Repository.log saves a
  reusable personal food and snapshots each entry's nutrition. Catalog records remain
  untouched by personal edits or deletions.
- No Room schema/version/migration changes: version 2 and MIGRATION_1_2 retained.
  No changes to check-ins, weights, recipes, alcohol screens or recommendations.

## Catalog reproducibility and size

- Asset: `app/src/main/assets/usda-sr-v1.db.bin` (gzip bytes; `.bin` avoids Android's
  automatic `.gz` decompression/renaming).
- Compressed source asset: **11,371,229 bytes (10.84 MiB)**.
- Installed SQLite database: **41,426,944 bytes (39.51 MiB)**; excluded from Android backup.
- APK asset hash verified against manifest: `0e52bcfb98b74f137db74400138ef7104f0264c31b4af4da6e40c0c06a5effee`.
- Official source ZIP SHA-256: `b80817294b8850530aaedf2e515c02593b1824f763a0ff356e5c2081643e6fd0`.
- Generator: `tools/build_food_catalog.py`. Standard-library Python only, pinned source
  checksum, fixed gzip timestamp; regenerating from the downloaded official ZIP produced
  byte-identical compressed output (verified with cmp).
- Full source attribution, reproduction, online setup and limitations: `docs/OFFLINE_CATALOG.md`.
  USDA data are public-domain/CC0. Nothing is downloaded by the app for offline search.

## Verification actually run

Final Gradle invocation: `assembleDebug testDebugUnitTest assembleDebugAndroidTest
--no-daemon --max-workers=2 --console=plain` with a local proxy-only init script.
Exit 0, **BUILD SUCCESSFUL in 26s**, 76 actionable tasks (20 executed, 56 up-to-date).

| Check | Actual result |
| --- | --- |
| Debug Android app | Built successfully |
| Existing EngineTest suite | **25 tests passed**, 0 failures/errors/skips |
| New OfflineCatalogTest suite | **6 tests passed**, 0 failures/errors/skips |
| Total JVM tests | **31 passed** |
| Python catalog validation | **4 tests passed** |
| Representative searches | chicken breast, banana, egg, rice, milk, ground beef all returned results |
| Network disabled | All six searches passed with outbound socket connections denied |
| Quantity → log → totals | Actual catalog banana at 150 g = 1.5 servings, 133.5 kcal, 1.635 g protein; all eight totals asserted |
| Reusable personal food | Actual Room insertion and last-used quantity verified |
| Historical snapshots | Remain equal after personal editing/deletion; catalog unchanged and reopens |
| Provenance/servings | Known USDA portion weight retained; missing sodium explicitly disclosed |
| Full source mapping | Every mapped nutrient compared against original retained USDA rows |
| SQLite integrity/read-only/index | integrity_check passed; mutation rejected; FTS virtual-table index used |
| Packaged APK asset | Correct `.bin` name, gzip signature, byte size and SHA-256 verified |
| Instrumentation test APK | Compiled successfully; **not executed on a device/emulator** |
| git diff --check | Passed |

Verification caught and fixed two integration issues before completion: Android asset
merging rewrote `.gz` assets, and explicit AND was not interpreted consistently by
standard/enhanced FTS4 parsers. The asset now retains its `.bin` name and search uses
implicit AND. Initial test-runtime download failures were environmental and resolved;
no tests are claimed passed from those failed runs.

## APK

- Debug package: `com.foodlogger.debug`, version 1.0.0 (existing version unchanged).
- Final APK: **69,806,858 bytes (66.57 MiB)**.
- SHA-256: `40ec70949a5e4d320ad785d5704d7f0fbc3169cd3a2da3aa273f76e1701676d5`.
- Source asset included in APK: 11,371,229 bytes before ZIP compression.
- Uses a newly generated debug signing key; no USDA credential configured. An older
  installed debug build may have a different signing key. Export data before any
  uninstall; this implementation does not require clearing user data.

## Online providers inspected

- Open Food Facts: existing credential-free search/barcode provider retained. A live
  search request returned one product successfully; exhaustive normalization and live
  barcode behavior were not tested.
- USDA: existing provider is present, but no environment key or secrets.properties was
  configured. It returns no online results with a blank key. Authenticated USDA requests
  were not run. Set USDA_API_KEY in ignored secrets.properties or build environment,
  rebuild/install, and test with internet access. No secret was added or committed.
- BuildConfig keys are extractable from an APK; a public-distribution credential strategy
  is outside this stage. See documentation for official key/rate-limit guidance.

## Build environment and remaining limitations

Gradle 8.9, full Temurin JDK 17, Android SDK 35 / Build Tools 34.0.0. Existing AGP 8.7.3
and Kotlin 2.0.21 retained. Added Robolectric 4.14.1 as a test-only dependency with
Android resources enabled. Network/trust configuration and SDK installations remained
outside the repository. Tests run Android SQLite/Room under Robolectric API 28 on the
JVM. The socket-denial test uses JDK 17 SecurityManager and emits its deprecation warning;
future JDK upgrades may require a different test-only network guard.

No physical-phone/emulator taps, airplane-mode system UI, process-restart UI flows,
or existing instrumentation tests were executed. A phone acceptance checklist is in
the catalog documentation. Search is bounded English token/prefix matching, not fuzzy
search; refine broad terms. The current quantity UI does not have a household-portion
picker. SR Legacy is an older reference dataset, not a current branded product catalog.
No remaining compile or executed test failures were observed in the final run.

## Preservation / publication

Command-line GitHub push was blocked by missing HTTPS write credentials (a push dry-run
failed with `could not read Username`). A complete Git bundle preserves the repository
history and this implementation for import/push with the user's normal credentials.
A source ZIP and debug APK are also delivered. GitHub main is not claimed updated.
See the bundle recovery instructions in `docs/OFFLINE_CATALOG.md`.
