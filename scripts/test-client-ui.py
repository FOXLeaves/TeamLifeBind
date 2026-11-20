"""Exercise native menu callbacks with real MC classes and SDL input values; no game window."""
from pathlib import Path
import hashlib, json, os, subprocess, sys
root = Path(__file__).resolve().parents[1]
out = root / '.tools/client-ui-input'
jdk = root / '.tools/jdk26-extract/jdk-26.0.2.1+1/bin'
if hasattr(sys.stdout, 'reconfigure'): sys.stdout.reconfigure(encoding='utf-8')
evidence = {'platforms': {}, 'clientRendered': False}
capture = root / '.tools/play-modes-regression/lobby-menu.json'
if not capture.is_file(): raise SystemExit('Run scripts/test-play-modes.py to capture the real lobby menu first.')
evidence['lobbyCaptureSha256'] = hashlib.sha256(capture.read_bytes()).hexdigest()
for platform in sys.argv[1:] or ['fabric', 'forge', 'neoforge']:
    old = [json.loads(line) for line in (root / f'.tools/client-gun-mixin-probe/java-{platform}.args').read_text(encoding='utf-8').splitlines()]
    paths = old[old.index('-classpath') + 1].split(os.pathsep)
    # Probe arguments can outlive a workspace rename. Keep their relative artifact paths.
    paths = [str(root / p.replace('\\', '/').split('/TeamLifeBind/', 1)[1])
             if '/TeamLifeBind/' in p.replace('\\', '/') else p for p in paths]
    paths = [p for p in paths if 'client-gun-mixin-probe/classes' not in p.replace('\\', '/')]
    classes = out / platform
    classes.mkdir(parents=True, exist_ok=True)
    vanilla = root / '.tools/gradle-upgrade-home/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar'
    # Forge's recompiled classes omit vanilla language assets. Append those resources after
    # its actual patched classes so the API under test is still Forge's implementation.
    cp = os.pathsep.join(map(str, [classes, *(root / '.tools/smoke-26.3/server/libraries').rglob('*.jar'), *paths, vanilla]))
    runtime = cp
    if platform == 'neoforge':
        runtime = os.pathsep.join([str(classes), str(vanilla), cp])
    for label, exe, args in [
        ('compile', 'javac.exe', ['-encoding', 'UTF-8', '-proc:none', '--release', '26', '-cp', cp, '-d', str(classes), str(root / 'scripts/probes/NativeUiProbe.java')]),
        ('probe', 'java.exe', ['-Djava.awt.headless=true', '-cp', runtime, 'NativeUiProbe', platform, str(capture)]),
    ]:
        argfile = classes / (label + '.args')
        argfile.write_text('\n'.join(json.dumps(arg) for arg in args), encoding='utf-8')
        result = subprocess.run([str(jdk / exe), '@' + str(argfile)], cwd=classes, capture_output=True, text=True, encoding='utf-8', errors='replace')
        log = result.stdout + result.stderr
        (classes / (label + '.log')).write_text(log, encoding='utf-8')
        if result.returncode:
            print(log[-6500:]); sys.exit(result.returncode)
        if label == 'probe': print('\n'.join(line for line in log.splitlines() if 'NATIVE_UI_INPUT_PASS' in line or 'NATIVE_LOBBY_LAYOUT_PASS' in line or 'AUI_SERVER_MENU_BRIDGE_PASS' in line))
    hashes = {}
    for folder, prefix in [(root / platform / 'build/classes/java/main', f'com/teamlifebind/{platform}'), (root / 'common/build/classes/java/main', 'com/teamlifebind/common')]:
        for path in (folder / prefix).glob('Native*.class'):
            hashes[path.relative_to(folder).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    evidence['platforms'][platform] = {'classes': hashes, 'runtime': 'vanilla26.3-api-fixture' if platform == 'neoforge' else 'actual-platform-minecraft-classes'}
(out / 'tested-classes.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
print('Native client input checks passed; NeoForge standalone API fixture does not claim a live FML client.')
