#!/usr/bin/env python3
"""Verify shared resource parity and translations using only the Python standard library."""

import argparse
import hashlib
import json
import math
import struct
import sys
import zlib
from pathlib import Path


MODS = ("fabric", "forge", "neoforge")
LANGUAGES = ("en_us", "zh_cn")
GUN_IDS = ("battle_pistol", "tactical_smg", "assault_rifle", "breach_shotgun",
           "marksman_rifle", "support_machine_gun", "siege_launcher")
GUN_ATLAS = "assets/teamlifebind/textures/item/weapon_atlas.png"
GUN_RESOURCE_IDS = {f"teamlifebind:{weapon}" for weapon in GUN_IDS}
ROTOR_MODEL_IDS = tuple(f"support_machine_gun_spin_{phase:02d}" for phase in range(16))
GUN_MODEL_IDS = GUN_IDS + ROTOR_MODEL_IDS
GUN_SOUNDS = {f"gun.fire.{weapon}": f"gun/fire_{weapon}" for weapon in GUN_IDS}
GUN_SOUNDS.update({f"gun.{event}": f"gun/{event}" for event in
                  ("reload_start", "reload_end", "shell_insert", "bolt", "spin_start", "spin_stop")})
GUN_SUBTITLES = ("subtitle.teamlifebind.gun.fire", "subtitle.teamlifebind.gun.reload",
                 "subtitle.teamlifebind.gun.spin")
MAGIC_COMPANIONS = ("arcane_grimoire", "mana_potion", "greater_mana_potion", "mana_apple", "amnesia_tome")
MAGIC_BOOK_MODELS = tuple(f"spell_book_tier_{tier}" for tier in range(1, 9))
MAGIC_TEXTURE_IDS = MAGIC_BOOK_MODELS + MAGIC_COMPANIONS + ("magic_school_rune",)
MAGIC_SHADERS = tuple(f"assets/teamlifebind/shaders/core/{name}.{extension}"
                      for name in ("magic_volume", "magic_composite") for extension in ("vsh", "fsh")) + ("assets/teamlifebind/shaders/core/world_detail.fsh",)
MAGIC_ITEM_IDS = ("spell_book",) + MAGIC_COMPANIONS
MAGIC_CONTROLS = ("key.teamlifebind.magic_cast", "key.teamlifebind.magic_wheel", "key.teamlifebind.magic_scroll",
                  "key.category.teamlifebind.magic", "magic.teamlifebind.empty_slot", "magic.teamlifebind.mana",
                  "magic.teamlifebind.casting", "magic.teamlifebind.wheel", "magic.teamlifebind.wheel.cost",
                  "magic.teamlifebind.wheel.release")
NATIVE_HUD_KEYS = tuple("hud.teamlifebind." + key for key in (
    "mana", "ready", "low_mana", "casting", "magic_hint", "unlearned_hint", "wheel_hint", "sidebar_more",
    "gun.syncing", "gun.overheated", "gun.auto", "gun.semi", "gun.reload", "gun.reserve", "gun.heat"))
NATIVE_UI_KEYS = tuple("ui.teamlifebind." + key for key in (
    "category.lobby", "category.campaign", "category.dev", "category.magic", "category.routes", "category.expedition",
    "controls", "controls.compact", "scroll", "empty"))


def magic_item_entry():
    def model(tier):
        return {"type": "minecraft:model", "model": f"teamlifebind:item/spell_book_tier_{tier}",
                "tints": [{"type": "minecraft:constant", "value": 16777215},
                          {"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]}
    return {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data",
                      "index": 0, "fallback": model(1),
                      "entries": [{"threshold": tier, "model": model(tier)} for tier in range(1, 9)]}}


def contains_magic_book_recipe_reference(value):
    if isinstance(value, str):
        return value == "teamlifebind:spell_book"
    if isinstance(value, dict):
        return any(contains_magic_book_recipe_reference(child) for child in value.values())
    if isinstance(value, list):
        return any(contains_magic_book_recipe_reference(child) for child in value)
    return False


def verify_magic_texture(data):
    """Decode PNG filters so a valid-looking header cannot conceal missing alpha/artwork."""
    require(data[:8] == b"\x89PNG\r\n\x1a\n", "invalid magic texture PNG signature")
    offset, chunks = 8, []
    while offset < len(data):
        require(offset + 12 <= len(data), "truncated magic texture chunk")
        length = int.from_bytes(data[offset:offset + 4], "big")
        kind, payload = data[offset + 4:offset + 8], data[offset + 8:offset + 8 + length]
        require(offset + length + 12 <= len(data), "truncated magic texture payload")
        require(zlib.crc32(kind + payload) & 0xffffffff == int.from_bytes(data[offset + 8 + length:offset + 12 + length], "big"), "magic texture CRC mismatch")
        chunks.append((kind, payload)); offset += length + 12
    require(chunks and chunks[0][0] == b"IHDR" and chunks[-1] == (b"IEND", b""), "invalid magic texture structure")
    require(struct.unpack(">IIBBBBB", chunks[0][1]) == (64, 64, 8, 6, 0, 0, 0), "magic texture must be non-interlaced 64x64 RGBA8")
    raw = zlib.decompress(b"".join(payload for kind, payload in chunks if kind == b"IDAT"))
    stride = 64 * 4
    require(len(raw) == 64 * (stride + 1), "invalid magic texture image length")
    previous, alpha = bytearray(stride), set()
    for y in range(64):
        offset = y * (stride + 1); mode = raw[offset]; row = bytearray(raw[offset + 1:offset + 1 + stride])
        require(mode in range(5), "invalid magic PNG filter")
        for x in range(stride):
            left, above, upper = row[x - 4] if x >= 4 else 0, previous[x], previous[x - 4] if x >= 4 else 0
            estimate = left + above - upper
            predictor = (0, left, above, (left + above) // 2,
                         min((left, above, upper), key=lambda value: abs(estimate - value)))[mode]
            row[x] = (row[x] + predictor) & 255
        alpha.update(row[3::4]); previous = row
    require(0 in alpha and max(alpha) >= 128, "magic texture needs a transparent background and visible artwork")
    return hashlib.sha256(data).hexdigest()


def verify_magic_resources(root, shared_resources, parsed_json, errors):
    try:
        exported = json.loads((root / "docs/magic-catalogue.json").read_text(encoding="utf-8"))
        require(exported.get("schema") == 2, "expected exported spell behaviour/visual schema 2")
        catalogue = exported["spells"]
        require(len(catalogue) == 300 and len({spell["id"] for spell in catalogue}) == 300, "expected 300 unique catalogue spells")
        require(sum(spell["tier"] == 8 for spell in catalogue) == 20, "expected 20 super-tier spells")
        structures, visuals = set(), set()
        single_hit = {"BOLT", "BEAM", "CONE", "NOVA", "CHAIN", "WAVE", "METEOR", "MINE", "DASH", "BLINK", "MARK", "DRAIN"}
        for spell in catalogue:
            behaviour = spell["behaviour"]
            rules, stages = behaviour["rules"], behaviour["stages"]
            require(2 <= len(rules) <= 6, f'missing executable spell rules: {spell["id"]}')
            structure = repr(([(r["event"], r["condition"], r["target"], r["actions"]) for r in rules],
                              [(s["mechanic"], s["opening"], s["closing"]) for s in stages]))
            visual = json.dumps(behaviour["visual"], sort_keys=True)
            require(structure not in structures, f'duplicate behaviour structure: {spell["id"]}')
            require(visual not in visuals, f'duplicate four-phase visual profile: {spell["id"]}')
            structures.add(structure); visuals.add(visual)
            if spell["tier"] != 8 and spell["mechanics"][0] in single_hit:
                require(all(r["event"] != "REPEAT_HIT" for r in rules), f'unreachable repeated-hit rule: {spell["id"]}')
        required_spell_keys = {"spell.teamlifebind." + spell["id"] for spell in catalogue}
    except (OSError, ValueError, KeyError, TypeError) as error:
        errors.append(f"magic catalogue: {error}"); catalogue, required_spell_keys = [], set()
    for mod in MODS:
        files = shared_resources[mod]
        hashes = []
        for texture in MAGIC_TEXTURE_IDS:
            path = f"assets/teamlifebind/textures/item/{texture}.png"
            try:
                digest = verify_magic_texture(files[path])
                if texture != "magic_school_rune": hashes.append(digest)
            except (KeyError, ValueError, zlib.error, struct.error) as error:
                errors.append(f"{mod}/{path}: {error}")
        if len(hashes) == 13 and len(set(hashes)) != 13:
            errors.append(f"{mod}: all eight tier books and five companions need distinct artwork")
        if parsed_json.get((mod, "assets/teamlifebind/items/spell_book.json")) != magic_item_entry():
            errors.append(f"{mod}: spell book must dispatch tiers 1..8 and tint only the school-rune overlay")
        for name in MAGIC_BOOK_MODELS + MAGIC_COMPANIONS:
            path = f"assets/teamlifebind/models/item/{name}.json"
            textures = {"layer0": f"teamlifebind:item/{name}"}
            if name in MAGIC_BOOK_MODELS: textures["layer1"] = "teamlifebind:item/magic_school_rune"
            if parsed_json.get((mod, path)) != {"parent": "minecraft:item/generated", "textures": textures}:
                errors.append(f"{mod}/{path}: unresolved generated magic item model")
        for name in MAGIC_COMPANIONS:
            path = f"assets/teamlifebind/items/{name}.json"
            if parsed_json.get((mod, path)) != {"model": {"type": "minecraft:model", "model": f"teamlifebind:item/{name}"}}:
                errors.append(f"{mod}/{path}: unresolved companion item entry")
        for language in LANGUAGES:
            entries = parsed_json.get((mod, f"assets/teamlifebind/lang/{language}.json"), {})
            expected = required_spell_keys | set(MAGIC_CONTROLS) | {f"item.teamlifebind.{name}" for name in MAGIC_ITEM_IDS}
            for key in sorted(expected):
                if not isinstance(entries.get(key), str) or not entries[key].strip():
                    errors.append(f"{mod}/{language}: missing magic translation: {key}")
            actual = {key for key in entries if key.startswith("spell.teamlifebind.")}
            if actual != required_spell_keys:
                errors.append(f"{mod}/{language}: spell names do not match the complete 300-spell catalogue")
            for spell in catalogue:
                if entries.get("spell.teamlifebind." + spell["id"]) != spell["zhName" if language == "zh_cn" else "enName"]:
                    errors.append(f"{mod}/{language}: stale spell name: {spell['id']}")
        for path in files:
            if path.startswith("data/") and path.endswith(".json") and ("/recipe/" in path or "/recipes/" in path):
                if contains_magic_book_recipe_reference(parsed_json.get((mod, path))):
                    errors.append(f"{mod}/{path}: learning books must come from loot, not crafting")


def gun_item_entry(weapon):
    model = {"type": "minecraft:model", "model": f"teamlifebind:item/{weapon}"}
    if weapon != "support_machine_gun":
        return {"model": model}
    return {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data",
                      "index": 0, "fallback": model,
                      "entries": [{"threshold": phase + 1, "model": {
                          "type": "minecraft:model", "model": f"teamlifebind:item/{rotor}"}}
                          for phase, rotor in enumerate(ROTOR_MODEL_IDS)]}}


def validate_team_bed(entry, head_model):
    """26.3 beds use ordinary head/foot meshes; the former bed special renderer is gone."""
    require(isinstance(entry, dict) and isinstance(entry.get("model"), dict), "missing team bed item entry")
    composite = entry["model"]
    require(composite.get("type") == "minecraft:composite", "team bed must use the 26.3 head/foot composite")
    parts = composite.get("models")
    require(isinstance(parts, list) and len(parts) == 2, "team bed must contain both head and foot models")
    head, foot = parts
    require(head.get("type") == "minecraft:model" and head.get("model") == "teamlifebind:item/team_bed",
            "team bed head must use its ordinary mesh, not the removed special renderer")
    require(foot.get("type") == "minecraft:model" and foot.get("model") == "minecraft:block/white_bed_foot",
            "team bed foot must reference the existing 26.3 white-bed block model")
    require(foot.get("transformation", {}).get("translation") == [0.0, 0.0, 1.0],
            "team bed foot must be placed one block behind its head")
    require(isinstance(head_model, dict) and head_model.get("parent") == "minecraft:block/white_bed_head",
            "team bed head must inherit the existing block model; minecraft:item/white_bed no longer exists")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def number(value):
    return type(value) in (int, float) and math.isfinite(value)


def contains_gun_recipe_reference(value):
    if isinstance(value, str):
        return value in GUN_RESOURCE_IDS
    if isinstance(value, dict):
        return any(contains_gun_recipe_reference(child) for child in value.values())
    if isinstance(value, list):
        return any(contains_gun_recipe_reference(child) for child in value)
    return False


def validate_gun_model(model):
    """Validate original cuboid meshes using the native model limits, without game libraries."""
    require(isinstance(model, dict), "expected a model object")
    require(model.get("texture_size") == [256, 256], "weapon atlas must use 256x256 texture coordinates")
    require(model.get("textures") == {"atlas": "teamlifebind:item/weapon_atlas", "particle": "teamlifebind:item/weapon_atlas"}, "weapon texture references must resolve to the shared atlas")
    elements = model.get("elements")
    require(isinstance(elements, list) and 0 < len(elements) <= 100, "weapon geometry must contain 1..100 cuboids")
    for element in elements:
        require(isinstance(element, dict), "expected a cuboid object")
        start, end = element.get("from"), element.get("to")
        require(isinstance(start, list) and isinstance(end, list) and len(start) == len(end) == 3,
                "cuboid bounds must have three coordinates")
        require(all(number(a) and number(b) and -16 <= a < b <= 32 for a, b in zip(start, end)),
                "cuboid coordinates must be finite, positive-volume and inside [-16,32]")
        rotation = element.get("rotation")
        if rotation is not None:
            require(isinstance(rotation, dict) and rotation.get("axis") in ("x", "y", "z"), "invalid cuboid rotation axis")
            require(rotation.get("angle") in (-45, -22.5, 0, 22.5, 45), "invalid native cuboid rotation angle")
            origin = rotation.get("origin")
            require(isinstance(origin, list) and len(origin) == 3 and all(number(v) for v in origin), "invalid cuboid rotation origin")
            require(rotation.get("rescale", False) is False, "weapon geometry requires unscaled cuboid rotations")
            angle = math.radians(rotation["angle"])
            first, second = {"x": (1, 2), "y": (2, 0), "z": (0, 1)}[rotation["axis"]]
            for x in (start[0], end[0]):
                for y in (start[1], end[1]):
                    for z in (start[2], end[2]):
                        point = [x, y, z]
                        a, b = point[first]-origin[first], point[second]-origin[second]
                        point[first] = origin[first]+a*math.cos(angle)-b*math.sin(angle)
                        point[second] = origin[second]+a*math.sin(angle)+b*math.cos(angle)
                        require(all(-16 <= v <= 32 for v in point), "rotated cuboid vertex outside [-16,32]")
        faces = element.get("faces")
        require(isinstance(faces, dict) and set(faces) == {"down", "up", "north", "south", "west", "east"}, "cuboid must define all six faces")
        for face in faces.values():
            require(isinstance(face, dict) and face.get("texture") == "#atlas", "face must use a valid atlas texture slot")
            uv = face.get("uv")
            require(isinstance(uv, list) and len(uv) == 4 and all(number(v) and 0 <= v <= 16 for v in uv), "invalid face UV bounds")
            require(uv[0] < uv[2] and uv[1] < uv[3], "face UV must have positive area")
            require(math.floor(uv[0]/4) == math.floor(uv[2]/4) and math.floor(uv[1]/4) == math.floor(uv[3]/4), "face UV must remain inside one material tile")
    contexts = {"firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand",
                "thirdperson_lefthand", "gui", "ground", "fixed", "head"}
    display = model.get("display")
    require(isinstance(display, dict) and contexts <= display.keys(), "weapon display transforms are incomplete")
    for context in contexts:
        transform = display[context]
        require(isinstance(transform, dict), "invalid display transform")
        for key in ("rotation", "translation", "scale"):
            values = transform.get(key)
            require(isinstance(values, list) and len(values) == 3 and all(number(v) for v in values), "invalid display vector")
        require(all(0 < value <= 4 for value in transform["scale"]), "weapon display scale must be positive and at most 4")
    return len(elements)


def verify_weapon_atlas(data):
    """Check every PNG chunk and decode every alpha byte, including filtered scanlines."""
    require(data[:8] == b"\x89PNG\r\n\x1a\n", "invalid atlas PNG signature")
    offset, chunks = 8, []
    while offset < len(data):
        require(offset+12 <= len(data), "truncated atlas PNG chunk")
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset+4:offset+8]
        require(offset+12+length <= len(data), "truncated atlas PNG payload")
        payload = data[offset+8:offset+8+length]
        crc = struct.unpack_from(">I", data, offset+8+length)[0]
        require(zlib.crc32(kind+payload) & 0xffffffff == crc, "atlas PNG CRC mismatch")
        chunks.append((kind, payload))
        offset += length+12
    require(bool(chunks) and chunks[0][0] == b"IHDR" and chunks[-1] == (b"IEND", b""), "invalid atlas PNG structure")
    require(len(chunks[0][1]) == 13, "invalid atlas PNG header length")
    header = struct.unpack(">IIBBBBB", chunks[0][1])
    require(header == (256, 256, 8, 6, 0, 0, 0), "weapon atlas must be non-interlaced 256x256 RGBA8")
    raw = zlib.decompress(b"".join(payload for kind, payload in chunks if kind == b"IDAT"))
    stride = 256*4
    require(len(raw) == 256*(stride+1), "invalid atlas PNG image length")
    previous = bytearray(stride)
    for y in range(256):
        offset = y*(stride+1)
        filter_kind = raw[offset]
        row = bytearray(raw[offset+1:offset+1+stride])
        require(filter_kind in range(5), "invalid atlas PNG filter")
        for x in range(stride):
            left = row[x-4] if x >= 4 else 0
            above = previous[x]
            upper_left = previous[x-4] if x >= 4 else 0
            if filter_kind == 1:
                predictor = left
            elif filter_kind == 2:
                predictor = above
            elif filter_kind == 3:
                predictor = (left+above)//2
            elif filter_kind == 4:
                estimate = left+above-upper_left
                predictor = min((left, above, upper_left), key=lambda value: abs(estimate-value))
            else:
                predictor = 0
            row[x] = (row[x]+predictor) & 255
        require(all(value == 255 for value in row[3::4]), "weapon atlas must be fully opaque")
        previous = row
    return hashlib.sha256(data).hexdigest()


def ogg_crc_table():
    table = []
    for byte in range(256):
        value = byte << 24
        for _ in range(8):
            value = ((value << 1) ^ (0x04c11db7 if value & 0x80000000 else 0)) & 0xffffffff
        table.append(value)
    return table


OGG_CRC_TABLE = ogg_crc_table()


def verify_gun_sound(data):
    """Validate complete, short mono Vorbis streams without depending on an audio decoder."""
    offset, serial, sequence, packets, packet = 0, None, 0, [], bytearray()
    last_granule, ended = 0, False
    while offset < len(data):
        require(not ended and offset + 27 <= len(data), "truncated or chained Ogg stream")
        require(data[offset:offset+5] == b"OggS\x00", "invalid Ogg page header")
        flags = data[offset+5]
        require(flags & ~7 == 0, "invalid Ogg page flags")
        require(bool(flags & 1) == bool(packet), "incomplete Ogg packet continuation")
        page_serial, page_sequence = struct.unpack_from("<II", data, offset+14)
        if serial is None:
            serial = page_serial
            require(flags & 2 and page_sequence == 0, "Ogg stream must start with BOS page zero")
        else:
            require(not flags & 2 and page_serial == serial, "unexpected Ogg stream change")
        require(page_sequence == sequence, "Ogg page sequence is incomplete")
        sequence += 1
        count = data[offset+26]
        require(offset + 27 + count <= len(data), "truncated Ogg lacing table")
        lacing = data[offset+27:offset+27+count]
        payload_start = offset + 27 + count
        end = payload_start + sum(lacing)
        require(end <= len(data), "truncated Ogg page payload")
        page = bytearray(data[offset:end])
        expected_crc = struct.unpack_from("<I", page, 22)[0]
        page[22:26] = b"\x00" * 4
        crc = 0
        for value in page:
            crc = ((crc << 8) ^ OGG_CRC_TABLE[((crc >> 24) ^ value) & 255]) & 0xffffffff
        require(crc == expected_crc, "Ogg page CRC mismatch")
        cursor = payload_start
        for size in lacing:
            packet.extend(data[cursor:cursor+size])
            cursor += size
            if size < 255:
                if len(packets) < 3:
                    packets.append(bytes(packet))
                packet.clear()
        granule = struct.unpack_from("<Q", data, offset+6)[0]
        if granule != 0xffffffffffffffff:
            require(granule >= last_granule, "Ogg sample position went backwards")
            last_granule = granule
        ended = bool(flags & 4)
        offset = end
    require(ended and not packet and len(packets) == 3, "incomplete Vorbis headers or missing EOS")
    require(all(value[:7] == bytes((kind,)) + b"vorbis"
                for value, kind in zip(packets, (1, 3, 5))), "invalid Vorbis header packets")
    header = packets[0]
    require(len(header) == 30 and struct.unpack_from("<I", header, 7)[0] == 0,
            "unsupported Vorbis identification header")
    rate = struct.unpack_from("<I", header, 12)[0]
    require(header[11] == 1 and 8000 <= rate <= 96000 and header[29] & 1,
            "gun sounds must be mono Vorbis at 8..96 kHz")
    duration = last_granule / rate
    require(0 < duration <= 0.6, "gun sound duration must be inside (0,0.6] seconds")
    return rate, duration


def validate_gun_sounds(entries):
    require(isinstance(entries, dict) and set(entries) == set(GUN_SOUNDS), "gun sound events are incomplete")
    for event, file in GUN_SOUNDS.items():
        value = entries[event]
        group = "fire" if event.startswith("gun.fire.") else "spin" if event.startswith("gun.spin_") else "reload"
        require(isinstance(value, dict) and value.get("subtitle") == f"subtitle.teamlifebind.gun.{group}",
                f"missing sound subtitle: {event}")
        sounds = value.get("sounds")
        require(isinstance(sounds, list) and len(sounds) == 1, f"invalid sound entry: {event}")
        sound = sounds[0]
        require((sound if isinstance(sound, str) else sound.get("name") if isinstance(sound, dict) else None)
                == f"teamlifebind:{file}", f"unresolved sound file: {event}")


def verify_gun_resources(shared_resources, parsed_json, errors):
    gun_paths = {GUN_ATLAS}
    for weapon in GUN_MODEL_IDS:
        gun_paths.add(f"assets/teamlifebind/models/item/{weapon}.json")
    for weapon in GUN_IDS:
        gun_paths.add(f"assets/teamlifebind/items/{weapon}.json")
    gun_paths.add("assets/teamlifebind/sounds.json")
    gun_paths.update(f"assets/teamlifebind/sounds/{file}.ogg" for file in GUN_SOUNDS.values())
    for mod in MODS:
        files = shared_resources[mod]
        for weapon in GUN_MODEL_IDS:
            model_path = f"assets/teamlifebind/models/item/{weapon}.json"
            try:
                validate_gun_model(parsed_json.get((mod, model_path)))
            except (ValueError, TypeError, KeyError) as error:
                errors.append(f"{mod}/{weapon}: {error}")
        for weapon in GUN_IDS:
            entry_path = f"assets/teamlifebind/items/{weapon}.json"
            if parsed_json.get((mod, entry_path)) != gun_item_entry(weapon):
                errors.append(f"{mod}/{weapon}: invalid gun item model entry")
            for language in LANGUAGES:
                entries = parsed_json.get((mod, f"assets/teamlifebind/lang/{language}.json"), {})
                translation = entries.get(f"item.teamlifebind.{weapon}") if isinstance(entries, dict) else None
                if not isinstance(translation, str) or not translation.strip():
                    errors.append(f"{mod}/{language}: missing gun item translation: {weapon}")
        for relative in files:
            if relative.startswith("data/") and relative.endswith(".json") and ("/recipe/" in relative or "/recipes/" in relative):
                if contains_gun_recipe_reference(parsed_json.get((mod, relative))):
                    errors.append(f"{mod}/{relative}: guns must have no crafting recipes")
        try:
            verify_weapon_atlas(files[GUN_ATLAS])
        except (ValueError, KeyError, zlib.error, struct.error) as error:
            errors.append(f"{mod}/{GUN_ATLAS}: {error}")
        try:
            validate_gun_sounds(parsed_json.get((mod, "assets/teamlifebind/sounds.json")))
            for file in GUN_SOUNDS.values():
                verify_gun_sound(files[f"assets/teamlifebind/sounds/{file}.ogg"])
        except (ValueError, KeyError, struct.error) as error:
            errors.append(f"{mod}/gun sounds: {error}")
        for language in LANGUAGES:
            entries = parsed_json.get((mod, f"assets/teamlifebind/lang/{language}.json"), {})
            for key in GUN_SUBTITLES + ("key.teamlifebind.reload", "key.category.teamlifebind.controls"):
                if not isinstance(entries, dict) or not isinstance(entries.get(key), str) or not entries[key].strip():
                    errors.append(f"{mod}/{language}: missing gun control/subtitle translation: {key}")
    for path in sorted(gun_paths):
        if all(path in shared_resources[mod] for mod in MODS):
            baseline = shared_resources[MODS[0]][path]
            for mod in MODS[1:]:
                if shared_resources[mod][path] != baseline:
                    errors.append(f"{path}: gun asset bytes differ between {MODS[0]} and {mod}")


def json_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def reject_json_constant(value):
    raise ValueError(f"invalid JSON number: {value}")


def property_key(raw):
    """Decode Java Properties key escapes, including escaped separators and Unicode."""
    result = []
    index = 0
    escapes = {"t": "\t", "n": "\n", "r": "\r", "f": "\f"}
    while index < len(raw):
        character = raw[index]
        index += 1
        if character != "\\":
            result.append(character)
            continue
        if index == len(raw):
            raise ValueError("unterminated property key escape")
        character = raw[index]
        index += 1
        if character == "u":
            digits = raw[index : index + 4]
            if len(digits) != 4 or any(digit not in "0123456789abcdefABCDEF" for digit in digits):
                raise ValueError("malformed property key Unicode escape")
            result.append(chr(int(digits, 16)))
            index += 4
        else:
            result.append(escapes.get(character, character))
    return "".join(result)


def properties_keys(path):
    keys = set()
    continued = ""
    for physical_line in path.read_text(encoding="utf-8").splitlines():
        line = physical_line.lstrip(" \t\f")
        if not continued and (not line or line.startswith(("#", "!"))):
            continue
        trailing_slashes = len(line) - len(line.rstrip("\\"))
        if trailing_slashes % 2:
            continued += line[:-1]
            continue
        line = continued + line
        continued = ""
        escaped = False
        key_end = len(line)
        for index, character in enumerate(line):
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character in "=: \t\f":
                key_end = index
                break
        key = property_key(line[:key_end])
        if key in keys:
            raise ValueError(f"duplicate property key: {key}")
        keys.add(key)
    if continued:
        raise ValueError("unterminated continued property line")
    return keys


def compare_keys(label, english, chinese, errors):
    for key in sorted(english - chinese):
        errors.append(f"{label}: zh_cn is missing key {key}")
    for key in sorted(chinese - english):
        errors.append(f"{label}: en_us is missing key {key}")


def verify_magic_shaders(root, shared_resources, errors):
    for mod in MODS:
        for path in MAGIC_SHADERS:
            data = shared_resources[mod].get(path)
            if not data:
                errors.append(f"{mod}: missing production magic shader {path}")
                continue
            try:
                source = data.decode("utf-8")
                require(source.startswith("#version 330"), "magic shader must declare its GL version")
                require("void main()" in source, "missing shader entry point")
                if path.endswith(".fsh"):
                    require("uniform MagicFrame" in source and "uniform sampler2D SceneDepth" in source,
                            "missing world frame/depth shader contract")
            except (UnicodeError, ValueError) as error:
                errors.append(f"{mod}/{path}: {error}")
        runtime = root / mod / f"src/main/java/com/teamlifebind/{mod}/TeamMagicRuntime.java"
        source = runtime.read_text(encoding="utf-8")
        for forbidden in ("sendParticles(", "ParticleTypes", "DustParticleOptions"):
            if forbidden in source:
                errors.append(f"{mod}: native magic must use shader scenes; found {forbidden}")


def verify(root):
    errors = []
    shared_resources = {}
    parsed_json = {}
    json_count = 0
    for mod in MODS:
        resource_root = root / mod / "src/main/resources"
        files = {}
        for category in ("data", "assets"):
            category_root = resource_root / category
            if not category_root.is_dir():
                errors.append(f"{mod}: missing resource directory {category}")
                continue
            for path in sorted(category_root.rglob("*")):
                if path.is_file():
                    relative = path.relative_to(resource_root).as_posix()
                    try:
                        files[relative] = path.read_bytes()
                    except OSError as error:
                        errors.append(f"{mod}/{relative}: {error}")
        shared_resources[mod] = files

        # Loader metadata is parsed but excluded from cross-loader parity comparisons.
        for path in sorted(resource_root.rglob("*")):
            if not path.is_file() or path.suffix not in (".json", ".mcmeta"):
                continue
            relative = path.relative_to(resource_root).as_posix()
            try:
                parsed_json[mod, relative] = json.loads(
                    path.read_text(encoding="utf-8"),
                    object_pairs_hook=json_object,
                    parse_constant=reject_json_constant,
                )
                json_count += 1
            except (OSError, UnicodeError, ValueError) as error:
                errors.append(f"{mod}/{relative}: invalid JSON: {error}")

    shared_paths = set().union(*(set(files) for files in shared_resources.values()))
    if not shared_paths:
        errors.append("No shared mod resources were found.")
    for relative in sorted(shared_paths):
        missing = [mod for mod in MODS if relative not in shared_resources[mod]]
        if missing:
            errors.append(f"{relative}: missing from {', '.join(missing)}")
            continue
        baseline = MODS[0]
        if relative.endswith(".json"):
            if any((mod, relative) not in parsed_json for mod in MODS):
                continue
            baseline_content = json.dumps(parsed_json[baseline, relative], sort_keys=True)
            for mod in MODS[1:]:
                if json.dumps(parsed_json[mod, relative], sort_keys=True) != baseline_content:
                    errors.append(f"{relative}: JSON content differs between {baseline} and {mod}")
        else:
            for mod in MODS[1:]:
                if shared_resources[mod][relative] != shared_resources[baseline][relative]:
                    errors.append(f"{relative}: file content differs between {baseline} and {mod}")

    mod_language_keys = 0
    for mod in MODS:
        keys = {}
        for language in LANGUAGES:
            relative = f"assets/teamlifebind/lang/{language}.json"
            entries = parsed_json.get((mod, relative))
            if not isinstance(entries, dict) or not all(isinstance(value, str) for value in entries.values()):
                errors.append(f"{mod}/{relative}: expected a language object containing string values")
                continue
            keys[language] = set(entries)
            missing_hud = set(NATIVE_HUD_KEYS + NATIVE_UI_KEYS) - keys[language]
            if missing_hud:
                errors.append(f"{mod}/{relative}: missing native HUD/menu translations: {sorted(missing_hud)}")
        if len(keys) == len(LANGUAGES):
            compare_keys(f"{mod} language", keys["en_us"], keys["zh_cn"], errors)
            mod_language_keys = len(keys["en_us"])

    common_keys = {}
    for language in LANGUAGES:
        path = root / f"common/src/main/resources/teamlifebind/lang/{language}.properties"
        try:
            common_keys[language] = properties_keys(path)
        except (OSError, UnicodeError, ValueError) as error:
            errors.append(f"common/{language}.properties: {error}")
    if len(common_keys) == len(LANGUAGES):
        compare_keys("common language", common_keys["en_us"], common_keys["zh_cn"], errors)

    verify_gun_resources(shared_resources, parsed_json, errors)
    verify_magic_resources(root, shared_resources, parsed_json, errors)
    verify_magic_shaders(root, shared_resources, errors)
    for mod in MODS:
        try:
            validate_team_bed(parsed_json.get((mod, "assets/teamlifebind/items/team_bed.json")),
                              parsed_json.get((mod, "assets/teamlifebind/models/item/team_bed.json")))
        except (ValueError, TypeError, KeyError, AttributeError) as error:
            errors.append(f"{mod}/team_bed: {error}")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        print(f"Resource verification failed ({len(errors)} errors).", file=sys.stderr)
        return 1
    print(
        f"Resource verification passed: {len(shared_paths)} shared files, {json_count} JSON files, "
        f"{mod_language_keys} mod language keys, {len(common_keys['en_us'])} common language keys."
    )
    print(f"Weapon resources passed: {len(GUN_MODEL_IDS)} original cuboid models including 16 rotor phases, native display/UV bounds, identical 256x256 opaque RGBA atlas, {len(GUN_SOUNDS)} mono Vorbis sounds, no gun recipes.")
    print("Team bed resources passed: 26.3 ordinary head/foot models and foot transform; no obsolete special renderer or item parent.")
    print("Magic resources passed: eight tier models with school tint, five companions, fourteen transparent 64px textures, all 300 bilingual spell names, rebindable controls, no learning-book recipes.")
    print("World/magic shader resources passed: five native GPU shader resources in sync, including native-resolution details; no native magic particle path. GPU checks: scripts/test-magic-renderpearl.py --world-effects / --attack-stages.")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent, help="Repository root; defaults to this script's repository.")
    return verify(parser.parse_args().root.resolve())


if __name__ == "__main__":
    sys.exit(main())
