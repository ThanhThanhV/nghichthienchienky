/**
 * Mapping effect Spine theo từng hero và skill code.
 *
 * Skill code được tạo từ GameBootstrapDataInitializer:
 * TOHKA_DARK_BASIC_1     - Kiếm chém
 * TOHKA_DARK_ACTIVE_2    - Kiếm khí
 * TOHKA_DARK_ULTIMATE_3  - Thiên thần giáng thế
 *
 * Không dùng mapping theo event type chung để tránh lấy nhầm cấu hình
 * của Tohka thường hoặc gán nhầm effect khi có nhiều kỹ năng cùng loại.
 */
window.SPINE_EFFECT_CONFIG = {
    effects: {},

    resolveEffects({ event, phase, heroCode, skillCode }) {
        const normalizedHeroCode = String(heroCode || "").trim().toLowerCase();
        if (!["tohka_dark", "asuna_dark", "gojo"].includes(normalizedHeroCode)) {
            return [];
        }

        const normalizedSkillCode = String(skillCode || event?.skillCode || "")
            .trim()
            .toUpperCase();

        const basePath = "/assets/spine/seffects/";
        const definitions = {
            TOHKA_DARK_BASIC_1: {
                cast: {
                    code: "tohka_dark_skill_1_cast",
                    file: "seffect_41514111011",
                    animation: "animation_0",
                    impactRatio: 0.48,
                    anchor: "source"
                },
                impact: {
                    code: "tohka_dark_skill_1_impact",
                    file: "seffect_41514121011",
                    animation: "animation_0",
                    impactRatio: 0.45,
                    anchor: "target"
                }
            },
            TOHKA_DARK_ACTIVE_2: {
                // Không phát cast effect của chiêu cũ (ngai vàng rơi xuống).
                // Chiêu active chỉ giữ effect mới ở phase impact.
                impact: {
                    code: "tohka_dark_skill_2_impact",
                    file: "seffect_41514121012",
                    animation: "animation_0",
                    // Đồng bộ từng DAMAGE theo thứ tự 5 hit được khai báo
                    // trực tiếp trong animation_0 của asset Spine.
                    impactEvents: [
                        "qishou_11_5_5",
                        "qishou_11_5_4",
                        "qishou_11_5_3",
                        "qishou_11_5_2",
                        "qishou_11_5_1"
                    ],
                    impactRatio: 0.5,
                    anchor: "enemy_formation"
                }
            },
            TOHKA_DARK_ULTIMATE_3: {
                cast: {
                    code: "tohka_dark_skill_3_cast",
                    file: "seffect_41514111013",
                    animation: "animation_0",
                    impactRatio: 0.62,
                    anchor: "source"
                },
                impact: {
                    code: "tohka_dark_skill_3_impact",
                    file: "seffect_41514121014",
                    animation: "animation_0",
                    impactRatio: 0.62,
                    anchor: "target"
                }
            },
            ASUNA_DARK_ACTIVE_2: {
                // Effect có event hit_21_1_1 tại 0.2s; dùng event Spine
                // làm mốc đồng bộ DAMAGE, không ước lượng theo impactRatio.
                impact: {
                    code: "asuna_dark_active_impact",
                    file: "seffect_31514130111",
                    animation: "animation_2",
                    impactEvent: "hit_21_1_1",
                    impactEvents: ["hit_21_1_1"],
                    anchor: "enemy_formation",
                    impactRatio: 1
                }
            }
,
            GOJO_BASIC_1: {
                // animation_1 chứa hit_11_1_1 tại 0.1s.
                impact: {
                    code: "gojo_basic_impact",
                    file: "seffect_52517111011",
                    animation: "animation_1",
                    impactEvent: "hit_11_1_1",
                    impactEvents: ["hit_11_1_1"],
                    anchor: "target",
                    impactRatio: 0.2
                }
            },
            GOJO_ACTIVE_2: {
                // animation_0 dài 2s, event qishou_21_1_1 tại 1.1667s.
                impact: {
                    code: "gojo_active_impact",
                    file: "seffect_52517111012",
                    animation: "animation_0",
                    impactEvent: "qishou_21_1_1",
                    impactEvents: ["qishou_21_1_1"],
                    anchor: "enemy_formation",
                    impactRatio: 0.58
                }
            },
            GOJO_ULTIMATE_3: {
                // Ba mốc hit được khai báo trực tiếp trong animation_1.
                impact: {
                    code: "gojo_ultimate_impact",
                    file: "seffect_52517111014",
                    animation: "animation_1",
                    impactEvent: "hit_31_3_3",
                    impactEvents: [
                        "hit_31_3_3",
                        "hit_31_3_2",
                        "hit_31_3_1"
                    ],
                    anchor: "enemy_formation",
                    impactRatio: 0.25
                }
            }        };

        // Runtime hiện tại phát mã animation "tohka_active" ở event cast,
        // còn event DAMAGE dùng mã effect "skill_effect_2".
        // Alias Tohka chỉ áp dụng riêng cho hero tohka_dark.
        const skillAliases = {
            TOHKA_ACTIVE: "TOHKA_DARK_ACTIVE_2",
            SKILL_EFFECT_2: "TOHKA_DARK_ACTIVE_2"
        };
        const definitionKey = definitions[normalizedSkillCode]
            ? normalizedSkillCode
            : normalizedHeroCode === "tohka_dark"
                ? skillAliases[normalizedSkillCode]
                : null;

        const selected = definitions[definitionKey]?.[phase];
        if (!selected) {
            return [];
        }

        const texturePages = {
            seffect_41514121012: [
                "seffect_41514121012.png",
                "seffect_415141210122.png",
                "seffect_415141210123.png"
            ],
            seffect_41514121014: [
                "seffect_41514121014.png",
                "seffect_415141210142.png",
                "seffect_415141210143.png",
                "seffect_415141210144.png"
            ]
        };
        const textures = texturePages[selected.file] || [selected.file + ".png"];

        return [{
            ...selected,
            skeleton: basePath + selected.file + ".json",
            atlas: basePath + selected.file + ".atlas",
            textures: textures.map(file => basePath + file),
            // Effect được export ở world scale lớn hơn nhân vật.
            // Giảm riêng effect để tương quan với displayScale 0.45 của hero.
            scale: 0.2,
            displayScale: 0.2,
            layer: "FRONT",
            phase
        }];
    }
};
