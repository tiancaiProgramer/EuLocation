"""Build first, then run the actual module callbacks against a rejecting API proxy."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT.parent / 'Loading/shared-tools'
JDK = TOOLS / 'android-build/jdk/jdk-17.0.20.1+1/bin'
API = TOOLS / 'tmp-libxposed/classes.jar'
ANDROID = TOOLS / 'android-build/sdk/platforms/android-35/android.jar'
JSON = TOOLS / 'java-libs/json-20180813.jar'
code = re.search(r'versionCode\s+(\d+)', (ROOT / 'app/build.gradle').read_text()).group(1)
classes = ROOT / f'app/build/standalone-v{code}/classes'
out = ROOT / 'app/build/xmsf-passive-tests'
out.mkdir(parents=True, exist_ok=True)
cp = ';'.join(map(str, (classes, API, ANDROID, JSON)))
subprocess.run([str(JDK / 'javac.exe'), '-encoding', 'UTF-8', '-cp', cp,
                '-d', str(out), str(ROOT / 'tests/XmsfScopeTest.java')], check=True)
subprocess.run([str(JDK / 'java.exe'), '-cp', str(out) + ';' + cp,
                'com.ux5.hyperos3.elemeisland.XmsfScopeTest'], check=True)
