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

On normal application startup, the ordered bootstrap pipeline:

1. `DatabaseResetInitializer` is disabled by default. It only runs when
   `game.bootstrap.reset-database.enabled=true` (or
   `GAME_BOOTSTRAP_RESET_DATABASE=true`) is explicitly configured.
2. `ResourceBootstrapDataInitializer` initializes shared resource definitions
   and player-level configs without requiring a full database reset.
3. `GameBootstrapDataInitializer` loads the TAB-derived JSON catalog, the
   per-hero Spine mapping, and `animation_config.lua`.
4. Before writing, it validates TAB hero source IDs, variant source IDs, all
   five skill-slot bindings, skill group IDs, level-one skill IDs, runtime
   skill types, and skill names.
5. It validates that every VFX mapping references a TAB skill binding and that
   the referenced VFX skeleton JSON, atlas, and texture pages exist.
6. It updates existing hero skill definitions in place. Existing skill IDs and
   `HeroInstanceSkill` links are retained; only the effect children are
   replaced from the current catalog. This preserves player skill levels.
7. It stores the resolved animation, TAB binding, and VFX mapping on
   `HeroDefinition.spineSkillAnimations`, which is returned to the battle API.

The reset initializer remains available for intentional disposable development
databases only. Enabling it deletes application data and must never be used on
a database whose player accounts or game progress need to be preserved.

**Catalog input note:** the runtime currently consumes
`static/spine/hero-tab-skill-config.json` and
`static/spine/hero-skill-mapping.json`. The raw Lua TAB files are retained as
source material and are listed in the TAB catalog, but the application does not
parse those TAB Lua files directly during startup. When a raw TAB file changes,
the JSON catalog must also be regenerated/updated before the next startup.

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


## Runtime animation validation and event mapping

- BASIC uses the hero's explicit `att` mapping; ACTIVE/ULTIMATE use the explicit per-skill mapping in this file.
- Iori and Iori Dark use `skill` for ACTIVE and `ultimateskill` for ULTIMATE, matching the confirmed action mapping.
- Gojo ACTIVE field uses `skill`; the counter passive uses `ultimateskill`. The chase passive uses its dedicated VFX and does not force a generic character animation.
- Passive slots without an explicit character animation do not fall back to a generic skill animation. Their VFX continues to resolve independently from `_SKILL_EFFECTS`.
- `GameBootstrapDataInitializer` validates every configured animation name against the actual hero skeleton JSON before saving the DB mapping. A bad name fails bootstrap with the hero, skill code, and missing animation.
- The browser renderer accepts both raw string mappings and BE-enriched `{ animation: ... }` objects. A missing or invalid skill mapping is logged and skipped instead of silently playing a generic animation.
