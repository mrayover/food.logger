from pathlib import Path
import argparse, hashlib, json, os, re, subprocess, zipfile
p = argparse.ArgumentParser(description='Verify a permanent-key Food.Logger release.')
p.add_argument('--apk', default='app/build/outputs/apk/release/app-release.apk')
p.add_argument('--out', default='deliverables/release/BUILD_INFO.json')
p.add_argument('--usda-mode', choices=['configured', 'disabled'], required=True)
a = p.parse_args()
root = Path(__file__).resolve().parent.parent
apk = Path(a.apk).resolve()
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT', ''))
tools = sdk / 'build-tools' / '34.0.0'
def run(tool, *args):
    path = tools / (tool + ('.bat' if tool == 'apksigner' else '.exe') if os.name == 'nt' else tool)
    command = ['cmd', '/c', str(path)] if os.name == 'nt' and path.suffix == '.bat' else [str(path)]
    return subprocess.check_output(command + list(args), text=True, stderr=subprocess.STDOUT)
def require(ok, message):
    if not ok: raise SystemExit(message)
signature = run('apksigner', 'verify', '--verbose', '--print-certs', '--min-sdk-version', '26', '--max-sdk-version', '35', str(apk))
certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: (\w+)', signature)
pinned = (root / 'config/release-certificate.sha256').read_text().strip().lower()
require(certs == [pinned], 'APK signing certificate differs from the permanent release pin.')
require('Verified using v2 scheme (APK Signature Scheme v2): true' in signature, 'APK v2 signature verification failed.')
badging = run('aapt', 'dump', 'badging', str(apk))
package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
require(package is not None, 'APK metadata missing.')
require(package[1] == 'com.foodlogger', 'Unexpected release application ID.')
require('application-debuggable' not in badging, 'Release must not be debuggable.')
gradle = (root / 'app/build.gradle.kts').read_text()
require(int(package[2]) == int(re.search(r'versionCode = (\d+)', gradle)[1]), 'APK versionCode differs from source.')
require(package[3] == re.search(r'versionName = "([^"]+)"', gradle)[1], 'APK versionName differs from source.')
require("sdkVersion:'26'" in badging and "targetSdkVersion:'35'" in badging, 'Unexpected Android compatibility.')
config_path = root / 'app/build/generated/source/buildConfig/release/com/foodlogger/BuildConfig.java'
config = config_path.read_text()
configured = 'USDA_API_KEY = "";' not in config
require(bool(re.search(r'USDA_API_KEY\s*=', config)), 'USDA build configuration missing.')
require(configured == (a.usda_mode == 'configured'), 'USDA credential mode differs from the intended delivery; do not upload configured APKs publicly.')
with zipfile.ZipFile(apk) as z:
    manifest = json.loads(z.read('assets/usda-sr-v1.db.manifest.json'))
    catalog = z.read('assets/usda-sr-v1.db.bin')
    require(hashlib.sha256(catalog).hexdigest() == manifest['asset_sha256'], 'Catalog checksum mismatch.')
    require(manifest['food_count'] == 7793, 'Offline catalog changed.')
source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
info = dict(package=package[1], versionCode=int(package[2]), versionName=package[3],
    apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), certificate_sha256=pinned,
    apk_bytes=apk.stat().st_size, minSdk=26, targetSdk=35, debuggable=False, usda_online=configured,
    native_abis=re.findall(r"'([^']+)'", next((x for x in badging.splitlines() if x.startswith('native-code:')), '')),
    catalog_food_count=manifest['food_count'], source_commit=source,
    compatibility='Signature/manifest checked for API 26-35. Device smoke results are separate.')
out = Path(a.out); out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(info, indent=2) + '\n')
print(json.dumps(info, indent=2))
