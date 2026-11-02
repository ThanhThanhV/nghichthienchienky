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
        const selectedSlots = Array.isArray(slots) ? slots : [];

        try {
            await this.initialize();
        } catch (error) {
            this.setStatus("SPINE PREVIEW CHƯA SẴN SÀNG", [error.message]);
            console.warn("[FormationSpinePreview]", error);
            return;
        }

        this.clearCharacters();

        if (selectedSlots.length === 0) {
            this.setStatus("CHƯA XẾP NHÂN VẬT");
            return;
        }

        const byId = new Map((heroes || []).map(hero => [Number(hero.instanceId), hero]));
        const errors = [];

        for (const slot of selectedSlots) {
            if (version !== this.syncVersion) return;

            const hero = byId.get(Number(slot.instanceId));
            if (!hero) {
                errors.push("Không tìm thấy nhân vật instanceId=" + slot.instanceId);
                continue;
            }

            const config = window.SPINE_CONFIG?.resolveCharacterConfig?.(hero);
            if (!config) {
                const name = hero.heroName || hero.heroCode || hero.instanceId;
                errors.push(name + ": chưa có mapping Spine");
                console.warn(
                    "[FormationSpinePreview] Chưa cấu hình asset cho:",
                    hero.heroCode,
                    { spineSkeleton: hero.spineSkeleton, avatarCode: hero.avatarCode }
                );
                continue;
            }

            let character = null;
            try {
                character = await this.manager.createCharacter({
                    ...config,
                    heroCode: hero.heroCode,
                    code: String(config.code || hero.heroCode || "hero") +
                        "-preview-" + hero.instanceId
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
                errors.push((hero.heroName || hero.heroCode) + ": " + error.message);
                console.warn(
                    "[FormationSpinePreview] Không tải được Spine của " + hero.heroCode + ":",
                    error
                );
            }
        }

        const loaded = this.formation.getTeamCharacters("PLAYER").length;
        if (errors.length === 0 && loaded === selectedSlots.length) {
            this.setStatus("SPINE · ĐÃ TẢI " + loaded + "/" + selectedSlots.length + " NHÂN VẬT");
        } else {
            this.setStatus(
                "SPINE · ĐÃ TẢI " + loaded + "/" + selectedSlots.length +
                " · LỖI " + errors.length,
                errors
            );
        }
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

    setStatus(message, details = []) {
        if (!this.statusElement) return;
        this.statusElement.textContent = message;
        this.statusElement.title = details.join("\n");
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
