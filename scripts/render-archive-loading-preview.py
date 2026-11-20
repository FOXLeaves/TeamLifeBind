"""Encode the real loading-shader GPU frames; the viewing canvas is not a game asset."""
from pathlib import Path
import hashlib
import json
import shutil
import struct
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
folder=ROOT/'.tools/archive-client-evidence/fabric'
evidence=json.loads((folder/'evidence.json').read_text(encoding='utf-8'))
frames=[folder/f'load-{i}.png' for i in range(17)]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for path in frames:
    assert sha(path)==evidence['loadingSnapshots'][path.name]['sha256'],f'Stale loading GPU frame: {path}'
width,height=struct.unpack('>II',frames[0].read_bytes()[16:24])
ffmpeg=shutil.which('ffmpeg')
if ffmpeg is None:
    sys.path.insert(0,str(ROOT/'.tools/video-deps'))
    import imageio_ffmpeg
    ffmpeg=imageio_ffmpeg.get_ffmpeg_exe()
lines=['ffconcat version 1.0']
for path,duration in [(frames[0],.45),*((p,1/24) for p in frames), (frames[-1],.5), (frames[-1],.04)]:
    escaped=path.as_posix().replace("'","'\\''")
    lines.extend([f"file '{escaped}'",f'duration {duration:.6f}'])
concat=ROOT/'.tools/archive-loading-preview.ffconcat'
concat.write_text('\n'.join(lines)+'\n',encoding='utf-8')
mp4=ROOT/'docs/images/archive-loading.mp4'
gif=ROOT/'docs/images/archive-loading.gif'
filters=f'[0:v]format=rgba[ui];color=c=0x202b3b:s={width}x{height}:r=24[base];[base][ui]overlay=shortest=1:format=auto,format=yuv420p[out]'
subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(concat),'-filter_complex',filters,'-map','[out]',
                '-an','-c:v','libx264','-crf','18','-preset','medium','-movflags','+faststart',str(mp4)],check=True)
subprocess.run([ffmpeg,'-hide_banner','-loglevel','error','-y','-i',str(mp4),'-vf',
                'fps=24,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer','-loop','0',str(gif)],check=True)
(ROOT/'.tools/archive-loading-preview-evidence.json').write_text(json.dumps({'source':'actual loading-wave GPU readback',
    'sourceEvidenceSha256':sha(folder/'evidence.json'),'frames':{p.name:sha(p) for p in frames},'mp4Sha256':sha(mp4),'gifSha256':sha(gif)},indent=2)+'\n',encoding='utf-8')
print(f'Encoded {len(frames)} actual GPU frames: {mp4.stat().st_size} byte MP4, {gif.stat().st_size} byte GIF')
