"""Read actual class definitions and DEX member descriptors, including bridge overloads."""
import argparse
import struct
import zipfile


def uleb(data, offset):
    value = shift = 0
    while True:
        part = data[offset]
        offset += 1
        value |= (part & 127) << shift
        if part < 128:
            return value, offset
        shift += 7


def layouts(apk, wanted):
    with zipfile.ZipFile(apk) as archive:
        for filename in archive.namelist():
            if not filename.endswith('.dex'):
                continue
            data = archive.read(filename)
            sc, so, tc, to, pc, po, fc, fo, mc, mo, cc, co = struct.unpack_from('<12I', data, 56)
            strings = []
            for i in range(sc):
                pos = struct.unpack_from('<I', data, so + 4 * i)[0]
                _, pos = uleb(data, pos)
                strings.append(data[pos:data.index(b'\0', pos)].decode('utf-8', errors='replace'))
            types = [strings[struct.unpack_from('<I', data, to + 4 * i)[0]] for i in range(tc)]
            for i in range(cc):
                ci, access, si, interfaces, source, annotations, cdata, values = struct.unpack_from('<8I', data, co + 32 * i)
                if types[ci] not in wanted:
                    continue
                entry = {'class': types[ci], 'super': types[si] if si != 0xffffffff else None,
                         'dex': filename, 'fields': [], 'methods': []}
                if cdata:
                    counts = []
                    pos = cdata
                    for _ in range(4):
                        count, pos = uleb(data, pos)
                        counts.append(count)
                    for count in counts[:2]:
                        index = 0
                        for _ in range(count):
                            diff, pos = uleb(data, pos)
                            flags, pos = uleb(data, pos)
                            index += diff
                            owner, typ, name = struct.unpack_from('<HHI', data, fo + 8 * index)
                            entry['fields'].append((strings[name], types[typ], flags))
                    for count in counts[2:]:
                        index = 0
                        for _ in range(count):
                            diff, pos = uleb(data, pos)
                            flags, pos = uleb(data, pos)
                            code, pos = uleb(data, pos)
                            index += diff
                            owner, proto, name = struct.unpack_from('<HHI', data, mo + 8 * index)
                            shorty, ret, params = struct.unpack_from('<III', data, po + 12 * proto)
                            args = []
                            if params:
                                size = struct.unpack_from('<I', data, params)[0]
                                args = [types[t] for t in struct.unpack_from('<' + 'H' * size, data, params + 4)]
                            entry['methods'].append((strings[name], '(' + ''.join(args) + ')' + types[ret], flags))
                yield entry


if __name__ == '__main__':
    import json
    p = argparse.ArgumentParser()
    p.add_argument('apk')
    p.add_argument('classes', nargs='+')
    a = p.parse_args()
    print(json.dumps(list(layouts(a.apk, set(a.classes))), indent=2))
