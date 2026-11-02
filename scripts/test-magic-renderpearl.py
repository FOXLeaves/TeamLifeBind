"""Exercise the production MagicShaderRenderer through MC 26.3 RenderPearl and GPU readback.

Build platform classes first. Uses cached Java 26 and dependencies, with a hidden SDL backend.
--negative-control recreates the old zero-vertex draw defect in an isolated test copy;
its expected nonzero exit verifies that the regression fails on the reported symptom.
--negative-follow-control disables caster attachment in an isolated test copy;
its expected nonzero exit reproduces visible lag between sparse scene packets.
"""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.tools/gradle-upgrade-home/caches'
JDK = ROOT / '.tools/jdk26-extract/jdk-26.0.2.1+1/bin'
parser = argparse.ArgumentParser()
parser.add_argument('--platform', nargs='+', choices=['fabric', 'forge', 'neoforge'], default=['fabric', 'forge', 'neoforge'])
negative = parser.add_mutually_exclusive_group()
negative.add_argument('--negative-control', action='store_true')
negative.add_argument('--negative-follow-control', action='store_true')
parser.add_argument('--output', type=Path, default=ROOT / '.tools/magic-renderpearl-production')
args = parser.parse_args()
OUT = args.output.resolve()
OUT.mkdir(parents=True, exist_ok=True)
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

# Match each loader's actual patched MC rather than testing all three against Fabric.
minecraft = {
    'fabric': CACHE / 'fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar',
    'forge': CACHE / 'minecraftforge/forgegradle/mavenizer/caches/forge/net/minecraftforge/forge/26.3-66.0.10/official/26.3/recompiled.jar',
    'neoforge': ROOT / 'neoforge/build/moddev/artifacts/minecraft-patched-26.3.0.71-beta-merged.jar',
}
deps = sorted(p for p in (CACHE / 'modules-2/files-2.1').rglob('*.jar')
              if not p.name.endswith(('-sources.jar', '-javadoc.jar')) and 'mixin' not in p.name)
# The cache also contains build-tool Gson releases; MC's registry bootstrap needs its current API.
gson = sorted((CACHE / 'modules-2/files-2.1/com.google.code.gson/gson/2.14.0').rglob('*.jar'))
deps = gson + [p for p in deps if '/com.google.code.gson/gson/' not in p.as_posix()]
fastutil = sorted((ROOT / '.tools/smoke-26.3/server/libraries/it/unimi/dsi/fastutil').rglob('*.jar'))
mixin_metadata = sorted(p for p in (CACHE / 'modules-2/files-2.1/net.fabricmc/sponge-mixin').rglob('*.jar')
                        if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
# Copy stable current common classes once; other build tasks cannot remove them mid-probe.
common = OUT / 'common-classes'
shutil.copytree(ROOT / 'common/build/classes/java/main', common, dirs_exist_ok=True)
# Forge's recompiled artifact contains classes only. Bootstrap reads the unmodified vanilla
# language/version resources, so supply those resources without introducing another MC classpath.
bootstrap_resources = OUT / 'minecraft-bootstrap-resources.jar'
with zipfile.ZipFile(minecraft['fabric']) as vanilla, zipfile.ZipFile(bootstrap_resources, 'w') as resources:
    for name in vanilla.namelist():
        if name == 'version.json' or name.startswith('assets/minecraft/lang/'):
            resources.writestr(name, vanilla.read(name))
for platform in args.platform:
    dest = OUT / ('negative-' + platform if args.negative_control else 'negative-follow-' + platform if args.negative_follow_control else platform)
    dest.mkdir(parents=True, exist_ok=True)
    if not minecraft[platform].exists():
        raise SystemExit(f'Build {platform} first: missing {minecraft[platform]}')
    source = ROOT / platform / f'src/main/java/com/teamlifebind/{platform}/MagicShaderRenderer.java'
    if args.negative_control or args.negative_follow_control:
        clone = dest / 'negative-control/MagicShaderRenderer.java'
        clone.parent.mkdir(parents=True, exist_ok=True)
        content = source.read_text(encoding='utf-8')
        if args.negative_control:
            if content.count('pass.draw(3, 1, 0, 0);') != 2:
                raise SystemExit('Negative control must replace precisely the two production draw calls')
            content=content.replace('pass.draw(3, 1, 0, 0);', 'pass.draw(0, 3, 1, 0);')
        else:
            follow='scenes.replaceAll(scene -> followCaster(scene, camera));'
            if content.count(follow)!=1:
                raise SystemExit('Follow negative control must disable precisely the per-frame caster resolution')
            content=content.replace(follow,'// Negative control: use the last received server position.')
        clone.write_text(content, encoding='utf-8')
        source = clone
    classpath = os.pathsep.join(map(str, [dest, minecraft[platform], ROOT / platform / 'build/classes/java/main', common, bootstrap_resources, *fastutil, *deps, *(mixin_metadata if platform == 'neoforge' else [])]))
    commands = [
        ('compile', 'javac.exe', ['-encoding', 'UTF-8', '-proc:none', '--release', '26', '-cp', classpath, '-d', str(dest), str(source), str(ROOT / 'scripts/gpu/MagicRenderPearlProbe.java')]),
        ('render', 'java.exe', ['--enable-native-access=ALL-UNNAMED', '--enable-final-field-mutation=ALL-UNNAMED', '-Djava.awt.headless=true', '-Dorg.slf4j.simpleLogger.cacheOutputStream=true', '-cp', classpath, 'MagicRenderPearlProbe', str(ROOT), platform, str(dest)]),
    ]
    for label, exe, parameters in commands:
        argfile = dest / (label + '.args')
        argfile.write_text('\n'.join(json.dumps(p) for p in parameters), encoding='utf-8')
        result = subprocess.run([str(JDK / exe), '@' + str(argfile)], cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace')
        log = result.stdout + result.stderr
        (dest / (label + '.log')).write_text(log, encoding='utf-8')
        if result.returncode:
            print('\n'.join(log.splitlines()[-45:]))
            raise SystemExit(result.returncode)
        if label == 'render':
            print('\n'.join(line for line in result.stdout.splitlines() if line.startswith(('ACTUAL_', 'PRODUCTION_', 'PASS '))))
