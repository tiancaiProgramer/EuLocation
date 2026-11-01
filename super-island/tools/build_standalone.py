"""Reproducible SDK-only build; uses the existing v0.9 signing identity."""
import hashlib
import os
import re
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
tool = Path(r'G:\Data\ApkFuture\Loading\shared-tools\android-build')
jdk = tool / 'jdk/jdk-17.0.20.1+1'
sdk = tool / 'sdk'
bt = sdk / 'build-tools/36.0.0'
android = sdk / 'platforms/android-35/android.jar'
api = tool.parent / 'tmp-libxposed/classes.jar'
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
version_code = re.search(r'versionCode\s+(\d+)', gradle).group(1)
version_name = re.search(r'versionName\s+"([^"]+)"', gradle).group(1)
stage = root / f'app/build/standalone-v{version_code}'
stage.mkdir(parents=True, exist_ok=True)
env = dict(os.environ, JAVA_HOME=str(jdk))

def run(*args):
    subprocess.run([str(a) for a in args], env=env, check=True)

ns = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ns)
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml')
manifest.getroot().set('package', 'com.ux5.hyperos3.elemeisland')
manifest.write(stage / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
run(bt / 'aapt2.exe', 'compile', '--dir', root / 'app/src/main/res', '-o', stage / 'resources.zip')
run(bt / 'aapt2.exe', 'link', '-I', android, '--manifest', stage / 'AndroidManifest.xml',
    '--min-sdk-version', '26', '--target-sdk-version', '35', '--version-code', version_code,
    '--version-name', version_name, '-o', stage / 'resources.apk', stage / 'resources.zip')
classes = stage / 'classes'
classes.mkdir(exist_ok=True)
sources = sorted((root / 'app/src/main/java').rglob('*.java'))
run(jdk / 'bin/javac.exe', '-encoding', 'UTF-8', '-source', '17', '-target', '17',
    '-classpath', str(android) + os.pathsep + str(api), '-d', classes, *sources)
dex = stage / 'dex'
dex.mkdir(exist_ok=True)
run(bt / 'd8.bat', '--release', '--min-api', '26', '--lib', android, '--classpath', api,
    '--output', dex, *sorted(classes.rglob('*.class')))
unsigned = stage / 'unsigned.apk'
with zipfile.ZipFile(stage / 'resources.apk') as source, zipfile.ZipFile(unsigned, 'w') as z:
    for entry in source.infolist():
        z.writestr(entry, source.read(entry.filename))
    for path in sorted(dex.glob('*.dex')):
        z.write(path, path.name, compress_type=zipfile.ZIP_DEFLATED)
    resources = root / 'app/src/main/resources'
    for path in sorted(resources.rglob('*')):
        if path.is_file():
            z.write(path, path.relative_to(resources).as_posix(), compress_type=zipfile.ZIP_DEFLATED)
run(bt / 'zipalign.exe', '-f', '-p', '4', unsigned, stage / 'aligned.apk')
dest = root / f'dist/Island-v{version_name}.apk'
dest.parent.mkdir(parents=True, exist_ok=True)
run(bt / 'apksigner.bat', 'sign', '--ks', tool / 'android-user/debug.keystore',
    '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
    '--out', dest, stage / 'aligned.apk')
run(bt / 'apksigner.bat', 'verify', '--verbose', '--print-certs', dest)
run(bt / 'zipalign.exe', '-c', '4', dest)
print('APK:', dest)
print('SHA256:', hashlib.sha256(dest.read_bytes()).hexdigest())
