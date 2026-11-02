/**
 * Render đội hình đã chọn bằng các Spine asset đang có trong game.
 * Chỉ đảm nhiệm phần nhìn; dữ liệu đội hình vẫn do backend quản lý.
 */
class FormationSpinePreview {
    constructor(canvas, statusElement) {
        this.canvas = canvas;
        this.statusElement = statusElement;
        this.manager = null;
        this.formation = null;
        this.initialized = false;
        this.resizeHandler = null;
        this.syncVersion = 0;
    }

    async initialize() {
        if (this.initialized) return;

        if (!window.spine || !window.spine.webgl) {
            throw new Error("Spine runtime chưa được build. Chạy scripts/build-spine-runtime.ps1.");
        }

        this.manager = new SpineManager(this.canvas);
        this.formation = new SpineFormation(this.manager);
        this.manager.initialize();
        this.manager.start();
        this.formation.setLayout({
            playerX: -145, enemyX: 145, columnSpacing: 145,
            rowSpacing: 92, verticalOffset: 0, playerYOffset: 0
        });

        this.resizeHandler = () => {
            this.manager.resize();
            this.formation.setLayout({
                playerX: -145, columnSpacing: 145,
                rowSpacing: 92, verticalOffset: 0, playerYOffset: 0
            });
        };
        window.addEventListener("resize", this.resizeHandler);
        this.initialized = true;
    }

    async sync(heroes, slots) {
        const version = ++this.syncVersion;

        try {
            await this.initialize();
        } catch (error) {
            this.setStatus("SPINE PREVIEW CHƯA SẴN SÀNG");
            console.warn("[FormationSpinePreview]", error);
            return;
        }

        this.clearCharacters();
        const byId = new Map((heroes || []).map(hero => [Number(hero.instanceId), hero]));

        for (const slot of slots || []) {
            if (version !== this.syncVersion) return;

            const hero = byId.get(Number(slot.instanceId));
            if (!hero) continue;

            const config = window.SPINE_CONFIG?.resolveCharacterConfig
                ? window.SPINE_CONFIG.resolveCharacterConfig(hero)
                : window.SPINE_CONFIG?.character;
            if (!config) continue;

            let character = null;
            try {
                character = await this.manager.createCharacter({
                    ...config,
                    code: String(config.code || hero.heroCode) + "-preview-" + hero.instanceId
                });

                if (version !== this.syncVersion) {
                    this.manager.removeCharacter(character);
                    character.dispose();
                    return;
                }

                character.setScale(Math.min(Number(config.displayScale ?? 0.45), 0.45));
                this.formation.addCharacter(
                    "PLAYER",
                    String(hero.instanceId),
                    character,
                    Number(slot.row) * 3 + Number(slot.column)
                );

                const animations = character.getAnimationNames();
                const idle = config.idleAnimation && animations.includes(config.idleAnimation)
                    ? config.idleAnimation
                    : animations.includes("holdon") ? "holdon" : animations[0];
                if (idle) character.animationController.play(idle, true);
            } catch (error) {
                if (character) {
                    this.manager.removeCharacter(character);
                    character.dispose();
                }
                console.warn("[FormationSpinePreview] Không tải được Spine của " + hero.heroCode + ":", error);
            }
        }

        this.setStatus(
            this.formation.getTeamCharacters("PLAYER").length > 0
                ? "SPINE · ĐÃ TẢI NHÂN VẬT"
                : "CHƯA CÓ SPINE ĐỂ HIỂN THỊ"
        );
    }

    clearCharacters() {
        if (!this.formation) return;
        const entries = this.formation.getTeamCharacters("PLAYER");
        for (const entry of entries) {
            this.formation.removeCharacter("PLAYER", entry.instanceId);
            entry.character.dispose();
        }
        this.formation.clear();
    }

    setStatus(message) {
        if (this.statusElement) this.statusElement.textContent = message;
    }

    dispose() {
        this.syncVersion++;
        this.clearCharacters();
        if (this.resizeHandler) window.removeEventListener("resize", this.resizeHandler);
        this.manager?.dispose();
        this.manager = null;
        this.formation = null;
        this.initialized = false;
    }
}

window.addEventListener("beforeunload", () => {
    window.playerFormationScreen?.spinePreview?.dispose();
}, { once: true });