"""Compile the current EU boost source offline; never install or sign an APK."""
from pathlib import Path
import re,subprocess,xml.etree.ElementTree as ET,zipfile

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'app/src/main'
TOOLS=ROOT.parent/'Loading/shared-tools/android-build'
JDK=TOOLS/'jdk/jdk-17.0.20.1+1/bin'
BT=TOOLS/'sdk/build-tools/36.0.0'
ANDROID=TOOLS/'sdk/platforms/android-35/android.jar'
API=ROOT/'deps/api-102.0.0.jar'
gradle=(ROOT/'app/build.gradle').read_text(encoding='utf-8')
code=re.search(r'versionCode\s+(\d+)',gradle).group(1)
version=re.search(r'versionName\s+"([^"]+)"',gradle).group(1)
OUT=ROOT/'build/offline'
for folder in (OUT,OUT/'classes',OUT/'dex',ROOT/'dist'):folder.mkdir(parents=True,exist_ok=True)
def run(*args):subprocess.run([str(x) for x in args],check=True)
ns='http://schemas.android.com/apk/res/android'
ET.register_namespace('android',ns)
manifest=ET.parse(SOURCE/'AndroidManifest.xml')
manifest.getroot().set('package','com.ux5.hyperos3.perappcn')
manifest.write(OUT/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
run(BT/'aapt2.exe','compile','--dir',SOURCE/'res','-o',OUT/'res.zip')
run(BT/'aapt2.exe','link','-o',OUT/'resources.apk','--manifest',OUT/'AndroidManifest.xml',
    '-I',ANDROID,'--version-code',code,'--version-name',version,'--min-sdk-version','26','--target-sdk-version','35',OUT/'res.zip')
run(JDK/'javac.exe','--release','17','-encoding','UTF-8','-cp',f'{ANDROID};{API}',
    '-d',OUT/'classes',*sorted((SOURCE/'java').rglob('*.java')))
run(JDK/'java.exe','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--lib',ANDROID,
    '--classpath',API,'--min-api','26','--output',OUT/'dex',*sorted((OUT/'classes').rglob('*.class')))
with zipfile.ZipFile(OUT/'resources.apk') as resources,zipfile.ZipFile(OUT/'unsigned.apk','w',zipfile.ZIP_DEFLATED) as apk:
    for entry in resources.infolist():apk.writestr(entry,resources.read(entry.filename))
    for path in (OUT/'dex').glob('*.dex'):apk.write(path,path.name)
    for path in (SOURCE/'resources').rglob('*'):
        if path.is_file():apk.write(path,path.relative_to(SOURCE/'resources').as_posix())
target=ROOT/'dist'/f'EUBoost-v{version}-aligned-unsigned.apk'
run(BT/'zipalign.exe','-f','4',OUT/'unsigned.apk',target)
run(BT/'zipalign.exe','-c','4',target)
print('Built unsigned APK:',target)
