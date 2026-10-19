"""Validate the shipped artifact using SQLite with no network access."""
import gzip, hashlib, json, pathlib, sqlite3, tempfile, unittest
ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSET = ROOT/'app/src/main/assets/usda-sr-v1.db.bin'

class CatalogTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        path = pathlib.Path(cls.tmp.name)/'catalog.db'
        with gzip.open(ASSET,'rb') as f:
            path.write_bytes(f.read())
        cls.db = sqlite3.connect(f'file:{path}?mode=ro',uri=True)
        cls.manifest = json.loads(ASSET.with_suffix('.manifest.json').read_text())

    @classmethod
    def tearDownClass(cls):
        cls.db.close()
        cls.tmp.cleanup()

    def test_counts_integrity_and_hash(self):
        self.assertEqual('ok', self.db.execute('PRAGMA integrity_check').fetchone()[0])
        self.assertEqual(7793,self.db.execute('SELECT count(*) FROM foods').fetchone()[0])
        self.assertEqual(644125,self.db.execute('SELECT count(*) FROM source_food_nutrient').fetchone()[0])
        self.assertEqual(14449,self.db.execute('SELECT count(*) FROM source_food_portion').fetchone()[0])
        self.assertEqual(self.manifest['asset_sha256'],hashlib.sha256(ASSET.read_bytes()).hexdigest())
        with self.assertRaises(sqlite3.OperationalError):
            self.db.execute("UPDATE foods SET name='changed'")

    def test_all_nutrition_matches_original_source(self):
        for field, nutrient in [('calories',1008),('protein',1003),('carbs',1005),('fat',1004),('fiber',1079),('sugar',2000),('sodium',1093),('ethanol',1018)]:
            rows=self.db.execute(f'''SELECT f.fdc_id, f.{field}, s.amount FROM foods f
              LEFT JOIN source_food_nutrient s ON s.fdc_id=CAST(f.fdc_id AS TEXT) AND s.nutrient_id=?''',(str(nutrient),))
            for fdc,value,source in rows:
                self.assertEqual(value,None if source is None else float(source),(fdc,field))

    def test_representative_indexed_searches(self):
        base='SELECT f.name FROM foods f JOIN food_search s ON s.docid=f.fdc_id WHERE food_search MATCH ? ORDER BY CASE WHEN {exact} THEN 0 WHEN {priority} THEN 1 ELSE 2 END,length(f.name),f.name COLLATE NOCASE,f.fdc_id LIMIT 50'
        for query in ['chicken breast','banana','egg','rice','milk','ground beef']:
            match=' '.join('"'+token+'*"' for token in query.split())
            starts=[t+'%' for t in query.split()]
            heads=[v for t in query.split() for v in (t,t+'s')]
            exact=' OR '.join("lower(substr(f.name,1,instr(f.name || ',',',')-1)) = ?" for _ in heads)
            sql=base.format(exact=exact,priority=' OR '.join('f.name LIKE ? COLLATE NOCASE' for _ in starts))
            args=[match]+heads+starts
            found=self.db.execute(sql,args).fetchall()
            self.assertTrue(found,query)
            if query == "banana": self.assertIn(("Bananas, raw",),found)
            if query == "ground beef": self.assertTrue(any(n[0].startswith("Beef, ground") for n in found))
            self.assertLessEqual(len(found),50)
            self.assertEqual(found,self.db.execute(sql,[match.upper()]+heads+starts).fetchall())
            print(query, '=>', found[0][0])
        plan=str(self.db.execute('EXPLAIN QUERY PLAN '+sql,args).fetchall())
        self.assertIn('VIRTUAL TABLE INDEX',plan)

    def test_source_nutrient_units_and_portions(self):
        for nutrient,unit in [('1008','KCAL'),('1003','G'),('1005','G'),('1004','G'),('1093','MG')]:
            self.assertEqual(unit,self.db.execute('SELECT unit_name FROM source_nutrient WHERE id=?',(nutrient,)).fetchone()[0].upper())
        self.assertEqual(0,self.db.execute('SELECT count(*) FROM source_food_portion WHERE CAST(gram_weight AS REAL)<=0').fetchone()[0])
        self.assertEqual(0,self.db.execute('SELECT count(*) FROM source_food_nutrient n LEFT JOIN source_nutrient u ON u.id=n.nutrient_id WHERE u.id IS NULL').fetchone()[0])

if __name__=='__main__': unittest.main(verbosity=2)
