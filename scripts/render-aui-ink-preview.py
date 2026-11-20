"""Encode the real native GPU probe frames as a small UI transition preview.

Run test-aui-client.py first. Requires ffmpeg or the local imageio-ffmpeg tool.
The dark preview canvas is for viewing transparent captures, not a menu asset.
"""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
folder = ROOT / '.tools/aui-client-evidence/fabric'
data = json.loads((folder/'evidence.json').read_text(encoding='utf-8'))
frames = [folder/f'ink-{i}.png' for i in range(34)]
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
for path in frames:
    assert digest(path) == data['inkSnapshots'][path.name]['sha256'], f'Stale GPU capture: {path}'
ffmpeg = shutil.which('ffmpeg')
if ffmpeg is None:
    sys.path.insert(0, str(ROOT/'.tools/video-deps'))
    import imageio_ffmpeg
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
concat = ROOT/'.tools/aui-ink-preview.ffconcat'
lines = ['ffconcat version 1.0']
def frame(path, duration):
    escaped = path.as_posix().replace("'", "'\\''")
    lines.extend([f"file '{escaped}'", f'duration {duration:.6f}'])
frame(frames[0], .25)
for path in frames[:17]: frame(path, 1/34)
frame(frames[16], .65)
for path in frames[17:]: frame(path, 1/34)
frame(frames[33], 1.1)
frame(frames[33], .04)
concat.write_text('\n'.join(lines)+'\n', encoding='utf-8')
mp4 = ROOT/'docs/images/aui-ink-transition.mp4'
gif = ROOT/'docs/images/aui-ink-transition.gif'
filters = '[0:v]format=rgba[ui];color=c=0x121820:s=1920x1080:r=34[base];[base][ui]overlay=shortest=1:format=auto,format=yuv420p[out]'
subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(concat),
    '-filter_complex',filters,'-map','[out]','-an','-c:v','libx264','-crf','19','-preset','medium',
    '-movflags','+faststart',str(mp4)],check=True)
subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-i',str(mp4),
    '-vf','fps=24,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer',
    '-loop','0',str(gif)],check=True)
record={'source':'actual 26.3 AUI RenderPearl GPU readback','sourceEvidenceSha256':digest(folder/'evidence.json'),
        'alpha':'preserved in PNG; composited onto a dark viewing canvas for the video only',
        'frames':{p.name:digest(p) for p in frames},'mp4Sha256':digest(mp4),'gifSha256':digest(gif)}
(ROOT/'.tools/aui-ink-preview-evidence.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'mp4Bytes':mp4.stat().st_size,'gifBytes':gif.stat().st_size,'sourceFrames':len(frames)},indent=2))
