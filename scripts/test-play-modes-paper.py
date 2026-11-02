"""Real Paper play-mode/survival/PVE regression in the prepared .tools fixture."""
from pathlib import Path
import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
out = root / '.tools/modes-paper-probe'
out.mkdir(parents=True, exist_ok=True)
fixture = root / '.tools/smoke-26.3/server'
jdk = root / '.tools/jdk26-extract/jdk-26.0.2.1+1/bin'
production = root / 'server/build/libs/TeamLifeBind-0.7.0-server.jar'
overlay = {}
for base in (root / 'server/build/classes/java/main', root / 'common/build/classes/java/main'):
    for path in base.rglob('*.class'):
        relative = path.relative_to(base).as_posix()
        if path.name.startswith(('Gun', 'NativeHud')) and '/common/' in relative:
            continue
        overlay[relative] = path.read_bytes()
for base in (root / 'server/build/resources/main', root / 'common/src/main/resources'):
    for path in base.rglob('*'):
        if path.is_file():
            relative = path.relative_to(base).as_posix()
            data = path.read_bytes()
            if relative.startswith('teamlifebind/lang/') and path.suffix == '.properties':
                lines = data.decode('utf-8').splitlines()
                data = ('\n'.join(line for line in lines if not line.split('=', 1)[0].startswith(('gun.', 'rules.guns', 'book.weapons.', 'dev.menu.gun.', 'dev.menu.info.guns',
                    'item.battle_pistol.', 'item.tactical_smg.', 'item.assault_rifle.', 'item.breach_shotgun.', 'item.marksman_rifle.', 'item.support_machine_gun.', 'item.siege_launcher.'))) + '\n').encode('utf-8')
            overlay[relative] = data
assert 'com/teamlifebind/paper/TeamPveController.class' in overlay, 'Build :server:classes first'
targeted = out / 'targeted-server.jar'
with zipfile.ZipFile(io.BytesIO(production.read_bytes())) as old, zipfile.ZipFile(targeted, 'w', compression=zipfile.ZIP_DEFLATED) as new:
    for item in old.infolist():
        if item.filename not in overlay: new.writestr(item, old.read(item.filename))
    for name, data in overlay.items(): new.writestr(name, data)
classes = out / 'classes'
classes.mkdir(exist_ok=True)
classpath = os.pathsep.join(map(str, [targeted, fixture / 'versions/26.3/paper-26.3.jar', *(fixture / 'libraries').rglob('*.jar')]))
arguments = ['--release', '26', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes), str(root / 'scripts/probes/PaperPlayModesProbe.java')]
argfile = out / 'compile.args'
argfile.write_text('\n'.join(json.dumps(value) for value in arguments), encoding='utf-8')
compiled = subprocess.run([str(jdk / 'javac.exe'), '@' + str(argfile)], cwd=root, capture_output=True, text=True, encoding='utf-8', errors='replace')
(out / 'compile.log').write_text(compiled.stdout + compiled.stderr, encoding='utf-8')
if compiled.returncode:
    print(compiled.stdout + compiled.stderr)
    raise SystemExit(compiled.returncode)
(classes / 'plugin.yml').write_text("name: TLBPaperPlayModesProbe\nversion: '1.0'\nmain: PaperPlayModesProbe\napi-version: '26.3'\ndepend: [TeamLifeBind]\n", encoding='utf-8')
subprocess.run([str(jdk / 'jar.exe'), '--create', '--file', str(out / 'probe.jar'), '-C', str(classes), '.'], check=True)
files = [fixture / 'plugins/TeamLifeBind-server.jar', fixture / 'plugins/TLBPaperPlayModesProbe.jar',
         fixture / 'server.properties', fixture / 'bukkit.yml', fixture / 'spigot.yml', fixture / 'usercache.json',
         fixture / 'whitelist.json', fixture / 'ops.json', fixture / 'banned-players.json', fixture / 'banned-ips.json']
saved = {path: path.read_bytes() if path.exists() else None for path in files}
fixture = fixture.resolve()
backup = out / 'fixture-backup.zip'
directories = [fixture / 'config', fixture / 'plugins/TeamLifeBind']
directories += [path for path in fixture.iterdir() if path.is_dir() and (path / 'level.dat').exists()]
before_roots = {path.name for path in fixture.iterdir() if path.is_dir()}
def tree_hashes():
    return {path.relative_to(fixture).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
            for directory in directories for path in directory.rglob('*') if path.is_file()}
before = tree_hashes()
with zipfile.ZipFile(backup, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    for directory in directories:
        for path in directory.rglob('*'):
            if path.is_file(): archive.write(path, path.relative_to(fixture).as_posix())
try:
    shutil.copyfile(targeted, files[0]); shutil.copyfile(out / 'probe.jar', files[1])
    result = subprocess.run([sys.executable, str(root / '.tools/smoke-server.py'), '--cwd', str(fixture),
        '--log', str(out / 'actual-paper.log'), '--wait-probe', '--probe-marker', 'TLB_PAPER_PLAY_MODES_PROBE', '--',
        str(jdk / 'java.exe'), '-Xms512M', '-Xmx2G', '-jar', str(root / '.tools/paper-26.3-175.jar'), 'nogui'],
        cwd=root, capture_output=True, text=True, encoding='utf-8', errors='replace')
    (out / 'driver.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    print('\n'.join(line for line in result.stdout.splitlines() if 'PLAY_MODES_PROBE' in line or 'ready=' in line))
    if result.returncode: print(result.stderr[-1500:])
    evidence = {'targetedJarSha256': hashlib.sha256(targeted.read_bytes()).hexdigest(), 'exit': result.returncode,
        'classes': {name: hashlib.sha256(data).hexdigest() for name, data in overlay.items() if name.endswith('.class')}}
    (out / 'tested-classes.json').write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
finally:
    # A real server saves its worlds on shutdown; restore the complete isolated fixture.
    removed = list(directories)
    removed += [path for path in fixture.iterdir() if path.is_dir() and path.name not in before_roots
                and ((path / 'level.dat').exists() or path.name.startswith('tlb_'))]
    for path in removed:
        resolved = path.resolve()
        assert resolved != fixture and resolved.is_relative_to(fixture), f'Unsafe fixture restore target: {resolved}'
        if path.exists(): shutil.rmtree(path)
    with zipfile.ZipFile(backup) as archive: archive.extractall(fixture)
    for path, prior in saved.items():
        if prior is None: path.unlink(missing_ok=True)
        else: path.write_bytes(prior)
    assert all((not path.exists() if prior is None else path.read_bytes() == prior) for path, prior in saved.items())
    assert tree_hashes() == before, 'World/config restoration differs from the saved fixture'
    (out / 'fixture-restoration.json').write_text(json.dumps({'restored': True, 'files': len(before),
        'worlds': [path.name for path in directories if (path / 'level.dat').exists()],
        'before': before, 'after': tree_hashes()}, indent=2) + '\n', encoding='utf-8')
    print('fixturePluginWorldAndConfigRestored=true')
raise SystemExit(result.returncode)
