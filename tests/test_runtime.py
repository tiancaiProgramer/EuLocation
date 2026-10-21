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
    def test_service_retries_transient_package_manager_failure(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            folder = Path(temp)
            (folder / "reports").mkdir()
            script = folder / "service.sh"
            shutil.copyfile(ROOT / "module/service.sh", script)
            (folder / "installer").mkdir()
            shutil.copyfile(ROOT / "module/installer/compat.sh", folder / "installer/compat.sh")
            (folder / "component-selection.conf").write_text("WALLET_MODE=cards\nWALLET_UI_MANAGED=1\nWALLET_ORIGINAL_ENABLED=0\n")
            counter = folder / "attempts.txt"
            command = '''getprop() {
  case "$1" in ro.product.device) echo other_device;; ro.product.mod_device) echo other_device_xiaomieu_global;; ro.build.version.sdk) echo 36;; ro.product.cpu.abi) echo arm64-v8a;; ro.build.version.incremental) echo OS3.0.1.0.OTHER;; sys.boot_completed) echo 1;; esac
}; sleep() { :; }; pm() {
  if [ "$1" = path ]; then echo package:/product/app/MipayWallet/MipayWallet.apk; return 0; fi
  N=0; [ ! -f "$PM_LOG" ] || N=$(cat "$PM_LOG"); N=$((N+1)); printf '%s' "$N" > "$PM_LOG"
  [ "$N" -gt 1 ]
}; export -f getprop pm sleep; export PM_LOG="$2"; bash "$1"
'''
            result = shell(command, script.as_posix(), counter.as_posix())
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(counter.read_text(), "2")
            self.assertIn("Attempts=2", (folder / "reports/wallet-ui-service.txt").read_text())

    def test_service_enabled_state_roundtrip(self):
        for mode, original, action in [("cards", 0, "disable-user"), ("full", 0, "default-state"),
                ("full", 1, "enable"), ("full", 2, "disable"), ("full", 3, "disable-user"),
                ("full", 4, "disable-until-used")]:
            with self.subTest(mode=mode, original=original), tempfile.TemporaryDirectory(dir=ROOT) as temp:
                folder = Path(temp)
                (folder / "reports").mkdir()
                script = folder / "service.sh"
                shutil.copyfile(ROOT / "module/service.sh", script)
                (folder / "installer").mkdir()
                shutil.copyfile(ROOT / "module/installer/compat.sh", folder / "installer/compat.sh")
                config = folder / "component-selection.conf"
                config.write_text(f"WALLET_MODE={mode}\nWALLET_UI_MANAGED=1\nWALLET_ORIGINAL_ENABLED={original}\n")
                record = folder / "calls.txt"
                command = '''getprop() {
  case "$1" in ro.product.device) echo other_device;; ro.product.mod_device) echo other_device_xiaomieu_global;; ro.build.version.sdk) echo 36;; ro.product.cpu.abi) echo arm64-v8a;; ro.build.version.incremental) echo OS3.0.1.0.OTHER;; sys.boot_completed) echo 1;; esac
}; pm() {
  if [ "$1" = path ]; then echo package:/product/app/MipayWallet/MipayWallet.apk
  else printf '%s\\n' "$*" > "$PM_LOG"; fi
}; export -f getprop pm; export PM_LOG="$2"; bash "$1"
'''
                result = shell(command, script.as_posix(), record.as_posix())
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(record.read_text().strip(), f"{action} --user 0 com.mipay.wallet")
                self.assertIn(f"WALLET_UI_MANAGED={0 if mode == 'full' else 1}", config.read_text())

    def test_selection_matrix(self):
        with tempfile.TemporaryDirectory(dir=ROOT) as temp:
            config = Path(temp) / "selection.conf"
            for xiaoai in (0, 1):
                for mode in ("none", "full", "cards"):
                    with self.subTest(xiaoai=xiaoai, mode=mode):
                        config.write_text(f"BUNDLE_ID=HyperOS3EU307Optional\nXIAOAI={xiaoai}\nWALLET_MODE={mode}\n")
                        result = shell('. "$1"; parse_options "$2"', SELECT, config.as_posix())
                        self.assertEqual(result.returncode == 0, (xiaoai, mode) != (0, "none"), result.stderr)

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
                if name.startswith("payload/"): self.assertEqual(new.read(name), old.read(name), name)
            for component, count in (("xiaoai", 109), ("wallet", 99)):
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
        java = (ROOT / "cn-hook/app/src/main/java/com/ux5/hyperos3/perappcn/SettingsCardHolder.java").read_text()
        self.assertIn("HASH.equals(hex.toString())", java)
        self.assertIn("& 1024", java)
        self.assertNotIn("ro.product.device", compat + java)

if __name__ == "__main__": unittest.main(verbosity=2)
