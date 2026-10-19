"""Verify Android did not rename/transform the gzip catalog during asset packaging."""
import hashlib
import json
import pathlib
import sys
import zipfile

apk = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'app/build/outputs/apk/debug/app-debug.apk')
with zipfile.ZipFile(apk) as z:
    manifest = json.loads(z.read('assets/usda-sr-v1.db.manifest.json'))
    payload = z.read('assets/usda-sr-v1.db.bin')
    assert payload[:2] == b'\x1f\x8b', 'Asset must remain gzip-compressed for OfflineCatalog'
    assert hashlib.sha256(payload).hexdigest() == manifest['asset_sha256']
    assert len(payload) == manifest['gzip_bytes']
print(json.dumps(dict(apk_bytes=apk.stat().st_size, apk_mib=round(apk.stat().st_size/1048576,2),
                      apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                      catalog_asset_bytes=len(payload), catalog_food_count=manifest['food_count']),indent=2))
