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

        if (
            !skillAnimations ||
            typeof skillAnimations !== "object" ||
            Array.isArray(skillAnimations)
        ) {
            return [];
        }

        const definitions = skillAnimations._SKILL_EFFECTS || {};
        const aliases = skillAnimations._SKILL_EFFECT_ALIASES || {};
        const normalizedSkillCode = String(
            skillCode || event?.skillCode || ""
        ).trim().toUpperCase();
        const heroPrefix = normalizedHeroCode.toUpperCase() + "_";

        const definitionKey = normalizedSkillCode.startsWith(heroPrefix) &&
                definitions[normalizedSkillCode]
            ? normalizedSkillCode
            : aliases[normalizedSkillCode] || null;

        const selectedPhase = definitions[definitionKey]?.[phase];
        const selectedEffects = Array.isArray(selectedPhase)
            ? selectedPhase
            : selectedPhase
                ? [selectedPhase]
                : [];

        if (!selectedEffects.length) {
            return [];
        }

        const basePath = String(
            skillAnimations._SPINE_EFFECT_BASE_PATH || "/assets/spine/seffects/"
        );
        const texturePages = skillAnimations._SPINE_EFFECT_TEXTURE_PAGES || {};
        const defaults = skillAnimations._SPINE_EFFECT_DEFAULTS || {};

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
