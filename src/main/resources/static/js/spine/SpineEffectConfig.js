/**
 * Resolve Spine VFX exclusively from the hero mapping stored in
 * HeroDefinition.spine_skill_animations and delivered by the battle DTO.
 *
 * A phase may contain one effect or an ordered array of layered effects.
 */
window.SPINE_EFFECT_CONFIG = {
    effects: {},

    resolveEffects({ event, phase, heroCode, skillCode, sourceCharacter }) {
        const normalizedHeroCode = String(heroCode || "")
            .trim()
            .toLowerCase();
        const skillAnimations = sourceCharacter?.config?.skillAnimations;
        const normalizedSkillCode = String(
            skillCode || event?.skillCode || ""
        ).trim().toUpperCase();
        const normalizedTrigger = String(
            event?.effectCategory || ""
        ).trim().toUpperCase();
        const isGojoDomainBattleStart =
            normalizedHeroCode === "gojo" &&
            normalizedSkillCode === "GOJO_PASSIVE_5" &&
            normalizedTrigger === "BATTLE_START";
        const isGojoHachCounter =
            normalizedHeroCode === "gojo" &&
            normalizedSkillCode === "GOJO_PASSIVE_2" &&
            ["ON_ENEMY_ACTION", "COUNTER"].includes(normalizedTrigger);

        const safeSkillAnimations =
            skillAnimations &&
            typeof skillAnimations === "object" &&
            !Array.isArray(skillAnimations)
                ? skillAnimations
                : {};
        const definitions = safeSkillAnimations._SKILL_EFFECTS || {};
        const aliases = safeSkillAnimations._SKILL_EFFECT_ALIASES || {};
        const heroPrefix = normalizedHeroCode.toUpperCase() + "_";

        const definitionKey = normalizedSkillCode.startsWith(heroPrefix) &&
                definitions[normalizedSkillCode]
            ? normalizedSkillCode
            : aliases[normalizedSkillCode] || null;

        const selectedPhase = definitions[definitionKey]?.[phase];
        let selectedEffects = Array.isArray(selectedPhase)
            ? selectedPhase
            : selectedPhase
                ? [selectedPhase]
                : [];

        // Fallback theo đúng TAB_EffectPerfor 52517410 + Spine sanim/effect:
        // C4 phải phát cast superskill rồi impact toàn đội địch ở BATTLE_START.
        // Mapping DB vẫn được ưu tiên; fallback chỉ chạy khi mapping phase bị thiếu.
        if (!selectedEffects.length && isGojoDomainBattleStart) {
            selectedEffects = phase === "cast"
                ? [{
                    code: "gojo_domain_cast",
                    file: "seffect_52517111014",
                    animation: "animation_0",
                    anchor: "source",
                    impactEvent: "qishou_31_1_1",
                    impactEvents: ["qishou_31_1_1"],
                    trigger: "BATTLE_START"
                }]
                : phase === "impact"
                    ? [{
                        code: "gojo_domain_impact",
                        file: "seffect_52517111014",
                        animation: "animation_1",
                        anchor: "enemy_formation",
                        impactEvent: "hit_31_3_3",
                        impactEvents: [
                            "hit_31_3_3",
                            "hit_31_3_2",
                            "hit_31_3_1"
                        ],
                        trigger: "BATTLE_START"
                    }]
                    : [];
        }

        // Hách (Lua group 525171): cast khi địch hành động,
        // impact chỉ phát khi backend xác nhận DAMAGE category COUNTER.
        if (!selectedEffects.length && isGojoHachCounter) {
            selectedEffects = phase === "cast" && normalizedTrigger === "ON_ENEMY_ACTION"
                ? [{
                    code: "gojo_hach_counter_cast",
                    file: "seffect_52517111012",
                    animation: "animation_0",
                    anchor: "source",
                    impactEvent: "qishou_21_1_1",
                    impactEvents: ["qishou_21_1_1"],
                    trigger: "ON_ENEMY_ACTION"
                }]
                : phase === "impact" && normalizedTrigger === "COUNTER"
                    ? [{
                        code: "gojo_hach_counter_impact",
                        file: "seffect_52517111011",
                        animation: "animation_1",
                        anchor: "target",
                        impactEvent: "hit_11_1_1",
                        impactEvents: ["hit_11_1_1"],
                        trigger: "COUNTER"
                    }]
                    : [];
        }

        if (!selectedEffects.length) {
            return [];
        }

        const basePath = String(
            safeSkillAnimations._SPINE_EFFECT_BASE_PATH || "/assets/spine/seffects/"
        );
        const texturePages = {
            ...(safeSkillAnimations._SPINE_EFFECT_TEXTURE_PAGES || {})
        };
        texturePages.seffect_52517111014 ||= [
            "seffect_52517111014.png",
            "seffect_525171110142.png"
        ];
        const defaults = safeSkillAnimations._SPINE_EFFECT_DEFAULTS || {};

        return selectedEffects
            .filter(selected => selected && typeof selected === "object" && selected.file)
            // Passive có nhiều trigger nhưng chỉ một số trigger có animation trong TAB.
            // Ví dụ Lục Nhãn chỉ có VFX truy kích khi ON_REVIVE, không phát khi BATTLE_START/ON_KILL.
            .filter(selected => !selected.trigger ||
                String(selected.trigger).trim().toUpperCase() ===
                    String(event?.effectCategory || "").trim().toUpperCase())
            .map(selected => {
                const textures = texturePages[selected.file] || [
                    selected.file + ".png"
                ];

                return {
                    ...selected,
                    skeleton: basePath + selected.file + ".json",
                    atlas: basePath + selected.file + ".atlas",
                    textures: textures.map(file => basePath + file),
                    scale: Number(selected.scale ?? defaults.scale ?? 0.2),
                    displayScale: Number(
                        selected.displayScale ?? defaults.displayScale ?? 0.2
                    ),
                    layer: selected.layer || defaults.layer || "FRONT",
                    phase
                };
            });
    }
};
