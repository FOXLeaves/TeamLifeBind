"""Offline regression checks for menu interaction; never launches Minecraft or connects."""
from pathlib import Path
import json
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '.tools/archive-usability'
OUT.mkdir(parents=True, exist_ok=True)
saved = [json.loads(line) for line in (ROOT / '.tools/client-ui-input/forge/compile.args').read_text().splitlines()]
classpath = str(OUT) + os.pathsep + saved[saved.index('-cp') + 1]
jdk = ROOT / '.tools/jdk26-extract/jdk-26.0.2.1+1/bin'
steps = [
    ('compile', 'javac.exe', ['-encoding', 'UTF-8', '-proc:none', '--release', '26', '-cp', classpath,
                            '-d', str(OUT), str(ROOT / 'scripts/probes/ArchiveUsabilityProbe.java'),
                            str(ROOT / 'scripts/probes/ArchiveFontProbe.java'),
                            str(ROOT / 'scripts/probes/ArchiveMenuModelProbe.java'),
                            str(ROOT / 'scripts/probes/ArchiveReadinessProbe.java'),
                            str(ROOT / 'scripts/probes/ArchiveWorkspaceLayoutProbe.java'),
                            str(ROOT / 'scripts/probes/ArchiveChatLayoutProbe.java'),
                            str(ROOT / 'scripts/probes/HudLayoutProbe.java'),
                            str(ROOT / 'scripts/probes/ArchiveOverlayLayoutProbe.java'),
                            str(ROOT / 'scripts/probes/CampaignMenuPolicyProbe.java'),
                            str(ROOT / 'scripts/probes/AuiInkTransitionProbe.java'),
                            *(str(ROOT / 'client-ui/src/main/java/com/teamlifebind/clientui' / (name + '.java'))
                              for name in ('ArchiveRefreshable', 'ArchiveModel', 'ArchiveRecipes', 'ArchivePolicy', 'ArchiveNavigation', 'AuiPageReadiness', 'ArchiveLoading', 'AuiInkTransition', 'ArchiveChatFeed', 'HudLayoutPreferences', 'HudLayout', 'ArchiveOverlayLayout', 'ArchiveNoticeQueue')),
                            str(ROOT / 'common/src/main/java/com/teamlifebind/common/CampaignMenuPolicy.java')]),
    ('ink-timing', 'java.exe', ['-Djava.awt.headless=true', '-cp', classpath,
                          'com.teamlifebind.clientui.AuiInkTransitionProbe']),
    ('model', 'java.exe', ['-Djava.awt.headless=true', '-XX:ActiveProcessorCount=2', '-Xmx384m', '-cp', classpath,
                          'com.teamlifebind.clientui.ArchiveMenuModelProbe']),
    ('fonts', 'java.exe', ['-Djava.awt.headless=true', '-XX:ActiveProcessorCount=2', '-Xmx384m', '-cp', classpath,
                          'ArchiveFontProbe', str(ROOT)]),
    ('readiness', 'java.exe', ['-Djava.awt.headless=true', '-XX:ActiveProcessorCount=2', '-Xmx384m', '-cp', classpath,
                          'com.teamlifebind.clientui.ArchiveReadinessProbe']),
    ('run', 'java.exe', ['-Djava.awt.headless=true', '-XX:ActiveProcessorCount=2', '-Xmx384m', '-cp', classpath,
                        'ArchiveUsabilityProbe', str(ROOT / 'client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind')]),
    ('workspace', 'java.exe', ['-Djava.awt.headless=true', '-XX:ActiveProcessorCount=2', '-Xmx384m', '-cp', classpath,
                        'ArchiveWorkspaceLayoutProbe', str(ROOT / 'client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind')]),
    ('chat', 'java.exe', ['-Djava.awt.headless=true', '-XX:ActiveProcessorCount=2', '-Xmx384m', '-cp', classpath,
                        'ArchiveChatLayoutProbe', str(ROOT / 'client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind')]),
    ('hud-layout', 'java.exe', ['-Djava.awt.headless=true', '-XX:ActiveProcessorCount=2', '-Xmx384m', '-cp', classpath,
                        'com.teamlifebind.clientui.HudLayoutProbe', str(ROOT / 'client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind'),str(OUT)]),
    ('campaign-policy', 'java.exe', ['-Djava.awt.headless=true', '-cp', classpath,'CampaignMenuPolicyProbe']),
    ('overlay-regions', 'java.exe', ['-Djava.awt.headless=true', '-XX:ActiveProcessorCount=2', '-Xmx384m', '-cp', classpath,
                        'com.teamlifebind.clientui.ArchiveOverlayLayoutProbe', str(ROOT / 'client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind'),str(OUT)]),
]
failed = False
for step, executable, args in steps:
    argfile = OUT / (step + '.args')
    argfile.write_text('\n'.join(json.dumps(arg) for arg in args), encoding='utf-8')
    process = subprocess.run([str(jdk / executable), '@' + str(argfile)], cwd=OUT, capture_output=True,
                             text=True, encoding='utf-8', errors='replace', timeout=45,
                             creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS if os.name == 'nt' else 0)
    log = process.stdout + process.stderr
    (OUT / (step + '.log')).write_text(log, encoding='utf-8')
    print('\n'.join(line for line in log.splitlines() if 'PASS' in line or 'AssertionError' in line) or log[-1200:])
    if process.returncode:
        failed = True
        if step == 'compile': break
sys.exit(1 if failed else 0)
