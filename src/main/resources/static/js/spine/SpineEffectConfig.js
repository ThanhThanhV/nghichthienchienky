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
        if (!["tohka_dark", "asuna_dark", "gojo", "iori", "iori_dark", "kirito", "leloud", "leloud_dark", "thor", "thor_rak"].includes(normalizedHeroCode)) {
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
                // Animation của nhân vật Tohka vẫn phát tại slot của cô ấy.
                // Effect của đòn kiếm diện rộng được đặt tại tâm đội hình địch.
                // qishou_* là mốc thời gian bên trong Spine effect, không phải
                // vị trí đặt effect trên sân.
                cast: {
                    code: "tohka_dark_skill_2_cast",
                    file: "seffect_41514121012",
                    animation: "animation_0",
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
                // Atlas 20111 chứa các frame skill1; 30111 chứa chuỗi shouji
                // và event hit_21_1_1 để đồng bộ thời điểm sát thương.
                cast: {
                    code: "asuna_dark_active_cast",
                    file: "seffect_31514120111",
                    animation: "animation_2",
                    impactRatio: 0.55,
                    anchor: "source"
                },
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
            IORI_BASIC_1: {
                // qishou_1_1_1 là mốc tung đòn; hit_11_1_1 là mốc trúng.
                cast: {
                    code: "iori_basic_cast",
                    file: "seffect_51515121011",
                    animation: "animation_0",
                    impactEvent: "qishou_1_1_1",
                    anchor: "source",
                    impactRatio: 0.45
                },
                impact: {
                    code: "iori_basic_impact",
                    file: "seffect_51515121013",
                    animation: "animation_0",
                    impactEvent: "hit_11_1_1",
                    impactEvents: ["hit_11_1_1"],
                    anchor: "target",
                    impactRatio: 0.5
                }
            },
            IORI_ACTIVE_2: {
                // Active: cast qishou_21_1_1, ba nhịp sát thương hit_21_3_3..1.
                cast: {
                    code: "iori_active_cast",
                    file: "seffect_51515121014",
                    animation: "animation_0",
                    impactEvent: "qishou_21_1_1",
                    anchor: "source",
                    impactRatio: 0.65
                },
                impact: {
                    code: "iori_active_impact",
                    file: "seffect_51515121015",
                    animation: "animation_0",
                    impactEvents: [
                        "hit_21_3_3",
                        "hit_21_3_2",
                        "hit_21_3_1"
                    ],
                    anchor: "enemy_formation",
                    impactRatio: 0.25
                }
            },
            IORI_ULTIMATE_3: {
                // Ultimate: bốn mốc khởi chiêu và năm nhịp sát thương.
                cast: {
                    code: "iori_ultimate_cast",
                    file: "seffect_51515121016",
                    animation: "animation_0",
                    impactEvents: [
                        "qishou_31_4_4",
                        "qishou_31_4_3",
                        "qishou_31_4_2",
                        "qishou_31_4_1"
                    ],
                    anchor: "source",
                    impactRatio: 0.7
                },
                impact: {
                    code: "iori_ultimate_impact",
                    file: "seffect_51515110211",
                    animation: "animation_0",
                    impactEvents: [
                        "hit_31_5_5",
                        "hit_31_5_4",
                        "hit_31_5_3",
                        "hit_31_5_2",
                        "hit_31_5_1"
                    ],
                    anchor: "enemy_formation",
                    impactRatio: 0.8
                }
            },

            KIRITO_BASIC_1: {
                cast: {
                    code: "kirito_basic_cast",
                    file: "seffect_31517110111",
                    animation: "animation_0",
                    impactEvent: "qishou_11_1_1",
                    anchor: "source"
                },
                impact: {
                    code: "kirito_basic_impact",
                    file: "seffect_31517110112",
                    animation: "animation_1",
                    impactEvents: ["hit_11_2_2", "hit_11_2_1"],
                    anchor: "target"
                }
            },
            KIRITO_ACTIVE_2: {
                cast: {
                    code: "kirito_active_cast",
                    file: "seffect_31517110111",
                    animation: "animation_1",
                    impactEvent: "qishou_21_1_1",
                    anchor: "source"
                },
                impact: {
                    code: "kirito_active_impact",
                    file: "seffect_31517110112",
                    animation: "animation_2",
                    impactEvents: [
                        "hit_21_5_5",
                        "hit_21_5_4",
                        "hit_21_5_3",
                        "hit_21_5_2",
                        "hit_21_5_1"
                    ],
                    anchor: "enemy_formation"
                }
            },
            KIRITO_ULTIMATE_3: {
                cast: {
                    code: "kirito_ultimate_cast",
                    file: "seffect_31517110111",
                    animation: "animation_2",
                    impactEvent: "qishou_31_1_1",
                    anchor: "source"
                },
                impact: {
                    code: "kirito_ultimate_impact",
                    file: "seffect_31517110112",
                    animation: "animation_3",
                    impactEvent: "hit_31_1_1",
                    impactEvents: ["hit_31_1_1"],
                    anchor: "enemy_formation"
                }
            },
            LELOUD_BASIC_1: {
                cast: {
                    code: "leloud_basic_cast",
                    file: "seffect_42515121011",
                    animation: "animation_0",
                    impactEvent: "qishou_1_1_1",
                    anchor: "source"
                },
                impact: {
                    code: "leloud_basic_impact",
                    file: "seffect_42515121011",
                    animation: "animation_1",
                    impactEvent: "hit_1_1_1",
                    impactEvents: ["hit_1_1_1"],
                    anchor: "target"
                }
            },
            LELOUD_ACTIVE_2: {
                cast: {
                    code: "leloud_active_cast",
                    file: "seffect_42515121012",
                    animation: "animation_1",
                    impactEvents: [
                        "qishou_11_4_4",
                        "qishou_11_4_3",
                        "qishou_11_4_2",
                        "qishou_11_4_1"
                    ],
                    anchor: "source"
                },
                impact: {
                    code: "leloud_active_impact",
                    file: "seffect_42515121012",
                    animation: "animation_2",
                    impactEvent: "hit_11_1_1",
                    impactEvents: ["hit_11_1_1"],
                    anchor: "enemy_formation"
                }
            },
            LELOUD_ULTIMATE_3: {
                cast: {
                    code: "leloud_ultimate_cast",
                    file: "seffect_42515121013",
                    animation: "animation_1",
                    impactEvent: "qishou_21_1_1",
                    anchor: "source"
                },
                impact: {
                    code: "leloud_ultimate_impact",
                    file: "seffect_42515121013",
                    animation: "animation_2",
                    impactEvents: [
                        "hit_21_3_3",
                        "hit_21_3_2",
                        "hit_21_3_1"
                    ],
                    anchor: "enemy_formation"
                }
            },
            THOR_BASIC_1: {
                cast: {
                    code: "thor_basic_cast",
                    file: "seffect_43513121011",
                    animation: "animation_0",
                    impactEvents: ["qishou_1_2_2", "qishou_1_2_1"],
                    anchor: "source"
                },
                impact: {
                    code: "thor_basic_impact",
                    file: "seffect_43503010111",
                    animation: "animation_5",
                    impactEvent: "hit_1_1_1",
                    impactEvents: ["hit_1_1_1"],
                    anchor: "target"
                }
            },
            THOR_ACTIVE_2: {
                cast: {
                    code: "thor_active_cast",
                    file: "seffect_43513121011",
                    animation: "animation_1",
                    impactEvents: [
                        "qishou_11_4_4",
                        "qishou_11_4_3",
                        "qishou_11_4_2",
                        "qishou_11_4_1"
                    ],
                    anchor: "source"
                },
                impact: {
                    code: "thor_active_impact",
                    file: "seffect_43513",
                    animation: "animation_0",
                    impactEvents: [
                        "hit_111_4_4",
                        "hit_111_4_3",
                        "hit_111_4_2",
                        "hit_111_4_1"
                    ],
                    anchor: "enemy_formation"
                }
            },
            THOR_ULTIMATE_3: {
                cast: {
                    code: "thor_ultimate_cast",
                    file: "seffect_43513121012",
                    animation: "animation_0",
                    impactEvent: "qishou_21_1_1",
                    anchor: "source"
                },
                impact: {
                    code: "thor_ultimate_impact",
                    file: "seffect_43503110111",
                    animation: "animation_1",
                    impactEvent: "hit_21_1_1",
                    impactEvents: ["hit_21_1_1"],
                    anchor: "enemy_formation"
                }
            },
            THOR_RAK_BASIC_1: {
                cast: {
                    code: "thor_rak_basic_cast",
                    file: "seffect_43517111011",
                    animation: "animation_0",
                    impactEvents: [
                        "qishou_1_3_3",
                        "qishou_1_3_2",
                        "qishou_1_3_1"
                    ],
                    anchor: "source"
                }
            },
            THOR_RAK_ACTIVE_2: {
                cast: {
                    code: "thor_rak_active_cast",
                    file: "seffect_43517111012",
                    animation: "animation_0",
                    impactEvent: "qishou_11_1_1",
                    anchor: "source"
                }
            },
            THOR_RAK_ULTIMATE_3: {
                cast: {
                    code: "thor_rak_ultimate_cast",
                    file: "seffect_43517111013",
                    animation: "animation_0",
                    impactEvents: [
                        "qishou_21_5_5",
                        "qishou_21_5_4",
                        "qishou_21_5_3",
                        "qishou_21_5_2",
                        "qishou_21_5_1"
                    ],
                    anchor: "source"
                }
            },
            GOJO_BASIC_1: {
                // Bộ 11011 có hai animation độc lập:
                // animation_0 phát qishou_1_1_1 khi Gojo khởi chiêu,
                // animation_1 phát hit_11_1_1 tại mục tiêu.
                cast: {
                    code: "gojo_basic_cast",
                    file: "seffect_52517111011",
                    animation: "animation_0",
                    impactEvent: "qishou_1_1_1",
                    impactEvents: ["qishou_1_1_1"],
                    anchor: "source",
                    impactRatio: 0.2
                },
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
                // Vô Hạ Hạn: superskill của Gojo phát faqi_31,
                // tương ứng qishou_31_1_1 trong effect 11014.
                cast: {
                    code: "gojo_infinity_active_cast",
                    file: "seffect_52517111014",
                    animation: "animation_0",
                    impactEvent: "qishou_31_1_1",
                    impactEvents: ["qishou_31_1_1"],
                    anchor: "source",
                    impactRatio: 0.7
                },
                // Đây là buff SELF, ba nhịp visual phải xuất hiện quanh Gojo,
                // không phải trên đội hình địch và không chờ event DAMAGE.
                impact: {
                    code: "gojo_infinity_active_impact",
                    file: "seffect_52517111014",
                    animation: "animation_1",
                    impactEvent: "hit_31_3_3",
                    impactEvents: [
                        "hit_31_3_3",
                        "hit_31_3_2",
                        "hit_31_3_1"
                    ],
                    anchor: "source",
                    trigger: "AFTER_CAST",
                    impactRatio: 0.25
                }
            },
            // Không khai báo cast effect riêng cho Gojo PASSIVE/counter:
            // phản kích dùng animation ultimateskill trên skeleton nhân vật.
            // seffect_52517111012/11013 có gần đầy đủ rig Gojo, không render
            // như VFX rời vì sẽ tạo hình Gojo thứ hai chồng lên nhân vật.
            IORI_DARK_BASIC_1: {
                // Iori Dark dùng bộ cast riêng (sanim_515151_1):
                // qishou_1_1_1; impact hit tương ứng dùng asset hit riêng hiện có.
                cast: {
                    code: "iori_dark_basic_cast",
                    file: "seffect_51515110111",
                    animation: "animation_0",
                    impactEvent: "qishou_1_1_1",
                    anchor: "source",
                    impactRatio: 0.45
                },
                // Không mượn impact của Iori thường: repo hiện không có
                // basic hit asset riêng cho Iori Dark.
            },
            IORI_DARK_ACTIVE_2: {
                // Iori thường dùng qishou_21_1; bản Dark dùng qishou_21_2.
                // Đây là animation_2 trong bộ asset riêng của Iori Dark.
                cast: {
                    code: "iori_dark_active_cast",
                    file: "seffect_51515110111",
                    animation: "animation_2",
                    impactEvents: [
                        "qishou_21_2_2",
                        "qishou_21_2_1"
                    ],
                    anchor: "source",
                    impactRatio: 0.65
                }
                // Không mượn impact của Iori thường: repo hiện không có
                // active hit asset riêng cho Iori Dark.
            },
            IORI_DARK_ULTIMATE_3: {
                cast: {
                    code: "iori_dark_ultimate_cast",
                    file: "seffect_51515110111",
                    animation: "animation_3",
                    impactEvent: "qishou_31_1_1",
                    anchor: "source",
                    impactRatio: 0.7
                },
                impact: {
                    code: "iori_dark_ultimate_impact",
                    file: "seffect_51515110211",
                    animation: "animation_0",
                    impactEvents: [
                        "hit_31_5_5",
                        "hit_31_5_4",
                        "hit_31_5_3",
                        "hit_31_5_2",
                        "hit_31_5_1"
                    ],
                    anchor: "enemy_formation",
                    impactRatio: 0.8
                }
            }
        };

        // Lelouch Dark chưa có bộ effect riêng trong asset hiện tại.
        // Leloud Dark dùng bộ effect riêng (4251511101x), không dùng chung
        // asset Leloud thường (4251512101x).
        definitions.LELOUD_DARK_BASIC_1 = {
            cast: {
                code: "leloud_dark_basic_cast",
                file: "seffect_42515111011",
                animation: "animation_0",
                impactEvent: "qishou_1_1_1",
                anchor: "source"
            },
            impact: {
                code: "leloud_dark_basic_impact",
                file: "seffect_42515111012",
                animation: "animation_0",
                impactEvent: "hit_1_1_1",
                impactEvents: ["hit_1_1_1"],
                anchor: "target"
            }
        };
        definitions.LELOUD_DARK_ACTIVE_2 = {
            cast: {
                code: "leloud_dark_active_cast",
                file: "seffect_42515111013",
                animation: "animation_0",
                impactEvent: "qishou_11_1_1",
                anchor: "source"
            },
            impact: {
                code: "leloud_dark_active_impact",
                file: "seffect_42515111012",
                animation: "animation_1",
                impactEvent: "hit_11_1_1",
                impactEvents: ["hit_11_1_1"],
                anchor: "enemy_formation"
            }
        };
        definitions.LELOUD_DARK_ULTIMATE_3 = {
            cast: {
                code: "leloud_dark_ultimate_cast",
                file: "seffect_42515111014",
                animation: "animation_0",
                impactEvent: "qishou_21_1_1",
                anchor: "source"
            },
            impact: {
                code: "leloud_dark_ultimate_impact",
                file: "seffect_42515111012",
                animation: "animation_2",
                impactEvents: [
                    "hit_21_5_5",
                    "hit_21_5_4",
                    "hit_21_5_3",
                    "hit_21_5_2",
                    "hit_21_5_1"
                ],
                anchor: "enemy_formation"
            }
        };

        // Runtime hiện tại phát mã animation "tohka_active" ở event cast,
        // còn event DAMAGE dùng mã effect "skill_effect_2".
        // Alias Tohka chỉ áp dụng riêng cho hero tohka_dark.
        const skillAliases = {
            TOHKA_ACTIVE: "TOHKA_DARK_ACTIVE_2",
            SKILL_EFFECT_2: "TOHKA_DARK_ACTIVE_2"
        };
        // Một skill chỉ được resolve từ chính hero đang thực hiện nó.
        // Ngăn skill code IORI_* và IORI_DARK_* bị lấy nhầm mapping của nhau.
        const heroPrefix = normalizedHeroCode.toUpperCase() + "_";
        const definitionKey = normalizedSkillCode.startsWith(heroPrefix) &&
                definitions[normalizedSkillCode]
            ? normalizedSkillCode
            : normalizedHeroCode === "tohka_dark"
                ? skillAliases[normalizedSkillCode]
                : null;

        const selected = definitions[definitionKey]?.[phase];
        if (!selected) {
            return [];
        }

        const texturePages = {
            seffect_51515110111: [
                "seffect_51515110111.png",
                "seffect_515151101112.png"
            ],
            seffect_31517110111: [
                "seffect_31517110111.png",
                "seffect_315171101112.png"
            ],
            seffect_42515121012: [
                "seffect_42515121012.png",
                "seffect_425151210122.png"
            ],
            seffect_42515121013: [
                "seffect_42515121013.png",
                "seffect_425151210132.png"
            ],
            seffect_42515121014: [
                "seffect_42515121014.png",
                "seffect_425151210142.png"
            ],
            seffect_43513121011: [
                "seffect_43513121011.png",
                "seffect_435131210112.png"
            ],
            // Iori active cast atlas sử dụng hai texture pages.
            seffect_51515121014: [
                "seffect_51515121014.png",
                "seffect_515151210142.png"
            ],
            // Gojo ultimate atlas sử dụng hai texture pages.
            seffect_52517111014: [
                "seffect_52517111014.png",
                "seffect_525171110142.png"
            ],
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
