# Hero TAB + Spine skill integration

## Source of truth

- `static/spine/hero-tab-skill-config.json` stores the TAB-derived hero row, the five runtime skill slots, level rows, script references, resolved `TAB_Sseffect` / `TAB_EffectPerfor` records, and the subset of battle effects that the current Java engine can execute.
- `static/spine/hero-skill-mapping.json` stores each hero's own skeleton, texture pages, skill animation names, VFX files/events, and `_TAB_SKILL_BINDINGS` metadata.
- `animation_config.lua` remains the source for per-animation event anchors/coordinates. The backend parses it and persists it into `HeroDefinition.spineSkillAnimations`.
- The frontend resolves skeletons and VFX from the database response; it does not invent hero animation defaults.

## Current ten heroes

| Runtime hero code | TAB source hero | TAB hero ID | Spine folder |
|---|---|---:|---|
| `asuna_dark` | Asuna | 31514 | `asuna_dark` |
| `gojo` | Gojo Satoru | 52517 | `gojo` |
| `iori` | Iori | 51515 | `iori` |
| `iori_dark` | Iori | 51515 | `iori_dark` |
| `kirito` | Kirito | 31517 | `kirito` |
| `leloud` | Lelouch | 42515 | `leloud` |
| `leloud_dark` | Lelouch | 42515 | `leloud_dark` |
| `thor` | Thần Sấm Thor | 43513 | `thor` |
| `thor_rak` | Thần Sấm Thor | 43513 | `thor_rak` |
| `tohka_dark` | Yatogami Tohka | 41514 | `tohka_dark` |

The dark/awakened variants have separate runtime hero codes, skill-code namespaces,
Spine folders, and VFX mapping entries. Where the TAB files contain only one base
hero row, `_TAB_HERO_SOURCE.variantSourceHeroCode` explicitly records the source
row used; it does not pretend that the source contains a second independent TAB
hero definition.

## Runtime skill slots

For the nine heroes with two active TAB skill groups, the runtime slots are
`BASIC -> ACTIVE -> ULTIMATE -> PASSIVE -> PASSIVE`. The actual group IDs and
level-one skill IDs are bound in `_TAB_SKILL_BINDINGS` and mirrored in the TAB
catalog. The full level rows remain available for future level-up synchronization.

Gojo's TAB row has one normal attack and four special groups that are all marked
as TAB passive-type skills. The game-specific runtime mapping preserves the
previously agreed active `Vô Hạ Hạn` slot and keeps the other three as PASSIVE;
there is no fabricated ULTIMATE slot for Gojo. The battle cycle therefore skips
the ULTIMATE action for Gojo rather than relabeling a TAB passive as an ultimate.

## Startup synchronization

On each application startup, the ordered bootstrap pipeline:

1. `DatabaseResetInitializer` truncates every application table with foreign-key checks temporarily disabled. Migration metadata tables are preserved.
2. `ResourceBootstrapDataInitializer` recreates resource definitions and player-level configs.
3. `GameBootstrapDataInitializer` loads and validates the TAB skill catalog, Spine mapping, and animation config.
4. It creates the ten hero definitions, base stats, growth defaults, and all five skill definitions/effects per hero.
5. It persists TAB skill bindings and animation/VFX metadata with each hero definition.

**This is a destructive development reset.** Account records, player profiles,
owned hero instances, formations, campaign progress, inventory, and other
application data are cleared on every startup. Do not point this configuration
at a database whose data must be retained.

## Effect execution scope

Direct damage targets, selected target counts/rows, supported permanent stat
passives, Gojo's approved 20-round defensive active, and the supported counter /
shield triggers are represented using the existing Java battle-effect handlers.
TAB script references and their `TAB_Sseffect` / `TAB_EffectPerfor` chain are
retained verbatim for audit. Complex source mechanics that the current engine
cannot represent exactly (for example conditional role-specific bonuses,
stacking, revival chase, and some max-HP-based effects) are not silently
converted into generic damage. They remain visible in the TAB source catalog
until their corresponding Java handlers are implemented.

## Spine VFX validation

Every referenced VFX JSON and atlas path in `_SKILL_EFFECTS` must exist under
`static/assets/spine/seffects/`. Animation names are taken from the referenced
Spine JSON animation list; asset filenames are never renamed or guessed.

## Adding a future hero

1. Add the TAB hero and skill groups to `hero-tab-skill-config.json`.
2. Add its skeleton, atlas, textures, animation names, and skill VFX to
   `hero-skill-mapping.json`.
3. Add the runtime hero seed with an explicit skill-slot mapping.
4. Validate the referenced Spine animation names and VFX files before enabling
   the hero.
