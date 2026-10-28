# DragonBrain — Feature Audit (3.0.0 → 3.1.0)

Evidence = file paths in this repo. "Verified (CI)" means a green GitHub
Actions run exercised it; "Verified (static)" means `tools/validate_assets.py`
cross-checked it locally; anything else says so plainly. Last updated for the
3.1.0 completion pass.

## What 3.0.0 actually had vs. what was missing

| Area | 3.0.0 state | 3.1.0 |
|---|---|---|
| 5 End biomes | **Missing** — no biome JSONs, no biome-source integration. Only biome *names* in spawn rules. | Implemented: `data/dragonbrain/worldgen/biome/*.json` (5) + `worldgen/ModWorldgen.java` (`TheEndBiomes` outer-island placement, config-gated). Static-verified; CI locates them in a generated End. |
| 4 structures | **Missing** — no structure JSON/NBT at all. Rift Compass scanned blocks and could not find mod structures. | Implemented: jigsaw structures + sets + pools + 5 painted `.nbt` templates (`data/dragonbrain/structure/**`), chest loot tables, `/locate` tags. Static-verified incl. NBT parse/foundation/loot refs; CI locates one. |
| Dragon combat | **Partial** — `DragonPhaseWatcher` only changed texture/sounds at HP thresholds; no attacks, stagger, or adaptive logic. Difficulties existed as config only. | Implemented: `core/DragonCombatController.java` (absorbs the watcher) — telegraphed FIREBALL/DIVE/WING_GUST/VOID_PULSE/CRYSTAL_OVERCHARGE/FAKEOUT, per-difficulty Tuning, poise stagger, adaptive factor 0.7–1.3. Server-side, no dragon mixins. **Not yet play-tested in a real fight** (no player in CI); code-path + balance rationale reviewed, first green CI run proves compile/boot only. |
| End Wisp | Wander-only. | + follow-holder guide goal (Wisp Essence/Resonance Shard). |
| Rift Stalker | Spawn rules only via biome modifiers. | + `SpawnRestriction` registered (`canSpawnIgnoreLightLevel`); ambush teleport is range/LOS/cooldown-capped in existing AI (unchanged, audited readable: stationary 1s aim window before strike). |
| Ancient Sentinel | Entity + loot only. | Now actually guards Sentinel Sanctuaries (template entity). |
| Rift Compass | Bounded block scan for gateways only. | Primary target = nearest mod structure via vanilla `locateStructure` (no chunk force-load); gateway scan kept as fallback; honest feedback either way. |
| Advancements | **Missing.** | 12 real advancements (`data/dragonbrain/advancement/`), lang keys, item/biome criteria. |
| Commands | help/status(reload/debug/difficulty)/test assets only. | Full surface: `status biomes|structures|entities|items|assets|dragon`, `test spawn|structure|boss [attack]` — all read live registries/locators; `test boss` refuses to spawn a dragon (no progression bypass). |
| Config | difficulty, thresholds, texture/sound/particle toggles. | + `dragonAttacksEnabled`, damage/cooldown/telegraph multipliers, `crystalOverchargeEnabled`, `customBiomes`. All ranges clamped on load. |
| Vanilla RP | 16 textures (~27 KB zip). | 24 textures (+ end rod, portal frame ×3, dragon breath, end crystal, elytra, shulker shell). Still honestly scoped: it is a *refresh*, not a full End overhaul; README says so. |
| CI/release | Top-level `contents: write`; logs force-pushed to `ci-logs` branch; release needed `[release]` in commit message + once an empty retry commit. | Least-privilege per-job permissions (write only in release job); logs are artifacts; **no branch publishing**; release auto-publishes from push only after build+server+client all pass, version read from `gradle.properties`, assets verified non-empty before publish, existing release assets refreshed with `--clobber`. |

## Identifiers (all `dragonbrain:` namespace)

- Biomes: `void_gardens`, `chorus_wilds`, `shattered_expanse`, `petrified_abyss`, `astral_barrens`
- Structures: `ancient_end_ruins`, `abandoned_observatory`, `resonance_vault`, `sentinel_sanctuary`
  (spacing 24–44 chunks; pools `template_pool/<id>/starts`; locate tags `tags/worldgen/structure/locate/<id>`)
- Feature type: `end_formation` (pillar/spike/tree; refuses void origins)
- Blocks (22), items (11), entities (`end_wisp`, `rift_stalker`, `ancient_sentinel`),
  sounds (30), particles (2): unchanged ids from 3.0.0 — see `tools/generate_json.py` manifest.

## Player/operator commands

```
/dragonbrain help
/dragonbrain status [biomes|structures|entities|items|assets|dragon]
/dragonbrain test assets | test spawn <entity> | test structure <structure> | test boss [attack]
/dragonbrain difficulty <rookie|normal|expert|adaptive> | reload | debug
/locate biome dragonbrain:void_gardens        (vanilla, in the End)
/locate structure dragonbrain:resonance_vault (vanilla)
/summon dragonbrain:ancient_sentinel  •  /give @p dragonbrain:rift_compass
```

## Honestly unverified

- **In-game dragon fight feel/balance** (damage numbers, telegraph readability
  with real latency, stagger pacing) — needs a human playtest; tuning lives in
  one `Tuning` table in `DragonCombatController` + config multipliers.
- **Long-term adaptive difficulty behaviour** across many deaths/fights.
- Structure chest `LootTable` NBT key spelling for 1.21.11 — if it were wrong,
  chests would simply be empty (templates also contain physical rewards), and
  CI can't open a chest; flagged here rather than claimed.
- Multi-seed worldgen distribution — CI generates one End; biome weights come
  from `TheEndBiomes` (Fabric-supported) with conservative spacing values.
- Client rendering is verified to *load* (atlas, no model errors); final
  visual judgement still needs eyes in-game.

## Existing worlds

All 3.0.0 registry ids are identical in 3.1.0 (drop-in upgrade). Biomes and
structures appear only in **newly generated chunks**; explored terrain is
never retroactively changed. `customBiomes=false` restores vanilla biome
selection for new chunks (existing generated biomes keep their ids safely).
