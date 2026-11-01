"""Host checks for EU boost dispatch/scope boundaries, not device install tests."""
from pathlib import Path
import os
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/ux5/hyperos3/perappcn'
RECOMMENDED = ['com.android.soundrecorder', 'com.android.settings',
               'com.miui.personalassistant', 'com.miui.packageinstaller',
               'com.miui.home', 'com.android.quicksearchbox']

class BoostSafety(unittest.TestCase):
    def test_recommended_scope_matches_features(self):
        scope = ROOT / 'app/src/main/resources/META-INF/xposed/scope.list'
        self.assertEqual(scope.read_text().split(), RECOMMENDED)

    def test_narrow_packages_excluded_from_generic_cn(self):
        source = (Path('G:/Data/ApkFuture/EuPower/app/src/main/java/com/ux5/hyperos3/perappcn/Main.java')).read_text(encoding='utf-8')
        gate = source.index('isProcess(process, "com.miui.personalassistant")')
        generic = source.index('int gate = evaluateApproval(process)')
        self.assertLess(gate, generic)
        self.assertIn('isProcess(process, "com.miui.packageinstaller")', source[gate:generic])
        self.assertIn('return;', source[gate:generic])
        self.assertIn('personalAssistant.install', source)
        self.assertIn('systemAppUpdate.install', source)

    def test_installer_is_virtual_parser_local_only(self):
        source = (Path('G:/Data/ApkFuture/EuPower/app/src/main/java/com/ux5/hyperos3/perappcn/SystemAppUpdateCompat.java')).read_text(encoding='utf-8')
        self.assertIn('ThreadLocal<Integer>', source)
        self.assertIn('KEY.equals(chain.getArg(0)) && depth.get() > 0', source)
        self.assertIn('finally', source)
        self.assertIn('depth.remove()', source)
        self.assertIn('parser.getDeclaredMethod("b", String.class, apkInfo)', source)
        for forbidden in ('setprop', 'resetprop', 'Runtime.getRuntime', 'ProcessBuilder', 'checkSignatures', 'system_server'):
            self.assertNotIn(forbidden, source)

    def test_travel_does_not_change_consent_or_network(self):
        source = (Path('G:/Data/ApkFuture/EuPower/app/src/main/java/com/ux5/hyperos3/perappcn/PersonalAssistantCompat.java')).read_text(encoding='utf-8')
        self.assertIn('finally', source)
        self.assertIn('HookSupport.writeBoolean(international, true)', source)
        self.assertIn('"pref_key_travel"', source)
        for forbidden in ('setChecked', 'SharedPreferences', 'SystemProperties', 'okhttp', 'provider.xiaomi.eu', 'http://', 'https://'):
            self.assertNotIn(forbidden, source)

    def test_no_codename_lock(self):
        for name in ('HookSupport.java', 'PersonalAssistantCompat.java', 'SystemAppUpdateCompat.java'):
            self.assertNotIn('ro.product.device', (JAVA / name).read_text(encoding='utf-8'))

    def test_xmsf_owned_only_by_island(self):
        source = (Path('G:/Data/ApkFuture/EuPower/app/src/main/java/com/ux5/hyperos3/perappcn/Main.java')).read_text(encoding='utf-8')
        exclusion = source.index('if (isXmsfProcess(process))')
        generic = source.index('int gate = evaluateApproval(process)')
        self.assertLess(exclusion, generic)
        self.assertIn('return;', source[exclusion:generic])
        self.assertNotIn('installXmsfGlobalHooks', source)
        self.assertNotIn('"x4.a"', source)
        self.assertNotIn('chain -> "SG"', source)
        self.assertNotIn('chain -> "Global"', source)

    def test_optional_built_apk_metadata(self):
        artifact = os.environ.get('EUBOOST_APK')
        if not artifact:
            self.skipTest('Set EUBOOST_APK to verify a built test APK')
        with zipfile.ZipFile(artifact) as archive:
            self.assertIsNone(archive.testzip())
            self.assertEqual(archive.read('META-INF/xposed/scope.list').decode().split(), RECOMMENDED)
            self.assertEqual(archive.read('META-INF/xposed/java_init.list').decode().strip(), 'com.ux5.hyperos3.perappcn.Main')

if __name__ == '__main__':
    unittest.main(verbosity=2)
