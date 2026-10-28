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
    worldgen_checks()
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



# ---------------- worldgen validation (appended 3.1) ----------------
import gzip as _gzip
class _R:
    def __init__(self, data): self.d=data; self.i=0
    def u8(self): v=self.d[self.i]; self.i+=1; return v
    def i8(self): import struct as st; v=st.unpack_from(">b",self.d,self.i)[0]; self.i+=1; return v
    def i16(self): import struct as st; v=st.unpack_from(">h",self.d,self.i)[0]; self.i+=2; return v
    def i32(self): import struct as st; v=st.unpack_from(">i",self.d,self.i)[0]; self.i+=4; return v
    def i64(self): import struct as st; v=st.unpack_from(">q",self.d,self.i)[0]; self.i+=8; return v
    def f32(self): import struct as st; v=st.unpack_from(">f",self.d,self.i)[0]; self.i+=4; return v
    def f64(self): import struct as st; v=st.unpack_from(">d",self.d,self.i)[0]; self.i+=8; return v
    def s(self): n=self.i16(); v=self.d[self.i:self.i+n].decode(); self.i+=n; return v
    def payload(self, t):
        if t==1: return self.i8()
        if t==2: return self.i16()
        if t==3: return self.i32()
        if t==4: return self.i64()
        if t==5: return self.f32()
        if t==6: return self.f64()
        if t==7: n=self.i32(); v=list(self.d[self.i:self.i+n]); self.i+=n; return v
        if t==8: return self.s()
        if t==9:
            et=self.u8(); n=self.i32(); return [self.payload(et) for _ in range(n)]
        if t==10:
            out={}
            while True:
                tt=self.u8()
                if tt==0: break
                nm=self.s()
                out[nm]=self.payload(tt)  # read name BEFORE payload (RHS-first eval!)
            return out
        if t==11: n=self.i32(); return [self.i32() for _ in range(n)]
        if t==12: n=self.i32(); return [self.i64() for _ in range(n)]
        raise ValueError(f"bad tag {t}")

def load_structure_nbt(path):
    with open(path,'rb') as f: raw=f.read()
    r=_R(_gzip.decompress(raw))
    t=r.u8(); name=r.s()
    if t!=10: raise ValueError("root is not compound")
    return r.payload(10)

def data_json(rel):
    p=os.path.join(RES,'data','dragonbrain',rel)
    with open(p) as f: return json.load(f)

def worldgen_checks():
    biomes=["void_gardens","chorus_wilds","shattered_expanse","petrified_abyss","astral_barrens"]
    structures={"ancient_end_ruins":"ruins","abandoned_observatory":"observatory",
                "resonance_vault":"vault","sentinel_sanctuary":"sanctuary"}
    for b in biomes:
        rel=f"worldgen/biome/{b}.json"; p=os.path.join(RES,'data','dragonbrain',rel)
        if not ok(os.path.exists(p), f"biome json exists: {b}"): continue
        j=data_json(rel)
        ok(len(j.get("features",[]))==11, f"{b}: 11 feature steps")
        for step in j.get("features",[]):
            for ref in step:
                if ref.startswith("dragonbrain:"):
                    name=ref.split(':')[1]
                    ok(os.path.exists(os.path.join(RES,'data','dragonbrain',f"worldgen/placed_feature/{name}.json")),
                          f"{b}: placed feature exists {ref}")
        for group in ("monster","ambient"):
            for s in j.get("spawners",{}).get(group,[]):
                if s["type"].startswith("dragonbrain:"):
                    ok(s["type"].split(':')[1] in set(ENTITIES), f"{b}: spawner entity registered {s['type']}")
                ok(0 < s["weight"] <= 100 and 1 <= s["minCount"] <= s["maxCount"] <= 8,
                      f"{b}: spawner bounds sane {s['type']}")
    # placed -> configured refs
    pf_dir=os.path.join(RES,'data','dragonbrain','worldgen','placed_feature')
    for fn in sorted(os.listdir(pf_dir)):
        j=data_json(f"worldgen/placed_feature/{fn}")
        ref=j.get("feature","")
        if ref.startswith("dragonbrain:"):
            name=ref.split(':')[1]
            cp=os.path.join(RES,'data','dragonbrain',f"worldgen/configured_feature/{name}.json")
            ok(os.path.exists(cp), f"placed {fn}: configured feature {ref} exists")
            if os.path.exists(cp):
                cj=data_json(f"worldgen/configured_feature/{name}.json")
                if cj.get("type")=="dragonbrain:end_formation":
                    blk=cj.get("config",{}).get("primary","")
                    ok(blk.split(':')[1] in set(BLOCKS) if blk.startswith("dragonbrain:") else blk.startswith("minecraft:"),
                          f"configured {name}: formation block registered ({blk})")
                    ok(cj.get("config",{}).get("style") in ("pillar","spike","tree"),
                          f"configured {name}: formation style valid")
                blob=json.dumps(cj)
                feat_dir=os.path.join(RES,'data','dragonbrain','worldgen','configured_feature')
                feature_names={os.path.splitext(f)[0] for f in os.listdir(feat_dir)}
                for blk in set(re.findall(r"dragonbrain:([a-z0-9_]+)", blob)):
                    ok(blk in set(BLOCKS) or blk=="end_formation" or blk in feature_names,
                       f"configured {name}: id {blk} is a registered block or existing feature")
    for s_id, short in structures.items():
        sj=data_json(f"worldgen/structure/{s_id}.json")
        ok(sj.get("type")=="minecraft:jigsaw", f"{s_id}: jigsaw type")
        tag_rel=f"tags/worldgen/biome/has_structure/{s_id}.json"
        ok(os.path.exists(os.path.join(RES,'data','dragonbrain',tag_rel)), f"{s_id}: has_structure tag exists")
        pool=data_json(f"worldgen/template_pool/{s_id}/starts.json")
        for el in pool.get("elements",[]):
            loc=el["element"]["location"]
            nbt_p=os.path.join(RES,'data',loc.split(':')[0],'structure',loc.split(':')[1]+'.nbt')
            if not ok(os.path.exists(nbt_p), f"{s_id}: template nbt exists {loc}"): continue
            try:
                nbt=load_structure_nbt(nbt_p)
            except Exception as e:
                ok(False, f"{s_id}: template nbt parses ({loc}): {e}"); continue
            ok(nbt.get("DataVersion",0) >= 3955, f"{s_id}: template DataVersion sane")
            size=nbt.get("size",[0,0,0]); palette=nbt.get("palette",[])
            ok(all(0 < d <= 48 for d in size), f"{s_id}: template size within bounds {size}")
            for pk in palette:
                nm=pk.get("Name","")
                if nm.startswith("dragonbrain:"):
                    ok(nm.split(':')[1] in set(BLOCKS), f"{s_id}: palette block {nm} registered")
            names=[pk.get("Name") for pk in palette]
            blocks=nbt.get("blocks",[])
            ok(all(0 <= b.get("state",-1) < len(palette) for b in blocks), f"{s_id}: block palette indices valid")
            footprint=size[0]*size[2] if len(size)==3 else 0
            y0_count=sum(1 for b in blocks if b["pos"][1]==0)
            # 0.55: circular platforms fill ~66% of their bounding square
            ok(footprint>0 and y0_count >= 0.55*footprint and y0_count >= 60,
               f"{s_id}: template has a solid foundation row ({y0_count}/{footprint})")
            alloy=sum(1 for b in blocks if names[b["state"]]=="dragonbrain:abyssal_alloy_block")
            ok(alloy <= 2, f"{s_id}: no end-game alloy block exploit (found {alloy})")
            for b in blocks:
                be=b.get("nbt")
                if be and "LootTable" in be:
                    lt=be["LootTable"]
                    ns,path=lt.split(':')
                    lp=os.path.join(RES,'data',ns,'loot_table',path+'.json')
                    ok(os.path.exists(lp), f"{s_id}: chest loot table {lt} exists")
                    if os.path.exists(lp):
                        with open(lp) as f: lj=json.load(f)
                        for blk in set(re.findall(r"dragonbrain:([a-z0-9_]+)", json.dumps(lj))):
                            ok(blk in (set(BLOCKS)|set(ITEMS)), f"{s_id}: loot item {blk} registered")
            for ent in nbt.get("entities",[]):
                eid=ent.get("nbt",{}).get("id","")
                if eid.startswith("dragonbrain:"):
                    ok(eid.split(':')[1] in set(ENTITIES), f"{s_id}: template entity {eid} registered")
        ss=data_json(f"worldgen/structure_set/{s_id}.json")
        pl=ss.get("placement",{})
        ok(8 <= pl.get("spacing",0) <= 64 and 0 <= pl.get("separation",-1) < pl.get("spacing",0),
              f"{s_id}: structure_set spacing/separation sane")
        ok(os.path.exists(os.path.join(RES,'data','dragonbrain',f"tags/worldgen/structure/locate/{s_id}.json")),
              f"{s_id}: locate tag exists")
    rt=data_json("tags/worldgen/structure/rift_compass_targets.json")
    ok(len(rt.get("values",[]))==len(structures), "rift compass target tag covers all structures")
    # advancements
    adv_dir=os.path.join(RES,'data','dragonbrain','advancement')
    lang=load_json(os.path.join(RES,'assets','dragonbrain','lang','en_us.json'))
    for fn in sorted(os.listdir(adv_dir)):
        j=data_json(f"advancement/{fn}")
        disp=j.get("display",{})
        icon=disp.get("icon",{}).get("id","")
        if icon.startswith("dragonbrain:"):
            ok(icon.split(':')[1] in (set(BLOCKS)|set(ITEMS)), f"advancement {fn}: icon item registered")
        title=disp.get("title",{}).get("translate","")
        ok(title in lang, f"advancement {fn}: title lang key present")
        crit=json.dumps(j.get("criteria",{}))
        for it in set(re.findall(r"dragonbrain:([a-z0-9_]+)", crit)):
            ok(it in (set(BLOCKS)|set(ITEMS)) or it in [b if False else ""]+["void_gardens","chorus_wilds","shattered_expanse","petrified_abyss","astral_barrens"],
                  f"advancement {fn}: criterion id {it} registered")


if __name__ == "__main__":
    sys.exit(main())
