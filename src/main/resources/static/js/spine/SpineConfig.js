/**
 * Spine runtime configuration.
 *
 * Asset paths and per-skill animation/VFX mappings are supplied by
 * HeroDefinition from the database. This file contains no hero-specific
 * animation defaults so the database remains the single source of truth.
 */
(function () {
    function parseJsonValue(value, fallback, label) {
        if (value == null || value === "") {
            return fallback;
        }

        if (typeof value !== "string") {
            return value;
        }

        try {
            return JSON.parse(value);
        } catch (error) {
            console.error("[SPINE_CONFIG] JSON không hợp lệ:", label, error);
            return null;
        }
    }

    window.SPINE_CONFIG = {
        runtimeUrl: "/lib/spine/spine-runtime-3.5/spine-ts/build/spine-webgl.js",
        character: null,
        characters: {},

        resolveCharacterConfig(unit) {
            if (!unit) {
                return null;
            }

            const heroCode = String(unit.heroCode || "").trim().toLowerCase();
            const textures = parseJsonValue(
                unit.spineTextures,
                [],
                heroCode + ".spineTextures"
            );
            const skillAnimations = parseJsonValue(
                unit.spineSkillAnimations,
                {},
                heroCode + ".spineSkillAnimations"
            );

            if (
                !unit.spineSkeleton ||
                !unit.spineAtlas ||
                !Array.isArray(textures) ||
                textures.length === 0 ||
                !skillAnimations ||
                typeof skillAnimations !== "object" ||
                Array.isArray(skillAnimations)
            ) {
                console.warn(
                    "[SPINE_CONFIG] Thiếu asset hoặc mapping Spine trong database:",
                    heroCode || unit.heroCode
                );
                return null;
            }

            const movement = String(
                skillAnimations._BASIC_ATTACK?.movement || "DASH"
            ).trim().toUpperCase();

            return {
                code: heroCode || unit.heroCode,
                heroCode,
                skeleton: unit.spineSkeleton,
                atlas: unit.spineAtlas,
                textures,
                // Bao gồm animation, VFX catalog và metadata effect được seed
                // trong cùng spine_skill_animations.
                skillAnimations,
                basicAttackMovement: ["DASH", "STATIONARY"].includes(movement)
                    ? movement
                    : "DASH",
                scale: 0.16,
                displayScale: 0.45,
                offsetX: 0,
                offsetY: 0
            };
        },

        render: {
            backgroundAlpha: false,
            debug: false,
            zoom: 1,
            speed: 1
        }
    };
})();
