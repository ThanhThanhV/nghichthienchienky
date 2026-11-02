/**
 * Mapping effect Spine riêng cho Tohka Hắc Ám.
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
        if (String(heroCode || "").toLowerCase() !== "tohka_dark") {
            return [];
        }

        const normalizedSkillCode = String(skillCode || event?.skillCode || "")
            .trim()
            .toUpperCase();

        const basePath = "/assets/spine/seffects/tohka_dark/";
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
                cast: {
                    code: "tohka_dark_skill_2_cast",
                    file: "seffect_41514111012",
                    animation: "animation_0",
                    impactRatio: 0.55,
                    anchor: "source"
                },
                impact: {
                    code: "tohka_dark_skill_2_impact",
                    file: "seffect_41514121013",
                    animation: "animation_0",
                    impactRatio: 0.5,
                    anchor: "target"
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
            }
        };

        const selected = definitions[normalizedSkillCode]?.[phase];
        if (!selected) {
            return [];
        }

        const textures = selected.file === "seffect_41514121014"
            ? [
                "seffect_41514121014.png",
                "seffect_415141210142.png",
                "seffect_415141210143.png",
                "seffect_415141210144.png"
            ]
            : [selected.file + ".png"];

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
