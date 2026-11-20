"""Run the HTML interface against an isolated loopback server and its own world.

Uses port 25587, .tools/archive-e2e and Java 26. Never opens an existing user world.
The fixture server is stopped in finally, including when the client test fails.
"""
from pathlib import Path
import hashlib
import argparse
import json
import os
import re
import subprocess
import time
import shutil

ROOT = Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--platform',choices=('fabric','forge','neoforge'),default='fabric')
parser.add_argument('--render-mods',choices=('sodium','iris'))
parser.add_argument('--shader-pack',choices=('fixture','complementary'),default='fixture')
parser.add_argument('--render-only',action='store_true',help='Fast world rendering repro; skips the separate 22-container protocol cases')
parser.add_argument('--chat-only',action='store_true',help='Actual native chat, resize and font checks; skips the separate 22-container protocol cases')
parser.add_argument('--interaction-only',action='store_true',help='Actual spell selection, 3D preview, HUD editor, debug modes and contextual settings')
parser.add_argument('--gun-workshop', action='store_true', help='Fit, swap, remove and fire a weapon in all three camera views')
args=parser.parse_args();platform=args.platform
if args.render_mods and platform=='forge':parser.error('Official Sodium/Iris builds target Fabric and NeoForge')
if args.shader_pack!='fixture' and args.render_mods!='iris':parser.error('--shader-pack=complementary requires --render-mods=iris')
if args.gun_workshop:
    if args.render_mods: parser.error('--gun-workshop uses the native camera fixture')
    args.interaction_only = True
if args.render_only and not args.render_mods:parser.error('--render-only requires --render-mods')
if sum((args.render_only,args.chat_only,args.interaction_only))>1:parser.error('Choose a single focused test mode')
RUN = ROOT / '.tools/archive-e2e'
OUT = ROOT / f'.tools/archive-e2e-evidence/{platform}'
if args.render_mods:
    label=args.render_mods+('-'+args.shader_pack if args.shader_pack!='fixture' else '')
    if args.render_only:label+='-render-only'
    RUN=ROOT/f'.tools/archive-compat-world/{label}'
    OUT=ROOT/f'.tools/archive-compat-world-evidence/{label}/{platform}'
SERVER = RUN / f'{platform}-server'
CLIENT = RUN / f'{platform}-client'
env = dict(os.environ)
env['JAVA_TOOL_OPTIONS']=env.get('JAVA_TOOL_OPTIONS','')+f' -Dteamlifebind.archive.platform={platform}'
if args.gun_workshop:env['JAVA_TOOL_OPTIONS']+=' -Dteamlifebind.gun.workshop.test=true'
if args.render_only:env['JAVA_TOOL_OPTIONS']=env.get('JAVA_TOOL_OPTIONS','')+' -Dteamlifebind.render.compat.only=true'
if args.chat_only:env['JAVA_TOOL_OPTIONS']=env.get('JAVA_TOOL_OPTIONS','')+' -Dteamlifebind.archive.chat.only=true'
if args.interaction_only:env['JAVA_TOOL_OPTIONS']=env.get('JAVA_TOOL_OPTIONS','')+' -Dteamlifebind.archive.interaction.only=true'
local_jdk = ROOT / '.tools/jdk26-extract/jdk-26.0.2.1+1'
if local_jdk.is_dir():env['JAVA_HOME'] = str(local_jdk)
env['GRADLE_USER_HOME'] = str(ROOT / '.tools/gradle-upgrade-home')
gradle = str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
base = [gradle, f'-Pplatforms={platform}', f'-Pdev_run_root={RUN.relative_to(ROOT).as_posix()}']
if args.render_mods:
    base.extend([f'-ParchiveEvidenceRoot={OUT.parent.relative_to(ROOT).as_posix()}',f'-PrenderCompatMode={args.render_mods}'])
base.extend(['--max-workers=1','-Dorg.gradle.parallel=false','-Dorg.gradle.jvmargs=-Xms256m -Xmx2g -XX:ActiveProcessorCount=3'])
if platform == 'neoforge' and os.getenv('TLB_NEOFORM_CACHED') == '1':
    cache=json.loads((ROOT/'.tools/archive-neoforge-cache-evidence.json').read_text())
    artifact=ROOT/'neoforge/build/moddev/artifacts/minecraft-patched-26.3.0.71-beta-merged.jar'
    assert cache['membersIdentical'] and hashlib.sha256(artifact.read_bytes()).hexdigest()==cache['sha256'], 'Unverified NeoForm cache'
    base.extend(['-x', ':neoforge:createMinecraftArtifacts'])
for folder in (OUT, SERVER, CLIENT, SERVER / 'config/teamlifebind'):
    folder.mkdir(parents=True, exist_ok=True)
if args.render_mods:
    import importlib.util
    spec=importlib.util.spec_from_file_location('prepare_render_compat',ROOT/'scripts/prepare-render-compat.py')
    setup=importlib.util.module_from_spec(spec);spec.loader.exec_module(setup)
    render_mods=setup.prepare(platform,args.render_mods,CLIENT)
    if args.render_mods=='iris':
        shader_pack=setup.shader_pack(CLIENT,args.shader_pack)
        (CLIENT/'config').mkdir(exist_ok=True)
        (CLIENT/'config/iris.properties').write_text('shaderPack='+shader_pack['filename']+'\nenableShaders=false\n',encoding='ascii')
(SERVER / 'eula.txt').write_text('eula=true\n', encoding='ascii')
(SERVER / 'server.properties').write_text('''server-ip=127.0.0.1
server-port=25587
online-mode=false
enforce-secure-profile=false
white-list=false
enable-rcon=false
enable-query=false
management-server-enabled=false
level-name=archive-ui-fixture
level-type=minecraft:flat
generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":3}],"features":false}
gamemode=survival
difficulty=peaceful
spawn-protection=0
view-distance=2
simulation-distance=2
max-players=2
''', encoding='ascii')
(SERVER / 'config/teamlifebind/settings.properties').write_text('play-mode=PVP\nscoreboard-enabled=false\n', encoding='ascii')
layout_file=CLIENT/'config/teamlifebind-hud-layout.properties'
assert layout_file.resolve().is_relative_to(RUN.resolve())
layout_backup=layout_file.read_bytes() if layout_file.exists() else None
layout_file.unlink(missing_ok=True)
options = CLIENT / 'options.txt'
lines = options.read_text(encoding='utf-8').splitlines() if options.exists() else []
lines = [line for line in lines if not line.startswith(('lang:', 'onboardAccessibility:', 'tutorialStep:'))]
options.write_text('\n'.join([*lines, 'lang:zh_cn', 'onboardAccessibility:false', 'tutorialStep:none'])+'\n', encoding='utf-8')
for name in ('ready.txt', 'prepared.txt', 'result.txt', 'brewing-request.txt', 'brewing-prepared.txt','compat-wall-request.txt','compat-wall-ready.txt','interaction-request.json','interaction-ack.txt'):
    (OUT / name).unlink(missing_ok=True)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sources():
    files = [*ROOT.glob('client-ui/**/*.java'), *ROOT.glob(f'{platform}/src/main/java/**/*.java'),
             *(p for p in ROOT.glob(f'{platform}/src/main/resources/**/*') if p.is_file()),
             *(p for p in ROOT.glob('client-ui/src/main/resources/**/*') if p.is_file()),
             ROOT / 'gradle/aui.gradle']
    files.append(Path(__file__))
    if args.render_mods:
        files.extend([ROOT/'scripts/prepare-render-compat.py',ROOT/'scripts/fixtures/render-compat-lock.json',
                      *ROOT.glob('scripts/fixtures/render-compat-shaders/*')])
    return {p.relative_to(ROOT).as_posix(): sha(p) for p in files}


def wait_for(test, process, seconds, label):
    deadline = time.monotonic()+seconds
    while not test():
        if process.poll() is not None:
            raise RuntimeError(f'{label}: process exited with {process.returncode}')
        if time.monotonic() > deadline:
            raise TimeoutError(label)
        time.sleep(.25)


def stop(process, server=False):
    if process is None or process.poll() is not None:
        return
    if server:
        try:
            process.stdin.write('stop\n')
            process.stdin.flush()
            process.wait(timeout=30)
            return
        except (BrokenPipeError, OSError, subprocess.TimeoutExpired):
            pass
    if os.name == 'nt':
        subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    else:
        process.terminate()


started = time.time()
tested = sources()
server_process = client_process = None
server_log = OUT / 'server.log'
client_log = OUT / 'client.log'
try:
    with server_log.open('w', encoding='utf-8') as log:
        server_process = subprocess.Popen([*base, f':{platform}:runServer', '--offline', '--console=plain'], cwd=ROOT, env=env,
                                          stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True, encoding='utf-8')
        wait_for(lambda: 'Done (' in server_log.read_text(encoding='utf-8', errors='replace'), server_process, 180, 'Server startup')
        server_process.stdin.write('save-all flush\n');server_process.stdin.flush()
        wait_for(lambda: 'Saved the game' in server_log.read_text(encoding='utf-8',errors='replace') and (SERVER/'archive-ui-fixture/data/minecraft').is_dir(),server_process,60,'Persisting isolated selector world data')
        selector_world=CLIENT/'saves/ArchiveSelectorFixture'
        assert selector_world.resolve().is_relative_to(RUN.resolve()) and selector_world.name=='ArchiveSelectorFixture'
        if selector_world.exists():shutil.rmtree(selector_world)
        selector_world.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(SERVER/'archive-ui-fixture/level.dat',selector_world/'level.dat')
        shutil.copytree(SERVER/'archive-ui-fixture/data/minecraft',selector_world/'data/minecraft',dirs_exist_ok=True)

        def command(text):
            server_process.stdin.write(text+'\n')
            server_process.stdin.flush()

        command('execute in minecraft:overworld run forceload add -16 -16 16 16')
        command('execute in minecraft:overworld run time set 6000')
        command('execute in minecraft:overworld run weather clear')
        time.sleep(1)
        command('execute in minecraft:overworld run fill -4 99 -4 4 99 4 minecraft:stone')
        for x, z, block in ((1,0,'chest'),(2,0,'crafting_table'),(3,0,'anvil'),(1,1,'enchanting_table'),
                            (2,1,'loom'),(3,1,'stonecutter'),(1,2,'furnace'),(2,2,'brewing_stand'),(3,2,'smithing_table'),
                            (-1,0,'grindstone'),(-2,0,'cartography_table'),(-1,1,'beacon'),(-2,1,'crafter'),(-1,2,'lectern')):
            command(f'execute in minecraft:overworld run setblock {x} 100 {z} minecraft:air')
            command(f'execute in minecraft:overworld run setblock {x} 100 {z} minecraft:{block}')
        command('execute in minecraft:overworld run fill -2 99 0 0 99 2 minecraft:iron_block')
        command('execute in minecraft:overworld run setblock -1 100 2 minecraft:lectern[has_book=true]{Book:{id:"minecraft:written_book",count:1,components:{"minecraft:written_book_content":{title:"UI fixture",author:"TLB",pages:["TLB reading fixture: this page must remain visible."]}}}}')
        with client_log.open('w', encoding='utf-8') as client_stream:
            client_process = subprocess.Popen([*base, '-ParchiveE2E', f':{platform}:runClient', '--offline', '--console=plain'],
                                              cwd=ROOT, env=env, stdout=client_stream, stderr=subprocess.STDOUT)
            wait_for(lambda: (OUT / 'ready.txt').exists(), client_process, 300, 'Client entering local world, then joining loopback fixture through selectors')
            player = (OUT / 'ready.txt').read_text(encoding='utf-8').strip()
            assert re.fullmatch(r'[A-Za-z0-9_]+', player), 'Unexpected fixture player name'
            for text in (f'op {player}',f'gamemode survival {player}', f'clear {player}', f'execute in minecraft:overworld run tp {player} 0 100 0 225 22',
                         'execute in minecraft:overworld run item replace block 1 100 0 container.0 with minecraft:diamond 16',
                         'execute in minecraft:overworld run item replace block 1 100 2 container.0 with minecraft:iron_ore 64',
                         'execute in minecraft:overworld run item replace block 1 100 2 container.1 with minecraft:coal 64',
                         f'experience set {player} 30 levels',
                         f'item replace entity {player} inventory.20 with minecraft:oak_log 3',
                         f'recipe give {player} *',
                         f'item replace entity {player} armor.head with minecraft:iron_helmet',
                         f'item replace entity {player} armor.chest with minecraft:iron_chestplate',
                         f'item replace entity {player} armor.legs with minecraft:iron_leggings',
                         f'item replace entity {player} armor.feet with minecraft:iron_boots',
                         'bossbar add tlb:archive_fixture {"text":"星辉守望 / UI Test"}',
                         'bossbar set tlb:archive_fixture max 100',
                         'bossbar set tlb:archive_fixture value 67',
                         f'bossbar set tlb:archive_fixture players {player}',
                         'scoreboard objectives add tlb_ui_fixture dummy {"text":"远征状态 / UI Test"}',
                         'scoreboard players reset * tlb_ui_fixture',
                         'scoreboard objectives setdisplay sidebar tlb_ui_fixture',
                         f'scoreboard players set {player} tlb_ui_fixture 3'):
                command(text)
            time.sleep(1)
            (OUT / 'prepared.txt').write_text('Loopback fixture ready; survival inventory', encoding='ascii')
            deadline=time.monotonic()+(600 if args.render_mods else 300)
            brewing_prepared=False;wall_prepared=False;interaction_id=None
            while client_process.poll() is None:
                if time.monotonic()>deadline:raise TimeoutError('Loopback UI protocol checks')
                interaction_request=OUT/'interaction-request.json'
                if interaction_request.exists():
                    try:request=json.loads(interaction_request.read_text(encoding='utf-8'))
                    except json.JSONDecodeError:request=None
                    if request and request['id']!=interaction_id:
                        for text in request['commands']:command(text.replace('$PLAYER',player))
                        interaction_id=request['id']
                        (OUT/'interaction-ack.txt').write_text(interaction_id,encoding='utf-8')
                if not brewing_prepared and (OUT/'brewing-request.txt').exists():
                    for slot,stack in ((0,'minecraft:potion[minecraft:potion_contents={potion:"minecraft:water"}]'),
                                       (3,'minecraft:nether_wart 16'),(4,'minecraft:blaze_powder 16')):
                        command(f'execute in minecraft:overworld run item replace block 2 100 2 container.{slot} with {stack}')
                    (OUT/'brewing-prepared.txt').write_text('Brewing items sent through server console',encoding='ascii')
                    brewing_prepared=True
                if not wall_prepared and (OUT/'compat-wall-request.txt').exists():
                    command('execute in minecraft:overworld run fill -4 99 4 4 106 4 minecraft:stone')
                    time.sleep(.5)
                    (OUT/'compat-wall-ready.txt').write_text('Foreground wall placed by the real server',encoding='ascii')
                    wall_prepared=True
                time.sleep(.2)
        result = OUT / 'result.txt'
        assert client_process.returncode == 0 and result.exists() and result.stat().st_mtime >= started, f'Client failed: {client_log}'
        report = result.read_text(encoding='utf-8')
        assert 'FAIL' not in report and 'PASS loopback server UI' in report, report
        assert 'PASS single-player:' in report and 'PASS multiplayer:' in report, 'Native world/server selector entry was not exercised'
        assert tested == sources(), 'Sources changed during protocol testing'
        captures = {p.name: sha(p) for p in OUT.glob('world-*.png') if p.stat().st_mtime >= started}
        assert len(captures) == (0 if args.render_only or args.chat_only or args.interaction_only else 22), f'Incomplete world captures: {captures.keys()}'
        chat_captures={p.name:sha(p) for p in OUT.glob('chat-*.png') if p.stat().st_mtime>=started}
        if not args.render_only:
            assert 'PASS CHAT_PREVIEW' in report and 'PASS CHAT_CARET' in report and len(chat_captures)==5, 'Missing actual HUD chat/font/history/caret checks'
        data={'platform':platform,'realClient': True, 'loopbackServer': True, 'world': 'isolated archive-ui-fixture',
            'survivalInventory': True, 'runnerSha256': sha(Path(__file__)), 'resultSha256': sha(result), 'sources': tested, 'captures': captures,'chatCaptures':chat_captures,'chatOnly':args.chat_only,'interactionOnly':args.interaction_only}
        if args.gun_workshop:
            for marker in ('GUN_WORKSHOP','GUN_VIEW FIRST_PERSON','GUN_VIEW THIRD_PERSON_BACK','GUN_VIEW THIRD_PERSON_FRONT'):
                assert 'PASS ' + marker in report, marker
        elif not args.render_only and not args.chat_only:
            for marker in ('MAGIC_SELECTION','GAMEMODE','HUD_EDITOR','CAMPAIGN','COUNTDOWN','NOTIFICATION_LAYOUT'):assert f'PASS {marker}' in report, f'Missing interaction proof: {marker}'
            data['interactionCaptures']={p.name:sha(p) for p in OUT.glob('interaction-*.png') if p.stat().st_mtime>=started}
        if args.render_mods:
            assert 'PASS RENDER_COMPAT_WORLD' in report,'Missing real world render compatibility check'
            data['renderMods']=render_mods
            if args.render_mods=='iris':data['shaderPack']=shader_pack
            data['renderCaptures']={p.name:sha(p) for p in OUT.glob('compat-*.png') if p.stat().st_mtime>=started}
        (OUT / 'evidence.json').write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
        if not args.render_only and not args.chat_only and not args.interaction_only:
            assert 'PASS native Escape' in report and 'PASS workspace furnace' in report and 'PASS workspace brewing' in report
        print(f'PASS {platform} loopback: '+('spell selection, GPU 3D preview, debug modes, HUD editor, contextual settings, countdown, notifications and chat caret' if args.interaction_only else 'chat, native history, real window resize, fonts and resource refresh; 5 chat captures' if args.chat_only else 'focused real-world render compatibility' if args.render_only else 'inventory protocol, recipes, named workspaces, live smelting/brewing, lectern, resource refresh and interactions; 22 captures'),flush=True)
finally:
    stop(client_process)
    stop(server_process, server=True)
    if layout_backup is None:layout_file.unlink(missing_ok=True)
    else:layout_file.write_bytes(layout_backup)
