from pathlib import Path
import argparse
import subprocess
import zipfile
import os
import hashlib
import tempfile
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--restore-three-tiles', action='store_true')
args = parser.parse_args()
project = Path(__file__).resolve().parent
task = project.parent
original = task / 'vendor/tvhome-base.apk'
build_root = project / 'build'
build_root.mkdir(parents=True, exist_ok=True)
build = Path(tempfile.mkdtemp(prefix='restore-' if args.restore_three_tiles else 'carousel-', dir=build_root))
sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or str(Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk'))
tools = sdk / 'build-tools/36.0.0'
framework = sdk / 'platforms/android-35/android.jar'
java_home = os.environ.get('JAVA_HOME') or os.environ['JAVA_HOME']
java = Path(java_home) / 'bin'
env = dict(os.environ, JAVA_HOME=java_home)
output = task / 'apk' / ('TvHomeThreeTilesRestore.apk' if args.restore_three_tiles else 'TvHomeSpace.apk')

def run(command):
    subprocess.run([str(part) for part in command], check=True, env=env)

manifest = project / 'AndroidManifest.xml'
if args.restore_three_tiles:
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    tree = ET.parse(project / 'AndroidManifest.three-tiles.xml')
    tree.getroot().set('{http://schemas.android.com/apk/res/android}versionCode', '5')
    tree.getroot().set('{http://schemas.android.com/apk/res/android}versionName', '1.0-restore')
    manifest = build / 'AndroidManifest.xml'
    tree.write(manifest, encoding='utf-8', xml_declaration=True)

run([tools / 'aapt2.exe', 'compile', '--dir', project / 'res', '-o', build / 'res.zip'])
run([tools / 'aapt2.exe', 'link', '-I', framework, '--manifest', manifest, '--stable-ids', project / 'stable-ids.txt', '-o', build / 'base.apk', build / 'res.zip'])
with zipfile.ZipFile(original) as source:
    (build / 'legacy.dex').write_bytes(source.read('classes.dex'))

dex = build / 'legacy.dex'
if not args.restore_three_tiles:
    stubs = build / 'stubs'
    classes = build / 'classes'
    dex_dir = build / 'dex'
    for directory in (stubs, classes, dex_dir):
        directory.mkdir()
    run([java / 'javac.exe', '--release', '8', '-encoding', 'UTF-8', '-classpath', framework, '-d', stubs, *project.glob('stubs/**/*.java')])
    run([java / 'javac.exe', '--release', '8', '-encoding', 'UTF-8', '-classpath', os.pathsep.join([str(framework), str(stubs)]), '-d', classes, *project.glob('src/**/*.java')])
    additions = list(classes.glob('**/*.class'))
    assert all(path.name != 'MainActivity.class' for path in additions), 'Compile-time stub must not enter APK'
    run([tools / 'd8.bat', '--min-api', '28', '--lib', framework, '--output', dex_dir, dex, *additions])
    dex = dex_dir / 'classes.dex'

with zipfile.ZipFile(original) as source, zipfile.ZipFile(build / 'base.apk', 'a', compression=zipfile.ZIP_DEFLATED) as target:
    for name in source.namelist():
        if name.startswith('assets/') and (args.restore_three_tiles or not name.startswith('assets/wallpapers/')):
            target.writestr(name, source.read(name))
    if not args.restore_three_tiles:
        for asset in project.glob('assets/**/*'):
            if asset.is_file(): target.write(asset, asset.relative_to(project).as_posix())
    target.write(dex, 'classes.dex')

dump = subprocess.run([str(tools / 'aapt.exe'), 'dump', 'resources', str(build / 'base.apk')], capture_output=True, text=True, check=True).stdout
for line in (project / 'stable-ids.txt').read_text().splitlines():
    name, resource_id = line.split(' = ')
    assert f'{resource_id} {name}:' in dump, f'Resource ID changed: {name}'
with zipfile.ZipFile(original) as before, zipfile.ZipFile(build / 'base.apk') as after:
    for name in before.namelist():
        if name.startswith('assets/') and (args.restore_three_tiles or not name.startswith('assets/wallpapers/')):
            assert before.read(name) == after.read(name), f'Unexpected asset change: {name}'
    if args.restore_three_tiles:
        assert before.read('classes.dex') == after.read('classes.dex')
print('Resource IDs verified; original wallpapers in rollback' if args.restore_three_tiles else 'Resource IDs verified; bundled space wallpapers included', flush=True)
run([tools / 'zipalign.exe', '-f', '4', build / 'base.apk', build / 'aligned.apk'])
run([tools / 'apksigner.bat', 'sign', '--ks', Path(os.environ.get('TVHOME_KEYSTORE') or str(task / 'keys/local.jks')), '--ks-pass', 'pass:android', '--ks-key-alias', 'guard', '--v4-signing-enabled', 'false', '--out', output, build / 'aligned.apk'])
run([tools / 'apksigner.bat', 'verify', '--verbose', '--min-sdk-version', '28', output])
run([tools / 'zipalign.exe', '-c', '4', output])
print('APK:', output, 'bytes:', output.stat().st_size, 'SHA256:', hashlib.sha256(output.read_bytes()).hexdigest(), flush=True)
