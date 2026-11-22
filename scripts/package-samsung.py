#!/usr/bin/env python3
"""Build an unsigned Tizen widget; Samsung device certificates are added locally."""
from pathlib import Path
import argparse,zipfile,xml.etree.ElementTree as ET
parser=argparse.ArgumentParser();parser.add_argument('--output',default='dist/TV-Player-Samsung-0.9.0-unsigned.wgt');args=parser.parse_args()
root=Path(__file__).resolve().parents[1]/'samsung'
required=['config.xml','index.html','style.css','core.js','catalog-worker.js','codec-worker.js','storage.js','app.js','icon.png']
for name in required:
    if not (root/name).is_file(): raise SystemExit('Missing widget file: '+name)
config=ET.parse(root/'config.xml').getroot()
ns={'w':'http://www.w3.org/ns/widgets','t':'http://tizen.org/ns/widgets'}
assert config.attrib['version']=='0.9.0'
assert config.find('t:application',ns).attrib['id']=='TVPlayer09.TVPlayer'
assert config.find('t:profile',ns).attrib['name']=='tv-samsung'
output=Path(args.output);output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as package:
    for name in required: package.write(root/name,name)
with zipfile.ZipFile(output) as package:
    assert set(package.namelist())==set(required)
    assert package.testzip() is None
print(str(output)+' — UNSIGNED, requires a Samsung certificate profile for the target TV.')
