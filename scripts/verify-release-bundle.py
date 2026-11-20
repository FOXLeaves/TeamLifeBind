"""Inspect the current release artifact only; this does not build or run a server."""
import hashlib
import io
import json
import re
import runpy
from functools import lru_cache
from pathlib import Path
import tomllib
import zipfile
import zlib

root = Path(__file__).resolve().parent.parent
resource_checks = runpy.run_path(str(root / 'scripts/verify-resources.py'))
gun_ids = resource_checks['GUN_IDS']
gun_model_ids = resource_checks['GUN_MODEL_IDS']
gun_item_entry = resource_checks['gun_item_entry']
gun_sounds = resource_checks['GUN_SOUNDS']
gun_subtitles = resource_checks['GUN_SUBTITLES']
validate_gun_sounds = resource_checks['validate_gun_sounds']
verify_gun_sound = resource_checks['verify_gun_sound']
gun_atlas_path = resource_checks['GUN_ATLAS']
validate_gun_model = resource_checks['validate_gun_model']
verify_weapon_atlas = resource_checks['verify_weapon_atlas']
contains_gun_recipe_reference = resource_checks['contains_gun_recipe_reference']
validate_team_bed = resource_checks['validate_team_bed']
magic_item_entry = resource_checks['magic_item_entry']
magic_textures = resource_checks['MAGIC_TEXTURE_IDS']
magic_companions = resource_checks['MAGIC_COMPANIONS']
magic_book_models = resource_checks['MAGIC_BOOK_MODELS']
magic_controls = resource_checks['MAGIC_CONTROLS']
magic_shaders = resource_checks['MAGIC_SHADERS']
verify_magic_texture = resource_checks['verify_magic_texture']
contains_magic_book_recipe_reference = resource_checks['contains_magic_book_recipe_reference']
magic_catalogue = json.loads((root / 'docs/magic-catalogue.json').read_text(encoding='utf-8'))['spells']
properties = dict(line.split('=', 1) for line in (root / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
version = properties['mod_version'].strip()
display_version = properties.get('display_version', version).strip()
minecraft_version = properties['minecraft_version'].strip()
java_runtime = int(properties['java_runtime_version'])
platform_target = int(properties['java_target_version'])
common_target = int(properties['common_java_target_version'])
platform_major = platform_target + 44
common_major = common_target + 44
bundle = root / f'build/distributions/TeamLifeBind-{version}-mc{minecraft_version}.zip'
utility_mixins = {
    'UtilityDamageMixin', 'UtilityAttackMixin', 'UtilitySlotMixin', 'SupplyLevelMixin',
    'SupplyContainerMixin', 'SupplyHopperMixin', 'SupplyPistonMixin', 'SupplyExplosionMixin',
}
gun_mixins = {'GunDamageAccessor'}
gun_client_mixins = {'GunAttackInputMixin', 'GunKeyStateMixin', 'GunCameraMixin', 'GunHandPoseMixin', 'GunEquipMixin', 'GunRotorModelMixin'}
magic_mixins = {'NaturalMagicLootMixin'}
landmark_mixins = {'LandmarkGenerationMixin'}
magic_client_mixins = {'MagicHudMixin', 'MagicScrollMixin', 'MagicCastingPoseMixin', 'MagicShaderRenderMixin', 'NativeSidebarMixin'}
items = {
    'resonance_whistle': ['minecraft:copper_ingot', 'minecraft:copper_ingot', 'minecraft:redstone'],
    'emergency_bandage': ['minecraft:paper', 'minecraft:paper', 'minecraft:string', 'minecraft:string'],
    'silent_sigil': ['minecraft:amethyst_shard', 'minecraft:echo_shard'],
    'scout_rocket': ['minecraft:firework_rocket', 'minecraft:redstone'],
}


def server_gun_key(key):
    return (key.startswith(('gun.', 'rules.guns', 'book.weapons.', 'dev.menu.gun.'))
            or key == 'dev.menu.info.guns'
            or any(key.startswith(f'item.{weapon}.') for weapon in gun_ids))


def server_pack_gun_key(key):
    return (key.startswith(('hud.teamlifebind.', 'ui.teamlifebind.')) or '.gun.' in key or key.startswith('key.teamlifebind.reload')
            or any(key == f'item.teamlifebind.{weapon}' or key.startswith(f'item.teamlifebind.{weapon}.') for weapon in gun_ids))


def assert_server_pack_has_no_guns(pack):
    for path in pack.namelist():
        assert path != gun_atlas_path and not path.startswith('assets/teamlifebind/sounds/gun/'), f'gun resource in server pack: {path}'
        assert path != 'assets/teamlifebind/sounds.json', 'native gun sound manifest in server pack'
        for weapon in gun_ids:
            assert path != f'assets/teamlifebind/items/{weapon}.json', f'gun entry in server pack: {path}'
            assert not (path.startswith(f'assets/teamlifebind/models/item/{weapon}') and path.endswith('.json')), f'gun model in server pack: {path}'
    for locale in ('en_us', 'zh_cn'):
        actual = json.loads(pack.read(f'assets/teamlifebind/lang/{locale}.json'))
        source = json.loads((root / f'fabric/src/main/resources/assets/teamlifebind/lang/{locale}.json').read_text(encoding='utf-8'))
        assert actual == {key: value for key, value in source.items() if not server_pack_gun_key(key)}, f'server pack language filtering: {locale}'


@lru_cache(maxsize=2)
def assert_item_png(data):
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'invalid PNG signature'
    chunks = []
    offset = 8
    while offset < len(data):
        length = int.from_bytes(data[offset:offset + 4], 'big')
        kind = data[offset + 4:offset + 8]
        payload = data[offset + 8:offset + 8 + length]
        crc = data[offset + 8 + length:offset + 12 + length]
        assert len(payload) == length and len(crc) == 4, 'truncated PNG chunk'
        assert zlib.crc32(kind + payload) == int.from_bytes(crc, 'big'), f'PNG CRC mismatch: {kind}'
        chunks.append((kind, payload))
        offset += length + 12
    assert offset == len(data) and chunks[0][0] == b'IHDR' and chunks[-1] == (b'IEND', b''), 'invalid PNG structure'
    header = chunks[0][1]
    assert len(header) == 13, 'invalid PNG IHDR'
    width, height = int.from_bytes(header[:4], 'big'), int.from_bytes(header[4:8], 'big')
    assert width == height and width in (16, 32, 64, 128, 256, 512, 1024), f'unsupported item dimensions: {width}x{height}'
    assert header[8:] == bytes((8, 6, 0, 0, 0)), 'item texture must be non-interlaced 8-bit RGBA PNG'
    raw = zlib.decompress(b''.join(payload for kind, payload in chunks if kind == b'IDAT'))
    stride = width * 4
    assert len(raw) == height * (stride + 1), 'invalid PNG image length'
    previous = bytearray(stride)
    alpha = set()
    for y in range(height):
        offset = y * (stride + 1)
        filter_kind = raw[offset]
        row = bytearray(raw[offset + 1:offset + 1 + stride])
        assert filter_kind in range(5), 'invalid PNG filter'
        for x in range(stride):
            left = row[x - 4] if x >= 4 else 0
            above, upper_left = previous[x], previous[x - 4] if x >= 4 else 0
            if filter_kind == 1:
                row[x] = (row[x] + left) & 255
            elif filter_kind == 2:
                row[x] = (row[x] + above) & 255
            elif filter_kind == 3:
                row[x] = (row[x] + (left + above) // 2) & 255
            elif filter_kind == 4:
                estimate = left + above - upper_left
                predictor = min((left, above, upper_left), key=lambda value: abs(estimate - value))
                row[x] = (row[x] + predictor) & 255
        alpha.update(row[3::4])
        previous = row
    assert 0 in alpha and max(alpha) >= 250, 'item texture needs a transparent background and visible near-opaque artwork'
    return width, height


def assert_major(jar, path, major):
    data = jar.read(path)
    assert data[:4] == b'\xca\xfe\xba\xbe', f'invalid class: {path}'
    actual = int.from_bytes(data[6:8], 'big')
    assert actual == major, f'{path}: bytecode {actual}, expected {major}'


def assert_items(archive, recipes=False):
    for item, ingredients in items.items():
        model_path = f'assets/teamlifebind/models/item/{item}.json'
        entry_path = f'assets/teamlifebind/items/{item}.json'
        texture_path = f'assets/teamlifebind/textures/item/{item}.png'
        model = json.loads(archive.read(model_path))
        assert model == {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'teamlifebind:item/{item}'}}, (model_path, model)
        entry = json.loads(archive.read(entry_path))
        assert entry['model'] == {'type': 'minecraft:model', 'model': f'teamlifebind:item/{item}'}, (entry_path, entry)
        for path in (model_path, entry_path, texture_path):
            current = (root / 'fabric/src/main/resources' / path).read_bytes()
            for platform in ('forge', 'neoforge'):
                assert current == (root / platform / 'src/main/resources' / path).read_bytes(), f'platform asset mismatch: {platform}/{path}'
            assert archive.read(path) == current, f'stale asset: {path}'
        assert assert_item_png(archive.read(texture_path)) == (32, 32), f'production texture must be 32x32: {texture_path}'
        if recipes:
            path = f'data/teamlifebind/recipe/{item}.json'
            recipe = json.loads(archive.read(path))
            assert recipe['type'] == 'minecraft:crafting_shapeless', (path, recipe)
            assert sorted(recipe['ingredients']) == sorted(ingredients), (path, recipe)
            assert recipe['result'] == {'id': f'teamlifebind:{item}', 'count': 1}, (path, recipe)
            assert archive.read(path) == (root / 'fabric/src/main/resources' / path).read_bytes(), f'stale recipe: {path}'
    for locale in ('en_us', 'zh_cn'):
        language = json.loads(archive.read(f'assets/teamlifebind/lang/{locale}.json'))
        for item in items:
            assert language[f'item.teamlifebind.{item}'].strip(), f'missing item translation: {locale}/{item}'


def assert_guns(archive, recipes=False):
    parts = ('wide_lens', 'prism_lens', 'star_lens', 'moss_wrap', 'amber_wrap', 'cloud_wrap',
             'dusk_rune', 'comet_rune', 'echo_rune', 'rune_spanner')
    if recipes:
        patterns = set()
        for part in parts:
            asset = 'assets/teamlifebind/'
            model = f'teamlifebind:item/{part}'
            assert json.loads(archive.read(f'{asset}items/{part}.json'))['model']['model'] == model
            assert json.loads(archive.read(f'{asset}models/item/{part}.json'))['textures']['layer0'] == model
            assert archive.read(f'{asset}textures/item/{part}.png').startswith(b'\x89PNG\r\n\x1a\n')
            recipe = json.loads(archive.read(f'data/teamlifebind/recipe/{part}.json'))
            assert recipe['type'] == 'minecraft:crafting_shaped' and recipe['result']['id'] == 'teamlifebind:' + part
            pattern = tuple(tuple(recipe['key'].get(c, '') for c in row) for row in recipe['pattern'])
            assert pattern not in patterns, f'Ambiguous attachment recipe for {part}'
            patterns.add(pattern)
            unlock = json.loads(archive.read(f'data/teamlifebind/advancement/recipes/{part}.json'))
            assert unlock['rewards']['recipes'] == ['teamlifebind:' + part]
            for locale in ('en_us', 'zh_cn'):
                strings = json.loads(archive.read(f'{asset}lang/{locale}.json'))
                assert strings[f'item.teamlifebind.gun.{part}'] and strings[f'ui.teamlifebind.gun.{part}']
    paths = [gun_atlas_path, 'assets/teamlifebind/sounds.json']
    for weapon in gun_model_ids:
        model_path = f'assets/teamlifebind/models/item/{weapon}.json'
        paths.append(model_path)
        model = json.loads(archive.read(model_path))
        validate_gun_model(model)
    for weapon in gun_ids:
        entry_path = f'assets/teamlifebind/items/{weapon}.json'
        paths.append(entry_path)
        entry = json.loads(archive.read(entry_path))
        assert entry == gun_item_entry(weapon), (entry_path, entry)
    validate_gun_sounds(json.loads(archive.read('assets/teamlifebind/sounds.json')))
    for sound in gun_sounds.values():
        path = f'assets/teamlifebind/sounds/{sound}.ogg'
        paths.append(path)
        verify_gun_sound(archive.read(path))
    for path in paths:
        current = (root / 'fabric/src/main/resources' / path).read_bytes()
        for platform in ('forge', 'neoforge'):
            assert current == (root / platform / 'src/main/resources' / path).read_bytes(), f'weapon asset mismatch: {platform}/{path}'
        assert archive.read(path) == current, f'stale weapon asset: {path}'
    atlas_sha = verify_weapon_atlas(archive.read(gun_atlas_path))
    assert atlas_sha == hashlib.sha256((root / 'fabric/src/main/resources' / gun_atlas_path).read_bytes()).hexdigest(), 'weapon atlas SHA mismatch'
    for locale in ('en_us', 'zh_cn'):
        language = json.loads(archive.read(f'assets/teamlifebind/lang/{locale}.json'))
        for weapon in gun_ids:
            assert language[f'item.teamlifebind.{weapon}'].strip(), f'missing gun translation: {locale}/{weapon}'
        for key in ('key.teamlifebind.reload', 'key.category.teamlifebind.controls') + gun_subtitles:
            assert language[key].strip(), f'missing reload binding translation: {locale}/{key}'
    if recipes:
        for path in archive.namelist():
            if path.startswith('data/') and path.endswith('.json') and ('/recipe/' in path or '/recipes/' in path):
                assert not contains_gun_recipe_reference(json.loads(archive.read(path))), f'gun crafting recipes are forbidden: {path}'


def assert_magic(archive, recipes=False, no_guns=False):
    paths = ['assets/teamlifebind/items/spell_book.json']
    assert json.loads(archive.read(paths[0])) == magic_item_entry(), 'invalid tier/school magic model dispatch'
    for name in magic_book_models + magic_companions:
        path = f'assets/teamlifebind/models/item/{name}.json'
        textures = {'layer0': f'teamlifebind:item/{name}'}
        if name in magic_book_models:
            textures['layer1'] = 'teamlifebind:item/magic_school_rune'
        assert json.loads(archive.read(path)) == {'parent': 'minecraft:item/generated', 'textures': textures}, path
        paths.append(path)
    for name in magic_companions:
        path = f'assets/teamlifebind/items/{name}.json'
        assert json.loads(archive.read(path)) == {'model': {'type': 'minecraft:model', 'model': f'teamlifebind:item/{name}'}}, path
        paths.append(path)
    for name in magic_textures:
        path = f'assets/teamlifebind/textures/item/{name}.png'
        verify_magic_texture(archive.read(path)); paths.append(path)
    if not no_guns:
        paths.extend(magic_shaders)
    else:
        assert all(not name.startswith('assets/teamlifebind/shaders/') for name in archive.namelist()), 'native GPU pipelines leaked into the Paper resource pack'
    for path in paths:
        current = (root / 'fabric/src/main/resources' / path).read_bytes()
        assert archive.read(path) == current, f'stale magic asset: {path}'
        for platform in ('forge', 'neoforge'):
            assert current == (root / platform / 'src/main/resources' / path).read_bytes(), f'magic asset mismatch: {platform}/{path}'
    for locale in ('en_us', 'zh_cn'):
        path = f'assets/teamlifebind/lang/{locale}.json'
        language = json.loads(archive.read(path))
        source_language = json.loads((root / 'fabric/src/main/resources' / path).read_text(encoding='utf-8'))
        expected_language = {key: value for key, value in source_language.items() if not server_pack_gun_key(key)} if no_guns else source_language
        assert language == expected_language, f'stale magic language: {path}'
        for spell in magic_catalogue:
            assert language[f"spell.teamlifebind.{spell['id']}"] == spell['zhName' if locale == 'zh_cn' else 'enName'], f'missing magic name: {locale}/{spell["id"]}'
        for key in magic_controls:
            assert language[key].strip(), f'missing magic input/HUD translation: {locale}/{key}'
        for item in ('spell_book',) + magic_companions:
            assert language[f'item.teamlifebind.{item}'].strip(), f'missing magic item translation: {locale}/{item}'
    if recipes:
        for path in archive.namelist():
            if path.startswith('data/') and path.endswith('.json') and ('/recipe/' in path or '/recipes/' in path):
                assert not contains_magic_book_recipe_reference(json.loads(archive.read(path))), f'learning-book crafting recipe: {path}'


def assert_team_bed(archive):
    entry_path = 'assets/teamlifebind/items/team_bed.json'
    model_path = 'assets/teamlifebind/models/item/team_bed.json'
    validate_team_bed(json.loads(archive.read(entry_path)), json.loads(archive.read(model_path)))
    for path in (entry_path, model_path):
        current = (root / 'fabric/src/main/resources' / path).read_bytes()
        assert archive.read(path) == current, f'stale team bed resource: {path}'
        for platform in ('forge', 'neoforge'):
            assert current == (root / platform / 'src/main/resources' / path).read_bytes(), f'team bed resource mismatch: {platform}/{path}'


def assert_mixins(jar, platform):
    path = 'teamlifebind.mixins.json'
    config = json.loads(jar.read(path))
    source = json.loads((root / platform / 'src/main/resources' / path).read_bytes())
    assert config == source, f'stale mixin config: {platform}'
    assert config['required'] is True and config['injectors']['defaultRequire'] == 1
    assert config['minVersion'] == '0.8.7'
    assert config['package'] == f'com.teamlifebind.{platform}.mixin'
    required = utility_mixins | gun_mixins | magic_mixins | landmark_mixins | {'PveArrowDamageMixin'} | ({'LivingEntityTotemMixin'} if platform == 'fabric' else set())
    assert required <= set(config['mixins']), (platform, required - set(config['mixins']))
    assert 'GunControlMixin' not in config['mixins'], 'F must retain native offhand swapping in mods'
    assert gun_client_mixins <= set(config['client']), (platform, gun_client_mixins - set(config['client']))
    assert not gun_client_mixins & set(config['mixins']), 'client classes must not load on dedicated servers'
    assert magic_client_mixins <= set(config['client']), (platform, magic_client_mixins - set(config['client']))
    assert not magic_client_mixins & set(config['mixins']), 'magic client classes must not load on dedicated servers'
    assert 'NativeMenuMixin' in config['client'] and 'NativeMenuMixin' not in config['mixins'], 'native menu hook must be client-only'
    assert 'AuiGraphicsMixin' in config['client'] and 'AuiGraphicsMixin' not in config['mixins'], 'AUI renderer hook must be client-only'
    assert not any(path.endswith('/GunControlMixin.class') for path in jar.namelist()), 'obsolete offhand reload Mixin included'
    for name in config['mixins'] + config['client']:
        assert_major(jar, f"{config['package'].replace('.', '/')}/{name}.class", platform_major)


def manifest_attributes(data):
    text = data.decode('utf-8').replace('\r\n ', '').replace('\n ', '')
    return dict(line.split(': ', 1) for line in text.splitlines() if ': ' in line)


def assert_aui(jar, platform):
    for name in ('AuiClient', 'AuiMenuPage', 'AuiMenuScreen', 'AuiGraphics', 'AuiMenuSettings', 'AuiInkEffect', 'AuiInkTransition', 'AuiPageReadiness', 'AuiScreenTransitions', 'ArchiveWorkstation',
                 'ArchivePresenter', 'ArchiveModel', 'ArchiveSpecials', 'ArchiveHud', 'ArchiveOverlays', 'AuiMagicPage',
                 'ArchiveRecipes', 'ArchiveTextInput', 'ArchiveGameModes', 'MagicSigilRenderer', 'MagicSigilState',
                 'HudLayoutPreferences', 'HudLayout', 'HudEditorScreen', 'ArchiveNoticeQueue', 'ArchiveOverlayLayout', 'ArchiveCountdown',
                 'ArchiveLoading', 'ArchiveLoadingState', 'ArchiveLoadingRenderer', 'ArchiveBackdrop', 'ArchiveBackdropState', 'ArchiveBackdropRenderer', 'ArchivePolicy', 'ArchiveNavigation', 'ArchiveRefreshable', 'ClientRenderCompat', 'ArchiveChatFeed'):
        assert_major(jar, f'com/teamlifebind/clientui/{name}.class', platform_major)
    for name in ('init/Document', 'element/Item', 'render/PortablePipeline', 'platform/RenderService'):
        assert_major(jar, f'com/teamlifebind/aui/{name}.class', platform_major)
    assert 'com/github/rjeschke/txtmark/Processor.class' in jar.namelist(), 'missing embedded markdown dependency'
    assert not any(path.startswith(('com/sighs/apricityui/', 'assets/apricityui/', 'dev/latvian/mods/rhino/'))
                   for path in jar.namelist()), 'unrelocated engine or optional JS runtime bundled'
    assert not any(path.startswith(('net/caffeinemc/mods/sodium/','net/irisshaders/iris/')) for path in jar.namelist()), 'optional render mods must remain external'
    if platform=='neoforge':
        assert_major(jar,'com/teamlifebind/neoforge/mixin/SodiumLegacyExtractionMixin.class',platform_major)
        assert 'SodiumLegacyExtractionMixin' in json.loads(jar.read('teamlifebind.mixins.json'))['client'], 'NeoForge Sodium ABI bridge missing'
    for filename in ('menu.html', 'menu.css', 'interface.html', 'interface.css', 'navigation.css', 'title.css', 'magic.html', 'magic.css', 'corners.css', 'motion.css',
                     'hud.html', 'hud.css', 'hud-editor.html', 'hud-editor.css', 'overlays.html', 'overlays.css', 'fonts/archive-serif.ttf', 'fonts/OFL.txt', 'fonts/SOURCE.json',
                     'fonts/unicode-fallback.otf', 'fonts/unicode-upper.otf', 'fonts/UNICODE-SOURCE.json', 'fonts/UNICODE-OFL.txt'):
        path = f'assets/tlb_aui/apricity/teamlifebind/{filename}'
        assert jar.read(path) == (root / 'client-ui/src/main/resources' / path).read_bytes(), f'stale AUI page: {platform}/{path}'
    for suffix in ('vsh', 'fsh'):
        path = f'assets/tlb_aui/shaders/core/menu_ink.{suffix}'
        assert jar.read(path) == (root / 'client-ui/src/main/resources' / path).read_bytes(), f'stale UI transition shader: {platform}'
    loading_shader = 'assets/tlb_aui/shaders/core/loading_wave.fsh'
    assert jar.read(loading_shader) == (root / 'client-ui/src/main/resources' / loading_shader).read_bytes(), f'stale loading shader: {platform}'
    orbital_shader='assets/tlb_aui/shaders/core/menu_orbit.fsh'
    assert jar.read(orbital_shader)==(root/'client-ui/src/main/resources'/orbital_shader).read_bytes(),f'stale orbital background shader: {platform}'
    sigil_shader='assets/tlb_aui/shaders/core/magic_sigil.fsh'
    assert jar.read(sigil_shader)==(root/'client-ui/src/main/resources'/sigil_shader).read_bytes(),f'stale 3D spell preview shader: {platform}'
    composite_shell='assets/teamlifebind/shaders/core/iris_composite_shell.fsh'
    assert jar.read(composite_shell)==(root/'client-ui/src/main/resources'/composite_shell).read_bytes(),f'missing/stale Iris compatibility shader: {platform}'
    shared_mixins = json.loads(jar.read('tlb-ui.mixins.json'))
    assert shared_mixins == json.loads((root / 'client-ui/src/main/resources/tlb-ui.mixins.json').read_text()), f'stale shared UI mixins: {platform}'
    assert 'mixins' not in shared_mixins and shared_mixins['client'], 'UI mixins must be client-only'
    for name in shared_mixins['client']:
        assert_major(jar, f'com/teamlifebind/clientui/mixin/{name}.class', platform_major)
    assert not any('/ArchiveStorageScreen' in path or '/ArchiveSlotAccess' in path for path in jar.namelist()), 'obsolete storage prototype packaged'
    assert not any('arcane-citadel' in path for path in jar.namelist()), 'unwanted scene artwork bundled in transparent UI'
    for path in jar.namelist():
        if path.startswith('assets/tlb_aui/') and not path.endswith('/'):
            resources = 'build/classes/java/main' if platform == 'forge' else 'build/resources/main'
            assert jar.read(path) == (root / platform / resources / path).read_bytes(), f'stale AUI asset: {platform}/{path}'
    for packaged, source in (
            ('META-INF/licenses/aui/LGPL-2.1.txt', 'vendor/apricityui/LICENSE'),
            ('META-INF/licenses/aui/UPSTREAM.json', 'vendor/apricityui/UPSTREAM.json'),
            ('META-INF/licenses/txtmark-LICENSE.txt', 'vendor/apricityui/common/src/main/resources/META-INF/licenses/txtmark-LICENSE.txt')):
        assert jar.read(packaged) == (root / source).read_bytes(), f'missing/stale AUI attribution: {platform}/{packaged}'
    assert jar.read('META-INF/licenses/tlb-ui-font-OFL.txt') == (root / 'client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind/fonts/OFL.txt').read_bytes()
    assert jar.read('META-INF/licenses/tlb-ui-unicode-OFL.txt') == (root / 'client-ui/src/main/resources/assets/tlb_aui/apricity/teamlifebind/fonts/UNICODE-OFL.txt').read_bytes()
    font_meta=json.loads(jar.read('assets/tlb_aui/apricity/teamlifebind/fonts/UNICODE-SOURCE.json'))
    for font in font_meta['fonts']:
        assert hashlib.sha256(jar.read('assets/tlb_aui/apricity/teamlifebind/fonts/'+font['file'])).hexdigest()==font['sha256'], 'Unicode fallback font metadata is stale'
    for locale in ('en_us', 'zh_cn'):
        language = json.loads(jar.read(f'assets/teamlifebind/lang/{locale}.json'))
        for suffix in ('detail', 'activate', 'alternate', 'hint', 'hint.lobby', 'count', 'waiting', 'no_results', 'choose',
                       'inspect', 'back', 'vote', 'adjust', 'toggle', 'open',
                       'group.all', 'group.match', 'group.rules', 'group.display',
                       'motion_on', 'motion_off', 'browse', 'browse_title', 'browse_detail',
                       'chapter.lobby', 'chapter.campaign', 'chapter.dev', 'chapter.magic', 'chapter.routes', 'chapter.expedition',
                       'subtitle.lobby', 'subtitle.dev', 'subtitle.magic', 'subtitle.routes', 'subtitle.campaign', 'subtitle.expedition'):
            assert language.get(f'ui.teamlifebind.web.{suffix}', '').strip(), f'missing AUI translation: {locale}/{suffix}'


with zipfile.ZipFile(bundle) as archive:
    expected = {
        f'plugins/TeamLifeBind-{version}-server.jar',
        f'mods/fabric/TeamLifeBind-{version}-fabric.jar',
        f'mods/forge/TeamLifeBind-{version}-forge.jar',
        f'mods/neoforge/TeamLifeBind-{version}-neoforge.jar',
        f'resourcepack/TeamLifeBind-resourcepack-{version}.zip',
        f'source/TeamLifeBind-{version}-aui-sources.zip',
        'README.md', 'LICENSE', 'CHANGELOG.md',
    }
    expected.update(path.relative_to(root).as_posix() for path in (root / 'docs').rglob('*') if path.is_file())
    files = {name for name in archive.namelist() if not name.endswith('/')}
    assert files == expected, (files - expected, expected - files)
    for name in files:
        assert '${' not in name
        if name.startswith('docs/') or name.endswith('.md') or name == 'LICENSE':
            assert archive.read(name) == (root / name).read_bytes(), f'stale document: {name}'
    source_name = f'source/TeamLifeBind-{version}-aui-sources.zip'
    assert archive.read(source_name) == (root / 'build/distributions' / Path(source_name).name).read_bytes(), 'stale corresponding-source bundle'
    with zipfile.ZipFile(io.BytesIO(archive.read(source_name))) as sources:
        required = {'settings.gradle', 'build.gradle', 'gradle.properties', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar',
                    'gradle/aui.gradle', 'scripts/prepare-aui.py', 'vendor/apricityui/LICENSE', 'vendor/apricityui/UPSTREAM.json',
                    'vendor/apricityui/common/libs/txtmark-0.13.jar', 'docs/aui-web-ui.md',
                    'client-ui/src/main/java/com/teamlifebind/clientui/AuiMenuScreen.java',
                    'client-ui/aui-overrides/render/PortablePipeline.java',
                    'client-ui/src/main/java/com/teamlifebind/clientui/ClientRenderCompat.java',
                    'client-ui/src/main/java/com/teamlifebind/clientui/ArchiveChatFeed.java',
                    'scripts/probes/ArchiveChatLayoutProbe.java',
                    'client-ui/src/main/java/com/teamlifebind/clientui/mixin/IrisCompositePipelineMixin.java',
                    'scripts/prepare-render-compat.py','scripts/fixtures/render-compat-lock.json','docs/render-compatibility.md'}
        required.update({'client-ui/src/main/java/com/teamlifebind/clientui/MagicSigilProbe.java','scripts/test-magic-sigil.py',
                         'client-ui/src/main/java/com/teamlifebind/clientui/HudLayout.java','client-ui/src/main/java/com/teamlifebind/clientui/HudEditorScreen.java',
                         'client-ui/src/main/resources/assets/tlb_aui/shaders/core/magic_sigil.fsh','common/src/main/java/com/teamlifebind/common/CampaignMenuPolicy.java',
                         'scripts/probes/HudLayoutProbe.java','scripts/probes/CampaignMenuPolicyProbe.java'})
        assert required <= set(sources.namelist()), f'missing corresponding source: {required - set(sources.namelist())}'
        for name in sources.namelist():
            if name.endswith('/'):
                continue
            assert not {'build', '.gradle', '.tools', '__pycache__', 'run'} & set(Path(name).parts), f'generated data in source bundle: {name}'
            assert sources.read(name) == (root / name).read_bytes(), f'stale source bundle member: {name}'
    for platform in ('server', 'fabric', 'forge', 'neoforge'):
        name = (f'plugins/TeamLifeBind-{version}-server.jar' if platform == 'server'
                else f'mods/{platform}/TeamLifeBind-{version}-{platform}.jar')
        current_jar = root / platform / 'build/libs' / f'TeamLifeBind-{version}-{platform}.jar'
        assert archive.read(name) == current_jar.read_bytes(), f'stale platform JAR: {platform}'
        with zipfile.ZipFile(io.BytesIO(archive.read(name))) as jar:
            for shared in ('TeamLifeBindEngine', 'MatchPace', 'TotemBalance', 'TeamTotemCooldown', 'TeamUtilityState', 'SupplyDropRules', 'CampaignRules', 'CampaignMenuPolicy', 'MatchMode', 'PlayMode', 'PveDifficulty', 'PveWaves', 'PveWaveState', 'RogueRun', 'RogueBallot', 'DragonVictoryMode', 'RoundCampaignState', 'LandmarkStructures', 'LandmarkPlacement', 'LandmarkLoot'):
                assert_major(jar, f'com/teamlifebind/common/{shared}.class', common_major)
            if platform != 'server':
                assert_aui(jar, platform)
                for shared in ('GunProfile', 'GunCombatState', 'GunMath', 'GunMotion', 'GunPresentationProfile', 'NativeHud', 'NativeUi'):
                    assert_major(jar, f'com/teamlifebind/common/{shared}.class', common_major)
                for client in ('NativeHudRenderer', 'NativeHudCanvas', 'NativeSidebarHud', 'NativeMenuTitle', 'NativeMenuScreen', 'NativeMenuScreen$ReadOnlyMenu'):
                    assert_major(jar, f'com/teamlifebind/{platform}/{client}.class', platform_major)
                for locale in ('en_us', 'zh_cn'):
                    language = json.loads(jar.read(f'assets/teamlifebind/lang/{locale}.json'))
                    assert all(language.get(key, '').strip() for key in resource_checks['NATIVE_UI_KEYS']), 'missing native menu translation'
            else:
                assert not any(path.startswith(('com/teamlifebind/aui/', 'com/teamlifebind/clientui/', 'assets/tlb_aui/')) for path in jar.namelist()), 'AUI client engine included in Paper JAR'
                assert not any(path.startswith('com/teamlifebind/common/Gun') for path in jar.namelist()), 'gun engine included in server JAR'
                assert not any(path.startswith(('com/teamlifebind/common/NativeHud', 'com/teamlifebind/common/NativeUi')) for path in jar.namelist()), 'native UI included in standalone server JAR'
            for shared in ('MagicSchool', 'MagicMechanic', 'SpellDefinition', 'SpellCatalogue', 'MagicLootRules', 'MagicState', 'SpellBehaviour', 'SpellBehaviourPlans', 'SpellGeometry'):
                assert_major(jar, f'com/teamlifebind/common/{shared}.class', common_major)
            package = 'paper' if platform == 'server' else platform
            assert_major(jar, f'com/teamlifebind/{package}/TeamUtilityController.class', platform_major)
            assert_major(jar, f'com/teamlifebind/{package}/TeamCampaignController.class', platform_major)
            assert_major(jar, f'com/teamlifebind/{package}/TeamPveController.class', platform_major)
            assert_major(jar, f'com/teamlifebind/{package}/TeamRogueController.class', platform_major)
            for source in (('SurvivalProfiles',) if platform == 'server' else ('TeamSurvivalProfiles', 'TeamSurvivalBeds')):
                assert_major(jar, f'com/teamlifebind/{package}/{source}.class', platform_major)
            assert_major(jar, f'com/teamlifebind/{package}/' + ('TeamLandmarkController.class' if platform == 'server' else 'TeamLandmarkGeneration.class'), platform_major)
            for source in ('TeamMagicController', 'TeamMagicRuntime'):
                assert_major(jar, f'com/teamlifebind/{package}/{source}.class', platform_major)
            if platform != 'server':
                assert_major(jar, f'com/teamlifebind/{package}/TeamGunController.class', platform_major)
                assert_major(jar, f'com/teamlifebind/{package}/TeamGunItem.class', platform_major)
                for source in ('GunReloadPayload', 'GunInputPayload', 'GunVisualPayload', 'GunClientPresentation', 'TeamGunNetwork', 'TeamGunSounds'):
                    assert_major(jar, f'com/teamlifebind/{package}/{source}.class', platform_major)
                client = 'TeamLifeBindFabricClient' if platform == 'fabric' else 'TeamGunClient'
                assert_major(jar, f'com/teamlifebind/{package}/{client}.class', platform_major)
                for source in ('TeamMagicItem', 'TeamMagicNetwork', 'TeamMagicClient', 'MagicInputPayload', 'MagicSyncPayload', 'MagicScenePayload', 'MagicShaderRenderer', 'MagicClientPresentation', 'MagicWheelScreen', 'WorldVfx', 'WorldVfxPayload', 'WorldVfxNetwork'):
                    assert_major(jar, f'com/teamlifebind/{package}/{source}.class', platform_major)
            else:
                assert_major(jar, 'com/teamlifebind/paper/ServerMagicDamageCompat.class', platform_major)
                assert_major(jar, 'com/teamlifebind/paper/LegacyItemMigration.class', platform_major)
                assert not any('TeamGun' in path or 'ServerGunDamageCompat' in path for path in jar.namelist()), 'gun adapter included in server JAR'
            for path in jar.namelist():
                if path.startswith('com/teamlifebind/') and path.endswith('.class'):
                    assert_major(jar, path, common_major if path.startswith('com/teamlifebind/common/') else platform_major)
                    if path.startswith('com/teamlifebind/common/'):
                        assert jar.read(path) == (root / 'common/build/classes/java/main' / path).read_bytes(), f'stale shared class: {platform}/{path}'
                    else:
                        assert jar.read(path) == (root / platform / 'build/classes/java/main' / path).read_bytes(), f'stale platform class: {platform}/{path}'
            for locale in ('en_us', 'zh_cn'):
                language_path = f'teamlifebind/lang/{locale}.properties'
                language = jar.read(language_path).decode('utf-8')
                source_language = (root / 'common/src/main/resources' / language_path).read_text(encoding='utf-8')
                expected_lines = source_language.splitlines()
                if platform == 'server':
                    expected_lines = [line for line in expected_lines if not server_gun_key(line.split('=', 1)[0])]
                assert language.splitlines() == expected_lines, f'stale common language: {platform}/{locale}'
                for key in ('item.resonance_whistle.name', 'item.emergency_bandage.name', 'rules.utilities', 'rules.supply.dynamic',
                            'item.silent_sigil.name', 'item.scout_rocket.name', 'rules.campaign', 'campaign.mode.limited_lives',
                            'campaign.dragon.contribution', 'campaign.reconnect.expired', 'campaign.point.reward', 'campaign.border.warning',
                            'play.mode.sur', 'play.mode.pve', 'play.mode.pvp', 'play.mode.rg', 'play.rules.rg', 'rg.offer', 'rg.menu.title', 'rg.run_only', 'rg.result', 'rg.hud.vote', 'rg.vote.rules', 'rg.vote.timer', 'rg.details.title', 'rg.details.fighting', 'play.pve.extract', 'play.sur.save_failed',
                            'play.pve.difficulty.easy', 'play.pve.difficulty.normal', 'play.pve.difficulty.hard', 'play.pve.boss.bar', 'play.pve.summary'):
                    assert key + '=' in language, f'missing common translation: {platform}/{locale}/{key}'
                for key in (() if platform == 'server' else ('gun.airdrop_only', 'gun.infinite_reserve', 'gun.controls', 'gun.hud.ready',
                            'gun.hud.reload', 'gun.hud.overheated', 'gun.hud.windup', 'rules.guns',
                            'gun.controls.mod', 'rules.guns.mod', 'book.weapons.page1.mod', 'gun.hud.feed_ready', 'gun.reload.not_needed',
                            'dev.menu.gun.test', 'dev.menu.info.guns', 'gun.unavailable')):
                    assert key + '=' in language, f'missing gun translation: {platform}/{locale}/{key}'
                for weapon in (() if platform == 'server' else gun_ids):
                    assert f'item.{weapon}.name=' in language and f'item.{weapon}.lore=' in language, f'missing gun name/lore: {platform}/{locale}/{weapon}'
                    assert f'item.{weapon}.native_name=' in language and f'item.{weapon}.native_lore=' in language, f'missing native name/lore: {platform}/{locale}/{weapon}'
                for key in ('campaign.mode.random_grimoire', 'magic.book.learn', 'magic.controls.native', 'magic.controls.paper', 'magic.dev.title', 'magic.hud'):
                    assert key + '=' in language, f'missing common magic translation: {platform}/{locale}/{key}'
                for item in magic_companions:
                    assert f'item.{item}.name=' in language and f'item.{item}.lore=' in language, f'missing companion name/lore: {platform}/{locale}/{item}'
            if platform == 'server':
                meta = jar.read('plugin.yml').decode()
                assert '${' not in meta and f'version: {display_version}' in meta and f'api-version: "{minecraft_version}"' in meta
                assert 'rules' in meta
                assert 'dynamic-count: true' in jar.read('config.yml').decode()
                config = jar.read('config.yml').decode()
                for entry in ('play-mode: SUR', 'pve-difficulty: NORMAL', 'match-mode: DRAGON_HUNT', 'team-lives: 3', 'dragon-victory: LAST_HIT', 'control-point-enabled: false', 'border-enabled: false'):
                    assert entry in config, f'missing campaign default: {entry}'
            elif platform == 'fabric':
                meta = json.loads(jar.read('fabric.mod.json'))
                assert meta['version'] == display_version
                assert meta['depends']['minecraft'] == f'~{minecraft_version}'
                assert meta['depends']['java'] == f'>={java_runtime}'
                assert meta['depends']['fabricloader'] == '>=' + properties['fabric_loader_version'].strip()
                assert meta['depends']['fabric-api'] == '>=' + properties['fabric_api_version'].strip()
                assert 'teamlifebind.mixins.json' in meta['mixins']
                assert 'com.teamlifebind.fabric.TeamLifeBindFabricClient' in meta['entrypoints']['client']
                assert_mixins(jar, platform)
                assert_items(jar, recipes=True)
            else:
                path = 'META-INF/mods.toml' if platform == 'forge' else 'META-INF/neoforge.mods.toml'
                meta_text = jar.read(path).decode()
                assert '${' not in meta_text, f'unexpanded mod metadata: {platform}'
                meta = tomllib.loads(meta_text)
                assert meta['mods'][0]['version'] == display_version
                assert meta['features']['teamlifebind']['javaVersion'] == f'[{java_runtime},)'
                minecraft = next(dep for dep in meta['dependencies']['teamlifebind'] if dep['modId'] == 'minecraft')
                assert minecraft['versionRange'] == f'[{minecraft_version},26.4)'
                loader = next(dep for dep in meta['dependencies']['teamlifebind'] if dep['modId'] == platform)
                expected_loader = properties['forge_version'].strip().split('-', 1)[1] if platform == 'forge' else properties['neoforge_version'].strip()
                assert loader['versionRange'] == f'[{expected_loader},)'
                if platform == 'forge':
                    manifest = manifest_attributes(jar.read('META-INF/MANIFEST.MF'))
                    assert 'teamlifebind.mixins.json' in manifest['MixinConfigs'].split(',')
                else:
                    assert any(mixin['config'] == 'teamlifebind.mixins.json' for mixin in meta['mixins'])
                assert_mixins(jar, platform)
                assert_items(jar, recipes=True)
            if platform != 'server':
                assert_guns(jar, recipes=True)
                assert_team_bed(jar)
                assert_magic(jar, recipes=True)
    pack_name = f'resourcepack/TeamLifeBind-resourcepack-{version}.zip'
    assert archive.read(pack_name) == (root / 'build' / pack_name).read_bytes(), 'stale server resource pack'
    pack_sha1 = hashlib.sha1(archive.read(pack_name)).hexdigest()
    install = archive.read(f'docs/install-{version}.md').decode('utf-8')
    assert f'`{pack_sha1}`' in install, 'install documentation contains a stale resource-pack SHA-1'
    with zipfile.ZipFile(io.BytesIO(archive.read(pack_name))) as pack:
        assert not any(path.startswith('assets/tlb_aui/') for path in pack.namelist()), 'AUI assets included in Paper resource pack'
        meta = json.loads(pack.read('pack.mcmeta'))['pack']
        assert meta['min_format'] == meta['max_format'] == [97, 1]
        assert all(not name.startswith('data/') for name in pack.namelist())
        assert 'assets/teamlifebind/textures/item/death_exemption_totem.png' in pack.namelist()
        assert_items(pack)
        assert_server_pack_has_no_guns(pack)
        assert_team_bed(pack)
        assert_magic(pack, no_guns=True)
print(f'Release bundle verified: {len(files)} files, displayed version {display_version}, build {version}, four SUR/PVE/RG/PVP campaign/utility/magic/landmark implementations, default SUR, ten-stage PVE and seeded RG routes/relics, native-only guns and gun-free server/resourcepack, common Java {common_target}/platform Java {platform_target}, required generation/server/client Mixins, utility recipes, three large landmark blueprints, 300 magic names/eight tier models/five companions, pack 97.1.')
print(f'SHA256: {hashlib.sha256(bundle.read_bytes()).hexdigest()}')
print('AUI verified: three relocated client engines, shared HTML/CSS, client-only hooks, licenses and complete corresponding-source bundle.')
