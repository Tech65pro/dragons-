#!/usr/bin/env python3
"""
DragonBrain — world-generation data generator (biomes, features,
structures, structure loot, advancements).

Everything here is data for content registered in Java
(ModWorldgen / ModFeatures / ModBlocks / ModEntities).  Structure
templates (.nbt) are painted voxel-by-voxel below and written as real
gzipped structure-template NBT (DataVersion 4671 == MC 1.21.11).

Run:  python3 tools/generate_worldgen.py
"""
import gzip
import io
import json
import os
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, "src/main/resources/data")
MOD = "dragonbrain"
DATA_VERSION = 4671  # Minecraft 1.21.11

BIOMES = ["void_gardens", "chorus_wilds", "shattered_expanse",
          "petrified_abyss", "astral_barrens"]
STRUCTURES = ["ancient_end_ruins", "abandoned_observatory",
              "resonance_vault", "sentinel_sanctuary"]


def w(rel, obj):
    p = os.path.join(D, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as fh:
        json.dump(obj, fh, indent=2)
        fh.write("\n")
    return p


# ---------------------------------------------------------------- NBT writer
# Minimal big-endian NBT writer (named tags) sufficient for structure
# templates: compounds, lists, strings, ints, longs, doubles, bytes.
TAG_COMPOUND, TAG_LIST, TAG_STRING, TAG_INT_ARRAY = 10, 9, 8, 11
TAG_INT, TAG_LONG, TAG_DOUBLE, TAG_BYTE = 3, 4, 6, 1


class NbtWriter(io.BytesIO):
    def _name(self, name):
        b = name.encode()
        self.write(struct.pack(">H", len(b)))
        self.write(b)

    def named(self, tag, name):
        self.write(bytes([tag]))
        self._name(name)

    def byte(self, name, v):
        self.named(TAG_BYTE, name); self.write(struct.pack(">b", v))

    def int(self, name, v):
        self.named(TAG_INT, name); self.write(struct.pack(">i", v))

    def long(self, name, v):
        self.named(TAG_LONG, name); self.write(struct.pack(">q", v))

    def double(self, name, v):
        self.named(TAG_DOUBLE, name); self.write(struct.pack(">d", v))

    def string(self, name, v):
        self.named(TAG_STRING, name); self._name(v)

    def int_list(self, name, values):
        self.named(TAG_LIST, name)
        self.write(bytes([TAG_INT])); self.write(struct.pack(">i", len(values)))
        for v in values:
            self.write(struct.pack(">i", v))

    def int_array(self, name, values):
        # Structure templates store size/pos/blockPos as TAG_Int_Array (11);
        # vanilla getIntArray() silently returns empty for plain int lists.
        self.named(TAG_INT_ARRAY, name)
        self.write(struct.pack(">i", len(values)))
        for v in values:
            self.write(struct.pack(">i", v))

    def double_list(self, name, values):
        self.named(TAG_LIST, name)
        self.write(bytes([TAG_DOUBLE])); self.write(struct.pack(">i", len(values)))
        for v in values:
            self.write(struct.pack(">d", v))

    def compound_list(self, name, items):
        self.named(TAG_LIST, name)
        self.write(bytes([TAG_COMPOUND])); self.write(struct.pack(">i", len(items)))
        for item in items:
            item(self)
            self.write(b"\x00")

    def begin_compound(self, name):
        self.named(TAG_COMPOUND, name)

    def end(self):
        self.write(b"\x00")


class Template:
    """Voxel-painted structure template. Coordinates are template-local."""

    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}     # (x,y,z) -> (block_id, properties dict, nbt dict|None)
        self.entities = []   # ((x,y,z), nbt dict-of-simple-strings)

    def set(self, x, y, z, block, props=None, nbt=None):
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            if block is None:
                self.blocks.pop((x, y, z), None)
            else:
                self.blocks[(x, y, z)] = (block, props or {}, nbt)

    def box(self, x0, y0, z0, x1, y1, z1, block, props=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, block, props)

    def frame(self, x0, y0, z0, x1, y1, z1, block):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    if x in (x0, x1) or y in (y0, y1) or z in (z0, z1):
                        self.set(x, y, z, block)

    def chest(self, x, y, z, loot_table, facing="north"):
        self.set(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"},
                 {"id": "minecraft:chest", "LootTable": loot_table})

    def entity(self, x, y, z, entity_id):
        self.entities.append(((x + 0.5, float(y), z + 0.5),
                              {"id": entity_id}))

    def save(self, rel_path):
        palette, index = [], {}
        block_entries = []
        for pos in sorted(self.blocks):
            block, props, nbt = self.blocks[pos]
            key = (block, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                palette.append((block, props))
            block_entries.append((pos, index[key], nbt))

        out = NbtWriter()
        out.begin_compound("")
        out.int("DataVersion", DATA_VERSION)
        out.int_array("size", list(self.size))

        def palette_entry(entry):
            block, props = entry
            def write(w_):
                w_.string("Name", block)
                if props:
                    w_.begin_compound("Properties")
                    for k, v in sorted(props.items()):
                        w_.string(k, v)
                    w_.end()
            return write
        out.compound_list("palette", [palette_entry(p) for p in palette])

        def block_entry(pos, state, nbt):
            def write(w_):
                w_.int_array("pos", list(pos))
                w_.int("state", state)
                if nbt:
                    w_.begin_compound("nbt")
                    for k, v in nbt.items():
                        w_.string(k, v)
                    w_.end()
            return write
        out.compound_list("blocks", [block_entry(p, s, n) for p, s, n in block_entries])

        def entity_entry(pos, nbt):
            def write(w_):
                w_.double_list("pos", list(pos))
                w_.int_array("blockPos", [int(pos[0]), int(pos[1]), int(pos[2])])
                w_.begin_compound("nbt")
                for k, v in nbt.items():
                    w_.string(k, v)
                w_.end()
            return write
        out.compound_list("entities", [entity_entry(p, n) for p, n in self.entities])
        out.end()

        p = os.path.join(D, rel_path)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with gzip.open(p, "wb") as fh:
            fh.write(out.getvalue())
        return p


# ---------------------------------------------------------------- structures
DB = MOD  # shorthand


def ruin_a():
    """Broken courtyard: weathered walls, fractured floor, one chest."""
    t = Template(13, 7, 11)
    t.box(0, 0, 0, 12, 0, 10, f"{DB}:weathered_masonry")
    for (x, z) in [(2, 2), (5, 7), (9, 3), (7, 8), (3, 5)]:
        t.set(x, 0, z, f"{DB}:fractured_end_stone")
    # west + north walls with battle damage
    for z in range(0, 11):
        for y in range(1, 5):
            if (z * 3 + y) % 5 != 0:
                t.set(0, y, z, f"{DB}:weathered_masonry")
    for x in range(0, 13):
        for y in range(1, 4):
            if (x + y * 2) % 4 != 0:
                t.set(x, y, 0, f"{DB}:weathered_masonry")
    t.box(0, 5, 0, 0, 5, 10, f"{DB}:engraved_abyssal_bricks")
    # fallen pillar pieces
    for i in range(4):
        t.set(5 + i, 1, 6, f"{DB}:shattered_pillar", {"axis": "x"})
    for y in range(1, 4):
        t.set(10, y, 8, f"{DB}:shattered_pillar", {"axis": "y"})
    t.set(6, 1, 3, f"{DB}:fossil_block")
    t.chest(1, 1, 9, f"{DB}:chests/ancient_end_ruins", facing="east")
    t.set(12, 1, 0, f"{DB}:chorus_roots")
    t.set(4, 1, 9, f"{DB}:endstone_rubble")
    return t


def ruin_b():
    """Collapsed hall: pillar stubs, engraved back wall, rubble, chest."""
    t = Template(11, 8, 9)
    t.box(0, 0, 0, 10, 0, 8, f"{DB}:petrified_stone")
    t.box(0, 1, 8, 10, 6, 8, f"{DB}:engraved_abyssal_bricks")
    for (x, z, h) in [(1, 1, 4), (5, 1, 2), (9, 1, 5), (1, 5, 3), (9, 5, 1)]:
        for y in range(1, h + 1):
            t.set(x, y, z, f"{DB}:shattered_pillar", {"axis": "y"})
    for (x, z) in [(3, 3), (6, 4), (4, 6), (7, 2)]:
        t.set(x, 1, z, f"{DB}:endstone_rubble")
    t.set(5, 1, 7, f"{DB}:fossil_block")
    t.chest(5, 1, 6, f"{DB}:chests/ancient_end_ruins", facing="north")
    t.set(2, 1, 0, f"{DB}:void_lily")
    return t


def observatory():
    """Tall round tower with crystal optics and a star-chart floor."""
    t = Template(11, 16, 11)
    cx, cz, r = 5, 5, 5
    for x in range(11):
        for z in range(11):
            d2 = (x - cx) ** 2 + (z - cz) ** 2
            if d2 <= r * r:
                t.set(x, 0, z, f"{DB}:petrified_stone")
                if 16 < d2 <= 25:
                    for y in range(1, 13):
                        block = f"{DB}:voidglass" if y in (5, 9) and (x + z) % 3 == 0 else f"{DB}:engraved_abyssal_bricks"
                        t.set(x, y, z, block)
    # door (south)
    for y in range(1, 4):
        t.set(cx, y, 10, None)
    # floors + stairs of starshale
    for lvl, fy in ((1, 4), (2, 8)):
        for x in range(11):
            for z in range(11):
                if (x - cx) ** 2 + (z - cz) ** 2 <= 16 and not (lvl == 1 and x > 7 and z < 3) and not (lvl == 2 and x < 3 and z > 7):
                    t.set(x, fy, z, f"{DB}:starshale")
    for i in range(4):
        t.set(8, 1 + i, 2, f"{DB}:petrified_stone")
        t.set(2, 5 + i, 8, f"{DB}:petrified_stone")
    # roof ring + astral crystal telescope core
    for x in range(11):
        for z in range(11):
            d2 = (x - cx) ** 2 + (z - cz) ** 2
            if 9 < d2 <= 25:
                t.set(x, 13, z, f"{DB}:petrified_stone")
    t.set(cx, 9, cz, f"{DB}:abyssal_alloy_block")
    t.set(cx, 10, cz, f"{DB}:astral_crystal_block")
    t.set(cx, 11, cz, f"{DB}:astral_crystal_block")
    t.set(cx + 1, 1, cz, f"{DB}:eclipse_lantern")
    t.chest(cx - 2, 9, cz + 2, f"{DB}:chests/abandoned_observatory", facing="east")
    t.chest(cx + 2, 1, cz - 2, f"{DB}:chests/ancient_end_ruins", facing="west")
    return t


def vault():
    """Sealed resonance vault: thick walls, alloy core room, two chests."""
    t = Template(13, 8, 13)
    t.frame(0, 0, 0, 12, 7, 12, f"{DB}:engraved_abyssal_bricks")
    t.box(1, 0, 1, 11, 0, 11, f"{DB}:engraved_abyssal_bricks")
    # entrance notch (north), deliberately narrow
    t.set(6, 1, 0, None); t.set(6, 2, 0, None)
    t.set(6, 3, 0, f"{DB}:voidglass")
    # inner resonance pylon
    t.set(6, 1, 6, f"{DB}:abyssal_alloy_block")
    t.set(6, 2, 6, f"{DB}:astral_crystal_block")
    t.set(6, 3, 6, f"{DB}:astral_crystal_block")
    t.set(6, 4, 6, f"{DB}:eclipse_lantern")
    # corner lanterns
    for (x, z) in [(2, 2), (10, 2), (2, 10), (10, 10)]:
        t.set(x, 1, z, f"{DB}:eclipse_lantern")
    t.chest(6, 1, 10, f"{DB}:chests/resonance_vault", facing="north")
    t.chest(3, 1, 6, f"{DB}:chests/resonance_vault", facing="east")
    # glyph floor inlay
    for x in range(4, 9):
        t.set(x, 0, 4, f"{DB}:petrified_stone")
        t.set(x, 0, 8, f"{DB}:petrified_stone")
    return t


def sanctuary():
    """Sentinel arena: ring platform, four pillars, guardian + reward."""
    t = Template(15, 10, 15)
    cx, cz = 7, 7
    for x in range(15):
        for z in range(15):
            d2 = (x - cx) ** 2 + (z - cz) ** 2
            if d2 <= 49:
                t.set(x, 0, z, f"{DB}:petrified_stone" if d2 > 16 else f"{DB}:engraved_abyssal_bricks")
            if 36 < d2 <= 49:
                t.set(x, 1, z, f"{DB}:weathered_masonry")
    for (x, z) in [(3, 3), (11, 3), (3, 11), (11, 11)]:
        for y in range(1, 7):
            t.set(x, y, z, f"{DB}:shattered_pillar", {"axis": "y"})
        t.set(x, 7, z, f"{DB}:eclipse_lantern")
    t.box(6, 1, 6, 8, 1, 8, f"{DB}:fossil_block")
    t.chest(7, 2, 7, f"{DB}:chests/sentinel_sanctuary", facing="south")
    t.entity(7, 1, 4, f"{DB}:ancient_sentinel")
    return t


STRUCTURE_TEMPLATES = {
    "ancient_end_ruins/ruin_a": ruin_a,
    "ancient_end_ruins/ruin_b": ruin_b,
    "abandoned_observatory/tower": observatory,
    "resonance_vault/vault": vault,
    "sentinel_sanctuary/arena": sanctuary,
}

STRUCTURE_META = {
    # name: (templates[(path,weight)], set spacing, separation, salt, biomes, terrain_adaptation)
    "ancient_end_ruins": ([("ancient_end_ruins/ruin_a", 2), ("ancient_end_ruins/ruin_b", 2)],
                          24, 8, 90210101, ["petrified_abyss", "shattered_expanse", "minecraft:end_highlands"], "beard_thin"),
    "abandoned_observatory": ([("abandoned_observatory/tower", 1)],
                              40, 12, 90210202, ["astral_barrens", "chorus_wilds"], "beard_thin"),
    "resonance_vault": ([("resonance_vault/vault", 1)],
                        36, 11, 90210303, ["void_gardens", "petrified_abyss"], "bury"),
    "sentinel_sanctuary": ([("sentinel_sanctuary/arena", 1)],
                           44, 14, 90210404, ["petrified_abyss", "astral_barrens"], "beard_thin"),
}


def make_structures():
    for path, factory in STRUCTURE_TEMPLATES.items():
        factory().save(f"{MOD}/structure/{path}.nbt")
    for name, (templates, spacing, separation, salt, biomes, adaptation) in STRUCTURE_META.items():
        w(f"{MOD}/worldgen/template_pool/{name}/starts.json", {
            "name": f"{DB}:{name}/starts",
            "fallback": "minecraft:empty",
            "elements": [
                {"weight": weight,
                 "element": {"element_type": "minecraft:single_pool_element",
                             "location": f"{DB}:{path}",
                             "projection": "rigid",
                             "processors": {"processor_type": "minecraft:empty",
                                            "processors": []}}}
                for path, weight in templates],
        })
        w(f"{MOD}/worldgen/structure/{name}.json", {
            "type": "minecraft:jigsaw",
            "biomes": f"#{DB}:has_structure/{name}",
            "step": "surface_structures",
            "spawn_overrides": {},
            "terrain_adaptation": adaptation,
            "start_pool": f"{DB}:{name}/starts",
            "size": 1,
            "start_height": {"absolute": 0},
            "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 48,
            "use_expansion_hack": False,
        })
        w(f"{MOD}/worldgen/structure_set/{name}.json", {
            "structures": [{"structure": f"{DB}:{name}", "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing,
                          "separation": separation, "salt": salt},
        })
        w(f"{MOD}/tags/worldgen/biome/has_structure/{name}.json",
          {"values": [b if ":" in b else f"{DB}:{b}" for b in biomes]})
        w(f"{MOD}/tags/worldgen/structure/locate/{name}.json",
          {"values": [f"{DB}:{name}"]})
    w(f"{MOD}/tags/worldgen/structure/rift_compass_targets.json",
      {"values": [f"{DB}:{s}" for s in STRUCTURES]})

    def chest_loot(name, pools):
        w(f"{MOD}/loot_table/chests/{name}.json", {"type": "minecraft:chest", "pools": pools})

    def item(name, wgt=1, count=None, chance=None):
        e = {"type": "minecraft:item", "name": name, "weight": wgt}
        if count:
            e["functions"] = [{"function": "minecraft:set_count",
                               "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}}]
        if chance is not None:
            e["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
        return e

    chest_loot("ancient_end_ruins", [
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 5},
         "entries": [item(f"{DB}:resonance_shard", 3, (1, 3)), item("minecraft:ender_pearl", 3, (1, 2)),
                     item("minecraft:chorus_fruit", 4, (1, 4)), item(f"{DB}:voidglass_shard", 3, (1, 3)),
                     item("minecraft:iron_ingot", 2, (1, 3)), item(f"{DB}:rift_fang", 1, (1, 2))]},
    ])
    chest_loot("abandoned_observatory", [
        {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6},
         "entries": [item(f"{DB}:resonance_shard", 4, (2, 4)), item(f"{DB}:astral_crystal_block", 2, (1, 2)),
                     item(f"{DB}:rift_compass", 1), item("minecraft:spyglass", 1),
                     item("minecraft:ender_pearl", 3, (1, 3)), item("minecraft:experience_bottle", 2, (2, 5))]},
    ])
    chest_loot("resonance_vault", [
        {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6},
         "entries": [item(f"{DB}:abyssal_alloy_ingot", 3, (1, 3)), item(f"{DB}:resonance_shard", 4, (2, 5)),
                     item(f"{DB}:eclipse_lantern", 2, (1, 1)), item(f"{DB}:voidglass", 2, (1, 2)),
                     item("minecraft:diamond", 2, (1, 3)), item(f"{DB}:sentinel_core", 1)]},
        {"rolls": 1, "entries": [item(f"{DB}:rif​t_compass".replace("​", ""), 1, chance=0.35)]},
    ])
    chest_loot("sentinel_sanctuary", [
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4},
         "entries": [item(f"{DB}:sentinel_core", 2), item(f"{DB}:abyssal_alloy_ingot", 3, (1, 2)),
                     item("minecraft:golden_apple", 1), item("minecraft:ender_pearl", 3, (2, 4))]},
    ])


# ---------------------------------------------------------------- features
def state(name, props=None):
    s = {"Name": name}
    if props:
        s["Properties"] = props
    return s


def ore_config(target_block, result, size, discard=0.0):
    return {"type": "minecraft:ore",
            "config": {"discard_chance_on_air_exposure": discard, "size": size,
                       "targets": [{"state": state(result),
                                    "target": {"predicate_type": "minecraft:block_match",
                                               "block": target_block}}]}}


def placed_heightmap(feature, count=None, chance=None):
    placement = []
    if count is not None:
        placement.append({"type": "minecraft:count", "count": count})
    if chance is not None:
        placement.append({"type": "minecraft:rarity_filter", "chance": chance})
    placement += [{"type": "minecraft:in_square"},
                  {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                  {"type": "minecraft:biome"}]
    return {"feature": feature, "placement": placement}


def uniform_height_placed(feature, chance, y_min=55, y_max=75):
    return {"feature": feature,
            "placement": [{"type": "minecraft:rarity_filter", "chance": chance},
                          {"type": "minecraft:in_square"},
                          {"type": "minecraft:height_range",
                           "height": {"type": "minecraft:uniform",
                                      "min_inclusive": {"absolute": y_min},
                                      "max_inclusive": {"absolute": y_max}}},
                          {"type": "minecraft:biome"}]}


def simple_patch(name, block, tries=24):
    """configured simple_block + inner placed + configured random_patch + outer placed"""
    w(f"{MOD}/worldgen/configured_feature/{name}_plant.json",
      {"type": "minecraft:simple_block",
       "config": {"to_place": {"type": "minecraft:simple_state_provider",
                               "state": state(block)}}})
    w(f"{MOD}/worldgen/placed_feature/{name}_plant.json",
      {"feature": f"{DB}:{name}_plant", "placement": []})
    w(f"{MOD}/worldgen/configured_feature/{name}_patch.json",
      {"type": "minecraft:random_patch",
       "config": {"feature": f"{DB}:{name}_plant", "tries": tries,
                  "xz_spread": 7, "y_spread": 3}})


def formation(name, primary, secondary, style, min_h, max_h, *, count=None, chance=None, air=False):
    w(f"{MOD}/worldgen/configured_feature/{name}.json",
      {"type": f"{DB}:end_formation",
       "config": {"primary": primary, "secondary": secondary, "style": style,
                  "min_height": min_h, "max_height": max_h}})
    if air:
        w(f"{MOD}/worldgen/placed_feature/{name}.json",
          uniform_height_placed(f"{DB}:{name}", chance))
    else:
        w(f"{MOD}/worldgen/placed_feature/{name}.json",
          placed_heightmap(f"{DB}:{name}", count=count, chance=chance))


def make_features():
    # ---- ground-material ore patches (biome surface identity) ----
    ores = {
        "lumen_moss_patches": ("minecraft:end_stone", f"{DB}:lumen_moss_block", 28),
        "gloam_petal_patches": ("minecraft:end_stone", f"{DB}:gloam_petal_block", 18),
        "fractured_ground": ("minecraft:end_stone", f"{DB}:fractured_end_stone", 32),
        "rubble_patches": ("minecraft:end_stone", f"{DB}:endstone_rubble", 24),
        "petrified_layers": ("minecraft:end_stone", f"{DB}:petrified_stone", 40),
        "fossil_veins": ("minecraft:end_stone", f"{DB}:fossil_block", 10),
        "astral_dust_fields": ("minecraft:end_stone", f"{DB}:astral_dust_block", 40),
        "starshale_veins": ("minecraft:end_stone", f"{DB}:starshale", 18),
        "moss_on_petrified": (f"{DB}:petrified_stone", f"{DB}:weathered_masonry", 16),
    }
    for name, (target, result, size) in ores.items():
        w(f"{MOD}/worldgen/configured_feature/{name}.json", ore_config(target, result, size))
        w(f"{MOD}/worldgen/placed_feature/{name}.json",
          placed_heightmap(f"{DB}:{name}", count=10))
    # patches that are rarer get smaller counts
    for name, count in (("fossil_veins", 3), ("starshale_veins", 5),
                        ("gloam_petal_patches", 5), ("moss_on_petrified", 4)):
        w(f"{MOD}/worldgen/placed_feature/{name}.json",
          placed_heightmap(f"{DB}:{name}", count=count))

    # ---- plants ----
    simple_patch("void_lily", f"{DB}:void_lily")
    simple_patch("chorus_roots", f"{DB}:chorus_roots")
    simple_patch("chorus_bulbs", f"{DB}:wild_chorus_bulb", tries=10)
    for name, count in (("void_lily", 6), ("chorus_roots", 8), ("chorus_bulbs", 4)):
        w(f"{MOD}/worldgen/placed_feature/{name}_patch.json",
          placed_heightmap(f"{DB}:{name}_patch", count=count))

    # ---- custom formations (Java: EndFormationFeature) ----
    formation("chorus_trees", f"{DB}:chorus_bark_log", f"{DB}:chorus_canopy",
              "tree", 5, 9, count=7)
    formation("shattered_spires", f"{DB}:fractured_end_stone", f"{DB}:shattered_pillar",
              "pillar", 6, 16, chance=3)
    formation("petrified_columns", f"{DB}:petrified_stone", f"{DB}:fossil_block",
              "pillar", 4, 9, chance=4)
    formation("astral_shards", f"{DB}:astral_crystal_block", f"{DB}:starshale",
              "spike", 3, 7, chance=5)
    formation("astral_monoliths", f"{DB}:starshale", f"{DB}:astral_crystal_block",
              "pillar", 9, 18, chance=16)
    formation("garden_spires", f"{DB}:chorus_bark_log", f"{DB}:gloam_petal_block",
              "tree", 4, 7, chance=4)


# ---------------------------------------------------------------- biomes
def spawner(entity, weight, mn, mx):
    return {"type": entity, "weight": weight, "minCount": mn, "maxCount": mx}


def biome(fog, *, monster=(), ambient=(), creature=(), features6=(), features7=(), features9=(),
          mood=None):
    spawners = {"ambient": list(ambient), "axolotls": [], "creature": list(creature),
                "misc": [], "monster": [spawner("minecraft:enderman", 10, 4, 4), *monster],
                "underground_water_creature": [], "water_ambient": [], "water_creature": []}
    feats = [[] for _ in range(11)]
    feats[6] = list(features6)
    feats[7] = list(features7)
    feats[9] = list(features9)
    effects = {"sky_color": 0, "fog_color": fog, "water_color": 4159204, "water_fog_color": 329011}
    if mood:
        effects["mood_sound"] = {"sound_id": mood, "tick_delay": 6000,
                                 "block_search_extent": 8, "offset": 2.0}
    return {"temperature": 0.5, "downfall": 0.5, "has_precipitation": False,
            "effects": effects, "spawners": spawners, "spawn_costs": {},
            "carvers": [], "features": feats}


def f(*names):
    return [f"{DB}:{n}" for n in names]


def make_biomes():
    w(f"{MOD}/worldgen/biome/void_gardens.json", biome(
        0x2A1E4A,
        ambient=[spawner(f"{DB}:end_wisp", 10, 1, 3)],
        features6=f("lumen_moss_patches", "gloam_petal_patches"),
        features9=f("void_lily_patch", "garden_spires") + ["minecraft:chorus_plant"],
        mood=f"{DB}:end_ambience"))
    w(f"{MOD}/worldgen/biome/chorus_wilds.json", biome(
        0x33204D,
        monster=[spawner(f"{DB}:rift_stalker", 4, 1, 2)],
        ambient=[spawner(f"{DB}:end_wisp", 4, 1, 2)],
        features9=f("chorus_trees", "chorus_roots_patch", "chorus_bulbs_patch") + ["minecraft:chorus_plant"]))
    w(f"{MOD}/worldgen/biome/shattered_expanse.json", biome(
        0x241D1E,
        monster=[spawner(f"{DB}:rift_stalker", 5, 1, 2)],
        features6=f("fractured_ground", "rubble_patches"),
        features7=f("shattered_spires")))
    w(f"{MOD}/worldgen/biome/petrified_abyss.json", biome(
        0x1C1A2E,
        features6=f("petrified_layers", "fossil_veins", "moss_on_petrified"),
        features7=f("petrified_columns"),
        mood=f"{DB}:vault_ambience"))
    w(f"{MOD}/worldgen/biome/astral_barrens.json", biome(
        0x1A2233,
        monster=[spawner(f"{DB}:rift_stalker", 3, 1, 1)],
        ambient=[spawner(f"{DB}:end_wisp", 3, 1, 2)],
        features6=f("astral_dust_fields", "starshale_veins"),
        features7=f("astral_shards", "astral_monoliths")))


# ---------------------------------------------------------------- advancements
def make_advancements():
    def adv(name, parent, icon_item, criteria, frame="task"):
        a = {"display": {"icon": {"id": icon_item},
                         "title": {"translate": f"advancements.dragonbrain.{name}"},
                         "description": {"translate": f"advancements.dragonbrain.{name}.desc"},
                         "frame": frame},
             "criteria": criteria}
        if parent:
            a["parent"] = f"{DB}:{parent}"
        w(f"{MOD}/advancement/{name}.json", a)

    def inv(item):
        return {"trigger": "minecraft:inventory_changed",
                "conditions": {"items": [{"items": item}]}}

    adv("root", None, f"{DB}:eclipse_lantern", {"enter_end": {
        "trigger": "minecraft:changed_dimension", "conditions": {"to": "minecraft:the_end"}}})
    adv("resonance", "root", f"{DB}:resonance_shard", {"has": inv(f"{DB}:resonance_shard")})
    adv("abyssal_alloy", "resonance", f"{DB}:abyssal_alloy_ingot", {"has": inv(f"{DB}:abyssal_alloy_ingot")})
    adv("rift_compass", "abyssal_alloy", f"{DB}:rift_compass", {"has": inv(f"{DB}:rift_compass")})
    adv("wisp_friend", "root", f"{DB}:wisp_essence", {"has": inv(f"{DB}:wisp_essence")})
    adv("sentinel_slayer", "resonance", f"{DB}:sentinel_core", {"has": inv(f"{DB}:sentinel_core")}, frame="challenge")
    adv("cataclysm", "root", f"{DB}:cataclysm_scale", {"has": inv(f"{DB}:cataclysm_scale")}, frame="goal")
    for b in BIOMES:
        adv(f"visit_{b}", "root", f"{DB}:resonance_shard",
            {"visit": {"trigger": "minecraft:location",
                       "conditions": {"player": [{
                           "condition": "minecraft:entity_properties",
                           "entity": "this",
                           "predicate": {"location": {"biomes": f"{DB}:{b}"}}}]}}})


if __name__ == "__main__":
    make_features()
    make_biomes()
    make_structures()
    make_advancements()
    n = sum(len(fs) for _r, _d, fs in os.walk(os.path.join(D, MOD, "worldgen")))
    print(f"worldgen: {len(BIOMES)} biomes, {len(STRUCTURES)} structures, {n} worldgen files")
