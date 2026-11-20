"""Fast intrinsic-layout regression, using the compiled engine and actual MC dependencies."""
from pathlib import Path
import json
import os
import subprocess
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'.tools/aui-flex-layout'
OUT.mkdir(parents=True,exist_ok=True)
saved=[json.loads(line) for line in (ROOT/'.tools/client-ui-input/forge/compile.args').read_text().splitlines()]
classpath=str(OUT)+os.pathsep+saved[saved.index('-cp')+1]
jdk=ROOT/'.tools/jdk26-extract/jdk-26.0.2.1+1/bin'
for step,args in [('compile',['-encoding','UTF-8','-proc:none','--release','26','-cp',classpath,'-d',str(OUT),str(ROOT/'scripts/probes/AuiFlexProbe.java')]),
                  ('run',['-Djava.awt.headless=true','-Dteamlifebind.archive.layoutDiagnostics=true','-cp',classpath,'AuiFlexProbe',str(ROOT/'client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind')])]:
    path=OUT/(step+'.args');path.write_text('\n'.join(json.dumps(a) for a in args),encoding='utf-8')
    result=subprocess.run([str(jdk/('javac.exe' if step=='compile' else 'java.exe')),'@'+str(path)],cwd=OUT,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=45)
    log=result.stdout+result.stderr;(OUT/(step+'.log')).write_text(log,encoding='utf-8')
    print(log[-2200:])
    if result.returncode:raise SystemExit(result.returncode)
