"""Read-only verification of pinned release artifacts; no device access."""
import argparse
import hashlib
from pathlib import Path, PurePosixPath
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_SHA256 = "c467a06f8a5337f76f02a107e1e2f4c4ffe0a8a94ccef85af0923161c91e0d6b"
EXPECTED_APK_SHA256 = "7ee5779e5f0a23726842795befa0b366785e5c65bc26b4de162fe631f15038fc"
COMPONENTS = {
    "xiaoai": (109, {"VoiceAssistAndroidT", "VoiceTrigger"}),
    "wallet": (99, {"MITSMClient", "MipayWallet", "MINextpay", "UPTsmService", "PaymentService"}),
}


def file_sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify(path):
    if file_sha256(path) != EXPECTED_SHA256:
        raise ValueError("Release ZIP SHA-256 differs from the published artifact")
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != 220 or len(set(names)) != len(names):
            raise ValueError("Unexpected/duplicate archive entries")
        expected = set()
        sources = sorted(p for p in (ROOT / "module").rglob("*") if p.is_file())
        if len(sources) != 10:
            raise ValueError("Expected ten public module source files")
        for source in sources:
            name = source.relative_to(ROOT / "module").as_posix()
            if archive.read(name) != source.read_bytes():
                raise ValueError(f"Public source differs from release: {name}")
            expected.add(name)
        for component, (count, allowed_apps) in COMPONENTS.items():
            prefix = f"payload/{component}/"
            manifest_name = prefix + "payload.sha256"
            expected.add(manifest_name)
            rows = archive.read(manifest_name).decode("ascii").splitlines()
            if len(rows) != count:
                raise ValueError(f"Unexpected payload count: {component}")
            for row in rows:
                digest, name = row.split(None, 1)
                parts = PurePosixPath(name).parts
                safe = (name == "system.prop" and component == "wallet") or (
                    len(parts) >= 5 and parts[:3] == ("system", "product", "app")
                    and parts[3] in allowed_apps
                )
                if (not safe or ".." in parts or "\\" in name
                        or not re.fullmatch(r"[0-9a-f]{64}", digest)):
                    raise ValueError(f"Invalid payload manifest path/hash: {name}")
                full_name = prefix + name
                if full_name in expected:
                    raise ValueError(f"Duplicate payload: {name}")
                expected.add(full_name)
                if hashlib.sha256(archive.read(full_name)).hexdigest() != digest:
                    raise ValueError(f"Payload SHA-256 mismatch: {name}")
        if set(names) != expected:
            raise ValueError("Archive has missing or unexpected files")
    print("PASS: pinned release SHA-256; 10 source files; 208 payload files; 220 ZIP entries")


def verify_apk(path):
    if file_sha256(path) != EXPECTED_APK_SHA256:
        raise ValueError("CN Hook APK SHA-256 differs from the published artifact")
    with zipfile.ZipFile(path) as archive:
        for name in ("java_init.list", "module.prop", "scope.list"):
            relative = f"META-INF/xposed/{name}"
            source = ROOT / "cn-hook/app/src/main/resources" / relative
            if archive.read(relative) != source.read_bytes():
                raise ValueError(f"CN Hook metadata differs: {relative}")
    print("PASS: pinned CN Hook APK SHA-256 and Xposed metadata; default scope Recorder only")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("zip", type=Path)
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    try:
        verify(args.zip)
        if args.apk:
            verify_apk(args.apk)
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as exc:
        parser.exit(1, f"FAIL: {exc}\n")
