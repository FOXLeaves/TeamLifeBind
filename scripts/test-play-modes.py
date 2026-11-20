"""Run real Fabric SUR/PVE/PVP integration in the prepared isolated Java 26 fixture.

The complete fixture world and mode/magic configuration are restored byte for byte.
Use --compile-only to prepare the probe without starting a server or touching fixture data.
"""
from pathlib import Path
import argparse
import ctypes
import hashlib
import json
import os
import shutil
import socket
import subprocess
import sys
import uuid
import zipfile

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
sys.stderr.reconfigure(encoding='utf-8', errors='replace')

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--log-name', default='probe.log')
parser.add_argument('--compile-only', action='store_true')
args = parser.parse_args()
if Path(args.log_name).name != args.log_name:
    raise SystemExit('Use a plain log filename.')
out = root / '.tools/play-modes-regression'
out.mkdir(parents=True, exist_ok=True)
fixture = root / '.tools/smoke-26.3/fabric-server'
jdk = root / '.tools/jdk26-extract/jdk-26.0.2.1+1/bin'
cache = root / '.tools/gradle-upgrade-home/caches'
minecraft = cache / 'fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar'
launch = root / '.tools/fabric-java26-process.json'
production_classes = root / 'fabric/build/classes/java/main'
common_classes = root / 'common/build/classes/java/main'
required = (jdk / 'java.exe', jdk / 'javac.exe', minecraft, launch, fixture / 'server.properties',
            production_classes / 'com/teamlifebind/fabric/TeamPveController.class',
            common_classes / 'com/teamlifebind/common/PlayMode.class')
missing = [str(path) for path in required if not path.exists()]
if missing:
    raise SystemExit('Build common and Fabric classes and prepare the isolated fixture first: ' + ', '.join(missing))

dependencies = sorted(path for path in (cache / 'modules-2/files-2.1').rglob('*.jar')
                      if not path.name.endswith(('-sources.jar', '-javadoc.jar')))
classes = out / 'classes'
classes.mkdir(exist_ok=True)
cp = os.pathsep.join(map(str, [minecraft, production_classes, common_classes, *dependencies]))
compile_args = ['--release', '26', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
                str(root / 'scripts/probes/PlayModesProbe.java')]
argfile = out / 'compile.args'
argfile.write_text('\n'.join(json.dumps(arg) for arg in compile_args), encoding='utf-8')
compiled = subprocess.run([str(jdk / 'javac.exe'), '@' + str(argfile)], cwd=root,
                         capture_output=True, text=True, encoding='utf-8', errors='replace')
(out / 'compile.log').write_text(compiled.stdout + compiled.stderr, encoding='utf-8')
if compiled.returncode:
    print(compiled.stdout + compiled.stderr)
    raise SystemExit(compiled.returncode)
metadata = out / 'resources'
metadata.mkdir(exist_ok=True)
(metadata / 'fabric.mod.json').write_text(json.dumps({'schemaVersion': 1, 'id': 'tlb_play_modes_regression',
    'version': '1.0', 'environment': 'server', 'entrypoints': {'main': ['com.teamlifebind.fabric.PlayModesProbe']},
    'depends': {'fabricloader': '>=0.19.5', 'minecraft': '26.3', 'teamlifebind': '*'}}), encoding='utf-8')
jar = out / 'probe.jar'
subprocess.run([str(jdk / 'jar.exe'), '--create', '--file', str(jar), '-C', str(classes), '.', '-C', str(metadata), '.'], check=True)
if args.compile_only:
    print('PlayModesProbe compiled; no server started and no fixture data modified.')
    raise SystemExit(0)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def manifest(path):
    return {file.relative_to(path).as_posix(): sha(file.read_bytes())
            for file in sorted(path.rglob('*')) if file.is_file()}

# Never snapshot/delete a fixture directory while another native test is using it.
properties = dict(line.split('=', 1) for line in (fixture / 'server.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
port = int(properties.get('server-port', '25634'))
with socket.socket() as connection:
    connection.settimeout(.5)
    if connection.connect_ex(('127.0.0.1', port)) == 0:
        raise SystemExit(f'Isolated fixture port {port} is in use; wait for that test to stop.')
world = fixture / properties.get('level-name', 'world')
resolved_fixture = fixture.resolve()
resolved_world = world.resolve()
if resolved_world.parent != resolved_fixture or world.is_symlink():
    raise SystemExit('The test only restores one ordinary world directory directly inside its fixture.')
snapshot = out / ('world-backup-' + uuid.uuid4().hex)
if snapshot.resolve().parent != out.resolve():
    raise SystemExit('Invalid world backup path.')
target = fixture / 'mods/tlb-play-modes-regression.jar'
if target.exists():
    raise SystemExit('An existing play-modes probe is installed; inspect the fixture before retrying.')
files = [fixture / 'config/teamlifebind/settings.properties', fixture / 'config/teamlifebind-magic.properties']
saved = {path: path.read_bytes() if path.exists() else None for path in files}
existed = world.is_dir()
original_manifest = manifest(world) if existed else {}
if existed:
    shutil.copytree(world, snapshot)

count = ctypes.c_int()
parse = ctypes.windll.shell32.CommandLineToArgvW
parse.argtypes = [ctypes.c_wchar_p, ctypes.POINTER(ctypes.c_int)]
parse.restype = ctypes.POINTER(ctypes.c_wchar_p)
info = json.loads(launch.read_text(encoding='utf-8-sig'))
parsed = parse(info['CommandLine'], ctypes.byref(count))
try:
    java_args = [parsed[index] for index in range(count.value)]
finally:
    ctypes.windll.kernel32.LocalFree(parsed)
java_args = ['-Xms512M' if arg == '-Xms2G' else '-Xmx2G' if arg == '-Xmx4G' else arg for arg in java_args]
# A checkout may have been moved since the isolated launch command was captured.
captured_root = Path(info['ExecutablePath']).parents[4]
if captured_root != root:
    def rebase(value):
        for old, new in ((str(captured_root), str(root)), (captured_root.as_posix(), root.as_posix()),
                         (str(captured_root).replace('\\', '\\\\'), str(root).replace('\\', '\\\\'))):
            value = value.replace(old, new).replace(old.replace(' ', '@@0020'), new.replace(' ', '@@0020'))
            value = value.replace(old.replace(' ', '" "'), new.replace(' ', '" "'))
        return value
    java_args = [rebase(value) for value in java_args]
    for index, value in enumerate(java_args):
        if value.startswith('-Dfabric.dli.config='):
            config = Path(value.split('=', 1)[1].replace('@@0020', ' '))
            relocated = out / 'launch.cfg'
            relocated.write_text(rebase(config.read_text(encoding='utf-8')), encoding='utf-8')
            java_args[index] = '-Dfabric.dli.config=' + str(relocated).replace(' ', '@@0020')
        elif value.startswith('@'):
            relocated = out / f'launch-{index}.args'
            relocated.write_text(rebase(Path(value[1:]).read_text(encoding='utf-8')), encoding='utf-8')
            java_args[index] = '@' + str(relocated)
runtime_classes = {}
runtime_sources = {}
for base, package, names in [
    (production_classes, 'com/teamlifebind/fabric', ('TeamLifeBindFabric', 'TeamLifeBindFabricManager', 'TeamPveController', 'TeamRogueController', 'TeamSurvivalProfiles', 'TeamCampaignController', 'TeamMagicController', 'TeamUtilityController', 'TeamGunController', 'WorldVfx', 'NativeMenuTitle')),
    (production_classes, 'com/teamlifebind/fabric/mixin', ('PveArrowDamageMixin',)),
    (common_classes, 'com/teamlifebind/common', ('PlayMode', 'PveDifficulty', 'CampaignRules', 'PveWaveState', 'PveWaves', 'RogueRun', 'RogueBallot', 'GameOptions', 'RoundCampaignState', 'TeamLifeBindEngine')),
]:
    for name in names:
        for path in (base / package).glob(name + '*.class'):
            runtime_classes[path.relative_to(base).as_posix()] = sha(path.read_bytes())
        source = base.parents[3] / 'src/main/java' / package / (name + '.java')
        if source.is_file():
            runtime_sources[source.relative_to(root).as_posix()] = sha(source.read_bytes())

# The captured Fabric runtime loads common from its jar. Bind those bytes to the
# classes recorded above instead of assuming the development jar is current.
common_jar = root / 'common/build/libs/common-0.7.0.jar'
with zipfile.ZipFile(common_jar) as archive:
    for name, expected in runtime_classes.items():
        if name.startswith('com/teamlifebind/common/') and sha(archive.read(name)) != expected:
            raise SystemExit('Rebuild the common jar before running the play-modes fixture: ' + name)
common_jar_sha256 = sha(common_jar.read_bytes())

result = None
try:
    # Omit the new mode key deliberately so the original production load path proves the default.
    settings = files[0]
    settings.parent.mkdir(parents=True, exist_ok=True)
    text = (saved[settings] or b'').decode('utf-8')
    settings.write_text('\n'.join(line for line in text.splitlines()
        if not line.startswith(('play-mode=', 'match-mode=', 'pve-difficulty='))) + '\nmatch-mode=DRAGON_HUNT\n', encoding='utf-8')
    shutil.copyfile(jar, target)
    result = subprocess.run([sys.executable, str(root / '.tools/smoke-server.py'), '--cwd', str(fixture),
        '--log', str(out / args.log_name), '--wait-probe', '--probe-marker', 'TLB_PLAY_MODES_PROBE', '--', *java_args],
        cwd=root, capture_output=True, text=True, encoding='utf-8', errors='replace')
    (out / (args.log_name + '.driver.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
    print('\n'.join(line for line in result.stdout.splitlines() if 'PLAY_' in line or 'ready=' in line))
    if result.returncode:
        print(result.stderr[-2000:])
finally:
    target.unlink(missing_ok=True)
    for path, prior in saved.items():
        if prior is None:
            path.unlink(missing_ok=True)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(prior)
    # Both absolute targets were checked above; restore only this isolated fixture's world.
    if world.exists():
        if world.resolve() != resolved_world:
            raise RuntimeError('World path changed during test; refusing to remove it.')
        shutil.rmtree(world)
    if existed:
        shutil.copytree(snapshot, world)
        if manifest(world) != original_manifest:
            raise RuntimeError('The isolated world did not restore byte for byte.')
    if snapshot.exists():
        if snapshot.resolve().parent != out.resolve():
            raise RuntimeError('Invalid backup path at cleanup.')
        shutil.rmtree(snapshot)
    if any((path.exists() if prior is None else not path.exists() or path.read_bytes() != prior)
           for path, prior in saved.items()):
        raise RuntimeError('The isolated configuration did not restore byte for byte.')
    print('fixtureWorldAndConfigurationRestored=true')

evidence = {'runtimeClasses': runtime_classes, 'runtimeSources': runtime_sources,
            'commonJarSha256': common_jar_sha256,
            'probeSourceSha256': sha((root / 'scripts/probes/PlayModesProbe.java').read_bytes()),
            'fixtureWorldRestored': True, 'configurationRestored': True, 'exit': result.returncode}
(out / 'tested-classes.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
raise SystemExit(result.returncode)
