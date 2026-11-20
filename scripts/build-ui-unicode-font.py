"""Build portable fallback faces from pinned GNU Unifont 17.0.04 (SIL OFL 1.1).

Only missing glyphs use these faces; the main UI keeps its smooth serif/system fonts.
Inputs are local, so reproducible builds do not need network access.
"""
from pathlib import Path
import argparse
import hashlib
import json
import unicodedata
from fontTools.ttLib import TTFont
from fontTools import subset

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-dir', type=Path, required=True)
args = parser.parse_args()
target = ROOT / 'client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind/fonts'
primary = set(TTFont(target / 'archive-serif.ttf').getBestCmap())
records = []
copyrights = set()
for source_name, output_name, family, postscript, upper in (
    ('unifont-17.0.04.otf', 'unicode-fallback.otf', 'TLB Unicode Fallback', 'TLBUnicodeFallback', False),
    ('unifont_upper-17.0.04.otf', 'unicode-upper.otf', 'TLB Unicode Upper', 'TLBUnicodeUpper', True),
):
    source = args.source_dir / source_name
    font = TTFont(source)
    font.recalcTimestamp = False
    copyrights.add(font['name'].getDebugName(0))
    codepoints = {cp for cp in font.getBestCmap() if unicodedata.category(chr(cp)) != 'Cn'
                  and (0x10000 <= cp < 0x20000 if upper else cp < 0x10000 and cp not in primary)}
    options = subset.Options()
    options.name_IDs = ['*']
    options.name_legacy = True
    options.name_languages = ['*']
    job = subset.Subsetter(options=options)
    job.populate(unicodes=codepoints)
    job.subset(font)
    names = {1: family, 2: 'Regular', 3: postscript + '-17.0.04-subset', 4: family, 6: postscript, 16: family, 17: 'Regular'}
    for entry in font['name'].names:
        if entry.nameID in names:
            entry.string = names[entry.nameID].encode(entry.getEncoding())
    if 'CFF ' in font:
        cff = font['CFF '].cff
        cff.fontNames = [postscript]
        cff.topDictIndex[0].FullName = family
        cff.topDictIndex[0].FamilyName = family
    output = target / output_name
    font.save(output, reorderTables=True)
    records.append({'file': output_name, 'family': family, 'upstream': source_name,
                    'source': 'https://ftp.gnu.org/gnu/unifont/unifont-17.0.04/' + source_name,
                    'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                    'sha256': hashlib.sha256(output.read_bytes()).hexdigest(), 'bytes': output.stat().st_size,
                    'glyphs': len(font.getGlyphOrder()), 'codepoints': len(codepoints)})
license_text = (target / 'OFL.txt').read_text(encoding='utf-8')
license_text = license_text[license_text.index('SIL OPEN FONT LICENSE Version 1.1'):]
(target / 'UNICODE-OFL.txt').write_text('\n'.join(sorted(copyrights)) + '\n\n' + license_text, encoding='utf-8')
meta = {'upstream': 'GNU Unifont 17.0.04', 'license': 'SIL Open Font License 1.1',
        'project': 'https://unifoundry.com/unifont.html',
        'modifications': 'Renamed subsets: BMP glyphs missing from primary serif; supplementary non-CJK scripts in Plane 1.',
        'fonts': records}
(target / 'UNICODE-SOURCE.json').write_text(json.dumps(meta, indent=2) + '\n', encoding='utf-8')
print(json.dumps(meta, indent=2))
