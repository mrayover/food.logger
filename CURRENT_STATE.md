# Food.Logger — serving sizes and barcode improvements

Verified 2026-10-28 America/Los_Angeles. Extended the existing GitHub repository
`mrayover/food.logger` from `d7c8836` on `main`. Delivery branch:
`serving-barcode-improvements`. The project was not recreated.

## Changes

- Open Food Facts uses a reliable manufacturer serving rather than unnecessarily
  defaulting to 100 g/ml. `2 tbsp (32 g)` becomes one serving with every supported
  nutrient scaled by 0.32, including sodium's original grams-to-milligrams conversion.
  Original serving text, declared quantity/unit, source and nutrition reference remain
  in notes. Decimal/fractional metric servings are supported.
- Missing, malformed, contradictory, multipack or implausibly large serving metadata
  retains the 100 g/ml reference. Required nutrition must be finite and nonnegative;
  incomplete products are rejected. Missing/invalid secondary OFF nutrients retain the
  app's zero representation with an explicit note. Malformed search-array rows are
  skipped. Products without an identifier cannot collapse into a shared empty-code ID.
- Explicit volume servings never acquire an invented gram weight. No density conversion
  is performed. Online USDA branded products can use consistent explicit gram servings;
  volume-only and SR Legacy results keep their 100 g mass reference. The bundled USDA
  database and its nutrition values are unchanged.
- Quantity entry shows the actual serving prominently. Newly normalized products default
  to 1 serving; servings/g/oz are offered only when legitimate mass conversion exists.
  Calories and all supported macros update immediately; equivalent grams are shown.
  Switching units preserves consumed amount; invalid input never resets to one serving.
  The dialog scrolls on smaller screens.
- The existing lastQuantity field remembers logged amounts. A per-food SharedPreferences
  preference remembers the unit across restarts without changing the nutrition schema.
  Optional unit preferences are not included in JSON backups; quantities are included.
- Barcode lookup reuses personal foods before networking, preserving edits, offline use
  and older serving definitions. Leading-zero UPC/EAN padding is matched. Valid numeric
  lengths are 8, 12, 13 and 14; incomplete inputs cannot be submitted. Returned codes must
  match before automatic selection. Provider results are deduplicated without mixing
  nutrition. Unusable/not-found results show a label-scan/manual-entry message. Existing
  intentional duplicates are not destructively merged. The scanner library is unchanged.
- Historical-entry quantity state now has a stable entry identifier during recomposition.
  Existing log snapshots, Room version 2 and MIGRATION_1_2 are unchanged. No migration,
  database reset or automatic renormalization of saved foods occurs. Personal food edits
  continue to leave historical nutrition snapshots intact.

## Verification actually run

Final command: `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
--no-daemon --max-workers=2 --console=plain`.
**BUILD SUCCESSFUL in 58s**; 76 tasks, 12 executed and 64 up-to-date. The initial clean
build also passed (6m 52s). JDK 17.0.20.1, Gradle 8.9, Android SDK 35 / Build Tools
34.0.0; existing AGP 8.7.3 and Kotlin 2.0.21 retained. No dependencies changed.

| Check | Actual result |
| --- | --- |
| Existing EngineTest | 25 passed |
| Existing OfflineCatalogTest | 6 passed |
| New ServingTest | 13 passed |
| New ServingPersistenceTest | 3 passed |
| All JVM tests | **47 passed, zero failures/errors/skips** |
| Existing Python catalog validation | **4 passed** |
| Debug APK and instrumentation APK | Both built |
| APK catalog validation | Passed: unchanged 7,793-food asset and checksum |
| git diff --check | Passed |
| Android device availability | adb reported no attached devices/emulators |

Targeted tests cover manufacturer nutrition conversion, missing/invalid/contradictory
metadata, fractional metric servings, liquids, grams/ounces conversion, amount-preserving
unit switching, initial/remembered units, repeat provider lookup and barcode deduplication,
older saved foods, actual Room quantity-to-log-to-totals calculations, immutable historical
snapshots, backup validation and reopening saved foods without a network provider.
Existing tests continue checking all offline catalog values and denied-network searches.
The existing SecurityManager-based offline test emits its JDK deprecation warning.

One live OFF HTTP lookup for barcode `3017620422003` returned Nutella with no serving_size
or serving_quantity; this is a 100 g fallback case. Manufacturer-serving and invalid-response
cases use deterministic fixtures. This HTTP check was not a camera/device test. No USDA
credential was configured, so authenticated live USDA requests were not tested.

## APK and delivery

- Package: `com.foodlogger.debug`, existing version 1.0.0.
- Debug APK: **66,942,682 bytes (63.84 MiB)**.
- SHA-256: `abe75d6c8c689a28e1c39a50d174d8516bc06aecb974a176fc9112e59c78d4ad`.
- Bundled catalog remains 11,371,229 bytes with SHA-256
  `0e52bcfb98b74f137db74400138ef7104f0264c31b4af4da6e40c0c06a5effee`.
- GitHub write-access dry-run succeeded. The final delivery response records the committed
  revision and confirmed push status. Source ZIP, APK and verification evidence are also
  copied outside ignored build directories into the workspace's `deliverables` folder.
- The APK uses this environment's debug signing key. Updating an installed app requires
  its original signing key. Do not clear/uninstall user data to bypass a signature mismatch.

## Remaining limitations and manual testing

No physical-phone/emulator UI taps, camera scans, process-restart UI checks or Android
instrumentation tests were executed. Room integration ran under Robolectric API 28.
No live manufacturer-serving product was camera-scanned. Provider data can change;
review source values before logging. Unrecognized serving formats intentionally use the
reference fallback. Volume-only servings offer multiples of the serving, without g/oz.
No full household-portion picker, density database or UPC-E expansion was added.

Older personal foods keep their saved 100 g definition even when new provider metadata
has a manufacturer serving. This preserves their meaning; any change requires explicit
personal-food editing and review of nutrition and serving together.

Phone acceptance steps and behavior details: `docs/SERVINGS_AND_BARCODES.md`.
Bundled catalog provenance/build instructions remain in `docs/OFFLINE_CATALOG.md`.
No changes to weights, goals, check-ins, recommendations, backend, accounts or providers.
