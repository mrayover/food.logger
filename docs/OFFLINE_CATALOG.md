# Bundled offline food catalog

## Source and attribution

U.S. Department of Agriculture, Agricultural Research Service. FoodData Central,
SR Legacy, April 2018 (FDC publication date April 1, 2019). Public domain / CC0 1.0.

- Official download listing: https://fdc.nal.usda.gov/download-datasets/
- Source ZIP: https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip
- Licensing and API guidance: https://fdc.nal.usda.gov/api-guide/
- Documentation: https://fdc.nal.usda.gov/data-documentation/

The exact downloaded ZIP is pinned by SHA-256 in `tools/build_food_catalog.py` and
`app/src/main/assets/usda-sr-v1.db.manifest.json`. USDA has repackaged this release
with supporting CSVs; the archive hash, rather than its filename alone, identifies
our input. A changed hash deliberately stops import pending review.

## Reproduction

Python 3 with standard-library SQLite/FTS4 support is sufficient. From repository root:

1. Run `python tools/build_food_catalog.py` to download and generate the asset, or
   `python tools/build_food_catalog.py --source /path/to/official.zip` for a downloaded ZIP.
2. Run `python tools/test_food_catalog.py` to validate the shipped artifact, source
   mappings, units, integrity, read-only behavior, and representative indexed searches.
3. On a machine with JDK 17 and Android SDK 35, run
   `bash gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest`.
   Windows: `gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest`.
4. Run `python tools/verify_apk_catalog.py` to confirm the packaged asset name and hash.

The import is deterministic in the same Python/SQLite/zlib environment. Gzip headers
have a fixed timestamp and no filename. A manifest records hashes, counts and sizes.
Source downloads are build inputs, not required on the phone. Do not regenerate the
asset as an Android build step or require downloads when installing the app.

## Data contract

- 7,793 foods; none excluded for missing required macros. Calories (1008, kcal),
  protein (1003, g), carbohydrate (1005, g) and fat (1004, g) are validated as finite,
  nonnegative values. Duplicate core nutrient rows fail generation.
- The app also maps fiber (1079, g), total sugars (2000, g), sodium (1093, mg), and
  alcohol (1018, g). All values are on the USDA per-100-g edible-portion basis.
- All 644,125 original nutrient records are retained with FDC and nutrient IDs,
  amounts, data-point counts, derivation IDs, ranges, footnotes and acquisition years.
  Nutrient definitions/units and derivation/source dictionaries are also retained.
  Additional vitamins, minerals and other secondary nutrients are queryable in the
  source tables; the existing UI/totals display only its eight supported nutrients.
- 14,449 original portion records and their measure-unit definitions are retained.
  No serving or density is invented. The app uses 100 g as its base serving; source
  portion descriptions/gram weights appear in saved food notes. Enter that gram
  weight in the existing quantity editor for a household portion. No ml conversion
  or new portion picker is introduced in this stage.
- Unreported secondary values remain NULL in the catalog. The existing nonnullable
  `Nutrition` model contributes zero for those fields, and notes explicitly disclose
  which values are unreported. A zero total is not evidence of measured absence.
- `source_*` tables preserve USDA CSV field values; `foods` holds a validated numeric
  projection and `food_search` is an FTS4 index with case-insensitive token prefixes.
  Queries use bound parameters and sanitized terms, all terms must match, results
  are capped at 50, and food-name heads precede less direct mentions. Search is
  English token/prefix matching, not fuzzy matching or nutritional equivalence.

## Android behavior and data safety

`OfflineCatalog` lazily streams the gzip asset to a versioned file in
`noBackupFilesDir`, atomically renames it after writing, and opens it read-only.
The gzip-compressed asset uses a `.bin` suffix because Android asset merging
automatically decompresses and renames `.gz` inputs. Copying and queries run on Dispatchers.IO. It does not deserialize all foods at
startup and has no network dependency. Only up to 50 food results and their portion
notes are materialized per query. The installed database is about 39.51 MiB;
its gzip asset is about 10.84 MiB. Full nutrient provenance accounts for most of
this footprint, and is retained deliberately instead of discarding source evidence.

Add Food searches this catalog automatically as text changes. Recent/favorite/saved
foods retain their existing order above the catalog. The existing Search databases
button adds Open Food Facts and configured USDA results independently; network
latency/failure cannot block the local search. Changing the query cancels prior
online work. Results sharing an ID are not repeated across sections.

Offline FDC IDs use the same deterministic UUID scheme as online USDA. Selecting
an already-saved identity uses the personal copy, preserving its edits and favorite
state. Logging passes through the existing `Repository.log` transaction, which
saves a personal food and a separate nutrition snapshot for the entry. Editing or
deleting that personal food never edits the catalog or old entry snapshots.

Room remains version 2. No entity schema, migration, backup format, recommendation,
check-in, weight, recipe, or alcohol UI changes are needed. Source alcohol nutrient
values are preserved just like other USDA nutrients; they do not create an alcohol
logging category or infer drink volume/ABV.

## Existing online integration

USDA is already implemented in `Usda` in `FoodProviders.kt`, using the FDC search
endpoint and requiring a key at build time. A blank key returns no USDA results.
`app/build.gradle.kts` reads `USDA_API_KEY` first from untracked root
`secrets.properties`, then from the build environment. This task adds no key.

To enable online USDA on a private build:

1. Obtain a personal data.gov/FDC API key through https://fdc.nal.usda.gov/api-key-signup/.
2. Set `USDA_API_KEY` in the build environment or the ignored `secrets.properties`.
3. Rebuild/install the APK. The existing provider does not accept a key at runtime.
4. Use Search databases with internet access and verify USDA results. Handle 429
   by waiting for the rate-limit window; USDA documents a default 1,000 requests
   per hour per IP. DEMO_KEY has lower limits and is not shipped by this project.

A BuildConfig key is extractable from an APK even if absent from Git. Do not publish
an APK containing a private key. Secure public distribution would require a separate
credential strategy outside this stage. Live authenticated USDA was not verified
because no key was configured in this build.

Open Food Facts is configured without credentials, using its search and v2 barcode
endpoints and an application User-Agent. It retains its existing ODbL attribution,
normalization and timeout/error handling. This stage does not rewrite either remote
provider. Remote response completeness, throttling and liquid serving ambiguity
remain limitations of the existing providers; review remote values before logging.

## Tests

`OfflineCatalogTest` uses Robolectric Android SQLite and real Room storage to open
the packaged asset, test all requested searches and case handling, sanitize queries,
select a raw banana, convert 150 g to 1.5 base servings, log it, check 133.5 kcal and
all daily nutrient totals, save the reusable food, edit/delete the personal copy,
verify its historical snapshot, and reopen the unchanged catalog. It constructs no
network providers. A dedicated test denies outbound socket connections while running
all six representative searches. Query terms use implicit AND, which works with both
standard and enhanced FTS4 parsers. This is Android-component verification on the JVM, not a physical
phone or touchscreen test. See CURRENT_STATE.md for what actually ran in this session.

## Phone acceptance check

After installing, enable airplane mode. Open Log Food → Breakfast → Add to Breakfast.
Type `banana` without pressing Search databases, select `Bananas, raw`, enter 150 g,
and tap Add. This entry should contribute 133.5 kcal and 1.635 g protein to the day.
It should also appear in Your food library. Try each of the six representative searches.
The catalog remains available after restarting the app. Existing personal foods should
appear before catalog matches. Optional online search may report a connection error
in airplane mode; the local results should remain usable.

A fresh debug signing key may differ from a previously installed debug build. If
Android refuses an update due to signature mismatch, export your personal data before
uninstalling anything. This implementation does not require clearing app data.

## Restoring the delivered Git bundle

The bundle contains the complete repository history and this task's commits. In an
existing clone, fetch the bundle's `main` into a temporary branch, review it, and merge
or cherry-pick the implementation commit. For a separate review copy, use
`git clone /path/to/Food.Logger-offline.bundle food.logger-offline`. Its initial origin
will be the bundle path; set origin to `https://github.com/mrayover/food.logger.git`
before pushing with your normal GitHub credentials. Do not overwrite unrelated work.
