# Hero Spine skill integration

## Scope

The current game seeds these ten heroes:

- `asuna_dark`
- `gojo`
- `iori`
- `iori_dark`
- `kirito`
- `leloud`
- `leloud_dark`
- `thor`
- `thor_rak`
- `tohka_dark`

All ten hero skeletons are registered in `static/js/spine/SpineConfig.js`.
This change adds explicit skill-code-to-animation mappings for all five seeded
skill slots, using the event codes created by `GameBootstrapDataInitializer`.

| Skill code suffix | Seeded type | Spine animation |
|---|---|---|
| `_BASIC_1` | BASIC | `att` |
| `_ACTIVE_2` | ACTIVE | `skill` |
| `_ULTIMATE_3` | ULTIMATE | `ultimateskill` |
| `_PASSIVE_4` | PASSIVE | `skill` |
| `_PASSIVE_5` | PASSIVE | `skill` |

The mapping is generated per hero code, for example
`TOHKA_DARK_ACTIVE_2 -> skill` and `GOJO_ULTIMATE_3 -> ultimateskill`.
The runtime checks that the selected animation exists in that hero's skeleton.
If a skeleton does not contain the configured animation, BattleRenderer falls
back to its existing event-type animation mapping.

## Effect asset families found by filename

The following files are candidates from the existing effect directory. A numeric
prefix links an effect to a source asset family; it does not prove the exact
skill dispatch on its own.

| Hero(s) | Effect asset family |
|---|---|
| Asuna Dark | `seffect_31514120111`, `seffect_31514130111` |
| Gojo | `seffect_52517111011`–`11014` |
| Iori / Iori Dark | `seffect_51515110111`, `10211`, `21011`–`21016` |
| Kirito | `seffect_31517110111`, `10112` |
| Lelouch / Lelouch Dark | `seffect_42515111011`–`11016`, `21011`–`21014` |
| Thor / Thor Awakened | `seffect_43513121011`, `21012` |
| Tohka Dark | `seffect_41514111011`–`11013`, `21011`–`21014` |

Iori/Iori Dark, Lelouch/Lelouch Dark, and Thor/Thor Awakened share numeric
effect prefixes. The source files do not identify which variant owns each
effect. The current runtime effect mapping remains intentionally limited to
the already-integrated Tohka Dark mappings; the other families are not assigned
to live skills by guesswork.

## Adding a future hero

1. Add its skeleton, atlas, and texture filenames to `SpineConfig.js`.
2. Add the hero seed and skill codes to `GameBootstrapDataInitializer`.
3. The five standard skill animation mappings are generated automatically
   from the hero code.
4. Add effect mappings only after checking the hero's own effect family and
   animation/event markers. Keep original asset filenames unchanged.
