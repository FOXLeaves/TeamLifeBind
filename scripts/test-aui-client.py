"""Run the real 26.3 client in a separate test directory, capture AUI, check inputs.

Usage: python scripts/test-aui-client.py fabric [forge neoforge]
Requires Java 26 and a graphical session. No world or external server is opened.
"""
from pathlib import Path
import hashlib
import json
import os
import struct
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
platforms = sys.argv[1:] or ["fabric", "forge", "neoforge"]
if not set(platforms) <= {"fabric", "forge", "neoforge"}:
    raise SystemExit("Choose fabric, forge, neoforge")
env = dict(os.environ)
local_jdk = ROOT / ".tools/jdk26-extract/jdk-26.0.2.1+1"
if local_jdk.is_dir():
    env["JAVA_HOME"] = str(local_jdk)
env["GRADLE_USER_HOME"] = str(ROOT / ".tools/gradle-upgrade-home")
outputs = ROOT / ".tools/aui-client-evidence"
outputs.mkdir(parents=True, exist_ok=True)
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()


def source_hashes():
    sources = [*ROOT.glob("client-ui/**/*.java"), *(p for p in ROOT.glob("client-ui/src/main/resources/**/*") if p.is_file()),
               *ROOT.glob("scripts/fixtures/aui-*.json"), *ROOT.glob("fabric/src/main/resources/assets/teamlifebind/lang/*.json"),
               ROOT / "scripts/prepare-aui.py", ROOT / "gradle/aui.gradle", ROOT / "vendor/apricityui/UPSTREAM.json"]
    # This runner never enables the separate loopback fixture.
    return {p.relative_to(ROOT).as_posix(): digest(p) for p in sources if p.name != 'ArchiveE2E.java'}

for platform in platforms:
    # This options file belongs only to the isolated fixture client.
    game = ROOT / f".tools/aui-client-smoke/{platform}-client"
    game.mkdir(parents=True, exist_ok=True)
    options = game / "options.txt"
    lines = options.read_text(encoding="utf-8").splitlines() if options.exists() else []
    lines = [line for line in lines if not line.startswith(("lang:", "onboardAccessibility:"))]
    options.write_text("\n".join([*lines, "lang:zh_cn", "onboardAccessibility:false"]) + "\n", encoding="utf-8")
    preferences = game / "config/teamlifebind-ui.properties"
    preferences.parent.mkdir(parents=True, exist_ok=True)
    preferences.write_text("motion=true\n", encoding="ascii")
    log = outputs / f"{platform}.log"
    started = time.time()
    tested_sources = source_hashes()
    command = [str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")),
               f"-Pplatforms={platform}", "-Pdev_run_root=.tools/aui-client-smoke", "-PauiSelfTest",
               f":{platform}:runClient", "--console=plain"]
    command.extend(['--offline','--max-workers=1','-Dorg.gradle.parallel=false','-Dorg.gradle.jvmargs=-Xms256m -Xmx2g -XX:ActiveProcessorCount=3'])
    if platform == 'neoforge' and os.getenv('TLB_NEOFORM_CACHED') == '1':
        cache=json.loads((ROOT/'.tools/archive-neoforge-cache-evidence.json').read_text())
        artifact=ROOT/'neoforge/build/moddev/artifacts/minecraft-patched-26.3.0.71-beta-merged.jar'
        assert cache['membersIdentical'] and digest(artifact)==cache['sha256'], 'Unverified NeoForm cache'
        command.extend(['-x', ':neoforge:createMinecraftArtifacts'])
    print(f"Starting {platform} AUI runtime check", flush=True)
    with log.open("w", encoding="utf-8") as stream:
        run = subprocess.run(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT, timeout=1200)
    result = outputs / platform / "result.txt"
    if run.returncode or not result.exists() or result.stat().st_mtime < started:
        raise SystemExit(f"{platform}: client failed; see {log}")
    report = result.read_text(encoding="utf-8")
    if "FAIL" in report or "PASS all 18 runtime scenarios" not in report or "PASS UI_INK_GPU" not in report:
        raise SystemExit(report + f"\nSee {log}")
    snapshots = {}
    for size in ("wide", "medium", "compact"):
        for name in (kind + detail for kind in ("lobby", "dev", "magic", "routes", "campaign", "expedition") for detail in ("", "-detail")):
            png = result.parent / f"{size}-{name}.png"
            if not png.is_file() or png.stat().st_mtime < started:
                raise SystemExit(f"Missing fresh capture: {png}")
            data = png.read_bytes()
            assert data[:8] == b"\x89PNG\r\n\x1a\n"
            dimensions = struct.unpack(">II", data[16:24])
            assert dimensions[0] >= 800 and dimensions[1] >= 500, (png, dimensions)
            snapshots[png.name] = {"sha256": digest(png), "dimensions": dimensions}
    ink_snapshots = {}
    for name in [*(f"ink-{i}.png" for i in range(36)), "ink-source.png"]:
        png = result.parent / name
        if not png.is_file() or png.stat().st_mtime < started:
            raise SystemExit(f"Missing fresh GPU transition capture: {png}")
        ink_snapshots[name] = {"sha256": digest(png), "dimensions": struct.unpack(">II", png.read_bytes()[16:24])}
    if tested_sources != source_hashes():
        raise SystemExit(f"{platform}: sources changed while testing; rerun before recording evidence")
    evidence = {"platform": platform, "realClient": True, "worldOpened": False, "rhinoRequired": False,
                "resultSha256": digest(result), "snapshots": snapshots, "inkSnapshots": ink_snapshots,
                "sources": tested_sources}
    (result.parent / "evidence.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"PASS {platform}: 18 actual-client render/input scenarios", flush=True)
