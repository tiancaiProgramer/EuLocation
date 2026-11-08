"""Build card backends/XiaoAI/Search; split the original wallet APK into dist."""
import argparse
import hashlib
from pathlib import Path
import zipfile
import json
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE_HASH = "e5f4c01c92a27b145674eba9dde4f913a26cc4158664e91aa3f34c67a1255122"

def digest(path):
    with path.open("rb") as stream:
        value = hashlib.sha256()
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
        return value.hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_zip", type=Path)
    parser.add_argument("output_zip", type=Path)
    args = parser.parse_args()
    if digest(args.source_zip) not in {SOURCE_HASH, "c467a06f8a5337f76f02a107e1e2f4c4ffe0a8a94ccef85af0923161c91e0d6b"}:
        raise ValueError("Pinned source ZIP changed")
    files = {p.relative_to(ROOT / "module").as_posix(): p.read_bytes()
             for p in (ROOT / "module").rglob("*") if p.is_file()}
    with zipfile.ZipFile(args.source_zip) as source:
        wallet_prefix = "payload/wallet/system/product/app/MipayWallet/"
        wallet_apk = source.read(wallet_prefix + "MipayWallet.apk")
        if hashlib.sha256(wallet_apk).hexdigest() != "ce369489bd4cf5262c0e35403566be2274a06bbeadb265fc8afe2897750db800":
            raise ValueError("Original signed wallet APK changed")
        files.update({n: source.read(n) for n in source.namelist()
                      if n.startswith("payload/") and not n.startswith(wallet_prefix)})
    # Regenerate the manifest; no excluded APK or duplicate native library can be
    # accepted by the new installer's wallet allowlist.
    files["payload/wallet/payload.sha256"] = "".join(
        f"{hashlib.sha256(data).hexdigest()}  {name[len('payload/wallet/'):] }\n"
        for name, data in sorted(files.items())
        if name.startswith("payload/wallet/") and name != "payload/wallet/payload.sha256"
    ).encode("ascii")
    search_hashes = {
        "phone": "c5c818831537b1edae9e50c8f2fb733068a8aeb9b07bfc2f000271d6f4efbc57",
        "fold": "f988c9720c97aac9ae7e747879060c7e92cb5eb81c7f414e60373f2fe4c7d422",
    }
    # Keep the exact original signed APK. Extract app-private native libraries
    # because system preinstalls do not use data-app extraction at install time.
    for mode, expected_hash in search_hashes.items():
        folder = ROOT / "payload-sources/search" / mode
        apk = folder / "Search.apk"
        if digest(apk) != expected_hash:
            raise ValueError(f"Original {mode} APK changed")
        app = "system/product/priv-app/MIUIQuickSearchBox"
        payload = {f"{app}/MIUIQuickSearchBox.apk": apk.read_bytes()}
        with zipfile.ZipFile(apk) as original:
            for name in original.namelist():
                if name.startswith("lib/arm64-v8a/") and name.endswith(".so"):
                    libname = Path(name).name
                    payload[f"{app}/lib/arm64/{libname}"] = original.read(name)
        permission = (folder / "privapp-permissions.xml").read_bytes()
        root = ET.fromstring(permission)
        if len(root) != 1 or root[0].get("package") != "com.android.quicksearchbox":
            raise ValueError("Permission allowlist includes unexpected package")
        payload["system/product/etc/permissions/privapp-permissions-eulocation-search.xml"] = permission
        prefix = f"payload/search/{mode}/"
        files.update({prefix + name: data for name, data in payload.items()})
        files[prefix + "payload.sha256"] = "".join(
            f"{hashlib.sha256(data).hexdigest()}  {name}\n" for name, data in sorted(payload.items())
        ).encode("ascii")
    provenance = json.loads(files["payload-provenance.json"])
    provenance["components"] = ["xiaoai", "wallet", "search"]
    provenance["search"] = {"originalApkSha256": search_hashes, "variants": "auto or manual phone/fold", "default": "cards + XiaoAI + Search auto", "tested": "APK phone local app search only; module regression pending"}
    files["payload-provenance.json"] = (json.dumps(provenance,ensure_ascii=False,indent=2)+"\n").encode("utf-8")
    for name, data in files.items():
        if name.endswith(".sh") or name.endswith("update-binary"):
            if b"\r" in data:
                raise ValueError(f"Non-LF shell: {name}")
            for prohibited in (b"pm clear ", b"pm uninstall ", b"rm -rf ", b"settings put ", b"resetprop "):
                if prohibited in data:
                    raise ValueError(f"Destructive/default command in {name}")
    args.output_zip.parent.mkdir(parents=True, exist_ok=True)
    separate = args.output_zip.parent / "MiWallet-6.111.0.5701.2736-original.apk"
    if separate.exists() and separate.read_bytes() != wallet_apk:
        raise ValueError("Refusing to overwrite a different standalone wallet APK")
    separate.write_bytes(wallet_apk)
    with zipfile.ZipFile(args.output_zip, "x", zipfile.ZIP_DEFLATED, compresslevel=6) as target:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 10, 22, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = (0o100755 if name.endswith(".sh") or name.endswith("update-binary") else 0o100644) << 16
            target.writestr(entry, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
    with zipfile.ZipFile(args.output_zip) as archive:
        if archive.testzip() is not None or len(archive.namelist()) != len(files):
            raise ValueError("ZIP CRC/count failed")
    print(f"{digest(args.output_zip)}  {args.output_zip.name} ({args.output_zip.stat().st_size} bytes)")

if __name__ == "__main__":
    main()
