from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BASH = os.environ.get("BASH_BIN", "bash")
SELECT = (ROOT / "module/installer/selection.sh").as_posix()

def shell(script, *args):
    return subprocess.run([BASH, "-c", script, "test", *map(str, args)], capture_output=True, text=True)

class Modes(unittest.TestCase):
    def test_wallet_split_migration_guards_and_private_backup(self):
        pinned = 'ce369489bd4cf5262c0e35403566be2274a06bbeadb265fc8afe2897750db800'
        for case in ('backup', 'locked', 'enabled', 'secondary-user', 'independent', 'unknown-apk'):
            with self.subTest(case=case), tempfile.TemporaryDirectory(dir=ROOT) as temp:
                folder = Path(temp)
                data = folder / 'data'
                old = data / 'adb/modules/HyperOS3EU307Optional/system/product/app/MipayWallet/MipayWallet.apk'
                old.parent.mkdir(parents=True)
                old.write_bytes(b'test-APK-not-published')
                for tree in ('user', 'user_de'):
                    source = data / tree / '0/com.mipay.wallet'
                    source.mkdir(parents=True)
                    (source / 'settings.txt').write_text('preserve-test-settings')
                if case == 'secondary-user':
                    (data / 'user/10/com.mipay.wallet').mkdir(parents=True)
                script = folder / 'compat.sh'
                script.write_text((ROOT / 'module/installer/compat.sh').read_text().replace('/data/', data.as_posix() + '/').replace('cd /data ', 'cd "' + data.as_posix() + '" '), newline='\n')
                command = '''abort() { echo "$*" >&2; exit 97; }; ui_print() { :; }
. "$1"; CASE="$2"; PINNED="$3"; DATA_ROOT="$4"; WALLET_MODE=cards
am() { [ "$CASE" = locked ] && echo RUNNING_LOCKED || echo RUNNING_UNLOCKED; }
pm() { [ "$CASE" = independent ] && echo "package:$DATA_ROOT/app/test/base.apk" || echo package:/product/app/MipayWallet/MipayWallet.apk; }
dumpsys() { [ "$CASE" = enabled ] && echo 'User 0: enabled=0 test' || echo 'User 0: enabled=3 test'; }
sha256sum() { [ "$CASE" = unknown-apk ] && echo 'wrong-hash file' || echo "$PINNED file"; }
# GNU tar on Windows treats G: as an scp host without --force-local.
tar() { command tar --force-local "$@"; }
prepare_wallet_split
'''
                result = shell(command, script.as_posix(), case, pinned, data.as_posix())
                self.assertEqual(result.returncode == 0, case in ('backup', 'independent'), result.stderr)
                archives = list((data / 'adb/eulocation-backups').glob('*/wallet-main-data.tar.gz'))
                if case == 'backup':
                    self.assertEqual(len(archives), 1)
                    import tarfile
                    with tarfile.open(archives[0]) as archive:
                        self.assertEqual(archive.extractfile('user/0/com.mipay.wallet/settings.txt').read(), b'preserve-test-settings')
                    self.assertTrue((archives[0].parent / 'MipayWallet.apk').exists())
                else:
                    self.assertFalse(archives)
                self.assertTrue((data / 'user/0/com.mipay.wallet/settings.txt').exists())

    def test_service_never_manages_wallet_even_with_legacy_config(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            folder = Path(temp)
            (folder / "reports").mkdir()
            (folder / "installer").mkdir()
            shutil.copyfile(ROOT / "module/service.sh", folder / "service.sh")
            shutil.copyfile(ROOT / "module/installer/compat.sh", folder / "installer/compat.sh")
            (folder / "component-selection.conf").write_text("WALLET_MODE=cards\nWALLET_UI_MANAGED=1\nWALLET_ORIGINAL_ENABLED=0\n")
            command = '''getprop() {
  case "$1" in ro.product.mod_device) echo dada_xiaomieu_global;; ro.build.version.sdk) echo 36;; ro.product.cpu.abi) echo arm64-v8a;; ro.build.version.incremental) echo OS3.0.307.0.WOCCNXM;; sys.boot_completed) echo 1;; esac
}; pm() { echo unexpected-package-manager-call >&2; return 91; }; export -f getprop pm; bash "$1"
'''
            result = shell(command, (folder / "service.sh").as_posix())
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertNotIn("unexpected-package-manager-call", result.stderr)

    def test_selection_matrix(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            config = Path(temp) / "selection.conf"
            for xiaoai in (0, 1):
                for mode in ("none", "full", "cards"):
                    with self.subTest(xiaoai=xiaoai, mode=mode):
                        config.write_text(f"BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI={xiaoai}\nWALLET_MODE={mode}\n")
                        result = shell('. "$1"; parse_options "$2"', SELECT, config.as_posix())
                        self.assertEqual(result.returncode == 0, mode != "full" and (xiaoai, mode) != (0, "none"), result.stderr)

    def test_bad_options(self):
        cases = ["BUNDLE_ID=WRONG\nXIAOAI=1\nWALLET_MODE=cards\n",
                 "BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI=1\nWALLET_MODE=cards;echo BAD\n",
                 "BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI=1\nWALLET=1\n",
                 "BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI=1\nWALLET_MODE=cards\nEXTRA=1\n"]
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            config = Path(temp) / "selection.conf"
            for value in cases:
                with self.subTest(value=value):
                    config.write_text(value)
                    self.assertNotEqual(shell('. "$1"; parse_options "$2"', SELECT, config.as_posix()).returncode, 0)

    def test_managed_component_protection(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            config = Path(temp) / "old.conf"
            config.write_text("XIAOAI=1\nWALLET=1\n")
            for xiaoai, mode, expected in [(1, "cards", True), (1, "full", True), (1, "none", False), (0, "cards", False)]:
                with self.subTest(xiaoai=xiaoai, mode=mode):
                    result = shell('abort() { exit 97; }; . "$1"; XIAOAI="$3"; WALLET_MODE="$4"; preserve_managed_components "$2"',
                                   SELECT, config.as_posix(), xiaoai, mode)
                    self.assertEqual(result.returncode == 0, expected, result.stderr)

    def test_key_and_timeout(self):
        for key, expected in [("KEY_VOLUMEUP", "1"), ("KEY_VOLUMEDOWN", "0"), ("", "abort")]:
            script = '''ui_print() { :; }; abort() { exit 97; }; . "$1"
timeout() { shift; "$@"; }; getevent() { [ -z "$KEY" ] || printf 'EV_KEY %s DOWN\\n' "$KEY"; }
KEY="$2"; select_component test; printf '%s' "$CHOICE"
'''
            result = shell(script, SELECT, key)
            if expected == "abort": self.assertEqual(result.returncode, 97)
            else: self.assertEqual(result.stdout, expected)

    def test_payload_identity_and_fresh_cards_filter(self):
        if not os.environ.get("RELEASE_ZIP") or not os.environ.get("PAYLOAD_SOURCE_ZIP"):
            self.skipTest("Set RELEASE_ZIP and PAYLOAD_SOURCE_ZIP for payload comparison")
        with zipfile.ZipFile(os.environ["RELEASE_ZIP"]) as new, \
             zipfile.ZipFile(os.environ["PAYLOAD_SOURCE_ZIP"]) as old:
            for name in old.namelist():
                if name.startswith("payload/") and "MipayWallet/" not in name and name != "payload/wallet/payload.sha256": self.assertEqual(new.read(name), old.read(name), name)
            for component, count in (("xiaoai", 109), ("wallet", 14)):
                rows = new.read(f"payload/{component}/payload.sha256").decode().splitlines()
                self.assertEqual(len(rows), count)
                for row in rows:
                    digest, name = row.split(None, 1)
                    self.assertEqual(hashlib.sha256(new.read(f"payload/{component}/{name}")).hexdigest(), digest)
            names = [row.split(None, 1)[1] for row in new.read("payload/wallet/payload.sha256").decode().splitlines()]
            cards = [name for name in names if not name.startswith("system/product/app/MipayWallet/")]
            self.assertEqual(sum(name.endswith(".apk") for name in cards), 4)
            self.assertIn("system.prop", cards)

    def test_model_independent_profile(self):
        compat = (ROOT / "module/installer/compat.sh").as_posix()
        profiles = []
        for device in ("dada", "babylon", "other_device"):
            for build in ("OS3.0.307.0.WOCCNXM", "OS3.0.1.0.OTHER"):
                for suffix in ("_xiaomieu_global", "_xiaomieu"):
                    profiles.append((build, device + suffix, "36", "arm64-v8a", True))
        profiles += [("OS2.0", "dada_xiaomieu", "36", "arm64-v8a", False),
                     ("OS3.0", "dada_global", "36", "arm64-v8a", False),
                     ("OS3.0", "dada_xiaomieu", "35", "arm64-v8a", False),
                     ("OS3.0", "dada_xiaomieu", "36", "x86_64", False)]
        for build, mod_device, sdk, abi, expected in profiles:
            command = '''. "$1"; BUILD="$2"; DEVICE="$3"; SDK="$4"; ABI="$5"
getprop() { case "$1" in ro.build.version.incremental) echo "$BUILD";; ro.product.mod_device) echo "$DEVICE";; ro.build.version.sdk) echo "$SDK";; ro.product.cpu.abi) echo "$ABI";; esac; }
runtime_profile_matches'''
            result = shell(command, compat, build, mod_device, sdk, abi)
            self.assertEqual(result.returncode == 0, expected, (build, mod_device, result.stderr))

    def test_safety_gates_retained(self):
        compat = (ROOT / "module/installer/compat.sh").read_text()
        for marker in ("android.hardware.nfc.ese", "android.hardware.nfc.hce", "check_existing",
                       "vendor.xiaomi.hardware.misys.V3_0", "com.xiaomi.mibrain.speech", "sha256sum -c"):
            self.assertIn(marker, compat)
        java = (ROOT.parent / 'EuPower/app/src/main/java/com/ux5/hyperos3/perappcn/SettingsCardHolder.java').read_text()
        self.assertIn("HASH.equals(hex.toString())", java)
        self.assertIn("& 1024", java)
        self.assertNotIn("ro.product.device", compat + java)

if __name__ == "__main__": unittest.main(verbosity=2)
