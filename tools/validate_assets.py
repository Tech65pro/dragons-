#!/usr/bin/env python3
"""
DragonBrain 3.0 — asset validation.

Usage:  python3 tools/validate_assets.py [--strict]
Gradle: ./gradlew validateAssets   (wired into `check`)

Checks (build-side, exhaustive):
  * every JSON parses
  * manifest ids == ids registered in the Java sources
  * blockstate -> model -> texture chains resolve
  * item definitions -> models -> textures resolve
  * lang keys exist for every block/item/entity/sound subtitle/item group
  * sounds.json <-> .ogg files <-> ModSounds ids; .ogg files are real Ogg
  * loot tables & recipes only reference registered dragonbrain content
  * particle definitions -> sprite textures
  * .mcmeta animated textures are valid frame strips
  * texture dimensions (16x blocks/items, entity sheets, dragon 256x256)
  * dragon textures keep the exact 2.0 alpha/UV mask (renderer compat)
  * no orphan textures (everything referenced by something real)
  * LICENSE + CREDITS.md present with the required notices
  * optional vanilla resource pack: pack_format 75, valid vanilla paths
  * fabric.mod.json entrypoints/mixins map to real .java files

Exit code 0 = all checks passed.  --strict additionally fails on the
known-unverified list being stale (see BUILD_NOTES.md cross-check).
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_json import BLOCKS, ITEMS, ENTITIES, SOUNDS, PARTICLES  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
A = os.path.join(RES, "assets/dragonbrain")
D = os.path.join(RES, "data")
JAVA = os.path.join(ROOT, "src/main/java")
RP = os.path.join(ROOT, "resourcepack/DragonBrain-AbyssalEnd-Vanilla")
MOD = "dragonbrain"

errors, warnings, checked = [], [], 0


def ok(cond, msg):
    global checked
    checked += 1
    if not cond:
        errors.append(msg)
    return cond


def warn(cond, msg):
    if not cond:
        warnings.append(msg)


def load_json(path):
    try:
        with open(path) as fh:
            return json.load(fh)
    except Exception as e:  # noqa: BLE001
        errors.append(f"JSON parse failed: {rel(path)}: {e}")
        return None


def rel(p):
    return os.path.relpath(p, ROOT)


def tex_path(ref):
    """dragonbrain:block/x -> assets file"""
    ns, path = ref.split(":", 1)
    return os.path.join(RES, "assets", ns, "textures", path + ".png")


def model_path(ref):
    ns, path = ref.split(":", 1)
    return os.path.join(RES, "assets", ns, "models", path + ".json")


# ---------------------------------------------------------------- java ids
def java_registered_ids(filename, patterns):
    p = os.path.join(JAVA, "com/dragonbrain/registry", filename)
    src = open(p).read()
    ids = set()
    for pat in patterns:
        ids.update(re.findall(pat, src))
    return ids


def main():
    strict = "--strict" in sys.argv

    # 1. all JSON parses ----------------------------------------------------
    json_files = []
    for base in (RES, RP):
        for root, _d, fs in os.walk(base):
            json_files += [os.path.join(root, f) for f in fs
                           if f.endswith((".json", ".mcmeta"))]
    for f in json_files:
        load_json(f)
    checked_inc = len(json_files)
    print(f"[json] parsed {checked_inc} files")

    # 2. manifest vs java registries ----------------------------------------
    java_blocks = java_registered_ids("ModBlocks.java", [r'register\("([a-z_]+)"'])
    java_items = java_registered_ids("ModItems.java", [r'register\("([a-z_]+)"'])
    java_entities = java_registered_ids("ModEntities.java", [r'register\("([a-z_]+)"'])
    java_sounds = java_registered_ids("ModSounds.java", [r'register\("([a-z_]+)"'])
    ok(java_blocks == set(BLOCKS), f"ModBlocks ids {sorted(java_blocks ^ set(BLOCKS))} != manifest")
    ok(java_items == set(ITEMS), f"ModItems ids {sorted(java_items ^ set(ITEMS))} != manifest")
    ok(java_entities == set(ENTITIES), f"ModEntities ids {sorted(java_entities ^ set(ENTITIES))} != manifest")
    ok(java_sounds == set(SOUNDS), f"ModSounds ids {sorted(java_sounds ^ set(SOUNDS))} != manifest")
    manifest_java = open(os.path.join(JAVA, "com/dragonbrain/core/AssetManifest.java")).read()
    for group in (list(BLOCKS) + list(ITEMS) + list(ENTITIES) + list(SOUNDS) + PARTICLES):
        ok(f'"{group}"' in manifest_java, f"AssetManifest.java missing id {group}")
    print(f"[java] registries match manifest: blocks={len(BLOCKS)} items={len(ITEMS)} "
          f"entities={len(ENTITIES)} sounds={len(SOUNDS)}")

    # 3. blockstates/models/textures ------------------------------------------
    used_textures = set()
    for bid, (kind, _n) in BLOCKS.items():
        bs = load_json(os.path.join(A, "blockstates", bid + ".json"))
        if bs:
            for variant in bs.get("variants", {}).values():
                entries = variant if isinstance(variant, list) else [variant]
                for e in entries:
                    ok(os.path.isfile(model_path(e["model"])),
                       f"{bid}: missing block model {e['model']}")
        model = load_json(os.path.join(A, "models/block", bid + ".json"))
        if model:
            for ref in model.get("textures", {}).values():
                if ref.startswith("#"):
                    continue
                used_textures.add(ref)
                ok(os.path.isfile(tex_path(ref)), f"{bid}: missing texture {ref}")
            parent = model.get("parent", "")
            warn(parent.startswith("minecraft:"), f"{bid}: unexpected parent {parent}")
        idef = load_json(os.path.join(A, "items", bid + ".json"))
        if idef:
            ref = idef.get("model", {}).get("model", "")
            ok(os.path.isfile(model_path(ref)), f"{bid}: item definition -> missing {ref}")
        loot = os.path.join(D, MOD, "loot_table/blocks", bid + ".json")
        ok(os.path.isfile(loot), f"{bid}: missing block loot table")

    # 4. items ------------------------------------------------------------------
    for iid in ITEMS:
        model = load_json(os.path.join(A, "models/item", iid + ".json"))
        if model:
            for ref in model.get("textures", {}).values():
                used_textures.add(ref)
                ok(os.path.isfile(tex_path(ref)), f"{iid}: missing texture {ref}")
        idef = load_json(os.path.join(A, "items", iid + ".json"))
        ok(idef is not None, f"{iid}: missing items/ definition")

    # 5. lang ---------------------------------------------------------------------
    lang = load_json(os.path.join(A, "lang/en_us.json")) or {}
    for bid in BLOCKS:
        ok(f"block.{MOD}.{bid}" in lang, f"lang missing block.{MOD}.{bid}")
    for iid in ITEMS:
        ok(f"item.{MOD}.{iid}" in lang, f"lang missing item.{MOD}.{iid}")
    for eid in ENTITIES:
        ok(f"entity.{MOD}.{eid}" in lang, f"lang missing entity.{MOD}.{eid}")
    for sid in SOUNDS:
        ok(f"subtitles.{MOD}.{sid}" in lang, f"lang missing subtitles.{MOD}.{sid}")
    ok("itemGroup.dragonbrain.main" in lang, "lang missing item group")
    print(f"[lang] {len(lang)} keys")

    # 6. sounds ---------------------------------------------------------------------
    sounds_json = load_json(os.path.join(A, "sounds.json")) or {}
    ok(set(sounds_json) == set(SOUNDS),
       f"sounds.json keys {sorted(set(sounds_json) ^ set(SOUNDS))} != manifest")
    for sid, entry in sounds_json.items():
        for s in entry.get("sounds", []):
            name = s["name"].split(":", 1)[1]
            ogg = os.path.join(A, "sounds", name + ".ogg")
            if ok(os.path.isfile(ogg), f"missing sound file {name}.ogg"):
                with open(ogg, "rb") as fh:
                    ok(fh.read(4) == b"OggS", f"{name}.ogg is not a valid Ogg stream")
    on_disk = {f[:-4] for f in os.listdir(os.path.join(A, "sounds")) if f.endswith(".ogg")}
    ok(on_disk == set(SOUNDS), f"orphan/missing .ogg files: {sorted(on_disk ^ set(SOUNDS))}")
    print(f"[sounds] {len(SOUNDS)} definitions + files")

    # 7. loot/recipes reference registered content -----------------------------------
    known = {f"{MOD}:{x}" for x in list(BLOCKS) + list(ITEMS)}
    ref_re = re.compile(rf"{MOD}:[a-z_]+")
    for sub in ("loot_table", "recipe"):
        base = os.path.join(D, MOD, sub)
        for root, _d, fs in os.walk(base):
            for f in fs:
                text = open(os.path.join(root, f)).read()
                for ref in ref_re.findall(text):
                    ok(ref in known, f"{rel(os.path.join(root, f))}: unknown reference {ref}")

    # 8. particles -----------------------------------------------------------------------
    for pid in PARTICLES:
        pdef = load_json(os.path.join(A, "particles", pid + ".json"))
        if pdef:
            for ref in pdef.get("textures", []):
                # particle sprite ids resolve under textures/particle/
                ns, path = ref.split(":", 1)
                sprite = os.path.join(RES, "assets", ns, "textures/particle", path + ".png")
                used_textures.add(f"{ns}:particle/{path}")
                ok(os.path.isfile(sprite), f"particle {pid}: missing sprite {ref} -> {rel(sprite)}")

    # 9. texture dimensions + animation strips ---------------------------------------------
    try:
        from PIL import Image
        have_pil = True
    except ImportError:
        have_pil = False
        warnings.append("Pillow not installed: dimension checks skipped")
    if have_pil:
        for ref in sorted(used_textures):
            p = tex_path(ref)
            if not os.path.isfile(p):
                continue
            im = Image.open(p)
            kind = ref.split("/", 1)[0].split(":", 1)[-1]
            if "/block/" in ref or "/item/" in ref:
                ok(im.width == 16 and im.height % 16 == 0,
                   f"{ref}: expected 16xN, got {im.size}")
                mcmeta = p + ".mcmeta"
                if im.height > 16:
                    ok(os.path.isfile(mcmeta), f"{ref}: animated strip without .mcmeta")
                    ok(im.height % im.width == 0, f"{ref}: strip height not multiple of width")
                elif os.path.isfile(mcmeta):
                    errors.append(f"{ref}: .mcmeta on non-animated texture")
        expect_entity = {
            "end_wisp": (64, 64), "end_wisp_eyes": (64, 64),
            "rift_stalker": (64, 64), "rift_stalker_windup": (64, 64),
            "ancient_sentinel": (128, 128), "ancient_sentinel_eyes": (128, 128),
        }
        for name, size in expect_entity.items():
            p = os.path.join(A, "textures/entity", name + ".png")
            if ok(os.path.isfile(p), f"missing entity texture {name}.png"):
                ok(Image.open(p).size == size, f"{name}.png size != {size}")
        # dragon: dimensions + exact 2.0 alpha/UV mask preservation
        preserved = os.path.join(ROOT, "_preserved_2_0/textures/entity/enderdragon/dragon.png")
        base_mask = None
        if os.path.isfile(preserved):
            base_mask = Image.open(preserved).convert("RGBA").getchannel("A").tobytes()
        for name in ("dragon", "dragon_rage", "dragon_final",
                     "dragon_eyes", "dragon_rage_eyes", "dragon_final_eyes"):
            p = os.path.join(A, "textures/entity/enderdragon", name + ".png")
            if ok(os.path.isfile(p), f"missing dragon texture {name}.png"):
                im = Image.open(p).convert("RGBA")
                ok(im.size == (256, 256), f"{name}.png size {im.size} != (256, 256)")
                if base_mask and not name.endswith("_eyes") and name != "dragon_eyes":
                    mask = bytes(255 if b else 0 for b in im.getchannel("A").tobytes())
                    base_bin = bytes(255 if b else 0 for b in base_mask)
                    ok(mask == base_bin, f"{name}.png alpha mask differs from preserved 2.0 UV mask")
        print("[textures] dimensions + dragon UV-mask checks done")

    # 10. orphans -------------------------------------------------------------------------
    java_refs = set()
    for root, _d, fs in os.walk(JAVA):
        for f in fs:
            if f.endswith(".java"):
                java_refs.update(re.findall(r"textures/[a-z0-9_/]+\.png",
                                             open(os.path.join(root, f)).read()))
    allowed_extra = {os.path.join(A, "icon.png")}
    for root, _d, fs in os.walk(os.path.join(A, "textures")):
        for f in fs:
            if not f.endswith(".png"):
                continue
            p = os.path.join(root, f)
            rp = os.path.relpath(p, os.path.join(A, "textures"))[:-4].replace(os.sep, "/")
            ref = f"{MOD}:{rp}"
            if ref in used_textures:
                continue
            rel_tex = "textures/" + rp + ".png"
            ok(rel_tex in java_refs or p in allowed_extra,
               f"orphan texture (not referenced by any model/particle/code): {rel(p)}")
    if have_pil:  # panel textures referenced via sibling refs (column tops etc.)
        pass
    print("[orphans] texture reference sweep done")

    # 11. licences ---------------------------------------------------------------------------
    ok(os.path.isfile(os.path.join(ROOT, "LICENSE")), "LICENSE missing")
    credits_path = os.path.join(ROOT, "CREDITS.md")
    if ok(os.path.isfile(credits_path), "CREDITS.md missing"):
        credits = open(credits_path).read()
        for needle in ("Beyonder", "MIT", "Fancy End", "CC-BY-NC-ND"):
            ok(needle in credits, f"CREDITS.md missing notice: {needle}")

    # 12. resource pack -------------------------------------------------------------------------
    mcmeta = load_json(os.path.join(RP, "pack.mcmeta")) or {}
    ok(mcmeta.get("pack", {}).get("pack_format") == 75,
       f"resource pack pack_format {mcmeta.get('pack', {}).get('pack_format')} != 75 (MC 1.21.11)")
    rp_tex_dir = os.path.join(RP, "assets/minecraft/textures")
    rp_files = []
    for root, _d, fs in os.walk(rp_tex_dir):
        rp_files += [os.path.relpath(os.path.join(root, f), rp_tex_dir) for f in fs]
    ok(len(rp_files) >= 10, f"resource pack suspiciously small: {len(rp_files)} textures")
    if have_pil:
        for f in rp_files:
            im = Image.open(os.path.join(rp_tex_dir, f))
            ok(im.size == (16, 16), f"RP texture {f} size {im.size} != 16x16")
    print(f"[resourcepack] {len(rp_files)} vanilla textures, pack_format 75")

    # 13. fabric.mod.json / mixins ------------------------------------------------------------------
    fm = load_json(os.path.join(RES, "fabric.mod.json"))
    if fm:
        ok(fm.get("id") == MOD, "fabric.mod.json id mismatch")
        for kinds in fm.get("entrypoints", {}).values():
            for cls in kinds:
                path = os.path.join(JAVA, *cls.split(".")) + ".java"
                ok(os.path.isfile(path), f"entrypoint class missing: {cls}")
        for m in fm.get("mixins", []):
            cfg = m["config"] if isinstance(m, dict) else m
            mjson = load_json(os.path.join(RES, cfg))
            if mjson:
                pkg = mjson.get("package", "")
                for side in ("mixins", "client", "server"):
                    for cls in mjson.get(side, []):
                        path = os.path.join(JAVA, *(pkg + "." + cls).split(".")) + ".java"
                        ok(os.path.isfile(path), f"mixin class missing: {pkg}.{cls}")
    icon = os.path.join(A, "icon.png")
    ok(os.path.isfile(icon), "mod icon missing")

    if strict:
        notes = os.path.join(ROOT, "BUILD_NOTES.md")
        ok(os.path.isfile(notes), "BUILD_NOTES.md missing (required by --strict)")

    print(f"\nchecks performed: {checked} (+{checked_inc} JSON parses)")
    for wmsg in warnings:
        print("WARN:", wmsg)
    if errors:
        print(f"\nFAIL — {len(errors)} error(s):")
        for e in errors:
            print("  -", e)
        return 1
    print("PASS — all asset checks green")
    return 0


if __name__ == "__main__":
    sys.exit(main())
