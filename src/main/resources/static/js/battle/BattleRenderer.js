/**
 * Điều phối hiển thị các sự kiện trong trận chiến.
 *
 * Backend chịu trách nhiệm tính toán kết quả.
 * Frontend chỉ phát animation và cập nhật giao diện.
 */
class BattleRenderer {

    constructor(options = {}) {
        this.formation = options.formation;
        this.effectManager = options.effectManager || null;
        this.damageRenderer = options.damageRenderer || new DamageRenderer({
            formation: this.formation,
            container: options.damageContainer || document.getElementById("damageOverlay")
        });
        this.statusEffectRenderer = options.statusEffectRenderer || new StatusEffectRenderer({
            formation: this.formation,
            container: options.statusContainer || document.getElementById("statusOverlay")
        });
        this.battleUI = options.battleUI || new BattleUI({
            formation: this.formation,
            container: options.battleUIContainer ||
                document.getElementById("battleUI")
        });

        this.eventQueue = [];
        this.playing = false;
        this.paused = false;

        this.eventDelay = options.eventDelay ?? 150;
        this.animationDelay = options.animationDelay ?? 350;
        this.transitionDelay = options.transitionDelay ?? 450;
        this.hitStaggerDelay = options.hitStaggerDelay ?? 90;
        this.speed = 1;
        this.actionContext = null;
        this.fallbackTimings = {
            BASIC_ATTACK: { impactMs: 380, durationMs: 720 },
            ACTIVE_SKILL: { impactMs: 550, durationMs: 1000 },
            ULTIMATE_SKILL: { impactMs: 850, durationMs: 1450 },
            PASSIVE: { impactMs: 320, durationMs: 650 },
            ATTACK: { impactMs: 380, durationMs: 720 }
        };
        this.currentEventIndex = 0;

        this.animationMap = {
            ATTACK: "att",
            BASIC_ATTACK: "att",
            ACTIVE_SKILL: "skill",
            ULTIMATE_SKILL: "ultimateskill",
            PASSIVE: "skill",
            HIT: "behit",
            DAMAGE: "behit",
            DEATH: "death",
            MOVE: "move",
            IDLE: "holdon"
        };

        this.listeners = new Map();
    }

    /**
     * Đăng ký callback cho một loại sự kiện.
     *
     * @param {string} eventType loại sự kiện
     * @param {Function} callback hàm xử lý
     */
    on(eventType, callback) {
        if (!this.listeners.has(eventType)) {
            this.listeners.set(eventType, []);
        }

        this.listeners.get(eventType).push(callback);
    }

    /**
     * Hủy đăng ký callback.
     *
     * @param {string} eventType loại sự kiện
     * @param {Function} callback hàm cần hủy
     */
    off(eventType, callback) {
        const callbacks = this.listeners.get(eventType);

        if (!callbacks) {
            return;
        }

        this.listeners.set(
            eventType,
            callbacks.filter(item => item !== callback)
        );
    }

    /**
     * Phát thông báo cho các thành phần đang lắng nghe.
     *
     * @param {string} eventType loại sự kiện
     * @param {Object} event dữ liệu sự kiện
     */
    emit(eventType, event) {
        const callbacks = this.listeners.get(eventType) || [];

        callbacks.forEach(callback => {
            try {
                callback(event);
            } catch (error) {
                console.error(
                    `[BattleRenderer] Listener error: ${eventType}`,
                    error
                );
            }
        });
    }

    /**
     * Nạp danh sách sự kiện chiến đấu vào hàng đợi.
     *
     * @param {Object[]} events danh sách sự kiện
     */
    loadEvents(events) {
        if (!Array.isArray(events)) {
            throw new Error("Battle events phải là một mảng.");
        }

        this.stop();

        this.eventQueue = [...events];
        this.currentEventIndex = 0;
    }

    /**
     * Bắt đầu phát lại sự kiện chiến đấu.
     */
    async play() {
        if (this.playing || this.eventQueue.length === 0) {
            return;
        }

        this.playing = true;
        this.paused = false;

        this.emit("BATTLE_PLAY_START", {
            totalEvents: this.eventQueue.length
        });

        try {
            while (
                this.currentEventIndex < this.eventQueue.length &&
                this.playing
                ) {
                if (this.paused) {
                    await this.waitUntilResumed();

                    if (!this.playing) {
                        break;
                    }
                }

                const event = this.eventQueue[this.currentEventIndex];

                await this.renderEvent(event);

                this.currentEventIndex++;

                this.emit("EVENT_RENDERED", {
                    event,
                    index: this.currentEventIndex,
                    total: this.eventQueue.length
                });

                if (this.eventDelay > 0) {
                    await this.delay(this.eventDelay);
                }
            }

            if (this.playing) {
                this.emit("BATTLE_PLAY_FINISHED", {
                    totalEvents: this.eventQueue.length
                });
            }

        } catch (error) {
            console.error("[BattleRenderer] Playback error:", error);

            this.emit("BATTLE_PLAY_ERROR", {
                error
            });

        } finally {
            this.playing = false;
            this.paused = false;
        }
    }

    /**
     * Phát một sự kiện chiến đấu.
     *
     * @param {Object} event dữ liệu sự kiện
     */
    async renderEvent(event) {
        if (!event || typeof event !== "object") {
            return;
        }

        this.emit("BEFORE_RENDER_EVENT", event);

        switch (event.type) {
            case "ACTION_START":
                this.beginAction(event);
                break;

            case "ACTION_END":
                await this.finishAction();
                break;

            case "ATTACK":
            case "BASIC_ATTACK":
            case "ACTIVE_SKILL":
            case "ULTIMATE_SKILL":
            case "PASSIVE":
                await this.renderAttack(event);
                break;

            case "DAMAGE":
                await this.renderDamage(event);
                break;

            case "HEAL":
                await this.renderHeal(event);
                break;

            case "BUFF":
            case "DEBUFF":
            case "CC":
            case "DISPEL":
            case "IMMUNITY":
                await this.renderStatusEffect(event);
                break;

            case "DEATH":
                await this.renderDeath(event);
                break;

            case "REVIVE":
                await this.renderRevive(event);
                break;

            case "MOVE":
                await this.renderMove(event);
                break;

            default:
                this.emit("UNKNOWN_EVENT", event);
                break;
        }

        this.emit("AFTER_RENDER_EVENT", event);
    }

    beginAction(event) {
        this.actionContext = {
            sourceTeam: event.sourceTeam,
            sourceInstanceId: event.sourceInstanceId,
            completionPromises: []
        };
    }

    registerActionCompletions(promises) {
        if (!this.actionContext) {
            this.actionContext = { completionPromises: [] };
        }

        promises.filter(Boolean).forEach(promise => {
            this.actionContext.completionPromises.push(
                Promise.resolve(promise).catch(error => {
                    console.warn("[BattleRenderer] Effect completion failed:", error);
                })
            );
        });
    }

    async finishAction() {
        const action = this.actionContext;
        this.actionContext = null;

        if (action?.completionPromises?.length) {
            await Promise.all(action.completionPromises);
        }

        // Khoảng nghỉ độc lập giữa hai tướng, sau khi toàn bộ effect đã kết thúc.
        await this.delayScaled(this.transitionDelay);
    }

    delayScaled(milliseconds) {
        const speed = Math.max(1, Number(this.speed) || 1);
        return this.delay(Math.max(0, Number(milliseconds) || 0) / speed);
    }

    setSpeed(speed) {
        this.speed = Math.max(1, Math.min(4, Number(speed) || 1));
    }

    /**
     * Phát animation tấn công của nhân vật.
     *
     * @param {Object} event Sự kiện tấn công, gồm team, instanceId và type.
     * @returns {Promise<void>}
     */
    async renderAttack(event) {
        const character = this.getCharacter(event);

        if (character) {
            const animationName = this.getAnimation(event.type);

            if (animationName) {
                this.playCharacterAnimation(character, animationName, false);
            }
        }

        const effects = await this.effectManager?.playForEvent(event, "cast") || [];

        if (effects.length > 0) {
            this.registerActionCompletions(
                effects.map(effect => effect.completionPromise)
            );
            await Promise.all(effects.map(effect => effect.impactPromise));
            return;
        }

        // Chưa có effect asset: dùng timing dự phòng theo từng loại kỹ năng.
        // Khi có Spine effect thật, impact và completion sẽ lấy từ animation của asset.
        const timing = this.fallbackTimings[event.type] ||
            this.fallbackTimings.BASIC_ATTACK;
        this.registerActionCompletions([
            this.delayScaled(timing.durationMs)
        ]);
        await this.delayScaled(timing.impactMs);
    }

    /**
     * Phát animation bị đánh, hiển thị sát thương và cập nhật HP mục tiêu.
     *
     * @param {Object} event sự kiện sát thương
     */
    /**
     * Phát hiệu ứng nhận sát thương và đồng bộ HP của mục tiêu.
     *
     * @param {Object} event Sự kiện sát thương từ backend.
     * @returns {Promise<void>}
     */
    async renderDamage(event) {
        const character = this.getTargetCharacter(event);

        // DAMAGE từ backend chính là mốc impact. Không chờ tải effect impact,
        // tránh làm số sát thương xuất hiện muộn hơn thời điểm va chạm.
        const impactEffectPromise = this.effectManager?.playForEvent(event, "impact");
        impactEffectPromise?.catch(error => {
            console.warn("[BattleRenderer] Không phát được impact effect:", error);
        });

        // Phát animation nhận sát thương.
        if (character) {
            this.playCharacterAnimation(character, "behit", false);
        }

        // Hiển thị số sát thương.
        if (this.damageRenderer) {
            this.damageRenderer.showDamage(event);
        }

        // Cập nhật HP theo kết quả backend.
        if (this.battleUI) {
            this.battleUI.updateCharacterState(event);
        }

        // Nếu mục tiêu chết, phát animation chết.
        if (event.alive === false ||
            (event.currentHp != null && Number(event.currentHp) <= 0)) {
            if (character) {
                this.playCharacterAnimation(character, "death", false);
            }

            if (this.battleUI) {
                this.battleUI.setDead(
                    event.targetTeam,
                    event.targetInstanceId
                );
            }
        }

        // Giãn nhẹ các hit liên tiếp của kỹ năng nhiều mục tiêu.
        await this.delayScaled(this.hitStaggerDelay);
    }

    /**
     * Hiển thị hồi máu, cập nhật HP và trạng thái nhân vật.
     *
     * @param {Object} event sự kiện hồi máu
     */
    async renderHeal(event) {
        const character = this.getTargetCharacter(event);
        if (character) {
            const animations = character.getAnimationNames();
            const idleAnimation = animations.includes("holdon")
                ? "holdon"
                : animations[0];

            if (idleAnimation) {
                this.playCharacterAnimation(character, idleAnimation, true);
            }
        }

        if (this.damageRenderer) {
            await this.damageRenderer.showHeal(event);
        }

        if (this.battleUI) {
            this.battleUI.updateCharacterState(event);
        }

        await this.delay(this.animationDelay);

        this.emit("HEAL_RENDERED", event);
    }

    /**
     * Hiển thị hiệu ứng trạng thái lên mục tiêu.
     *
     * @param {Object} event Sự kiện hiệu ứng trạng thái.
     * @returns {Promise<void>}
     */
    async renderStatusEffect(event) {
        if (!this.statusEffectRenderer) {
            return;
        }

        this.statusEffectRenderer.render(event);

        await this.delay(this.animationDelay);
    }

    /**
     * Phát animation chết.
     *
     * @param {Object} event sự kiện chết
     */
    async renderDeath(event) {
        const character = this.getTargetCharacter(event);

        if (character) {
            this.playCharacterAnimation(
                character,
                this.getAnimation("DEATH"),
                false
            );
        }

        if (this.battleUI) {
            this.battleUI.updateCharacterState(event);
        }

        this.emit("DEATH_RENDERED", event);
    }

    /**
     * Phát animation hồi sinh và cập nhật trạng thái nhân vật.
     *
     * @param {Object} event Sự kiện hồi sinh từ backend.
     * @returns {Promise<void>}
     */
    async renderRevive(event) {
        const character = this.getTargetCharacter(event);

        if (character) {
            const animations = character.getAnimationNames();
            const animation = animations.includes("holdon")
                ? "holdon"
                : animations.find(name => name !== "death");

            if (animation) {
                this.playCharacterAnimation(character, animation, false);
            }
        }

        if (this.battleUI) {
            this.battleUI.updateCharacterState(event);

            this.battleUI.setAlive(
                event.targetTeam,
                event.targetInstanceId
            );
        }

        await this.delay(this.animationDelay);
    }

    /**
     * Phát animation di chuyển.
     *
     * @param {Object} event sự kiện di chuyển
     */
    async renderMove(event) {
        const character = this.getCharacter(event);

        if (!character) {
            return;
        }

        this.playCharacterAnimation(
            character,
            this.getAnimation("MOVE"),
            false
        );

        this.emit("MOVE_RENDERED", event);
    }

    /**
     * Tìm nhân vật từ thông tin sự kiện.
     *
     * @param {Object} event sự kiện
     * @returns {SpineCharacter|null} nhân vật Spine
     */
    getCharacter(event) {
        if (!this.formation || !event) {
            return null;
        }

        const team = event.team || event.sourceTeam;
        const instanceId = event.instanceId ?? event.sourceInstanceId;

        if (!team || instanceId == null) {
            return null;
        }

        return this.formation.getCharacter(team, instanceId);
    }

    /**
     * Tìm nhân vật nhận sát thương hoặc hồi máu.
     *
     * @param {Object} event sự kiện chiến đấu
     * @returns {SpineCharacter|null} nhân vật mục tiêu
     */
    getTargetCharacter(event) {
        if (!this.formation || !event) {
            return null;
        }

        if (!event.targetTeam || event.targetInstanceId == null) {
            return null;
        }

        return this.formation.getCharacter(
            event.targetTeam,
            event.targetInstanceId
        );
    }

    /**
     * Lấy animation tương ứng với loại sự kiện.
     *
     * @param {string} eventType loại sự kiện
     * @returns {string} tên animation
     */
    getAnimation(eventType) {
        const animationMap = {
            ...this.animationMap,
            DAMAGE: "behit",
            HEAL: "holdon",
            REVIVE: "holdon"
        };

        return animationMap[eventType] || "holdon";
    }

    /**
     * Phát animation trên nhân vật.
     *
     * @param {SpineCharacter} character nhân vật
     * @param {string} animation tên animation
     * @param {boolean} loop lặp animation
     */
    playCharacterAnimation(character, animation, loop) {
        const availableAnimations = character.getAnimationNames();

        if (!availableAnimations.includes(animation)) {
            console.warn(
                `[BattleRenderer] Animation không tồn tại: ${animation}`
            );
            return;
        }

        character.animationController.play(animation, loop);
    }

    /**
     * Tạm dừng phát lại trận chiến.
     */
    pause() {
        if (!this.playing) {
            return;
        }

        this.paused = true;
        this.emit("BATTLE_PAUSED", {});
    }

    /**
     * Tiếp tục phát lại trận chiến.
     */
    resume() {
        if (!this.paused) {
            return;
        }

        this.paused = false;
        this.emit("BATTLE_RESUMED", {});
    }

    /**
     * Chờ cho đến khi trận chiến được tiếp tục hoặc dừng.
     *
     * @returns {Promise<void>}
     */
    waitUntilResumed() {
        return new Promise(resolve => {
            const check = () => {
                if (!this.paused || !this.playing) {
                    resolve();
                    return;
                }

                setTimeout(check, 50);
            };

            check();
        });
    }

    /**
     * Dừng phát lại trận chiến.
     */
    stop() {
        this.playing = false;
        this.paused = false;
        this.actionContext = null;

        this.emit("BATTLE_STOPPED", {});
    }

    /**
     * Chuyển về sự kiện đầu tiên.
     */
    reset() {
        this.stop();
        this.currentEventIndex = 0;
    }

    /**
     * Thiết lập tốc độ phát lại thông qua khoảng nghỉ giữa các event.
     *
     * @param {number} delay thời gian nghỉ tính bằng ms
     */
    setEventDelay(delay) {
        this.eventDelay = Math.max(0, Number(delay) || 0);
    }

    /**
     * Tạo khoảng nghỉ.
     *
     * @param {number} ms thời gian nghỉ
     * @returns {Promise<void>}
     */
    delay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}

/**
 * Hiển thị số sát thương và hồi máu trên nhân vật.
 */
class DamageRenderer {

    constructor(options = {}) {
        this.formation = options.formation;
        this.container = options.container;
        this.duration = 1000;
    }

    /**
     * Hiển thị sát thương trên nhân vật bị đánh.
     *
     * @param {Object} event dữ liệu sát thương
     */
    async showDamage(event) {
        this.showValue(event, "damage");
    }

    /**
     * Hiển thị lượng hồi máu trên nhân vật được hồi.
     *
     * @param {Object} event dữ liệu hồi máu
     */
    async showHeal(event) {
        this.showValue(event, "heal");
    }

    /**
     * Hiển thị giá trị trên vị trí nhân vật.
     *
     * @param {Object} event dữ liệu sự kiện
     * @param {string} type damage hoặc heal
     */
    showValue(event, type) {
        if (!this.container || !this.formation) {
            return;
        }

        const team = event.targetTeam;
        const instanceId = event.targetInstanceId;

        if (!team || instanceId == null) {
            console.warn("[DamageRenderer] Thiếu thông tin mục tiêu.", event);
            return;
        }

        const entry = this.formation.findCharacter(instanceId);

        if (!entry || entry.team !== team) {
            console.warn("[DamageRenderer] Không tìm thấy mục tiêu.", event);
            return;
        }

        const slot = entry.slotIndex;
        const row = Math.floor(slot / 3);
        const col = slot % 3;

        // Vị trí tương đối trong lớp phủ 3x3.
        const x = team === "PLAYER"
            ? 18 + col * 12
            : 82 - col * 12;

        const y = 25 + row * 22;

        const amount = Number(event.amount ?? 0);

        const element = document.createElement("div");

        element.className = `damage-number ${type}`;

        if (event.isCritical === true && type === "damage") {
            element.classList.add("critical");
        }

        element.textContent =
            (type === "heal" ? "+" : "-") +
            Math.abs(amount).toLocaleString("vi-VN") +
            (event.isCritical === true && type === "damage" ? "!" : "");

        element.style.left = `${x}%`;
        element.style.top = `${y}%`;

        this.container.appendChild(element);

        requestAnimationFrame(() => {
            element.classList.add("show");
        });

        setTimeout(() => {
            element.remove();
        }, this.duration);
    }
}

/**
 * Hiển thị biểu tượng và trạng thái hiệu ứng trên nhân vật.
 */
class StatusEffectRenderer {

    constructor(options = {}) {
        this.formation = options.formation;
        this.container = options.container;

        this.activeEffects = new Map();

        this.effectLabels = {
            ATK_UP: "ATK ↑",
            DEF_UP: "DEF ↑",
            SPEED_UP: "SPD ↑",
            CRIT_UP: "CRIT ↑",
            CRIT_DAMAGE_UP: "CRIT DMG ↑",
            ACCURACY_UP: "ACC ↑",
            RESISTANCE_UP: "RES ↑",
            PEN_UP: "PEN ↑",
            LIFESTEAL_UP: "LIFESTEAL ↑",

            ATK_DOWN: "ATK ↓",
            DEF_DOWN: "DEF ↓",
            SPEED_DOWN: "SPD ↓",
            CRIT_DOWN: "CRIT ↓",
            CRIT_DAMAGE_DOWN: "CRIT DMG ↓",
            ACCURACY_DOWN: "ACC ↓",
            RESISTANCE_DOWN: "RES ↓",
            PEN_DOWN: "PEN ↓",
            LIFESTEAL_DOWN: "LIFESTEAL ↓",

            STUN: "STUN",
            FREEZE: "FREEZE",
            SILENCE: "SILENCE",
            TAUNT: "TAUNT",

            IMMUNITY_BUFF: "BUFF IMMUNE",
            IMMUNITY_DEBUFF: "DEBUFF IMMUNE",
            IMMUNITY_CC: "CC IMMUNE"
        };
    }

    /**
     * Điều phối hiển thị hiệu ứng trạng thái.
     *
     * @param {Object} event Sự kiện hiệu ứng từ backend.
     * @returns {Promise<void>}
     */
    async render(event) {
        switch (event.type) {
            case "BUFF":
                this.addEffect(event, "buff");
                break;

            case "DEBUFF":
                this.addEffect(event, "debuff");
                break;

            case "CC":
                this.addCrowdControl(event);
                break;

            case "IMMUNITY":
                this.handleImmunity(event);
                break;

            case "DISPEL":
                this.dispelByCategory(event);
                break;

            default:
                break;
        }
    }

    /**
     * Thêm hoặc cập nhật hiệu ứng trên nhân vật.
     *
     * @param {Object} event Sự kiện hiệu ứng từ backend.
     */
    addEffect(event) {
        const {
            targetTeam,
            targetInstanceId,
            effectCode,
            effectCategory,
            duration
        } = event;

        if (!targetTeam || targetInstanceId == null || !effectCode) {
            return;
        }

        const characterKey = `${targetTeam}_${targetInstanceId}`;

        if (!this.activeEffects.has(characterKey)) {
            this.activeEffects.set(characterKey, new Map());
        }

        const effects = this.activeEffects.get(characterKey);

        const effectKey = `${effectCategory}_${effectCode}`;

        const effect = {
            effectCode,
            effectCategory,
            duration: Number(duration) || 0,
            remainingDuration: Number(duration) || 0,
            event,
            linkedCcCode: event.linkedCcCode || null
        };

        // Cùng loại hiệu ứng trên cùng nhân vật: cập nhật hiệu ứng.
        effects.set(effectKey, effect);

        // Cập nhật giao diện.
        this.updateCharacterEffects(characterKey);
    }

    /**
     * Thêm CC theo quy tắc thay thế và gia hạn.
     *
     * @param {Object} event Sự kiện CC.
     */
    addCrowdControl(event) {
        const {
            targetTeam,
            targetInstanceId,
            effectCode,
            duration
        } = event;

        if (!targetTeam || targetInstanceId == null || !effectCode) {
            return;
        }

        const characterKey = `${targetTeam}_${targetInstanceId}`;

        if (!this.activeEffects.has(characterKey)) {
            this.activeEffects.set(characterKey, new Map());
        }

        const effects = this.activeEffects.get(characterKey);
        const currentCC = [...effects.entries()].find(
            ([, effect]) => effect.effectCategory === "CC"
        );

        const newDuration = Number(duration) || 0;

        // Cùng CC: giữ thời hạn dài hơn.
        if (currentCC && currentCC[1].effectCode === effectCode) {
            const oldEffect = currentCC[1];

            oldEffect.remainingDuration = Math.max(
                oldEffect.remainingDuration,
                newDuration
            );

            oldEffect.duration = Math.max(
                oldEffect.duration,
                newDuration
            );

            this.updateCharacterEffects(characterKey);
            return;
        }

        // CC khác: xóa CC cũ và hiệu ứng phụ liên kết.
        if (currentCC) {
            const [oldKey, oldEffect] = currentCC;

            effects.delete(oldKey);

            for (const [key, effect] of effects) {
                if (effect.linkedCcCode === oldEffect.effectCode) {
                    effects.delete(key);
                }
            }
        }

        const effectKey = `CC_${effectCode}`;

        effects.set(effectKey, {
            effectCode,
            effectCategory: "CC",
            duration: newDuration,
            remainingDuration: newDuration,
            event,
            linkedCcCode: null
        });

        this.updateCharacterEffects(characterKey);
    }

    /**
     * Giải trừ hiệu ứng theo nhóm.
     *
     * @param {Object} event Sự kiện giải trừ.
     */
    dispelByCategory(event) {
        const characterKey =
            `${event.targetTeam}_${event.targetInstanceId}`;

        const effects = this.activeEffects.get(characterKey);

        if (!effects) {
            return;
        }

        const categoryMap = {
            DISPEL_BUFF: "BUFF",
            DISPEL_DEBUFF: "DEBUFF",
            DISPEL_CC: "CC"
        };

        const category = categoryMap[event.effectCode];

        if (!category) {
            return;
        }

        for (const [key, effect] of effects) {
            if (effect.effectCategory === category) {
                effects.delete(key);

                // Khi xóa CC, xóa luôn hiệu ứng phụ liên kết.
                if (category === "CC") {
                    for (const [linkedKey, linkedEffect] of effects) {
                        if (linkedEffect.linkedCcCode === effect.effectCode) {
                            effects.delete(linkedKey);
                        }
                    }
                }
            }
        }

        if (effects.size === 0) {
            this.activeEffects.delete(characterKey);
        }

        this.updateCharacterEffects(characterKey);
    }

    /**
     * Xử lý hiệu ứng miễn nhiễm.
     *
     * IMMUNITY_CC sẽ loại bỏ CC hiện tại và các hiệu ứng phụ liên kết.
     * IMMUNITY_BUFF và IMMUNITY_DEBUFF chỉ ghi nhận trạng thái miễn nhiễm,
     * không xóa những hiệu ứng đã tồn tại.
     *
     * @param {Object} event Sự kiện miễn nhiễm từ backend.
     */
    handleImmunity(event) {
        const characterKey =
            `${event.targetTeam}_${event.targetInstanceId}`;

        const effects = this.activeEffects.get(characterKey);

        // Miễn nhiễm CC loại bỏ CC hiện tại và hiệu ứng phụ.
        if (event.effectCode === "IMMUNITY_CC" && effects) {
            const currentCC = [...effects.entries()].find(
                ([, effect]) => effect.effectCategory === "CC"
            );

            if (currentCC) {
                const oldCCCode = currentCC[1].effectCode;

                effects.delete(currentCC[0]);

                for (const [key, effect] of effects) {
                    if (effect.linkedCcCode === oldCCCode) {
                        effects.delete(key);
                    }
                }
            }

            if (effects.size === 0) {
                this.activeEffects.delete(characterKey);
            }

            this.updateCharacterEffects(characterKey);
        }

        // Hiển thị trạng thái miễn nhiễm.
        this.addEffect(event, "immunity");
    }

    /**
     * Cập nhật danh sách hiệu ứng đang hiển thị của nhân vật.
     *
     * @param {string} characterKey Khóa nhân vật.
     */
    updateCharacterEffects(characterKey) {
        const effects = this.activeEffects.get(characterKey);

        if (!effects) {
            return;
        }

        // Tái sử dụng hàm hiển thị hiệu ứng hiện có.
        effects.forEach(effect => {
            this.addEffectElement(characterKey, effect);
        });
    }

    /**
     * Giảm thời gian tồn tại của tất cả hiệu ứng theo số round.
     *
     * @param {number} rounds Số round đã trôi qua.
     */
    tick(rounds = 1) {
        for (const [characterKey, effects] of this.activeEffects) {
            for (const [effectKey, effect] of effects) {
                if (effect.remainingDuration <= 0) {
                    continue;
                }

                effect.remainingDuration -= rounds;

                if (effect.remainingDuration <= 0) {
                    effects.delete(effectKey);
                    this.removeEffectElement(characterKey, effectKey);
                }
            }

            if (effects.size === 0) {
                this.activeEffects.delete(characterKey);
            } else {
                this.updateCharacterEffects(characterKey);
            }
        }
    }

    /**
     * Gỡ hiệu ứng theo mã hoặc gỡ toàn bộ hiệu ứng của nhân vật.
     *
     * @param {Object} event dữ liệu dispel
     */
    dispel(event) {
        const team = event.targetTeam;
        const instanceId = String(event.targetInstanceId ?? "");
        const effectCategory = event.effectCategory;

        if (!team || !instanceId) {
            return;
        }

        for (const [key, effect] of this.activeEffects.entries()) {
            if (
                effect.team !== team ||
                effect.instanceId !== instanceId
            ) {
                continue;
            }

            if (
                effectCategory &&
                effect.category !== effectCategory
            ) {
                continue;
            }

            this.removeEffectElement(key);
        }
    }

    /**
     * Xóa một hiệu ứng khỏi giao diện.
     *
     * @param {string} key khóa hiệu ứng
     */
    removeEffectElement(key) {
        const effect = this.activeEffects.get(key);

        if (!effect) {
            return;
        }

        effect.element.remove();
        this.activeEffects.delete(key);
    }

    /**
     * Tính vị trí hiển thị hiệu ứng theo slot.
     *
     * @param {string} team đội hình
     * @param {number} slotIndex slot nhân vật
     * @returns {{x:number,y:number}} tọa độ
     */
    calculatePosition(team, slotIndex) {
        const row = Math.floor(slotIndex / 3);
        const col = slotIndex % 3;

        return {
            x: team === "PLAYER"
                ? 18 + col * 12
                : 82 - col * 12,

            y: 15 + row * 22
        };
    }

    /**
     * Xóa toàn bộ hiệu ứng đang hiển thị.
     */
    clear() {
        for (const effect of this.activeEffects.values()) {
            effect.element.remove();
        }

        this.activeEffects.clear();
    }
}

/**
 * Quản lý thanh HP và trạng thái hiển thị của nhân vật.
 */
class BattleUI {

    constructor(options = {}) {
        this.formation = options.formation;
        this.container = options.container;

        this.characterElements = new Map();
    }

    /**
     * Tạo giao diện HP cho toàn bộ nhân vật trong hai đội.
     */
    initialize() {
        if (!this.container || !this.formation) {
            return;
        }

        this.container.innerHTML = "";
        this.characterElements.clear();

        for (const team of ["PLAYER", "ENEMY"]) {
            const characters = this.formation.getTeamCharacters(team);

            for (const entry of characters) {
                this.createCharacterUI(entry);
            }
        }
    }

    /**
     * Tạo thanh HP cho một nhân vật.
     *
     * @param {Object} entry thông tin nhân vật trong đội hình
     */
    createCharacterUI(entry) {
        const key = `${entry.team}:${entry.instanceId}`;

        const element = document.createElement("div");
        element.className = `battle-character-ui ${entry.team.toLowerCase()}`;
        element.dataset.characterKey = key;

        element.innerHTML = `
            <div class="battle-character-name">
                ${this.escapeHTML(entry.instanceId)}
            </div>
            <div class="battle-hp-track">
                <div class="battle-hp-fill"></div>
            </div>
            <div class="battle-hp-text">-- / --</div>
            <div class="battle-character-state"></div>
        `;

        const position = this.calculatePosition(
            entry.team,
            entry.slotIndex
        );

        element.style.left = `${position.x}%`;
        element.style.top = `${position.y}%`;

        this.container.appendChild(element);

        this.characterElements.set(key, {
            element,
            hpFill: element.querySelector(".battle-hp-fill"),
            hpText: element.querySelector(".battle-hp-text"),
            state: element.querySelector(".battle-character-state")
        });
    }

    /**
     * Cập nhật HP và trạng thái nhân vật từ event backend.
     *
     * @param {Object} event sự kiện chiến đấu
     */
    updateCharacterState(event) {
        if (!event) {
            return;
        }

        const team = event.targetTeam;
        const instanceId = event.targetInstanceId;

        if (!team || instanceId == null) {
            return;
        }

        const key = `${team}:${instanceId}`;
        const ui = this.characterElements.get(key);

        if (!ui) {
            return;
        }

        if (event.currentHp != null && event.maxHp != null) {
            this.updateHP(
                team,
                instanceId,
                event.currentHp,
                event.maxHp
            );
        }

        if (event.alive === false || event.type === "DEATH") {
            this.setDead(team, instanceId);
        }

        if (event.alive === true || event.type === "REVIVE") {
            this.setAlive(team, instanceId);
        }
    }

    /**
     * Cập nhật thanh HP.
     *
     * @param {string} team đội hình
     * @param {string|number} instanceId ID nhân vật
     * @param {number} currentHp HP hiện tại
     * @param {number} maxHp HP tối đa
     */
    updateHP(team, instanceId, currentHp, maxHp) {
        const key = `${team}:${instanceId}`;
        const ui = this.characterElements.get(key);

        if (!ui) {
            return;
        }

        const max = Math.max(0, Number(maxHp));
        const current = Math.max(0, Math.min(Number(currentHp), max));

        const percent = max > 0 ? current / max * 100 : 0;

        ui.hpFill.style.width = `${percent}%`;
        ui.hpText.textContent =
            `${Math.round(current).toLocaleString("vi-VN")} / ` +
            `${Math.round(max).toLocaleString("vi-VN")}`;

        ui.element.classList.toggle("low-hp", percent <= 25);
    }

    /**
     * Đánh dấu nhân vật đã chết.
     *
     * @param {string} team đội hình
     * @param {string|number} instanceId ID nhân vật
     */
    setDead(team, instanceId) {
        const key = `${team}:${instanceId}`;
        const ui = this.characterElements.get(key);

        if (!ui) {
            return;
        }

        ui.element.classList.add("dead");
        ui.state.textContent = "DEAD";
    }

    /**
     * Khôi phục trạng thái nhân vật sau hồi sinh.
     *
     * @param {string} team đội hình
     * @param {string|number} instanceId ID nhân vật
     */
    setAlive(team, instanceId) {
        const key = `${team}:${instanceId}`;
        const ui = this.characterElements.get(key);

        if (!ui) {
            return;
        }

        ui.element.classList.remove("dead");
        ui.state.textContent = "REVIVED";
    }

    /**
     * Tính vị trí giao diện theo slot nhân vật.
     *
     * @param {string} team đội hình
     * @param {number} slotIndex slot
     * @returns {{x:number,y:number}} tọa độ phần trăm
     */
    calculatePosition(team, slotIndex) {
        const row = Math.floor(slotIndex / 3);
        const col = slotIndex % 3;

        return {
            x: team === "PLAYER"
                ? 18 + col * 12
                : 82 - col * 12,

            y: 35 + row * 22
        };
    }

    /**
     * Làm sạch nội dung HTML trước khi hiển thị.
     *
     * @param {string} value nội dung
     * @returns {string} nội dung an toàn
     */
    escapeHTML(value) {
        return String(value).replace(/[&<>"']/g, char => ({
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': "&quot;",
            "'": "&#39;"
        })[char]);
    }

    /**
     * Xóa toàn bộ giao diện HP.
     */
    clear() {
        if (this.container) {
            this.container.innerHTML = "";
        }

        this.characterElements.clear();
    }

    /**
     * Cập nhật HP của nhân vật theo dữ liệu backend.
     *
     * @param {Object} event Sự kiện chứa targetTeam, targetInstanceId,
     * currentHp và maxHp.
     */
    updateCharacterState(event) {
        const key = `${event.targetTeam}_${event.targetInstanceId}`;
        const elements = this.characterElements?.get(key);

        if (!elements) {
            return;
        }

        const maxHp = Math.max(1, Number(event.maxHp) || 1);
        const currentHp = Math.max(
            0,
            Math.min(Number(event.currentHp) || 0, maxHp)
        );

        const percent = currentHp / maxHp * 100;

        if (elements.hpBar) {
            elements.hpBar.style.width = `${percent}%`;
        }

        if (elements.hpText) {
            elements.hpText.textContent =
                `${currentHp.toLocaleString()} / ${maxHp.toLocaleString()}`;
        }

        if (event.alive === false || currentHp <= 0) {
            this.setDead(event.targetTeam, event.targetInstanceId);
        } else {
            this.setAlive(event.targetTeam, event.targetInstanceId);
        }
    }

    /**
     * Đánh dấu nhân vật đã chết trên giao diện.
     *
     * @param {string} team Đội sở hữu nhân vật.
     * @param {number|string} instanceId ID nhân vật.
     */
    setDead(team, instanceId) {
        const key = `${team}_${instanceId}`;
        const elements = this.characterElements?.get(key);

        if (!elements) {
            return;
        }

        elements.container?.classList.add("dead");
    }

    /**
     * Đánh dấu nhân vật còn sống trên giao diện.
     *
     * @param {string} team Đội sở hữu nhân vật.
     * @param {number|string} instanceId ID nhân vật.
     */
    setAlive(team, instanceId) {
        const key = `${team}_${instanceId}`;
        const elements = this.characterElements?.get(key);

        if (!elements) {
            return;
        }

        elements.container?.classList.remove("dead");
    }
}