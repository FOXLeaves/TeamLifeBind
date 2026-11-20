"""Render the production native layouts and verify menu hit areas without launching Minecraft."""
from pathlib import Path
import json, os, subprocess, sys
root = Path(__file__).resolve().parents[1]
out = root / '.tools/client-ui-preview'
classes = out / 'classes'
classes.mkdir(parents=True, exist_ok=True)
jdk = root / '.tools/jdk26-extract/jdk-26.0.2.1+1/bin'
gson = next(path for path in (root / '.tools/gradle-upgrade-home/caches/modules-2/files-2.1/com.google.code.gson/gson').rglob('*.jar') if not path.name.endswith(('-sources.jar', '-javadoc.jar')))
cp = os.pathsep.join(map(str, [root / 'common/build/classes/java/main', classes, gson]))
commands = [('compile', 'javac.exe', ['-encoding', 'UTF-8', '--release', '26', '-cp', cp, '-d', str(classes), str(root / 'scripts/ExportNativeHud.java'), str(root / 'scripts/ExportClientMenus.java')]),
            ('hud', 'java.exe', ['-Djava.awt.headless=true', '-cp', cp, 'ExportNativeHud']),
            ('menus', 'java.exe', ['-Djava.awt.headless=true', '-cp', cp, 'ExportClientMenus'])]
for label, executable, arguments in commands:
    argfile = out / (label + '.args')
    argfile.write_text('\n'.join(json.dumps(arg) for arg in arguments), encoding='utf-8')
    result = subprocess.run([str(jdk / executable), '@' + str(argfile)], cwd=root, capture_output=True, text=True, encoding='utf-8', errors='replace')
    (out / (label + '.log')).write_text(result.stdout + result.stderr, encoding='utf-8')
    print(result.stdout + result.stderr)
    if result.returncode: sys.exit(result.returncode)
