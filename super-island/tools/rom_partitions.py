"""Read Android sparse/LP images and extract selected partitions for ROM comparison."""
import argparse
import bisect
import json
import os
import struct
import tarfile
import zipfile
from pathlib import Path


class SparseImage:
    def __init__(self, paths):
        self.ranges = []
        self.files = []
        self.size = 0
        for path in paths:
            f = open(path, 'rb')
            self.files.append(f)
            header = f.read(28)
            magic, major, minor, fh, ch, block, blocks, chunks, checksum = struct.unpack('<I4H4I', header)
            if magic != 0xED26FF3A:
                if len(paths) != 1:
                    raise ValueError('Expected Android sparse fragments')
                self.size = os.path.getsize(path)
                self.ranges.append((0, self.size, f, 0, None))
                break
            if major != 1:
                raise ValueError('Unsupported sparse version')
            self.size = max(self.size, block * blocks)
            f.seek(fh)
            logical = 0
            for _ in range(chunks):
                raw = f.read(ch)
                kind, reserved, count, total = struct.unpack('<2H2I', raw[:12])
                length = count * block
                offset = f.tell()
                payload = total - ch
                if kind == 0xCAC1:
                    if payload != length:
                        raise ValueError('Invalid raw sparse chunk')
                    self.ranges.append((logical, logical + length, f, offset, None))
                elif kind == 0xCAC2:
                    self.ranges.append((logical, logical + length, None, 0, f.read(4)))
                elif kind not in (0xCAC3, 0xCAC4):
                    raise ValueError(f'Unknown sparse chunk {kind:x}')
                f.seek(offset + payload)
                logical += length
        self.ranges.sort(key=lambda r: r[0])
        for a, b in zip(self.ranges, self.ranges[1:]):
            if a[1] > b[0]:
                raise ValueError('Overlapping nonempty sparse fragments')
        self.starts = [r[0] for r in self.ranges]

    def read(self, offset, length):
        data = bytearray()
        end = offset + length
        while offset < end:
            i = bisect.bisect_right(self.starts, offset) - 1
            if i >= 0 and offset < self.ranges[i][1]:
                start, stop, f, physical, fill = self.ranges[i]
                count = min(stop, end) - offset
                if f:
                    f.seek(physical + offset - start)
                    chunk = f.read(count)
                    if len(chunk) != count:
                        raise ValueError('Truncated image')
                    data.extend(chunk)
                else:
                    phase = (offset - start) % 4
                    data.extend((fill * ((count + phase + 3) // 4))[phase:phase + count])
            else:
                next_start = self.starts[i + 1] if i + 1 < len(self.starts) else end
                count = min(next_start, end) - offset
                data.extend(bytes(count))
            offset += count
        return bytes(data)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('archive')
    parser.add_argument('output')
    parser.add_argument('--partitions', nargs='+', default=['product', 'system_ext', 'system'])
    args = parser.parse_args()
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    archive = Path(args.archive)
    if archive.suffix == '.zip':
        with zipfile.ZipFile(archive) as z:
            names = [n for n in z.namelist() if Path(n).name.startswith('super.img')]
            names.sort(key=lambda n: int(n.rsplit('.', 1)[1]) if n.rsplit('.', 1)[1].isdigit() else -1)
            paths = []
            for n in names:
                path = out / Path(n).name
                if not path.exists() or path.stat().st_size != z.getinfo(n).file_size:
                    print('Extracting', n, flush=True)
                    with z.open(n) as source, open(path, 'wb') as dest:
                        import shutil
                        shutil.copyfileobj(source, dest, 8 * 1024 * 1024)
                paths.append(path)
    else:
        path = out / 'super.img'
        if not path.exists():
            with tarfile.open(archive, 'r|gz') as t:
                for member in t:
                    if Path(member.name).name == 'super.img':
                        print('Extracting', member.name, member.size, flush=True)
                        with t.extractfile(member) as source, open(path, 'xb') as dest:
                            import shutil
                            shutil.copyfileobj(source, dest, 8 * 1024 * 1024)
                        break
                else:
                    raise ValueError('super.img not found')
        paths = [path]
    image = SparseImage(paths)
    geometry = image.read(4096, 52)
    magic, size = struct.unpack_from('<II', geometry)
    if magic != 0x616C4467:
        raise ValueError('Invalid LP geometry')
    header = image.read(12288, 256)
    magic, major, minor, header_size = struct.unpack_from('<IHHI', header)
    if magic != 0x414C5030:
        raise ValueError('Invalid LP metadata')
    tables_size = struct.unpack_from('<I', header, 44)[0]
    tables = image.read(12288 + header_size, tables_size)
    po, pc, ps = struct.unpack_from('<III', header, 80)
    eo, ec, es = struct.unpack_from('<III', header, 92)
    entries = []
    for i in range(pc):
        name, attributes, first, count, group = struct.unpack_from('<36s4I', tables, po + i * ps)
        name = name.split(b'\0')[0].decode()
        extents = [struct.unpack_from('<QIQI', tables, eo + (first + j) * es) for j in range(count)]
        entries.append({'name': name, 'extents': extents, 'bytes': sum(e[0] * 512 for e in extents)})
        if name not in args.partitions and name.removesuffix('_a') not in args.partitions:
            continue
        dest = out / (name.removesuffix('_a') + '.img')
        if dest.exists() and dest.stat().st_size == sum(e[0] * 512 for e in extents):
            continue
        print('Partition', name, '->', dest, flush=True)
        with open(dest, 'wb') as target:
            for sectors, kind, sector, source in extents:
                if source != 0:
                    raise ValueError('External block device unsupported')
                remaining = sectors * 512
                offset = sector * 512
                while remaining:
                    count = min(8 * 1024 * 1024, remaining)
                    target.write(image.read(offset, count) if kind == 0 else bytes(count))
                    offset += count
                    remaining -= count
    (out / 'partitions.json').write_text(json.dumps(entries, indent=2), encoding='utf-8')
    print('DONE', flush=True)


if __name__ == '__main__':
    main()
