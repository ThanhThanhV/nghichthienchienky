/**
 * Đồng bộ Spine formation với BattleScreen và BattleEventDTO.
 *
 * Backend vẫn quyết định HP, sát thương, hiệu ứng và kết quả trận đấu.
 * Lớp này chỉ tải skeleton, đặt vị trí và phát animation tương ứng.
 */
class BattleSpineIntegration {
    constructor(screen) {
        this.screen = screen;
        this.canvas = document.getElementById("battle-spine-canvas");
        this.manager = null;
        this.formation = null;
        this.renderer = null;
        this.initialized = false;
        this.unitsSignature = null;
        this.visibleCharacterCount = 0;
        this.speed = Number(screen.speed) || 1;
        this.resizeHandler = null;
    }

    /**
     * Khởi tạo WebGL, Spine manager và bộ điều phối animation.
     */
    async initialize() {
        if (this.initialized) {
            return;
        }

        if (!this.canvas) {
            throw new Error("Không tìm thấy battle-spine-canvas.");
        }

        if (!window.spine || !window.spine.webgl) {
            throw new Error(
                "Spine WebGL runtime chưa được build. " +
                "Chạy scripts/build-spine-runtime.ps1 trước khi mở trận."
            );
        }

        this.manager = new SpineManager(this.canvas);
        this.formation = new SpineFormation(this.manager);
        this.effectManager = new BattleEffectManager({
            manager: this.manager,
            formation: this.formation,
            canvas: this.canvas
        });
        this.renderer = new BattleRenderer({
            formation: this.formation,
            effectManager: this.effectManager,
            eventDelay: 0,
            animationDelay: 0,
            damageContainer: null,
            statusContainer: null,
            battleUIContainer: null
        });

        this.manager.initialize();
        this.manager.start();
        this.initialized = true;
        this.updateLayout();

        this.resizeHandler = () => {
            this.manager.resize();
            this.updateLayout();
        };

        window.addEventListener("resize", this.resizeHandler);
    }

    /**
     * Chọn cấu hình asset theo spineSkeleton, avatarCode hoặc heroCode.
     * Khi chưa có mapping riêng, sử dụng fallback trong SPINE_CONFIG.
     */
    resolveCharacterConfig(unit) {
        const spineConfig = window.SPINE_CONFIG || {};

        if (typeof spineConfig.resolveCharacterConfig === "function") {
            return spineConfig.resolveCharacterConfig(unit);
        }

        const characterConfigs = spineConfig.characters || {};
        const keys = [
            unit.spineSkeleton,
            unit.avatarCode,
            unit.heroCode
        ].filter(Boolean);

        for (const key of keys) {
            if (characterConfigs[key]) {
                return characterConfigs[key];
            }
        }

        return spineConfig.character || null;
    }

    /**
     * Tính tọa độ world phù hợp với màn hình desktop hoặc mobile.
     */
    getLayout() {
        const width = Math.max(320, this.canvas.clientWidth || 320);
        const mobile = window.matchMedia("(max-width: 600px)").matches;

        if (mobile) {
            return {
                playerX: -(width * 0.32),
                enemyX: width * 0.32,
                columnSpacing: width * 0.10,
                rowSpacing: 62,
                verticalOffset: 0,
                playerYOffset: 155,
                enemyYOffset: -155
            };
        }

        const halfWidth = width / 2;
        const baseX = Math.min(300, halfWidth * 0.58);
        const columnSpacing = Math.min(82, halfWidth * 0.16);

        return {
            playerX: -baseX,
            enemyX: baseX,
            columnSpacing,
            rowSpacing: 105,
            verticalOffset: 0,
            playerYOffset: 0,
            enemyYOffset: 0
        };
    }

    /**
     * Áp dụng lại vị trí đội hình sau khi canvas thay đổi kích thước.
     */
    updateLayout() {
        if (!this.formation) {
            return;
        }

        this.formation.setLayout(this.getLayout());
        this.positionCharactersFromSlots();
    }

    /**
     * Căn tâm từng skeleton theo đúng ô 3x3 đang hiển thị trên giao diện.
     * Nhờ vậy Spine, thanh máu và số sát thương dùng chung một vị trí nhân vật.
     */
    positionCharactersFromSlots() {
        if (!this.canvas || !this.formation) {
            return;
        }

        const canvasRect = this.canvas.getBoundingClientRect();
        const canvasCenterX = canvasRect.left + canvasRect.width / 2;
        const canvasBottom = canvasRect.bottom;

        for (const team of ["PLAYER", "ENEMY"]) {
            const container = document.getElementById(
                team === "PLAYER" ? "player-formation" : "enemy-formation"
            );
            if (!container) continue;

            for (const entry of this.formation.getTeamCharacters(team)) {
                const slot = [...container.querySelectorAll(".unit-slot")]
                    .find(element => element.dataset.instanceId === String(entry.instanceId));
                if (!slot) continue;

                const rect = slot.getBoundingClientRect();
                const x = rect.left + rect.width / 2 - canvasCenterX;
                const y = canvasBottom - (rect.top + rect.height * 0.39);
                entry.character.setPosition(x, y);
            }
        }
    }

    /**
     * Xóa skeleton cũ khỏi manager và formation.
     */
    clearCharacters() {
        this.effectManager?.clear();

        if (this.formation) {
            for (const team of ["PLAYER", "ENEMY"]) {
                const entries = this.formation.getTeamCharacters(team);

                for (const entry of entries) {
                    this.formation.removeCharacter(team, entry.instanceId);
                    entry.character.dispose();
                }
            }

            this.formation.clear();
        }

        this.visibleCharacterCount = 0;
        this.unitsSignature = null;
        document.querySelector(".battle-page")
            ?.classList.remove("battle-spine-active");
    }

    /**
     * Tạo lại Spine characters theo dữ liệu BattleScreenDTO.
     */
    async sync(screenState) {
        if (!screenState) {
            return;
        }

        try {
            await this.initialize();
        } catch (error) {
            console.warn("[BattleSpineIntegration] Spine chưa sẵn sàng:", error);
            return;
        }

        const units = [
            ...(screenState.playerUnits || []).map(unit => ({
                ...unit,
                team: "PLAYER"
            })),
            ...(screenState.enemyUnits || []).map(unit => ({
                ...unit,
                team: "ENEMY"
            }))
        ];

        const signature = units.map(unit => [
            unit.team,
            unit.instanceId,
            unit.spineSkeleton || "",
            unit.avatarCode || "",
            unit.row,
            unit.column
        ].join(":")).join("|");

        if (signature === this.unitsSignature) {
            return;
        }

        this.clearCharacters();
        this.updateLayout();

        for (const unit of units) {
            const instanceId = unit.instanceId;
            const row = Number(unit.row);
            const column = Number(unit.column);

            if (
                instanceId == null ||
                !Number.isInteger(row) || row < 0 || row > 2 ||
                !Number.isInteger(column) || column < 0 || column > 2
            ) {
                continue;
            }

            const config = this.resolveCharacterConfig(unit);
            if (!config) {
                console.warn(
                    "[BattleSpineIntegration] Chưa cấu hình asset cho:",
                    unit.heroCode
                );
                continue;
            }

            let character = null;

            try {
                character = await this.manager.createCharacter({
                    ...config,
                    heroCode: unit.heroCode || config.code,
                    code: String(config.code || unit.heroCode || "hero") +
                        "-" + unit.team + "-" + instanceId
                });

                character.setScale(Number(config.displayScale ?? 0.45));
                character.setFacingLeft(unit.team === "ENEMY");

                this.formation.addCharacter(
                    unit.team,
                    String(instanceId),
                    character,
                    row * 3 + column
                );

                const animations = character.getAnimationNames();
                const idleAnimation =
                    character.animationController.resolveIdleAnimation();

                if (unit.alive === false && animations.includes("death")) {
                    character.animationController.play("death", false);
                } else if (idleAnimation) {
                    character.animationController.play(idleAnimation, true);
                }

                character.animationController.setSpeed(this.speed);
                this.visibleCharacterCount++;
            } catch (error) {
                if (character) {
                    this.manager.removeCharacter(character);
                    character.dispose();
                }

                console.warn(
                    "[BattleSpineIntegration] Không tải được nhân vật " +
                    unit.heroCode + " (" + instanceId + "):",
                    error
                );
            }
        }

        this.unitsSignature = signature;

        if (this.visibleCharacterCount > 0) {
            document.querySelector(".battle-page")
                ?.classList.add("battle-spine-active");
            this.positionCharactersFromSlots();
        }
    }

    /**
     * Chuẩn hóa event backend và giao cho BattleRenderer phát animation.
     */
    async renderEvent(event) {
        if (!this.renderer || !event) {
            return;
        }

        const supportedTypes = new Set([
            "ACTION_START",
            "ACTION_END",
            "BASIC_ATTACK",
            "ACTIVE_SKILL",
            "ULTIMATE_SKILL",
            "PASSIVE",
            "DAMAGE",
            "HEAL",
            "DEATH",
            "REVIVE",
            "MOVE"
        ]);

        if (!supportedTypes.has(String(event.type).toUpperCase())) {
            return;
        }

        const sourceCharacter = this.formation?.getCharacter(
            event.sourceTeam,
            event.sourceInstanceId
        );
        const normalizedEvent = {
            ...event,
            heroCode: event.heroCode || sourceCharacter?.config?.heroCode,
            team: event.sourceTeam,
            instanceId: event.sourceInstanceId,
            isCritical: event.critical
        };

        await this.renderer.renderEvent(normalizedEvent);
    }

    /**
     * Đồng bộ tốc độ animation với nút x1/x2/x4 của BattleScreen.
     */
    setSpeed(speed) {
        this.speed = Math.max(1, Number(speed) || 1);

        if (!this.formation) {
            return;
        }

        for (const team of ["PLAYER", "ENEMY"]) {
            for (const entry of this.formation.getTeamCharacters(team)) {
                entry.character.animationController.setSpeed(this.speed);
            }
        }

        this.effectManager?.setSpeed(this.speed);
        this.renderer?.setSpeed(this.speed);
    }

    /**
     * Giải phóng listener, nhân vật và WebGL renderer khi rời trang.
     */
    dispose() {
        this.clearCharacters();

        if (this.resizeHandler) {
            window.removeEventListener("resize", this.resizeHandler);
            this.resizeHandler = null;
        }

        this.effectManager?.clear();
        this.manager?.dispose();
        this.manager = null;
        this.formation = null;
        this.effectManager = null;
        this.renderer = null;
        this.initialized = false;
    }
}

document.addEventListener("DOMContentLoaded", () => {
    const screen = window.BattleScreen;

    if (!screen) {
        console.warn("[BattleSpineIntegration] BattleScreen chưa được khởi tạo.");
        return;
    }

    const spineView = new BattleSpineIntegration(screen);
    screen.spineView = spineView;
    window.BattleSpineView = spineView;

    const originalApplyScreen = screen.applyScreen;
    screen.applyScreen = function (screenData, replace) {
        const result = originalApplyScreen.call(this, screenData, replace);

        if (replace) {
            this.spineSyncPromise = spineView.sync(this.state);
        }

        return result;
    };

    const originalStartBattle = screen.startBattle;
    screen.startBattle = async function () {
        await originalStartBattle.call(this);

        if (this.spineSyncPromise) {
            await this.spineSyncPromise;
        }
    };

    const originalPlayEvent = screen.playEvent;
    screen.playEvent = async function (event) {
        await spineView.renderEvent(event);
        return originalPlayEvent.call(this, event);
    };

    const originalUpdateControls = screen.updateControls;
    screen.updateControls = function () {
        const result = originalUpdateControls.call(this);
        spineView.setSpeed(this.speed);
        return result;
    };

    const originalReset = screen.reset;
    screen.reset = function () {
        const result = originalReset.call(this);
        spineView.clearCharacters();
        return result;
    };

    window.addEventListener("beforeunload", () => {
        spineView.dispose();
    }, { once: true });
});
