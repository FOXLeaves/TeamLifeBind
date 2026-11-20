"""Check preview transparency in the final native GUI compositor in an isolated client."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,time

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('platform',choices=('fabric','forge','neoforge'),default='fabric',nargs='?')
args=parser.parse_args();platform=args.platform
run=ROOT/'.tools/sigil-alpha-run';out=ROOT/f'.tools/sigil-alpha-evidence/{platform}'
out.mkdir(parents=True,exist_ok=True)
game=run/f'{platform}-client';(game/'config').mkdir(parents=True,exist_ok=True)
(game/'config/teamlifebind-ui.properties').write_text('motion=false\n',encoding='ascii')
(game/'options.txt').write_text('lang:zh_cn\nonboardAccessibility:false\nfullscreen:false\n',encoding='ascii')
(out/'result.txt').unlink(missing_ok=True)
env=dict(os.environ);env['JAVA_HOME']=str(ROOT/'.tools/jdk26-extract/jdk-26.0.2.1+1');env['GRADLE_USER_HOME']=str(ROOT/'.tools/gradle-upgrade-home')
env['JAVA_TOOL_OPTIONS']=env.get('JAVA_TOOL_OPTIONS','')+f' -Dteamlifebind.sigil.selftest=true -Dteamlifebind.sigil.output="{out}" -Dteamlifebind.archive.e2e.output="{out}"'
command=[str(ROOT/'gradlew.bat'),f'-Pplatforms={platform}',f'-Pdev_run_root={run.relative_to(ROOT).as_posix()}',f':{platform}:runClient','--offline','--console=plain','--max-workers=1','-Dorg.gradle.parallel=false','-Dorg.gradle.jvmargs=-Xms256m -Xmx2g -XX:ActiveProcessorCount=3']
if platform=='neoforge' and os.getenv('TLB_NEOFORM_CACHED')=='1':
    cache=json.loads((ROOT/'.tools/archive-neoforge-cache-evidence.json').read_text())
    artifact=ROOT/'neoforge/build/moddev/artifacts/minecraft-patched-26.3.0.71-beta-merged.jar'
    assert cache['membersIdentical'] and hashlib.sha256(artifact.read_bytes()).hexdigest()==cache['sha256']
    command.extend(['-x',':neoforge:createMinecraftArtifacts'])
sources=[*ROOT.glob('client-ui/**/*.java'),*(p for p in ROOT.glob('client-ui/src/main/resources/**/*') if p.is_file()),Path(__file__)]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
before={p.relative_to(ROOT).as_posix():sha(p) for p in sources};started=time.time()
with (out/'client.log').open('w',encoding='utf-8') as log:
    process=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=240)
result=out/'result.txt'
assert process.returncode==0 and result.exists() and result.stat().st_mtime>=started,f'Client failed: {out/"client.log"}'
report=result.read_text(encoding='utf-8');print(report)
assert 'FAIL' not in report and 'SIGIL_ALPHA_PASS' in report,report
assert before=={p.relative_to(ROOT).as_posix():sha(p) for p in sources},'Sources changed during the GPU regression'
(out/'evidence.json').write_text(json.dumps({'platform':platform,'sources':before,'resultSha256':sha(result),'realClient':True,'finalCompositor':True},indent=2)+'\n',encoding='utf-8')
