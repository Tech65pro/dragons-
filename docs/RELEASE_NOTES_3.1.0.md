# DragonBrain 3.1.0 — The Abyssal End, completed

For **Minecraft Java 1.21.11** + Fabric (Loader 0.20.1+, Fabric API 0.141.6+1.21.11, Java 21+).

**Downloads**
- `dragonbrain-3.1.0.jar` — the mod (put in `mods/`)
- `DragonBrain-AbyssalEnd-Vanilla.zip` — OPTIONAL vanilla-End texture refresh
  (24 original textures; put in `resourcepacks/`). The mod is complete without it.

## What's new in 3.1.0

**Five real End biomes** on the outer islands (main island/dragon arena untouched):
`dragonbrain:void_gardens`, `dragonbrain:chorus_wilds`, `dragonbrain:shattered_expanse`,
`dragonbrain:petrified_abyss`, `dragonbrain:astral_barrens` — each with its own
ground materials, formations, flora, fog and spawns. Find them with
`/locate biome dragonbrain:<id>`.

**Four real structures** with painted templates, loot chests and guardians:
`dragonbrain:ancient_end_ruins`, `dragonbrain:abandoned_observatory`,
`dragonbrain:resonance_vault`, `dragonbrain:sentinel_sanctuary` —
`/locate structure dragonbrain:<id>`, or craft a Rift Compass.

**A complete, fair Ender Dragon fight**: Rookie/Normal/Expert/Adaptive
difficulties; every added attack (fireball volley, telegraphed dive,
Wing Gust, jumping-dodgeable Void Pulse, interruptible Crystal Overcharge,
Expert fakeouts) has a visible/audible telegraph and counterplay. Vanilla
dragon AI, death, portal, dragon egg and resummoning are preserved.

**Plus**: 22 blocks, 11 items, 3 mobs (End Wisp guide companion, Rift Stalker,
Ancient Sentinel), 12 advancements, 30 sounds, full `/dragonbrain` status &
test commands, config toggles for everything.

## Existing worlds

Registry ids are unchanged from 3.0.0 — 3.1.0 is a drop-in upgrade. New biomes
and structures generate only in **newly generated chunks**; already-explored
End terrain never changes retroactively (vanilla behaviour — explore further
out, or use `/locate` beyond your explored area).

Verified in CI for this exact build: compile + 700+ asset/worldgen checks,
dedicated-server smoke test (commands, summons, biome/structure `locate` in a
real generated End), and headless-client resource load. All artwork is
original or preserved DragonBrain assets — no third-party pack files are
bundled (Beyonder/Fancy End remain separate downloads from their authors).
