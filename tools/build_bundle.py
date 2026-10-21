"""Build with unchanged payloads from the pinned v0.3 ZIP. No device access."""
import argparse
import hashlib
from pathlib import Path
import zipfile

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
    if digest(args.source_zip) != SOURCE_HASH:
        raise ValueError("Pinned source ZIP changed")
    files = {p.relative_to(ROOT / "module").as_posix(): p.read_bytes()
             for p in (ROOT / "module").rglob("*") if p.is_file()}
    with zipfile.ZipFile(args.source_zip) as source:
        files.update({n: source.read(n) for n in source.namelist() if n.startswith("payload/")})
    for name, data in files.items():
        if name.endswith(".sh") or name.endswith("update-binary"):
            if b"\r" in data:
                raise ValueError(f"Non-LF shell: {name}")
            for prohibited in (b"pm clear ", b"pm uninstall ", b"rm -rf ", b"settings put ", b"resetprop "):
                if prohibited in data:
                    raise ValueError(f"Destructive/default command in {name}")
    with zipfile.ZipFile(args.output_zip, "x", zipfile.ZIP_DEFLATED, compresslevel=6) as target:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (2026, 10, 22, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = (0o100755 if name.endswith(".sh") or name.endswith("update-binary") else 0o100644) << 16
            target.writestr(entry, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=6)
    with zipfile.ZipFile(args.output_zip) as archive:
        if archive.testzip() is not None or len(archive.namelist()) != 220:
            raise ValueError("ZIP CRC/count failed")
    print(f"{digest(args.output_zip)}  {args.output_zip.name} ({args.output_zip.stat().st_size} bytes)")

if __name__ == "__main__":
    main()
