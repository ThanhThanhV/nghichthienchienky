(function () {
    const assetRoot = "/assets/spine/heroes/";
    const definitions = {
        asuna_dark: {
            folder: "asuna_dark",
            base: "sanim_315141_1",
            textures: ["sanim_315141_1.png", "sanim_315141_12.png"]
        },
        gojo: {
            folder: "gojo",
            base: "sanim_525171",
            textures: ["sanim_525171.png"]
        },
        iori: {
            folder: "iori",
            base: "sanim_515151",
            textures: ["sanim_515151.png"]
        },
        iori_dark: {
            folder: "iori_dark",
            base: "sanim_515151_1",
            textures: ["sanim_515151_1.png"]
        },
        kirito: {
            folder: "kirito",
            base: "sanim_315171",
            textures: ["sanim_315171.png"]
        },
        leloud: {
            folder: "leloud",
            base: "sanim_425151",
            textures: ["sanim_425151.png"]
        },
        leloud_dark: {
            folder: "leloud_dark",
            base: "sanim_425151_1",
            textures: ["sanim_425151_1.png"]
        },
        thor: {
            folder: "thor",
            base: "sanim_435131",
            textures: ["sanim_435131.png", "sanim_4351312.png"]
        },
        thor_rak: {
            folder: "thor_rak",
            base: "sanim_435131_1",
            textures: ["sanim_435131_1.png", "sanim_435131_12.png"]
        },
        tohka_dark: {
            folder: "tohka_dark",
            base: "sanim_415141_1",
            textures: ["sanim_415141_1.png", "sanim_415141_12.png"]
        }
    };

    const characters = {};

    Object.entries(definitions).forEach(([key, definition]) => {
        const directory = assetRoot + definition.folder + "/";
        const character = {
            code: key,
            skeleton: directory + definition.base + ".json",
            atlas: directory + definition.base + ".atlas",
            textures: definition.textures.map(file => directory + file),
            scale: 0.2,
            displayScale: 0.45,
            offsetX: 0,
            offsetY: 0
        };

        characters[key] = character;
        characters[definition.base.toLowerCase()] = character;
    });

    function candidateKeys(value) {
        if (!value) {
            return [];
        }

        const normalized = String(value)
            .replace(/\\/g, "/")
            .split(/[?#]/, 1)[0]
            .toLowerCase();

        const segments = normalized.split("/").filter(Boolean);
        const filename = segments.length
            ? segments[segments.length - 1]
            : normalized;
        const baseName = filename.replace(/\.(json|atlas|skel)$/i, "");
        const folderName = segments.length > 1
            ? segments[segments.length - 2]
            : "";

        return [normalized, filename, baseName, folderName].filter(Boolean);
    }

    window.SPINE_CONFIG = {
        runtimeUrl: "/lib/spine/spine-runtime-3.5/spine-ts/build/spine-webgl.js",
        // No global fallback: every hero must resolve to its own configured asset.
        character: null,
        characters,
        resolveCharacterConfig(unit) {
            const values = [
                unit?.spineSkeleton,
                unit?.avatarCode,
                unit?.heroCode
            ];

            // Tướng được tạo từ giao diện quản lý dùng asset mapping trong database.
            if (unit?.spineSkeleton && unit?.spineAtlas && unit?.spineTextures) {
                let textures = unit.spineTextures;
                if (typeof textures === "string") {
                    try {
                        textures = JSON.parse(textures);
                    } catch (error) {
                        console.error("Spine texture mapping không phải JSON hợp lệ:", error);
                        return null;
                    }
                }

                let skillAnimations = unit.spineSkillAnimations || {};
                if (typeof skillAnimations === "string") {
                    try {
                        skillAnimations = JSON.parse(skillAnimations);
                    } catch (error) {
                        console.error("Spine skill animation mapping không phải JSON hợp lệ:", error);
                        skillAnimations = {};
                    }
                }

                if (Array.isArray(textures) && textures.length > 0) {
                    return {
                        code: unit.heroCode,
                        skeleton: unit.spineSkeleton,
                        atlas: unit.spineAtlas,
                        textures,
                        skillAnimations: skillAnimations && typeof skillAnimations === "object"
                            ? skillAnimations
                            : {},
                        scale: 0.2,
                        displayScale: 0.45,
                        offsetX: 0,
                        offsetY: 0
                    };
                }
            }

            for (const value of values) {
                for (const key of candidateKeys(value)) {
                    if (characters[key]) {
                        return characters[key];
                    }
                }
            }



            return null;
        },
        render: {
            backgroundAlpha: false,
            debug: false,
            zoom: 1,
            speed: 1
        }
    };
})();
