/**
 * Resolve Spine VFX exclusively from the hero mapping stored in
 * HeroDefinition.spine_skill_animations and delivered by the battle DTO.
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

        let definitionKey = normalizedSkillCode.startsWith(heroPrefix) &&
                definitions[normalizedSkillCode]
            ? normalizedSkillCode
            : aliases[normalizedSkillCode] || null;

        const selected = definitions[definitionKey]?.[phase];
        if (!selected?.file) {
            return [];
        }

        const basePath = String(
            skillAnimations._SPINE_EFFECT_BASE_PATH || "/assets/spine/seffects/"
        );
        const texturePages = skillAnimations._SPINE_EFFECT_TEXTURE_PAGES || {};
        const defaults = skillAnimations._SPINE_EFFECT_DEFAULTS || {};
        const textures = texturePages[selected.file] || [
            selected.file + ".png"
        ];

        return [{
            ...selected,
            skeleton: basePath + selected.file + ".json",
            atlas: basePath + selected.file + ".atlas",
            textures: textures.map(file => basePath + file),
            scale: Number(defaults.scale ?? 0.2),
            displayScale: Number(defaults.displayScale ?? 0.2),
            layer: defaults.layer || "FRONT",
            phase
        }];
    }
};
