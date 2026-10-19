#!/usr/bin/env python3
"""Rebuild the bundled USDA SR Legacy SQLite catalog. Python standard library only."""
import argparse, csv, gzip, hashlib, io, json, math, pathlib, sqlite3, tempfile, urllib.request, zipfile

URL = 'https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip'
ROOT = pathlib.Path(__file__).resolve().parents[1]
MACROS = {'calories': 1008, 'protein': 1003, 'carbs': 1005, 'fat': 1004,
          'fiber': 1079, 'sugar': 2000, 'sodium': 1093, 'ethanol': 1018}

def build(archive, output):
    checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
    if checksum != 'b80817294b8850530aaedf2e515c02593b1824f763a0ff356e5c2081643e6fd0':
        raise ValueError('Source ZIP differs from the reviewed USDA release; review before updating the pin')
    with zipfile.ZipFile(archive) as z, tempfile.TemporaryDirectory() as tmp:
        def rows(table):
            name = next(n for n in z.namelist() if n.endswith('/' + table + '.csv'))
            return csv.DictReader(io.TextIOWrapper(z.open(name), encoding='utf-8-sig'))
        path = pathlib.Path(tmp) / 'catalog.db'
        db = sqlite3.connect(path)
        # Keep source rows, identifiers, units and derivation metadata verbatim.
        counts = {}
        for table in ['food', 'sr_legacy_food', 'food_category', 'food_portion', 'measure_unit',
                      'nutrient', 'food_nutrient', 'food_nutrient_derivation', 'food_nutrient_source']:
            r = rows(table)
            fields = r.fieldnames
            db.execute('CREATE TABLE source_' + table + ' (' + ','.join('"'+f+'" TEXT' for f in fields) + ')')
            db.executemany('INSERT INTO source_' + table + ' VALUES (' + ','.join('?' for _ in fields) + ')',
                           ([row[f] for f in fields] for row in r))
            counts[table] = db.execute('SELECT count(*) FROM source_' + table).fetchone()[0]
        db.execute('CREATE INDEX nutrient_food ON source_food_nutrient(fdc_id)')
        db.execute('CREATE INDEX portion_food ON source_food_portion(fdc_id)')
        db.execute('CREATE TABLE foods (fdc_id INTEGER PRIMARY KEY, name TEXT NOT NULL, ndb_number TEXT NOT NULL,' +
                   ','.join(k+' REAL'+(' NOT NULL' if i < 4 else '') for i,k in enumerate(MACROS)) + ')')
        values = {}
        for fdc, nutrient, amount in db.execute('SELECT fdc_id,nutrient_id,amount FROM source_food_nutrient'):
            if int(nutrient) in MACROS.values():
                value = float(amount)
                if not math.isfinite(value) or value < 0:
                    raise ValueError(f'Invalid nutrition: {fdc}/{nutrient}')
                per_food = values.setdefault(fdc, {})
                if int(nutrient) in per_food:
                    raise ValueError(f'Duplicate nutrient: {fdc}/{nutrient}')
                per_food[int(nutrient)] = value
        excluded = []
        for fdc, name, ndb in db.execute('SELECT f.fdc_id,f.description,s.NDB_number FROM source_food f JOIN source_sr_legacy_food s USING(fdc_id) ORDER BY CAST(f.fdc_id AS INTEGER)').fetchall():
            n = values.get(fdc, {})
            if not all(i in n for i in list(MACROS.values())[:4]):
                excluded.append(fdc)
                continue
            db.execute('INSERT INTO foods VALUES (?,?,?,?,?,?,?,?,?,?,?)', (int(fdc),name,ndb,*(n.get(i) for i in MACROS.values())))
        db.execute('CREATE INDEX food_name ON foods(name COLLATE NOCASE)')
        # FTS4 is supported by Android SQLite on the minimum SDK (26).
        db.execute('CREATE VIRTUAL TABLE food_search USING fts4(name, tokenize=unicode61, prefix="2,3,4")')
        db.execute('INSERT INTO food_search(docid,name) SELECT fdc_id,name FROM foods ORDER BY fdc_id')
        count = db.execute('SELECT count(*) FROM foods').fetchone()[0]
        assert 5000 <= count <= 10000, count
        manifest = dict(source='USDA FoodData Central SR Legacy April 2018', source_url=URL,
                        source_sha256=checksum, catalog_version=1, food_count=count,
                        source_counts=counts, excluded_missing_macros=excluded,
                        nutrient_basis='per 100 g edible portion', license='CC0 1.0')
        db.execute('CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)')
        db.executemany('INSERT INTO metadata VALUES (?,?)', [(k,json.dumps(v,sort_keys=True)) for k,v in manifest.items()])
        db.execute('PRAGMA user_version=1')
        db.commit()
        assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
        db.execute('VACUUM')
        db.close()
        output.parent.mkdir(parents=True,exist_ok=True)
        with output.open('wb') as f, gzip.GzipFile(filename='', mode='wb', fileobj=f, mtime=0, compresslevel=9) as gz:
            with path.open('rb') as source:
                import shutil
                shutil.copyfileobj(source, gz)
        manifest.update(sqlite_bytes=path.stat().st_size, gzip_bytes=output.stat().st_size,
                        asset_sha256=hashlib.sha256(output.read_bytes()).hexdigest())
        output.with_suffix('.manifest.json').write_text(json.dumps(manifest,indent=2,sort_keys=True)+'\n')
        print(json.dumps(manifest,indent=2))

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=pathlib.Path, help='Existing official USDA ZIP; otherwise download')
    p.add_argument('--output', type=pathlib.Path, default=ROOT/'app/src/main/assets/usda-sr-v1.db.bin')
    args = p.parse_args()
    if args.source:
        build(args.source,args.output)
    else:
        with tempfile.TemporaryDirectory() as tmp:
            source = pathlib.Path(tmp)/'sr.zip'
            urllib.request.urlretrieve(URL,source)
            build(source,args.output)
