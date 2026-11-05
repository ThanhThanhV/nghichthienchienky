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
| `dark_tohka` | Yatogami Tohka | 41514 | `tohka_dark` |

The dark/awakened variants have separate runtime hero codes, skill-code namespaces,
Spine folders, and VFX mapping entries. Where the TAB files contain only one base
hero row, `_TAB_HERO_SOURCE.variantSourceHeroCode` explicitly records the source
row used; it does not pretend that the source contains a second independent TAB
hero definition.

**Tohka asset identity:** the runtime hero code is `dark_tohka`, while the existing physical asset directory remains `static/assets/spine/heroes/tohka_dark/`. It contains `sanim_415141_1` plus its two texture pages. The exact `animation_config.lua` key is `415141_1`; do not strip `_1` or rename the physical directory as part of the runtime-code correction. This is the Tohka skin/variant asset currently used by the `dark_tohka` runtime hero.

## Runtime skill slots

For the nine heroes with two active TAB skill groups, the runtime slots are
`BASIC -> ACTIVE -> ULTIMATE -> PASSIVE -> PASSIVE`. The actual group IDs and
level-one skill IDs are bound in `_TAB_SKILL_BINDINGS` and mirrored in the TAB
catalog. The full level rows remain available for future level-up synchronization.

Gojo's runtime slots follow the exact order in `TAB_Hero[52517]`: BASIC
`525170`, then groups `525171`, `525172`, `525173`, and `525174`.
Their runtime types are BASIC, PASSIVE, PASSIVE, PASSIVE, PASSIVE respectively.
Hách, Vô Hạ Hạn, Lục Nhãn, and Domain are all PASSIVE by TAB type; Domain's
passive is triggered at battle start. No ACTIVE or ULTIMATE slot is fabricated.

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

**Catalog input note:** `static/spine/hero-skill-mapping.json` remains the
explicit source for runtime Spine animation/VFX assignments. The TAB JSON file
provides runtime slot metadata (runtime skill type, battle handlers, and the
mapping from each runtime slot to its TAB group/level-one ID), but its copied
skill text and level rows are not trusted as source data.

Before seeding, `TabLuaCatalogSynchronizer` parses the raw Lua tables directly:
- `TAB_Hero_1.lua` validates the hero ID and the skill groups referenced by the hero.
- `TAB_Skill_1.lua`, `TAB_Skill_2.lua`, and `TAB_Skill_3.lua` supply every level row, name, description, icon, TAB type, script and attribute.
- `TAB_Sseffect_1.lua` resolves script references through `TAB_EffectPerfor_1.lua`.
- `TAB_Effect_1.lua` through `TAB_Effect_3.lua` and `TAB_Buffer_1.lua` /
  `TAB_Buffer_2.lua` resolve direct effect and buffer references, including
  their linked performance and buffer IDs.

The synchronizer hydrates the in-memory TAB catalog from these source tables
before validation and database writes. It also regenerates `_TAB_HERO_SOURCE`
and all `_TAB_SKILL_BINDINGS` in the in-memory Spine catalog from the TAB hero
and runtime slot metadata. Those duplicated binding objects are no longer
independent sources of truth.

Therefore changing a raw TAB row takes effect on the next application startup
without manually editing copied skill text, level rows, or TAB binding names in
either JSON file. The TAB JSON still must contain the runtime hero/slot metadata
and correct group/level-one IDs; the Spine JSON remains the source for explicit
animation and VFX choices.

## Effect execution scope

The following mechanics are represented by Java battle-effect handlers and
are written into each skill's eight TAB level rows before database persistence:

- `dark_tohka`: Sandalphon damages all enemies and grants the non-purgeable
  Kingdom Blessing marker/role-specific CRIT bonus for two rounds; Halvanhelev
  attacks the enemy row with the most living units and applies the TAB Tank /
  Support damage bonus; Adonai Melekh applies its level-scaled ATK, MAX HP,
  CRIT, RESISTANCE, control-duration reduction and SPEED bonuses; Kabala-Malkuth
  selects the fastest-ally branch for the current attack or grants its
  non-stacking persistent stat bundle when Tohka is not the fastest ally.
- `gojo`: Hách (`GOJO_PASSIVE_2`) locks Gojo for rounds 1-10, deals
  level-scaled MAGIC damage after each enemy action during those rounds, then
  performs a 50% BASIC follow-up at round 11; Vô Hạ Hạn
  (`GOJO_PASSIVE_3`) applies its TAB stat bonuses; Lục Nhãn
  (`GOJO_PASSIVE_4`) grants five non-purgeable damage-reduction stacks, up to
  three permanent ATK stacks on kills, and a MAGIC chase against the exact
  enemy instance that revives; Domain (`GOJO_PASSIVE_5`) debuffs all enemies'
  damage taken at battle start.

Gojo MAGIC damage uses ATK multiplied by the configured coefficient and does
not use the physical DEF formula (the current unit model has no separate
MAGIC_DEF attribute). It still passes through the shared damage pipeline,
including CRIT, magic-specific / generic damage reduction, immunity, BLOCK,
SHIELD, lifesteal, and death events. Round-start passive damage is dispatched
through the same `ON_DAMAGED` / `ON_KILL` reaction pipeline as normal actions.

TAB script references and their `TAB_Sseffect`, `TAB_Effect`,
`TAB_EffectPerfor`, and `TAB_Buffer` resolutions remain preserved for audit.
The other eight heroes' source mechanics are not inferred from generic
placeholders; any behavior not yet implemented remains visible in the TAB
catalog until its dedicated Java handler is added.

## Spine VFX validation

Every referenced VFX JSON and atlas path in `_SKILL_EFFECTS` must exist under
`static/assets/spine/seffects/`. Animation names are taken from the referenced
Spine JSON animation list; asset filenames are never renamed or guessed.

## Adding a future hero

1. Add the TAB hero and skill rows to the raw Lua source files, then add the
   hero's runtime slot metadata and exact TAB group/level-one IDs to
   `hero-tab-skill-config.json`.
2. Add its skeleton, atlas, textures, animation names, and skill VFX to
   `hero-skill-mapping.json`.
3. Add the runtime hero seed with an explicit skill-slot mapping.
4. Validate the referenced Spine animation names and VFX files before enabling
   the hero.


## Complete skill-to-animation mapping

The current ten runtime heroes contain 50 skill slots. The 35 slots below have
an explicit character action animation; the other 15 are passive effects without
a TAB action-performance binding and intentionally do not play a character
attack animation.

| Hero | Slot 1 | Slot 2 | Slot 3 | Slot 4 | Slot 5 |
|---|---|---|---|---|---|
| `asuna_dark` | BASIC `att` | ACTIVE `skill` | ULTIMATE `ultimateskill` | PASSIVE — | PASSIVE — |
| `gojo` | BASIC `att` | PASSIVE `skill`* | PASSIVE — | PASSIVE `ultimateskill`* | PASSIVE `superskill`* |
| `iori` | BASIC `att` | ACTIVE `skill` | ULTIMATE `ultimateskill` | PASSIVE — | PASSIVE `ultimateskill`* |
| `iori_dark` | BASIC `att` | ACTIVE `skill` | ULTIMATE `ultimateskill` | PASSIVE — | PASSIVE `ultimateskill`* |
| `kirito` | BASIC `att` | ACTIVE `skill` | ULTIMATE `ultimateskill` | PASSIVE — | PASSIVE — |
| `leloud` | BASIC `att` | ACTIVE `skill` | ULTIMATE `ultimateskill` | PASSIVE — | PASSIVE `ultimateskill`* |
| `leloud_dark` | BASIC `att` | ACTIVE `skill` | ULTIMATE `ultimateskill` | PASSIVE — | PASSIVE `ultimateskill`* |
| `thor` | BASIC `att` | ACTIVE `skill` | ULTIMATE `ultimateskill` | PASSIVE — | PASSIVE — |
| `thor_rak` | BASIC `att` | ACTIVE `skill` | ULTIMATE `ultimateskill` | PASSIVE — | PASSIVE — |
| `dark_tohka` | BASIC `att` | ACTIVE `skill` | ULTIMATE `ultimateskill` | PASSIVE — | PASSIVE — |

For Gojo, action names are derived from the action-family field
(`TAB_EffectPerfor.fields[7]`): `1 -> att`, `11 -> skill`,
`21 -> ultimateskill`, and `31 -> superskill`. This is required because
Lục Nhãn and Domain share the same `TAB_Sseffect` performance ID family but
use different Spine action families. Cast and impact VFX IDs are read from
`TAB_EffectPerfor.fields[10]` and `fields[17]`; event markers are then read
from the referenced Spine effect skeletons.

`*` marks a trigger-driven action, not a continuously playing animation.
Hách and Lục Nhãn play only on their Lua passive triggers; Domain plays at
`BATTLE_START`. A dash means the skill has no TAB action-performance binding
and must not receive a fabricated attack animation.

All configured names were checked against the ten actual hero skeleton JSON
files. The Lua anchor table is not equally complete for every hero: the
Asuna Dark and base Thor entries do not define `att` / `skill` /
`ultimateskill` anchor blocks, although those animation names exist in their
Spine skeletons. In those cases the character animation still plays; no
nonexistent Lua anchor metadata is fabricated.

## Runtime animation validation and event mapping

- BASIC uses the hero's explicit `att` mapping; ACTIVE/ULTIMATE use the explicit per-skill mapping in this file.
- Iori and Iori Dark use `skill` for ACTIVE and `ultimateskill` for ULTIMATE, matching the confirmed action mapping.
- Gojo follows the Lua slot codes: `GOJO_PASSIVE_2` Hách uses `skill`
  (`faqi_11`), `GOJO_PASSIVE_4` Lục Nhãn uses `ultimateskill`
  (`faqi_21`), and `GOJO_PASSIVE_5` Domain uses `superskill`
  (`faqi_31`). Vô Hạ Hạn (`GOJO_PASSIVE_3`) has no character action animation.
- TAB `TAB_Sseffect` performance IDs also bind triggered PASSIVE actions: `10000210 -> skill` and `10000310 -> ultimateskill`. This adds `IORI_PASSIVE_5`, `IORI_DARK_PASSIVE_5`, `LELOUD_PASSIVE_5`, and `LELOUD_DARK_PASSIVE_5` as `ultimateskill`; passive slots without a resolved action performance (stat-only or non-attack passives) intentionally have no character action animation.
- Passive slots without an explicit character animation do not fall back to a generic skill animation. Their VFX continues to resolve independently from `_SKILL_EFFECTS`.
- The animation config ID is resolved from the exact `sanim_<id>` asset first (including `_1` variants); only if that exact Lua key is absent does bootstrap fall back to the base ID. This prevents Dark/Awakened variants from silently borrowing another variant's anchors.
- `GameBootstrapDataInitializer` validates every configured animation name against the actual hero skeleton JSON before saving the DB mapping. A bad name fails bootstrap with the hero, skill code, and missing animation.
- The browser renderer accepts both raw string mappings and BE-enriched `{ animation: ... }` objects. A missing or invalid skill mapping is logged and skipped instead of silently playing a generic animation.
