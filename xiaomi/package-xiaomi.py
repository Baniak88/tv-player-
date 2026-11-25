#!/usr/bin/env python3
"""Make an offline installer bundle for Xiaomi TV Box S (Google TV) from the signed TV APK.

The APK is byte-for-byte the same build as the Fire Stick bundle: one package, one
signature, so updates and data stay compatible across devices.
"""
import argparse
import hashlib
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--apk', type=Path, default=root / 'tv/build/outputs/apk/debug/tv-debug.apk')
parser.add_argument('--output', type=Path, default=root / 'dist/OKTV-Xiaomi-TV-Box.zip')
args = parser.parse_args()
apk = args.apk.read_bytes()
with zipfile.ZipFile(args.apk) as archive:
    if not {'AndroidManifest.xml', 'classes.dex'} <= set(archive.namelist()):
        raise SystemExit('Input is not an Android application APK')
args.output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(args.output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('OKTV-Player-TV.apk', apk)
    archive.writestr('SHA256SUMS', hashlib.sha256(apk).hexdigest() + '  OKTV-Player-TV.apk\n')
    archive.writestr('install-xiaomi.sh', (root / 'xiaomi/install-xiaomi.sh').read_bytes())
    archive.writestr('CZYTAJ-XIAOMI.md', (root / 'xiaomi/README.md').read_bytes())
print(args.output)
