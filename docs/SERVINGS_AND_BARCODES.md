# Serving sizes and barcode acceptance checks

New Open Food Facts lookups use an explicit, consistent manufacturer mass or volume
serving. For example, `2 tbsp (32 g)` becomes one serving with nutrition scaled from
the provider's per-100 reference by 0.32. Numeric serving quantities without a reliable
unit/description are insufficient. Fractional metric quantities, decimal commas and
common fraction characters are supported. Conflicting measures, multipack expressions,
nonpositive/nonfinite values and servings above 10,000 g/ml use the reference fallback.
The original label, quantity, unit and source remain in notes. Missing or invalid
required nutrition rejects a product; unavailable secondary OFF nutrients use the
existing zero representation with a disclosure in notes.

An explicit volume serving has no invented gram weight. It offers servings only.
Online USDA branded foods may use an explicit gram serving; volume-only USDA servings
retain 100 g because the search nutrition reference is mass-based. SR Legacy and the
bundled offline database remain at their correct 100 g bases. This does not add a
household-portion picker or density conversions. Unsupported serving formats retain
the reference; users may review/edit a personal food explicitly.

Barcode lookup checks personal foods before any network request. Matching ignores
GTIN leading-zero padding and accepts numeric UPC/EAN lengths 8, 12, 13 and 14. It does
not expand UPC-E, infer missing digits or repair check digits. Returned products must
match the scanned barcode before automatic selection. Cross-provider results are
deduplicated without blending nutrition. Existing personal records are preferred;
existing intentional library duplicates are not deleted or merged.

No Room migration is needed: version 2 and MIGRATION_1_2 are unchanged. Saved foods are
not renormalized on upgrade or repeat scans. This includes older personal products
with a 100 g base. Logs continue to snapshot nutrition, description and gram weight;
editing/deleting personal foods cannot rewrite historical snapshots. The last quantity
is stored in the existing Food.lastQuantity field. The selected unit is an optional
per-food SharedPreferences preference and survives process restart; it is not part of
JSON export/import. Newly normalized foods start with one serving. Legacy foods retain
their configured mass-unit preference. Invalid quantity input disables logging and
does not reset to one when a unit is tapped.

## Phone acceptance checklist

1. Update the debug app using the existing install's signing key. This build uses the
   build environment's debug key. Do not uninstall or clear data to bypass an Android
   signature mismatch; preserve/export existing data and use a matching signing key.
2. In Add Food, scan (or Enter barcode for) a product whose OFF record has a valid
   manufacturer serving. Confirm the quantity dialog shows the label and **1 serving**.
3. For a 32 g serving, enter **1.5 servings**. Confirm **48 g**, and verify calories,
   protein, carbs, fat, fiber, sugar and sodium against 1.5 times the label values.
   Switch servings → g → oz → servings and check the consumed amount remains equal.
4. Tap Add. Confirm the chosen meal and daily totals increase by the displayed values.
   Scan it again, including with the network disabled after saving; confirm the personal
   product and last quantity/unit return, with only one new library record overall.
   Restart the app and repeat the lookup.
5. Repeat with a volume serving such as 240 ml: no g/oz choices should appear. For a
   missing/unreliable serving, check the 100 g/ml reference is retained. A missing or
   unusable barcode response should offer label scanning/manual entry with a message.
6. Log an older saved 100 g product, then scan it again: verify its saved nutrition and
   serving do not change. Edit that library item and confirm the earlier meal's totals
   and serving remain unchanged. Edit only that historical entry's quantity and check
   it still uses the original snapshot.
7. Search the offline catalog for banana. Select 150 g and verify 133.5 kcal and
   1.635 g protein. Confirm search, logging and reopening personal foods work offline.

## Automated verification

Run `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest` with JDK 17,
Android SDK 35 and Build Tools 34.0.0. `ServingTest` checks provider normalization,
invalid/fractional/volume servings, mass conversions, unit switching, initial units,
personal-food identity and repeated lookup deduplication. `ServingPersistenceTest`
uses actual Room/SQLite under Robolectric for selection → quantity → log → totals,
repeat reuse, older saved definitions, historical snapshots, backup validation and
database reopen. Existing EngineTest and OfflineCatalogTest remain required.

Run `python -m unittest discover -s tools -p 'test_*.py'` for catalog validation and
`python tools/verify_apk_catalog.py` to verify the packaged asset. These tests do not
constitute camera or phone UI testing. Instrumentation APK compilation does not mean
instrumentation tests were executed. See CURRENT_STATE.md for this run's actual results.
