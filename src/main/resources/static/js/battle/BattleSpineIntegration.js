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
        this.statusElement = document.getElementById("battle-spine-status");
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
        this.renderer.setSpeed(this.speed);
        this.effectManager.setSpeed(this.speed);

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
     * Chỉ trả về asset khi tìm được mapping rõ ràng cho chính nhân vật đó.
     * Không dùng fallback chung vì sẽ hiển thị sai tướng khi dữ liệu mapping thiếu.
     */
    resolveCharacterConfig(unit) {
        return window.SPINE_CONFIG?.resolveCharacterConfig?.(unit) || null;
    }

    /**
     * Tính tọa độ world phù hợp với màn hình desktop hoặc mobile.
     */
    getLayout() {
        const width = Math.max(320, this.canvas.clientWidth || 320);
        const mobile = window.matchMedia("(max-width: 600px)").matches;

        if (mobile) {
            return {
                playerX: -(width * 0.23),
                enemyX: width * 0.23,
                columnSpacing: width * 0.07,
                rowSpacing: 68,
                verticalOffset: 0,
                playerYOffset: 155,
                enemyYOffset: -155
            };
        }

        const halfWidth = width / 2;
        const baseX = Math.min(210, halfWidth * 0.40);
        const columnSpacing = Math.min(58, halfWidth * 0.11);

        return {
            playerX: -baseX,
            enemyX: baseX,
            columnSpacing,
            rowSpacing: 84,
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
        const canvasCenterY = canvasRect.top + canvasRect.height / 2;

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

                // Scale every skeleton against the REAL 3x3 slot dimensions.
                // The old fixed 0.45 displayScale was tuned for the former
                // 116px+ rows; the portrait layout has much shorter rows.
                // 140x116 is the reference slot size used by that old layout.
                // This keeps each rig proportional while preventing adjacent
                // characters from spilling into one another's cells.
                const baseScale = Number(entry.character.config?.displayScale ?? 0.45);
                const slotFit = Math.min(
                    rect.width / 140,
                    rect.height / 116
                );
                // Thu nhỏ thêm 20% so với tỷ lệ đã fit theo slot hiện tại.
                entry.character.setScale(baseScale * Math.max(0.01, slotFit));

                // Character placement is anchored to the bottom of its slot.
                // HP UI is an independent overlay above the character and must
                // never move the skeleton when HP text/bar dimensions change.
                const groundY = rect.bottom - Math.max(5, rect.height * 0.04);
                const y = canvasCenterY - groundY;
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
        this.setStatus("");
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
            this.setStatus("SPINE CHƯA SẴN SÀNG", [error.message]);
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
            unit.heroCode || "",
            unit.spineSkeleton || "",
            unit.spineAtlas || "",
            unit.spineTextures || "",
            unit.spineSkillAnimations || "",
            unit.avatarCode || "",
            unit.row,
            unit.column
        ].join(":")).join("|");

        if (signature === this.unitsSignature) {
            return;
        }

        this.clearCharacters();
        this.updateLayout();

        if (units.length === 0) {
            this.unitsSignature = signature;
            this.setStatus("TRẬN ĐẤU KHÔNG CÓ NHÂN VẬT");
            return;
        }

        const errors = [];
        const occupiedPositions = new Set();

        for (const unit of units) {
            const instanceId = unit.instanceId;
            const row = Number(unit.row);
            const column = Number(unit.column);
            const positionKey = unit.team + ":" + row + ":" + column;

            if (
                instanceId == null ||
                !Number.isInteger(row) || row < 0 || row > 2 ||
                !Number.isInteger(column) || column < 0 || column > 2
            ) {
                errors.push((unit.heroName || unit.heroCode || instanceId) +
                    ": vị trí đội hình không hợp lệ");
                continue;
            }

            if (occupiedPositions.has(positionKey)) {
                errors.push((unit.heroName || unit.heroCode || instanceId) +
                    ": trùng vị trí " + (row + 1) + "-" + (column + 1));
                continue;
            }
            occupiedPositions.add(positionKey);

            const config = this.resolveCharacterConfig(unit);
            if (!config) {
                const name = unit.heroName || unit.heroCode || instanceId;
                errors.push(name + ": chưa có mapping Spine");
                console.warn(
                    "[BattleSpineIntegration] Chưa cấu hình asset cho:",
                    unit.heroCode,
                    { spineSkeleton: unit.spineSkeleton, avatarCode: unit.avatarCode }
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

                // Scale is applied after the character is attached to its
                // actual formation slot, when its rendered dimensions are known.
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

                errors.push((unit.heroName || unit.heroCode || instanceId) +
                    ": " + error.message);
                console.warn(
                    "[BattleSpineIntegration] Không tải được nhân vật " +
                    unit.heroCode + " (" + instanceId + "):",
                    error
                );
            }
        }

        // Chỉ ẩn UI dự phòng khi toàn bộ nhân vật đã tải đúng asset.
        // Nếu thiếu dù chỉ một mapping, giữ nguyên đội hình HTML để không che mất tướng.
        if (errors.length > 0 || this.visibleCharacterCount !== units.length) {
            const loaded = this.visibleCharacterCount;
            this.clearCharacters();
            this.unitsSignature = signature;
            this.setStatus(
                "SPINE CHƯA ĐỒNG BỘ · " + loaded + "/" + units.length +
                " · XEM CHI TIẾT LỖI",
                errors
            );
            console.warn("[BattleSpineIntegration] Đội hình Spine chưa đồng bộ:", errors);
            return;
        }

        this.unitsSignature = signature;
        document.querySelector(".battle-page")
            ?.classList.add("battle-spine-active");
        this.positionCharactersFromSlots();
        this.setStatus("SPINE · ĐÃ TẢI " + this.visibleCharacterCount + "/" + units.length);
    }

    setStatus(message, details = []) {
        if (!this.statusElement) return;
        this.statusElement.textContent = message;
        this.statusElement.title = details.join("\n");
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
            "BUFF",
            "DEBUFF",
            "CC",
            "DISPEL",
            "IMMUNITY",
            "DEATH",
            "REVIVE",
            "MOVE"
        ]);

        if (!supportedTypes.has(String(event.type).toUpperCase())) {
            return;
        }

        const sourceTeam = event.sourceTeam || event.team;
        const sourceInstanceId = event.sourceInstanceId ?? event.instanceId;
        const sourceCharacter = this.formation?.getCharacter(
            sourceTeam,
            sourceInstanceId
        );
        const normalizedEvent = {
            ...event,
            sourceTeam,
            sourceInstanceId,
            heroCode: event.heroCode || event.sourceHeroCode ||
                sourceCharacter?.config?.heroCode,
            team: sourceTeam,
            instanceId: sourceInstanceId,
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
