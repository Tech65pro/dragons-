#!/usr/bin/env python3
"""
DragonBrain 3.0 — resource JSON generator.

Single source of truth: BLOCKS / ITEMS / ENTITIES / SOUNDS below.
Java registry classes (ModBlocks, ModItems, ModEntities, ModSounds,
ModParticles) register exactly these ids; tools/validate_assets.py
re-reads this manifest and cross-checks the Java sources against it.

Run:  python3 tools/generate_json.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
A = os.path.join(RES, "assets/dragonbrain")
D = os.path.join(RES, "data")
MOD = "dragonbrain"

# id -> (model kind, display name)   kinds: cube, column, cross, glass, canopy, custom
BLOCKS = {
    # Void Gardens
    "lumen_moss_block":        ("cube",   "Lumen Moss Block"),
    "gloam_petal_block":       ("cube",   "Gloam Petal Block"),
    "void_lily":               ("cross",  "Void Lily"),
    # Chorus Wilds
    "chorus_bark_log":         ("column", "Chorus Bark Log"),
    "chorus_canopy":           ("canopy", "Chorus Canopy"),
    "wild_chorus_bulb":        ("cube",   "Wild Chorus Bulb"),
    "chorus_roots":            ("cross",  "Chorus Roots"),
    # Shattered Expanse
    "fractured_end_stone":     ("cube",   "Fractured End Stone"),
    "shattered_pillar":        ("column", "Shattered Pillar"),
    "endstone_rubble":         ("cube",   "Endstone Rubble"),
    "cracked_endstone_bricks": ("cube",   "Cracked Endstone Bricks"),
    # Petrified Abyss
    "petrified_stone":         ("cube",   "Petrified Stone"),
    "fossil_block":            ("cube",   "Fossil Block"),
    "engraved_abyssal_bricks": ("cube",   "Engraved Abyssal Bricks"),
    "weathered_masonry":       ("cube",   "Weathered Masonry"),
    # Astral Barrens
    "astral_dust_block":       ("cube",   "Astral Dust Block"),
    "starshale":               ("cube",   "Starshale"),
    "astral_crystal_block":    ("cube",   "Astral Crystal Block"),
    # Functional / special
    "voidglass":               ("glass",  "Voidglass"),
    "abyssal_alloy_block":     ("cube",   "Block of Abyssal Alloy"),
    "eclipse_lantern":         ("custom", "Eclipse Lantern"),
    "abyssal_trophy":          ("custom", "Abyssal Trophy"),
}

ITEMS = {
    "resonance_shard":   "Resonance Shard",
    "abyssal_alloy_ingot": "Abyssal Alloy Ingot",
    "voidglass_shard":   "Voidglass Shard",
    "rift_compass":      "Rift Compass",
    "cataclysm_scale":   "Cataclysm Scale",
    "wisp_essence":      "Wisp Essence",
    "rift_fang":         "Rift Fang",
    "sentinel_core":     "Sentinel Core",
    "end_wisp_spawn_egg": "End Wisp Spawn Egg",
    "rift_stalker_spawn_egg": "Rift Stalker Spawn Egg",
    "ancient_sentinel_spawn_egg": "Ancient Sentinel Spawn Egg",
}

ENTITIES = {
    "end_wisp": "End Wisp",
    "rift_stalker": "Rift Stalker",
    "ancient_sentinel": "Ancient Sentinel",
}

# id -> (subtitle, volume, attenuation_distance or stream)
SOUNDS = {
    # preserved from DragonBrain 2.0 (own MIT assets) — values kept from 2.0
    "dragon_roar": ("Ender Dragon roars", 0.9, 64),
    "dive_warning": ("Ender Dragon dives", 0.85, 96),
    "fireball_charge": ("Ender Dragon fireball charges", 0.55, 48),
    "fireball_launch": ("Ender Dragon spits fire", 0.7, 48),
    "wing_gust": ("Wings beat the air", 0.75, 64),
    "void_pulse": ("Void pulse ripples", 0.8, 80),
    "crystal_overcharge": ("End crystal hums with energy", 0.45, 64),
    "phase_transition": ("The dragon changes", 0.9, 128),
    "rage": ("Ender Dragon rages", 0.95, 128),
    "victory": ("Ender Dragon falls", 0.9, 128),
    "defeat": ("The void closes in", 0.8, 128),
    "stagger": ("Ender Dragon staggers", 0.7, 48),
    "end_ambience": ("End winds whisper", 0.35, None),
    "music_boss": ("DragonBrain combat theme", 0.55, "stream"),
    "music_final": ("DragonBrain final phase theme", 0.6, "stream"),
    # new in 3.0 (original synthesis)
    "end_wisp_ambient": ("End Wisp chimes", 0.5, 24),
    "end_wisp_hurt": ("End Wisp tinkles", 0.5, 24),
    "end_wisp_death": ("End Wisp fades", 0.5, 24),
    "rift_stalker_growl": ("Rift Stalker growls", 0.7, 32),
    "rift_stalker_windup": ("Rift Stalker snarls", 0.75, 32),
    "rift_stalker_bite": ("Rift Stalker bites", 0.7, 24),
    "ancient_sentinel_clang": ("Ancient Sentinel clangs", 0.7, 32),
    "ancient_sentinel_awaken": ("Ancient Sentinel awakens", 0.85, 48),
    "ancient_sentinel_step": ("Ancient Sentinel steps", 0.45, 24),
    "vault_ambience": ("Ruin winds moan", 0.35, 32),
    "discovery_chime": ("Something is discovered", 0.6, 32),
    "reward_fanfare": ("A reward is claimed", 0.7, 48),
    "rift_compass_ping": ("Rift Compass pings", 0.6, 24),
    "dragon_abyssal_transition": ("The Abyssal Cataclysm awakens", 0.95, 128),
    "eclipse_lantern_hum": ("Eclipse Lantern hums", 0.3, 16),
}

PARTICLES = ["void_mote", "resonance_spark"]

PICKAXE_BLOCKS = [b for b, (k, _n) in BLOCKS.items()
                  if k in ("cube", "column") and b not in ("lumen_moss_block", "gloam_petal_block")]
PICKAXE_BLOCKS += ["voidglass", "eclipse_lantern", "abyssal_trophy"]


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(obj, fh, indent=2)
        fh.write("\n")


def block_model(bid, kind):
    t = f"{MOD}:block/{bid}"
    if kind in ("cube", "glass", "canopy"):
        return {"parent": "minecraft:block/cube_all", "textures": {"all": t}}
    if kind == "column":
        return {"parent": "minecraft:block/cube_column",
                "textures": {"end": f"{t}_top", "side": t}}
    if kind == "cross":
        return {"parent": "minecraft:block/cross", "textures": {"cross": t}}
    if bid == "eclipse_lantern":
        side, top = t, f"{t}_top"
        return {
            "parent": "minecraft:block/block",
            "textures": {"side": side, "top": top, "particle": side},
            "elements": [
                {"from": [5, 0, 5], "to": [11, 2, 11],
                 "faces": {f: {"texture": "#top"} for f in
                           ("north", "south", "east", "west", "up", "down")}},
                {"from": [5, 2, 5], "to": [11, 9, 11],
                 "faces": {"north": {"texture": "#side"}, "south": {"texture": "#side"},
                           "east": {"texture": "#side"}, "west": {"texture": "#side"},
                           "up": {"texture": "#top"}, "down": {"texture": "#top"}}},
                {"from": [6, 9, 6], "to": [10, 11, 10],
                 "faces": {f: {"texture": "#top"} for f in
                           ("north", "south", "east", "west", "up", "down")}},
                {"from": [7, 11, 7], "to": [9, 13, 9],
                 "faces": {f: {"texture": "#top"} for f in
                           ("north", "south", "east", "west", "up")}},
            ],
        }
    if bid == "abyssal_trophy":
        base, scale = f"{MOD}:block/abyssal_trophy_base", f"{MOD}:block/abyssal_trophy_scale"
        return {
            "parent": "minecraft:block/block",
            "textures": {"base": base, "scale": scale, "particle": base},
            "elements": [
                {"from": [2, 0, 2], "to": [14, 3, 14],
                 "faces": {f: {"texture": "#base"} for f in
                           ("north", "south", "east", "west", "up", "down")}},
                {"from": [6, 3, 6], "to": [10, 7, 10],
                 "faces": {f: {"texture": "#base"} for f in
                           ("north", "south", "east", "west", "up")}},
                {"from": [5, 7, 7], "to": [11, 15, 9],
                 "rotation": {"angle": 22.5, "axis": "y", "origin": [8, 11, 8]},
                 "faces": {f: {"texture": "#scale"} for f in
                           ("north", "south", "east", "west", "up", "down")}},
            ],
        }
    raise ValueError((bid, kind))


def self_drop(bid):
    return {"type": "minecraft:block",
            "pools": [{"rolls": 1.0,
                       "entries": [{"type": "minecraft:item", "name": f"{MOD}:{bid}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


def main():
    lang = {}
    # ---- blocks: model, blockstate, item definition, loot -------------
    for bid, (kind, name) in BLOCKS.items():
        w(f"{A}/models/block/{bid}.json", block_model(bid, kind))
        w(f"{A}/blockstates/{bid}.json",
          {"variants": {"": {"model": f"{MOD}:block/{bid}"}}})
        w(f"{A}/items/{bid}.json",
          {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{bid}"}})
        w(f"{D}/{MOD}/loot_table/blocks/{bid}.json", self_drop(bid))
        lang[f"block.{MOD}.{bid}"] = name
    # axis blockstates for columns
    for bid in ("chorus_bark_log", "shattered_pillar"):
        m = f"{MOD}:block/{bid}"
        w(f"{A}/blockstates/{bid}.json", {"variants": {
            "axis=x": {"model": m, "x": 90, "y": 90},
            "axis=y": {"model": m},
            "axis=z": {"model": m, "x": 90}}})

    # ---- items ----------------------------------------------------------
    for iid, name in ITEMS.items():
        w(f"{A}/models/item/{iid}.json",
          {"parent": "minecraft:item/generated",
           "textures": {"layer0": f"{MOD}:item/{iid}"}})
        w(f"{A}/items/{iid}.json",
          {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{iid}"}})
        lang[f"item.{MOD}.{iid}"] = name

    # ---- entities, sounds, particles ------------------------------------
    for eid, name in ENTITIES.items():
        lang[f"entity.{MOD}.{eid}"] = name
    sounds_json = {}
    for sid, (sub, vol, att) in SOUNDS.items():
        entry = {"name": f"{MOD}:{sid}", "volume": vol}
        if att == "stream":
            entry["stream"] = True
        elif att:
            entry["attenuation_distance"] = att
        sounds_json[sid] = {"subtitle": f"subtitles.{MOD}.{sid}", "sounds": [entry]}
        lang[f"subtitles.{MOD}.{sid}"] = sub
    w(f"{A}/sounds.json", sounds_json)
    for pid in PARTICLES:
        w(f"{A}/particles/{pid}.json", {"textures": [f"{MOD}:{pid}"]})

    # ---- entity loot ------------------------------------------------------
    def entity_loot(pools):
        return {"type": "minecraft:entity", "pools": pools}
    def pool(entries, rolls=1.0):
        return {"rolls": rolls, "entries": entries}
    def item_e(name, weight=1, count=None, chance=None):
        e = {"type": "minecraft:item", "name": name, "weight": weight}
        if count:
            e["functions"] = [{"function": "minecraft:set_count",
                               "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}}]
        if chance is not None:
            e["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
        return e
    w(f"{D}/{MOD}/loot_table/entities/end_wisp.json",
      entity_loot([pool([item_e(f"{MOD}:wisp_essence", count=(1, 2)),
                         item_e(f"{MOD}:resonance_shard", chance=0.25)])]))
    w(f"{D}/{MOD}/loot_table/entities/rift_stalker.json",
      entity_loot([pool([item_e(f"{MOD}:rift_fang", count=(1, 2)),
                         item_e(f"{MOD}:resonance_shard", count=(0, 2))])]))
    w(f"{D}/{MOD}/loot_table/entities/ancient_sentinel.json",
      entity_loot([pool([item_e(f"{MOD}:sentinel_core"),
                         item_e(f"{MOD}:abyssal_alloy_ingot", count=(1, 3))])]))

    # ---- recipes ------------------------------------------------------------
    def shaped(rid, pattern, key, result, count=1, category="misc"):
        w(f"{D}/{MOD}/recipe/{rid}.json",
          {"type": "minecraft:crafting_shaped", "category": category,
           "pattern": pattern,
           "key": {k: {"item": v} for k, v in key.items()},
           "result": {"id": result, "count": count}})
    shaped("voidglass", ["SS", "SS"], {"S": f"{MOD}:voidglass_shard"},
           f"{MOD}:voidglass", category="building_blocks")
    shaped("abyssal_alloy_block", ["III", "III", "III"],
           {"I": f"{MOD}:abyssal_alloy_ingot"}, f"{MOD}:abyssal_alloy_block",
           category="building_blocks")
    w(f"{D}/{MOD}/recipe/abyssal_alloy_ingot_from_block.json",
      {"type": "minecraft:crafting_shapeless", "category": "misc",
       "ingredients": [{"item": f"{MOD}:abyssal_alloy_block"}],
       "result": {"id": f"{MOD}:abyssal_alloy_ingot", "count": 9}})
    shaped("abyssal_alloy_ingot", [" R ", "IPI", " R "],
           {"R": f"{MOD}:resonance_shard", "I": "minecraft:iron_ingot",
            "P": "minecraft:ender_pearl"}, f"{MOD}:abyssal_alloy_ingot", count=2)
    shaped("eclipse_lantern", [" A ", "ACA", " A "],
           {"A": f"{MOD}:abyssal_alloy_ingot", "C": f"{MOD}:astral_crystal_block"},
           f"{MOD}:eclipse_lantern", category="decorations")
    shaped("rift_compass", [" R ", "RAR", " R "],
           {"R": f"{MOD}:resonance_shard", "A": f"{MOD}:abyssal_alloy_ingot"},
           f"{MOD}:rift_compass")
    shaped("fractured_end_stone", ["EE", "EE"], {"E": f"{MOD}:endstone_rubble"},
           f"{MOD}:fractured_end_stone", count=4, category="building_blocks")
    shaped("cracked_endstone_bricks", ["EE", "EE"], {"E": "minecraft:end_stone_bricks"},
           f"{MOD}:cracked_endstone_bricks", count=4, category="building_blocks")
    shaped("abyssal_trophy", [" S ", "ABA", "BBB"],
           {"S": f"{MOD}:cataclysm_scale", "A": f"{MOD}:abyssal_alloy_ingot",
            "B": f"{MOD}:engraved_abyssal_bricks"}, f"{MOD}:abyssal_trophy",
           category="decorations")

    # ---- tags -----------------------------------------------------------------
    w(f"{D}/minecraft/tags/block/mineable/pickaxe.json",
      {"replace": False, "values": [f"{MOD}:{b}" for b in sorted(PICKAXE_BLOCKS)]})
    w(f"{D}/minecraft/tags/block/mineable/hoe.json",
      {"replace": False, "values": [f"{MOD}:lumen_moss_block", f"{MOD}:gloam_petal_block",
                                     f"{MOD}:chorus_canopy"]})
    w(f"{D}/minecraft/tags/block/needs_stone_tool.json",
      {"replace": False, "values": [f"{MOD}:voidglass", f"{MOD}:astral_crystal_block"]})
    w(f"{D}/minecraft/tags/block/needs_iron_tool.json",
      {"replace": False, "values": [f"{MOD}:abyssal_alloy_block", f"{MOD}:eclipse_lantern",
                                     f"{MOD}:abyssal_trophy", f"{MOD}:engraved_abyssal_bricks"]})

    # ---- lang -------------------------------------------------------------------
    lang.update({
        "itemGroup.dragonbrain.main": "DragonBrain",
        "text.dragonbrain.compass.found": "Rift Compass: resonance %s blocks %s",
        "text.dragonbrain.compass.none": "Rift Compass: the needle spins — no rift signature nearby",
        "text.dragonbrain.compass.not_end": "Rift Compass: dormant outside the End",
        "commands.dragonbrain.assets.header": "DragonBrain asset groups:",
        "commands.dragonbrain.assets.blocks": " blocks: %s registered",
        "commands.dragonbrain.assets.items": " items: %s registered",
        "commands.dragonbrain.assets.entities": " entity types: %s registered",
        "commands.dragonbrain.assets.sounds": " sound events: %s registered",
        "commands.dragonbrain.assets.particles": " particle types: %s registered",
        "commands.dragonbrain.assets.missing": "MISSING resource: %s",
        "commands.dragonbrain.test.assets.pass": "Asset check passed: %s/%s resources present",
        "commands.dragonbrain.test.assets.fail": "Asset check FAILED: %s missing (see above)",
        "commands.dragonbrain.test.assets.note":
            "In-game checks verify registry + resource presence only; full render validation: ./gradlew validateAssets",
    })
    w(f"{A}/lang/en_us.json", dict(sorted(lang.items())))
    emit_asset_manifest_java()
    print(f"blocks={len(BLOCKS)} items={len(ITEMS)} entities={len(ENTITIES)} "
          f"sounds={len(SOUNDS)} lang={len(lang)}")



def emit_asset_manifest_java():
    def arr(name, keys):
        body = ",\n            ".join(f'"{k}"' for k in keys)
        return f"    public static final String[] {name} = {{\n            {body}}};\n"
    java = ("package com.dragonbrain.core;\n\n"
            "/**\n * GENERATED by tools/generate_json.py from the content manifest.\n"
            " * Do not edit by hand — regenerate after changing the manifest.\n */\n"
            "public final class AssetManifest {\n"
            + arr("BLOCK_IDS", list(BLOCKS)) + arr("ITEM_IDS", list(ITEMS))
            + arr("ENTITY_IDS", list(ENTITIES)) + arr("SOUND_IDS", list(SOUNDS))
            + arr("PARTICLE_IDS", PARTICLES)
            + """    /** texture paths the client renderer depends on (checked by the Gradle validator). */
    public static final String[] DRAGON_TEXTURES = {
            "textures/entity/enderdragon/dragon.png",
            "textures/entity/enderdragon/dragon_eyes.png",
            "textures/entity/enderdragon/dragon_rage.png",
            "textures/entity/enderdragon/dragon_rage_eyes.png",
            "textures/entity/enderdragon/dragon_final.png",
            "textures/entity/enderdragon/dragon_final_eyes.png"};

    private AssetManifest() {
    }
}
""")
    path = os.path.join(ROOT, "src/main/java/com/dragonbrain/core/AssetManifest.java")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(java)


if __name__ == "__main__":
    main()
