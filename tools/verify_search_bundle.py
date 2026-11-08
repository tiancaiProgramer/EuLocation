"""Verify the development search bundle against current source and pinned payloads."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def verify(path, previous):
    expected = set()
    with zipfile.ZipFile(path) as current, zipfile.ZipFile(previous) as old:
        assert current.testzip() is None
        assert len(current.namelist()) == len(set(current.namelist()))
        for source in (ROOT / "module").rglob("*"):
            if source.is_file():
                name = source.relative_to(ROOT / "module").as_posix()
                assert current.read(name) == source.read_bytes(), name
                expected.add(name)
        for name in old.namelist():
            if name.startswith("payload/"):
                if name.startswith("payload/wallet/system/product/app/MipayWallet/") or name == "payload/wallet/payload.sha256":
                    assert name not in current.namelist() or name.endswith("payload.sha256"), name
                    continue
                assert current.read(name) == old.read(name), name
                expected.add(name)
        manifest = "payload/wallet/payload.sha256"
        expected.add(manifest)
        rows = current.read(manifest).decode("ascii").splitlines()
        assert len(rows) == 14
        assert sum(row.endswith('.apk') for row in rows) == 4
        for row in rows:
            digest, name = row.split(None, 1)
            assert 'MipayWallet' not in name
            assert sha(current.read('payload/wallet/' + name)) == digest
        apk_hashes = {
            "phone": "c5c818831537b1edae9e50c8f2fb733068a8aeb9b07bfc2f000271d6f4efbc57",
            "fold": "f988c9720c97aac9ae7e747879060c7e92cb5eb81c7f414e60373f2fe4c7d422",
        }
        for mode, pinned in apk_hashes.items():
            prefix = f"payload/search/{mode}/"
            manifest = prefix + "payload.sha256"
            expected.add(manifest)
            rows = current.read(manifest).decode("ascii").splitlines()
            apks, libraries, permissions = [], [], []
            for line in rows:
                digest, name = line.split(None, 1)
                assert ".." not in name and not name.startswith("/") and "\\" not in name
                assert sha(current.read(prefix + name)) == digest, name
                expected.add(prefix + name)
                if name.endswith(".apk"):
                    apks.append(name)
                elif name.endswith(".so"):
                    libraries.append(name)
                elif name.endswith(".xml"):
                    permissions.append(name)
                else:
                    raise AssertionError(name)
            assert apks == ["system/product/priv-app/MIUIQuickSearchBox/MIUIQuickSearchBox.apk"]
            assert sha(current.read(prefix + apks[0])) == pinned
            assert len(libraries) == (9 if mode == "phone" else 6)
            assert permissions == ["system/product/etc/permissions/privapp-permissions-eulocation-search.xml"]
        assert set(current.namelist()) == expected
        config = current.read("customize.sh").decode()
        assert 'recommended_selection' in config and 'resolve_search_selection' in config
        assert not any('MipayWallet/' in n for n in current.namelist())
        print(f"PASS: {len(expected)} ZIP entries, CRC, current source, unchanged card/XiaoAI payloads, no wallet main/84 duplicate libraries, two signed Search APKs")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("previous", type=Path)
    args = parser.parse_args()
    verify(args.bundle, args.previous)
