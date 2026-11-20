"""Build the pinned AUI HTML/CSS client engine for Minecraft 26.3.

The upstream files in vendor/apricityui remain unchanged. Platform overrides
are maintained separately and the engine is namespaced to coexist with AUI.
TLB uses its existing server menus, not AUI's separate container protocol.
"""
from pathlib import Path
import argparse
import json

ROOT = Path(__file__).resolve().parents[1]
VENDOR = ROOT / "vendor/apricityui"
PREFIX = "com/sighs/apricityui/"
EXCLUDED = (
    "network/", "container/datasource/", "mixin/", "config/",
    "util/kjs/", "client/ApricityUIClientCommands.java", "client/InitEvent.java",
    "client/ClientRuntimeSelfTest.java", "client/ClientWptSnapshotRunner.java",
    "loader/ClientLoaderForge.java", "registry/ApricityMenus.java",
    "registry/ClientMenuScreens.java", "registry/Keybindings.java",
    "screen/ApricityContainer", "screen/SlotDataBinder.java",
    "world/WorldWindowRenderer.java", "world/WorldWindowTestSpawner.java",
    "neoforge/ApricityUINeoForge.java", "neoforge/AuiServicesBootstrap.java",
    "neoforge/ClientServicesBootstrap.java", "neoforge/NetworkService.java",
    "neoforge/KeyService.java", "neoforge/ConfigService.java",
)


def relocate(text):
    return (text.replace("com.sighs.apricityui", "com.teamlifebind.aui")
            .replace("com/sighs/apricityui", "com/teamlifebind/aui")
            .replace("com.teamlifebind.aui.neoforge", "com.teamlifebind.aui.platform")
            .replace("apricityui", "tlb_aui"))


def put(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or path.read_bytes() != data:
        path.write_bytes(data)


def prepare(destination):
    sources = {}
    resources = {}
    for tree in (VENDOR / "common/src/main", VENDOR / "targets/neoforge-26.3/src/main"):
        for path in (tree / "java").rglob("*.java"):
            rel = path.relative_to(tree / "java").as_posix()
            key = rel.removeprefix(PREFIX)
            if not key.startswith(EXCLUDED):
                sources[key] = path.read_text(encoding="utf-8")
        for path in (tree / "resources").rglob("*"):
            if path.is_file() and path.relative_to(tree / "resources").as_posix().startswith("assets/"):
                resources[path.relative_to(tree / "resources").as_posix()] = path.read_bytes()
    for path in (ROOT / "client-ui/aui-overrides").rglob("*.java"):
        sources[path.relative_to(ROOT / "client-ui/aui-overrides").as_posix()] = path.read_text(encoding="utf-8")
    expected = set()
    for key, content in sources.items():
        target = destination / "java/com/teamlifebind/aui" / key.replace("neoforge/", "platform/")
        notice = "// ApricityUI 1.3.0.1, LGPL-2.1; embedded 26.3 port by TeamLifeBind. See vendor/apricityui.\n"
        put(target, (notice + relocate(content)).encode("utf-8"))
        expected.add(target)
    for key, content in resources.items():
        target = destination / "resources" / key.replace("apricityui", "tlb_aui")
        if target.suffix in (".html", ".css", ".js", ".json", ".vsh", ".fsh", ".glsl", ".txt"):
            content = relocate(content.decode("utf-8")).encode("utf-8")
        put(target, content)
        expected.add(target)
    # Only stale generated files inside the explicitly resolved build directory.
    assert destination.resolve().is_relative_to((ROOT / "build").resolve())
    for path in destination.rglob("*"):
        if path.is_file() and path not in expected:
            path.unlink()
    print(json.dumps({"java": len(sources), "resources": len(resources), "output": str(destination)}))


if __name__ == "__main__":
    prepare(ROOT / "build/generated/aui")
