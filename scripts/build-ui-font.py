"""Rebuild the renamed, static Noto Serif SC subset used by the AUI headings.

Requires fonttools. Pass --source to use an already downloaded upstream font.
The game loads the bundled result and never fetches fonts over the network.
"""
from pathlib import Path
import argparse
import hashlib
import json
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools import subset

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, required=True)
args = parser.parse_args()
target = ROOT / 'client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind/fonts'
target.mkdir(parents=True, exist_ok=True)
text = ''.join(chr(c) for c in range(32, 127)) + '◆◇✦×＋—·…'
text += ''.join(chr(c) for start,end in ((0x3000,0x3100),(0x3400,0x4dc0),(0x4e00,0xa000),(0xff00,0xfff0)) for c in range(start,end))
for folder in ('common/src', 'fabric/src', 'client-ui/src', 'scripts/fixtures', 'docs'):
    for path in (ROOT / folder).rglob('*'):
        if path.suffix in ('.java', '.json', '.properties', '.html', '.md'):
            text += path.read_text(encoding='utf-8-sig')
font = TTFont(args.source)
font.recalcTimestamp = False
instantiateVariableFont(font, {'wght':600}, inplace=True)
options = subset.Options()
options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14, 16, 17]
options.name_legacy = True
options.name_languages = ['*']
subsetter = subset.Subsetter(options=options)
subsetter.populate(unicodes=set(map(ord, text)))
subsetter.subset(font)
names = {1:'TLB Archive Serif', 2:'Regular', 3:'TLBArchiveSerif-600-subset-1',
         4:'TLB Archive Serif', 6:'TLBArchiveSerif', 16:'TLB Archive Serif', 17:'Regular'}
for record in font['name'].names:
    if record.nameID in names:
        record.string = names[record.nameID].encode(record.getEncoding(), errors='replace')
output = target / 'archive-serif.ttf'
font.save(output, reorderTables=True)
license_path = args.source.parent / 'OFL.txt'
if not license_path.is_file():
    raise SystemExit('Keep the upstream OFL.txt next to the input font.')
(target / 'OFL.txt').write_bytes(license_path.read_bytes())
meta = {'family':'TLB Archive Serif', 'upstream':'Noto Serif SC', 'license':'SIL Open Font License 1.1',
        'source':'https://github.com/google/fonts/tree/main/ofl/notoserifsc',
        'sourceSha256':hashlib.sha256(args.source.read_bytes()).hexdigest(),
        'modifications':'Static weight 600, CJK unified ideographs, kana, punctuation and project text subset, renamed family',
        'glyphs':len(font.getGlyphOrder()), 'bytes':output.stat().st_size,
        'sha256':hashlib.sha256(output.read_bytes()).hexdigest()}
(target / 'SOURCE.json').write_text(json.dumps(meta,indent=2)+'\n',encoding='utf-8')
print(json.dumps(meta,indent=2))
