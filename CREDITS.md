# DragonBrain 3.0 — Credits & Asset Provenance

## Licence of this mod
DragonBrain (including the former 2.0 release bundled in this repository
history as `dragonbrain-2.0.0.jar`) is released under the **MIT License** —
see `LICENSE`. This matches `"license": "MIT"` in the 2.0 `fabric.mod.json`.

## Asset provenance (audited 2026-10-26)

### Preserved DragonBrain 2.0 assets (this project's own work)
- 15 sound files (`dragon_roar`, `dive_warning`, `fireball_charge`,
  `fireball_launch`, `wing_gust`, `void_pulse`, `crystal_overcharge`,
  `phase_transition`, `rage`, `victory`, `defeat`, `stagger`,
  `end_ambience`, `music_boss`, `music_final`) — copied bit-for-bit from
  the DragonBrain 2.0 jar (staged in `_preserved_2_0/`, which is audit
  material only and is not packaged into the mod jar).
- The 2.0 dragon texture UV layout: the new dragon textures keep the
  exact alpha/UV mask of the 2.0 sheets so renderer behaviour is
  preserved. The pixel art on those sheets is new in 3.0.

### New original DragonBrain 3.0 assets
Every other texture, model, particle sprite, icon and the 15 new sounds
were created from scratch for 3.0 by the generators in `tools/`
(`generate_assets.py`, `generate_sounds.py`, `generate_json.py`).
No pixels, audio samples, models or JSON from any third-party resource
pack are bundled in this mod or in the optional vanilla resource pack.

### Third-party packs investigated — NOT bundled

#### Beyonder - End Tweaks (Modrinth: resourcepack/beyonder)
- Licence **verified via the Modrinth API on 2026-10-26**:
  project `ItNRyDNk`, `"license": {"id": "MIT", "name": "MIT License"}`,
  latest version 1.1 (`UGLiB6L8`, supports MC 1.21–1.21.11).
- The MIT licence would in principle permit reuse with attribution.
  **However**, asset reuse was not performed: this build environment
  cannot download the pack archive (`cdn.modrinth.com` is unreachable
  from the sandbox), so the archive's internal licence files and any
  separate third-party credits inside it could not be inspected, and the
  DragonBrain asset policy is to reuse only files that have actually
  been inspected. **Zero Beyonder files are included.**
- Beyonder is recommended as an optional, separately-installed pack:
  download it from https://modrinth.com/resourcepack/beyonder/ and keep
  its MIT licence notice with your copy.

#### Fancy End (Modrinth: resourcepack/fancy-end)
- Licence **verified via the Modrinth API on 2026-10-26**:
  **CC-BY-NC-ND-4.0** (Creative Commons Attribution NonCommercial
  NoDerivatives 4.0 International, https://creativecommons.org/licenses/by-nc-nd/4.0/).
- This licence does **not** authorise modifying and redistributing its
  textures. **No Fancy End assets are included, modified or otherwise.**
- Players who want Fancy End must obtain it separately from its author:
  https://modrinth.com/resourcepack/fancy-end

### Policy
DragonBrain will not bundle any asset whose licence is All Rights
Reserved, non-derivative, non-commercial-with-unclear-scope, or whose
provenance cannot be inspected directly. When in doubt, assets are
created from scratch instead.
