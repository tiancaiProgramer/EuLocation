"""Print selected DEX strings without decompiling entire APKs."""
import argparse
import struct
import zipfile

p = argparse.ArgumentParser()
p.add_argument('apk')
p.add_argument('needles', nargs='+')
a = p.parse_args()
with zipfile.ZipFile(a.apk) as z:
    for name in z.namelist():
        if not name.endswith('.dex'):
            continue
        data = z.read(name)
        count, offset = struct.unpack_from('<II', data, 56)
        for i in range(count):
            pos = struct.unpack_from('<I', data, offset + i * 4)[0]
            while data[pos] & 128:
                pos += 1
            pos += 1
            end = data.index(b'\0', pos)
            value = data[pos:end].decode('utf-8', errors='replace')
            if any(n.lower() in value.lower() for n in a.needles):
                print(name, value)
