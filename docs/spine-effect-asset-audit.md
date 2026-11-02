# Spine effect asset audit

## Scope

This audit covers only the effect JSON files whose numeric prefixes match the ten hero assets already configured in `SpineConfig.js`. Original asset filenames are preserved. No effect asset is renamed or modified.

The audit records:
- the hero-ID prefix associated with each effect filename;
- animation names and Spine event markers found in each effect JSON;
- event markers from hero skeleton JSONs where the skeleton file could be read;
- whether a skill-level mapping is actually proven.

## Important interpretation

A filename prefix is evidence that an effect belongs to the same source asset/hero-ID family. It is **not by itself proof** that a particular skill uses that effect.

Event names such as `qishou_11_...`, `hit_21_...` and `qishou_31_...` are useful timing and action-family clues. They are not enough to identify the exact skill name in the new game without the old game's effect dispatch/configuration or a runtime Network capture showing which file loads when a skill is triggered.

## Candidate inventory

| Hero asset family | Candidate effect files | Metadata observations |
|---|---|---|
| Asuna Dark — `315141` | `seffect_31514120111`, `seffect_31514130111` | `20111`: `animation_2`, no event marker. `30111`: `animation_2`, event `hit_21_1_1` at 0.2s. |
| Gojo — `525171` | `seffect_52517111011`–`11014` | `11011`: `animation_0` / `qishou_1_1_1`, `animation_1` / `hit_11_1_1`. `11012`: `animation_0` / `qishou_21_1_1`. `11013`: `animation_0` / `qishou_21_1_1`. `11014`: `animation_0` / `qishou_31_1_1`; `animation_1` has three hit markers `hit_31_3_3` to `hit_31_3_1`. |
| Iori / Iori Dark — `515151` | `seffect_51515110111`, `10211`, `21011`–`21016` | `10111` contains animation families 1/11/21/31. `10211` has five `hit_31_5_*` markers. `21011` has `qishou_1_1_1`; `21012` has five `qishou_11_5_*` markers; `21013` has `hit_11_1_1`; `21014` has `qishou_21_1_1`; `21015` has three `hit_21_3_*` markers; `21016` has four `qishou_31_4_*` markers. |
| Kirito — `315171` | `seffect_31517110111`, `10112` | `10111` contains `qishou_11_1_1`, `qishou_21_1_1`, and `qishou_31_1_1` in animations 0/1/2. `10112` contains `hit_1_1_1`, two `hit_11_2_*`, five `hit_21_5_*`, and `hit_31_1_1` across animations 0–3. |
| Lelouch / Lelouch Dark — `425151` | `seffect_42515111011`–`11016`, `21011`–`21014` | `11011`: `qishou_1_1_1`. `11012`: `hit_1_1_1`, `hit_11_1_1`, and five `hit_21_5_*` markers across animations. `11013`: `qishou_11_1_1`. `11014`: `qishou_21_1_1` plus named ultimate animations. `11015`: `qishou_31_1_1`. `11016`: `hit_31_1_1`. `21011`: `qishou_1_1_1` and `hit_1_1_1`. `21012`: four `qishou_11_4_*` markers. `21013`: three `hit_21_3_*` markers and `qishou_21_1_1`. `21014`: `qishou_31_1_1`. |
| Thor / Thor Awakened — `435131` | `seffect_43513121011`, `21012` | `21011`: two `qishou_1_2_*` markers and four `qishou_11_4_*` markers in separate animations. `21012`: `qishou_21_1_1`. |
| Tohka Dark — `415141` | `seffect_41514111011`–`11013`, `21011`–`21014` | `11011`: two `qishou_1_2_*` markers. `11012`: `qishou_11_1_1`. `11013`: four `qishou_21_4_*` markers. `21011`: two `hit_1_2_*` markers. `21012`: five `qishou_11_5_*` markers. `21013`: `hit_11_1_1`. `21014`: `qishou_21_1_1`. |

## Cross-check against readable hero skeletons

The following hero skeletons were readable and their action event markers were inspected:

- Gojo (`sanim_525171`): `att` emits `faqi_1`; `skill` emits `faqi_11`; `ultimateskill` emits `faqi_21`; `superskill` emits `faqi_31`.
- Kirito (`sanim_315171`): the same action-family markers `faqi_1`, `faqi_11`, `faqi_21`, `faqi_31` are present in `att`, `skill`, `ultimateskill`, and `superskill`.
- Thor (`sanim_435131`): `att` emits `faqi_1`; `skill` emits `faqi_11`; `ultimateskill` emits `faqi_21`.
- Asuna Dark (`sanim_315141_1`): `skill` contains `chufa_11_4_4` through `chufa_11_4_1`; `ultimateskill` emits `faqi_21`.

These confirm the hero skeletons distinguish action families, but the names do not directly equal the effect JSON event names. They are corroborating clues, not a complete dispatch map.

## Variant ambiguity

These numeric families are shared by two configured variants:
- `515151`: `iori` and `iori_dark`;
- `425151`: `leloud` and `leloud_dark`;
- `435131`: `thor` and `thor_rak`.

The current asset filenames and JSON metadata do not prove which of the two variants invokes each effect. Do not duplicate the entire family onto both variants until runtime evidence or the old source configuration distinguishes them.

## Existing runtime mapping

`src/main/resources/static/js/spine/SpineEffectConfig.js` currently contains the Tohka Dark skill definitions. Those mappings are left unchanged by this audit.

## Next evidence required for exact skill mapping

Use the old game in Chrome DevTools:
1. Open Network and enable Preserve log.
2. Filter requests by `seffect_`.
3. Clear the log.
4. Trigger exactly one skill on exactly one hero.
5. Save the requests containing `.json`, `.atlas`, and texture pages, with the skill name and hero variant noted.
6. Repeat for the remaining skills.

That runtime observation directly connects a skill activation to its effect asset. Until then, the catalog above should be treated as an asset-family inventory and event-signature index, not a final skill mapping.
