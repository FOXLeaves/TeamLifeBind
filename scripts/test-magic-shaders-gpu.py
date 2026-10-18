"""Compile production magic GLSL in a hidden SDL GL context and write GPU previews.

Uses only the project's cached LWJGL/JOML JARs and Java 26; no reference mod runs.
Run from any directory: python scripts/test-magic-shaders-gpu.py
"""
from pathlib import Path
import os
import subprocess
import argparse
import shutil

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.tools/gradle-upgrade-home/caches/modules-2/files-2.1'
parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, default=ROOT / 'fabric/src/main/resources/assets/teamlifebind/shaders/core')
parser.add_argument('--output', type=Path, default=ROOT / '.tools/magic-shader-probe')
parser.add_argument('--identity-only', action='store_true')
parser.add_argument('--publish-previews', action='store_true', help='Copy verified identity comparison sheets into docs/images')
args = parser.parse_args()
OUT = args.output.resolve()
OUT.mkdir(parents=True, exist_ok=True)


def cached(group, artifact, filename):
    candidates = sorted((CACHE / group / artifact).rglob(filename))
    if not candidates:
        raise SystemExit(f'Missing cached dependency: {group}:{artifact}:{filename}')
    return candidates[-1]


jdk = ROOT / '.tools/jdk26-extract/jdk-26.0.2.1+1/bin'
jars = []
for artifact in ('lwjgl', 'lwjgl-sdl', 'lwjgl-opengl'):
    jars += [cached('org.lwjgl', artifact, f'{artifact}-3.4.3.jar'),
             cached('org.lwjgl', artifact, f'{artifact}-3.4.3-natives-windows.jar')]
jars += [cached('org.joml', 'joml', 'joml-1.10.9.jar')]
classpath = os.pathsep.join(map(str, jars))
source = ROOT / 'scripts/gpu/MagicShaderGpuProbe.java'
subprocess.run([str(jdk / 'javac.exe'), '-encoding', 'UTF-8', '-cp', classpath,
                '-d', str(OUT), str(source)], cwd=ROOT, check=True)
with (OUT / 'gpu-test.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([str(jdk / 'java.exe'), '--enable-native-access=ALL-UNNAMED',
                             '-Djava.awt.headless=true', '-cp', str(OUT) + os.pathsep + classpath,
                             'MagicShaderGpuProbe', str(ROOT), str(OUT), str(args.source.resolve()),
                             'identity' if args.identity_only else 'all'],
                            cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
print((OUT / 'gpu-test.log').read_text(encoding='utf-8'))
if result.returncode == 0 and args.publish_previews:
    destination = ROOT / 'docs/images'
    destination.mkdir(parents=True, exist_ok=True)
    for source_name, target_name in (
        ('identity-1-monochrome.png', 'magic-school-beams-monochrome.png'),
        ('identity-13-monochrome.png', 'magic-school-shields-monochrome.png'),
        ('delivery-arcane-monochrome.png', 'magic-school-arcane-deliveries.png'),
    ):
        shutil.copy2(OUT / 'preview' / source_name, destination / target_name)
        print(f'Published verified GPU preview: docs/images/{target_name}')
raise SystemExit(result.returncode)
