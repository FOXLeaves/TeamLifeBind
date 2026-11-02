"""Real Fabric command, restart persistence and HUD regression in an isolated prepared server.

Build :common:jar and :fabric:classes first. Requires the existing .tools/smoke-26.3
fixture and its captured Java 26 launcher; no downloads and no user worlds are modified.
"""
from pathlib import Path
import argparse
import ctypes
import json
import os
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--log-name', default='probe.log')
args = parser.parse_args()
assert Path(args.log_name).name == args.log_name
out = root / '.tools/magic-teststop-regression'
out.mkdir(parents=True, exist_ok=True)
fixture = root / '.tools/smoke-26.3/fabric-server'
jdk = root / '.tools/jdk26-extract/jdk-26.0.2.1+1/bin'
cache = root / '.tools/gradle-upgrade-home/caches'
minecraft = cache / 'fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar'
launch = root / '.tools/fabric-java26-process.json'
required = (jdk / 'java.exe', jdk / 'javac.exe', minecraft, launch, fixture / 'server.properties',
            root / 'fabric/build/classes/java/main/com/teamlifebind/fabric/TeamMagicController.class')
missing = [str(path) for path in required if not path.exists()]
if missing:
    raise SystemExit('Prepare the Java 26 isolated Fabric fixture and build :fabric:classes first. Missing: ' + ', '.join(missing))
dependencies = sorted(path for path in (cache / 'modules-2/files-2.1').rglob('*.jar')
                      if not path.name.endswith(('-sources.jar', '-javadoc.jar')))
classes = out / 'classes'
classes.mkdir(exist_ok=True)
cp = os.pathsep.join(map(str, [minecraft, root / 'fabric/build/classes/java/main', root / 'common/build/classes/java/main', *dependencies]))
compile_args = ['--release', '26', '-proc:none', '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
                str(root / 'scripts/probes/MagicTestStopProbe.java')]
argfile = out / 'compile.args'
argfile.write_text('\n'.join(json.dumps(arg) for arg in compile_args), encoding='utf-8')
result = subprocess.run([str(jdk / 'javac.exe'), '@' + str(argfile)], cwd=root, capture_output=True, text=True, encoding='utf-8', errors='replace')
(out / 'compile.log').write_text(result.stdout + result.stderr, encoding='utf-8')
if result.returncode:
    print(result.stdout + result.stderr)
    raise SystemExit(result.returncode)
metadata = out / 'resources'
metadata.mkdir(exist_ok=True)
(metadata / 'fabric.mod.json').write_text(json.dumps({'schemaVersion': 1, 'id': 'tlb_magic_teststop_regression',
    'version': '1.0', 'environment': 'server', 'entrypoints': {'main': ['com.teamlifebind.probe.MagicTestStopProbe']},
    'depends': {'fabricloader': '>=0.19.5', 'minecraft': '26.3', 'teamlifebind': '*'}}), encoding='utf-8')
jar = out / 'probe.jar'
subprocess.run([str(jdk / 'jar.exe'), '--create', '--file', str(jar), '-C', str(classes), '.', '-C', str(metadata), '.'], check=True)
count = ctypes.c_int()
parse = ctypes.windll.shell32.CommandLineToArgvW
parse.argtypes = [ctypes.c_wchar_p, ctypes.POINTER(ctypes.c_int)]
parse.restype = ctypes.POINTER(ctypes.c_wchar_p)
info = json.loads(launch.read_text(encoding='utf-8-sig'))
parsed = parse(info['CommandLine'], ctypes.byref(count))
try: java_args = [parsed[index] for index in range(count.value)]
finally: ctypes.windll.kernel32.LocalFree(parsed)
target = fixture / 'mods/tlb-magic-teststop-regression.jar'
if target.exists(): raise SystemExit('An existing test probe is installed; inspect the fixture before retrying.')
persistence = fixture / 'config/teamlifebind-magic.properties'
saved = persistence.read_bytes() if persistence.exists() else None
try:
    shutil.copyfile(jar, target)
    result = subprocess.run([sys.executable, str(root / '.tools/smoke-server.py'), '--cwd', str(fixture),
        '--log', str(out / args.log_name), '--wait-probe', '--probe-marker', 'TLB_MAGIC_TESTSTOP_PROBE', '--', *java_args],
        cwd=root, capture_output=True, text=True, encoding='utf-8', errors='replace')
    (out / (args.log_name + '.driver.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
    print('\n'.join(line for line in result.stdout.splitlines() if 'MANA_RESTART_' in line or 'TLB_MAGIC_TESTSTOP_PROBE' in line or 'ready=' in line))
    if result.returncode: print(result.stderr[-1500:])
finally:
    target.unlink(missing_ok=True)
    if saved is None: persistence.unlink(missing_ok=True)
    else: persistence.write_bytes(saved)
raise SystemExit(result.returncode)
