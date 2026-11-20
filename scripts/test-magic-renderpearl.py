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
negative.add_argument('--negative-attack-control', action='store_true', help='Disable shared resolution in an isolated renderer copy; must fail --attack-stages')
parser.add_argument('--output', type=Path, default=ROOT / '.tools/magic-renderpearl-production')
parser.add_argument('--attack-stages', action='store_true', help='Check all 300 simultaneous cast/attack and cast/impact scenes')
parser.add_argument('--world-effects', action='store_true', help='Check all native world VFX, lifetime, depth and native-resolution detail contribution')
parser.add_argument('--depth-conventions', action='store_true', help='Check forward and reversed world depth, clear planes and foreground occlusion')
parser.add_argument('--school-details', action='store_true', help='Check 14 school hit/domain engravings in monochrome, plus production color previews')
parser.add_argument('--delivery-details', action='store_true', help='Check 20 attack structures and high-tier stage progression against a disabled-detail control')
parser.add_argument('--combat-details', action='store_true', help='Check eight combat/supply structures against a disabled-detail control, monochrome shapes and timeline')
parser.add_argument('--restoration-details', action='store_true', help='Check four recovery structures, inside/partial depth, timeline and sparse-packet player following')
parser.add_argument('--width', type=int, default=1920, help='Attack-stage framebuffer width')
parser.add_argument('--height', type=int, default=1080, help='Attack-stage framebuffer height')
parser.add_argument('--built-renderer', action='store_true', help='Test the exact Gradle-built renderer classes destined for the release JAR')
args = parser.parse_args()
if args.negative_attack_control and not args.attack_stages:
    parser.error('--negative-attack-control requires --attack-stages')
if args.built_renderer and (args.negative_control or args.negative_follow_control or args.negative_attack_control):
    parser.error('--built-renderer cannot be used with a modified negative control')
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
    dest = OUT / ('negative-' + platform if args.negative_control else 'negative-follow-' + platform if args.negative_follow_control else 'negative-attack-' + platform if args.negative_attack_control else platform)
    dest.mkdir(parents=True, exist_ok=True)
    if not minecraft[platform].exists():
        raise SystemExit(f'Build {platform} first: missing {minecraft[platform]}')
    source = ROOT / platform / f'src/main/java/com/teamlifebind/{platform}/MagicShaderRenderer.java'
    if args.built_renderer and (dest / f'com/teamlifebind/{platform}/MagicShaderRenderer.class').exists():
        raise SystemExit('Use a fresh output directory: an isolated renderer would shadow the built class')
    if args.negative_control or args.negative_follow_control or args.negative_attack_control:
        clone = dest / 'negative-control/MagicShaderRenderer.java'
        clone.parent.mkdir(parents=True, exist_ok=True)
        content = source.read_text(encoding='utf-8')
        if args.negative_control:
            if content.count('pass.draw(3, 1, 0, 0);') != 3:
                raise SystemExit('Negative control must replace precisely the three production draw calls')
            content=content.replace('pass.draw(3, 1, 0, 0);', 'pass.draw(0, 3, 1, 0);')
        elif args.negative_follow_control:
            follow='scenes.replaceAll(scene -> followCaster(scene, camera));'
            if content.count(follow)!=1:
                raise SystemExit('Follow negative control must disable precisely the per-frame caster resolution')
            content=content.replace(follow,'// Negative control: use the last received server position.')
        else:
            budget='while (layers < MAX_DRAWS && projectedPixels(visible, camera.pos, viewProjection, main, layers) > MAX_EFFECT_PIXELS) layers *= 2;'
            if content.count(budget)!=1:
                raise SystemExit('Attack negative control must disable precisely the shared pixel budget')
            content=content.replace(budget,'// Negative control: one volume can consume the whole pixel budget.')
        clone.write_text(content, encoding='utf-8')
        source = clone
    classpath = os.pathsep.join(map(str, [dest, minecraft[platform], ROOT / platform / 'build/classes/java/main', common, bootstrap_resources, *fastutil, *deps, *(mixin_metadata if platform == 'neoforge' else [])]))
    commands = [
        ('compile', 'javac.exe', ['-encoding', 'UTF-8', '-proc:none', '--release', '26', '-cp', classpath, '-d', str(dest), *([] if args.built_renderer else [str(source),str(ROOT/'client-ui/src/main/java/com/teamlifebind/clientui/ClientRenderCompat.java')]), str(ROOT / 'scripts/gpu/MagicRenderPearlProbe.java')]),
        ('render', 'java.exe', ['--enable-native-access=ALL-UNNAMED', '--enable-final-field-mutation=ALL-UNNAMED', '-Djava.awt.headless=true', '-Dorg.slf4j.simpleLogger.cacheOutputStream=true',
             *(['-Dtlb.probe.attackStages=true'] if args.attack_stages else []),
             *(['-Dtlb.probe.worldEffects=true'] if args.world_effects else []),
             *(['-Dtlb.probe.depthConventions=true'] if args.depth_conventions else []),
             *(['-Dtlb.probe.schoolDetails=true'] if args.school_details else []),
             *(['-Dtlb.probe.deliveryDetails=true'] if args.delivery_details else []),
             *(['-Dtlb.probe.combatDetails=true'] if args.combat_details else []),
             *(['-Dtlb.probe.restorationDetails=true'] if args.restoration_details else []),
             *([f'-Dtlb.probe.width={args.width}', f'-Dtlb.probe.height={args.height}'] if args.attack_stages or args.world_effects or args.school_details or args.delivery_details or args.combat_details or args.restoration_details else []),
             '-cp', classpath, 'MagicRenderPearlProbe', str(ROOT), platform, str(dest)]),
    ]
    for label, exe, parameters in commands:
        argfile = dest / (label + '.args')
        argfile.write_text('\n'.join(json.dumps(p) for p in parameters), encoding='utf-8')
        result = subprocess.run([str(JDK / exe), '@' + str(argfile)], cwd=dest, capture_output=True, text=True, encoding='utf-8', errors='replace')
        log = result.stdout + result.stderr
        (dest / (label + '.log')).write_text(log, encoding='utf-8')
        if result.returncode:
            print('\n'.join(log.splitlines()[-45:]))
            raise SystemExit(result.returncode)
        if label == 'render':
            print('\n'.join(line for line in result.stdout.splitlines() if line.startswith(('ACTUAL_', 'PRODUCTION_', 'PASS '))))
