/**
 * Mapping hiệu ứng Spine theo heroCode.skillCode hoặc effectCode.
 *
 * Tohka Dark dùng các bộ seffect được export riêng:
 * - 41514111011 / 11012 / 11013: hiệu ứng lúc bắt đầu đánh, tung kỹ năng, đại chiêu.
 * - 41514121011 / 21013 / 21014: hiệu ứng va chạm tương ứng.
 *
 * Các asset này đều dùng animation_0 (riêng 21014 có thêm animation_1).
 * Các event Spine trong file export là marker nội bộ của asset; runtime hiện tại
 * không cần phụ thuộc vào tên event vì SpineEffect hỗ trợ impactRatio fallback.
 */
window.SPINE_EFFECT_CONFIG = {
    effects: {},

    resolveEffects({ event, phase, heroCode }) {
        if (String(heroCode || "").toLowerCase() !== "tohka_dark") {
            return [];
        }

        const type = String(event?.type || "").toUpperCase();
        const basePath = "/assets/spine/seffects/tohka_dark/";

        const definitions = {
            BASIC_ATTACK: {
                cast: {
                    code: "tohka_dark_basic_cast",
                    file: "seffect_41514111011",
                    animation: "animation_0",
                    impactRatio: 0.48,
                    anchor: "source",
                    layer: "FRONT"
                },
                impact: {
                    code: "tohka_dark_basic_impact",
                    file: "seffect_41514121011",
                    animation: "animation_0",
                    impactRatio: 0.45,
                    anchor: "target",
                    layer: "FRONT"
                }
            },
            ATTACK: {
                cast: {
                    code: "tohka_dark_basic_cast",
                    file: "seffect_41514111011",
                    animation: "animation_0",
                    impactRatio: 0.48,
                    anchor: "source",
                    layer: "FRONT"
                },
                impact: {
                    code: "tohka_dark_basic_impact",
                    file: "seffect_41514121011",
                    animation: "animation_0",
                    impactRatio: 0.45,
                    anchor: "target",
                    layer: "FRONT"
                }
            },
            ACTIVE_SKILL: {
                cast: {
                    code: "tohka_dark_active_cast",
                    file: "seffect_41514111012",
                    animation: "animation_0",
                    impactRatio: 0.55,
                    anchor: "source",
                    layer: "FRONT"
                },
                impact: {
                    code: "tohka_dark_active_impact",
                    file: "seffect_41514121013",
                    animation: "animation_0",
                    impactRatio: 0.5,
                    anchor: "target",
                    layer: "FRONT"
                }
            },
            ULTIMATE_SKILL: {
                cast: {
                    code: "tohka_dark_ultimate_cast",
                    file: "seffect_41514111013",
                    animation: "animation_0",
                    impactRatio: 0.62,
                    anchor: "source",
                    layer: "FRONT"
                },
                impact: {
                    code: "tohka_dark_ultimate_impact",
                    file: "seffect_41514121014",
                    animation: "animation_0",
                    impactRatio: 0.62,
                    anchor: "target",
                    layer: "FRONT"
                }
            }
        };

        const selected = definitions[type]?.[phase];
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
            displayScale: 1,
            phase
        }];
    }
};
