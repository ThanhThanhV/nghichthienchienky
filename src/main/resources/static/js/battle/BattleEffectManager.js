/**
 * Điều phối hiệu ứng Spine theo event chiến đấu.
 *
 * Cấu hình asset nằm trong SPINE_EFFECT_CONFIG; event backend chỉ mang mã
 * kỹ năng/hiệu ứng và ID nguồn/mục tiêu, không mang đường dẫn file.
 */
class BattleEffectManager {

    constructor(options = {}) {
        this.manager = options.manager;
        this.formation = options.formation;
        this.canvas = options.canvas;
        this.activeEffects = new Set();
        this.generation = 0;
    }

    getCharacter(team, instanceId) {
        if (!team || instanceId == null || !this.formation) {
            return null;
        }

        return this.formation.getCharacter(team, instanceId);
    }

    getHeroCode(event, sourceCharacter) {
        if (event.heroCode || event.sourceHeroCode) {
            return String(event.heroCode || event.sourceHeroCode).toLowerCase();
        }

        const configuredCode = sourceCharacter?.config?.heroCode;
        if (configuredCode) {
            return String(configuredCode).toLowerCase();
        }

        return String(sourceCharacter?.config?.code || "")
            .split(/-(PLAYER|ENEMY)-/i)[0]
            .toLowerCase();
    }

    resolveEffectConfigs(event, phase) {
        const registry = window.SPINE_EFFECT_CONFIG || {};
        const effects = registry.effects || {};
        const source = this.getCharacter(
            event.sourceTeam || event.team,
            event.sourceInstanceId ?? event.instanceId
        );
        const heroCode = this.getHeroCode(event, source);
        const skillCode = String(event.skillCode || "").toLowerCase();
        const effectCode = String(event.effectCode || "").toLowerCase();

        // Iori và Iori Dark dùng chung family asset "515151", vì vậy
        // không được resolve theo effectCode/skillCode global trước:
        // những alias này có thể trỏ sang bộ effect của hero còn lại.
        // Với hai hero này, chỉ nhận mapping đã được xác thực theo hero prefix.
        if (
            ["iori", "iori_dark"].includes(heroCode) &&
            typeof registry.resolveEffects === "function"
        ) {
            const resolved = registry.resolveEffects({
                event,
                phase,
                heroCode,
                skillCode,
                effectCode,
                sourceCharacter: source
            });

            return (Array.isArray(resolved) ? resolved : resolved ? [resolved] : [])
                .filter(config => !config.phase || config.phase === phase)
                .filter(config => this.matchesTrigger(config, event, phase));
        }

        const candidates = [
            effectCode && effects[effectCode],
            heroCode && skillCode && effects[heroCode + "." + skillCode],
            heroCode && skillCode && effects[heroCode + ":" + skillCode],
            skillCode && effects[skillCode]
        ].filter(Boolean);

        if (!candidates.length && typeof registry.resolveEffects === "function") {
            const resolved = registry.resolveEffects({
                event,
                phase,
                heroCode,
                skillCode,
                effectCode,
                sourceCharacter: source
            });
            if (resolved) {
                return (Array.isArray(resolved) ? resolved : [resolved])
                    .filter(config => !config.phase || config.phase === phase)
                    .filter(config => this.matchesTrigger(config, event, phase));
            }
        }

        const selected = candidates[0];
        if (!selected) {
            return [];
        }

        return (Array.isArray(selected) ? selected : [selected])
            .filter(config => !config.phase || config.phase === phase)
            .filter(config => this.matchesTrigger(config, event, phase));
    }

    /**
     * Kiểm tra impact của event có phải effect neo vào đội hình hay không.
     * Các effect này phải chờ ACTION_END để tránh lặp trên từng DAMAGE.
     */
    /**
     * Chỉ phát VFX đúng trigger mà backend ghi trên event.
     * Ví dụ Hách cast dùng ON_ENEMY_ACTION, còn impact chỉ dùng COUNTER.
     * AFTER_CAST được dành riêng cho nhánh hậu cast của BattleRenderer.
     */
    matchesTrigger(config, event, phase) {
        const trigger = String(config?.trigger || "").trim().toUpperCase();
        if (!trigger) {
            return true;
        }

        if (trigger === "AFTER_CAST") {
            return phase === "impact" &&
                ["ATTACK", "BASIC_ATTACK", "ACTIVE_SKILL", "ULTIMATE_SKILL", "PASSIVE"]
                    .includes(String(event?.type || "").toUpperCase());
        }

        const eventTrigger = String(event?.effectCategory || "")
            .trim().toUpperCase();
        return trigger === eventTrigger;
    }

    shouldDeferImpact(event) {
        return this.resolveEffectConfigs(event, "impact")
            .some(config => {
                const anchor = String(config.anchor || "").toLowerCase();
                return anchor === "enemy_formation" ||
                    anchor === "target_formation" ||
                    anchor === "formation";
            });
    }

    async playForEvent(event, phase = "cast") {
        if (!event || !this.manager) {
            return;
        }

        const configs = this.resolveEffectConfigs(event, phase);
        if (!configs.length) {
            return [];
        }

        const effects = [];
        for (const config of configs) {
            try {
                const effect = await this.playEffect(config, event);
                if (effect) effects.push(effect);
            } catch (error) {
                console.warn(
                    "[BattleEffectManager] Không phát được hiệu ứng:",
                    config.code || config.skeleton,
                    error
                );
            }
        }

        return effects;
    }

    /**
     * Lấy tọa độ world của slot số 5 (ô giữa) theo đúng vị trí đang hiển thị.
     *
     * Nhân vật được căn theo DOM slot trong BattleSpineIntegration, vì vậy
     * không dùng calculatePosition() ở đây: hệ tọa độ layout trừu tượng đó
     * không trùng với vị trí DOM của hai vùng đội hình trên battle-field.
     */
    getFormationCenter(team) {
        const normalizedTeam = String(team || "").toUpperCase();
        if (!["PLAYER", "ENEMY"].includes(normalizedTeam) || !this.canvas) {
            return null;
        }

        const container = document.getElementById(
            normalizedTeam === "PLAYER" ? "player-formation" : "enemy-formation"
        );
        const slot = container?.children?.[4];
        if (!slot) {
            return null;
        }

        const canvasRect = this.canvas.getBoundingClientRect();
        const slotRect = slot.getBoundingClientRect();
        if (!canvasRect.width || !canvasRect.height ||
            !slotRect.width || !slotRect.height) {
            return null;
        }

        const canvasCenterX = canvasRect.left + canvasRect.width / 2;
        const canvasCenterY = canvasRect.top + canvasRect.height / 2;
        const groundY = slotRect.bottom - Math.max(8, slotRect.height * 0.06);

        return {
            x: slotRect.left + slotRect.width / 2 - canvasCenterX,
            y: canvasCenterY - groundY
        };
    }

    /**
     * Lấy tọa độ neo trực tiếp từ DOM slot đang hiển thị trên battlefield.
     * DOM là nguồn tọa độ cuối cùng vì BattleSpineIntegration cũng đặt nhân vật
     * theo chính slot này; positionX/Y có thể cũ nếu layout vừa thay đổi.
     */
    getTargetSlotPosition(team, instanceId) {
        const normalizedTeam = String(team || "").toUpperCase();
        if (!["PLAYER", "ENEMY"].includes(normalizedTeam) ||
            instanceId == null || !this.canvas) {
            return null;
        }

        const container = document.getElementById(
            normalizedTeam === "PLAYER" ? "player-formation" : "enemy-formation"
        );
        const slot = [...(container?.querySelectorAll(".unit-slot") || [])]
            .find(element => element.dataset.instanceId === String(instanceId));
        if (!slot) {
            return null;
        }

        const canvasRect = this.canvas.getBoundingClientRect();
        const slotRect = slot.getBoundingClientRect();
        if (!canvasRect.width || !canvasRect.height ||
            !slotRect.width || !slotRect.height) {
            return null;
        }

        const canvasCenterX = canvasRect.left + canvasRect.width / 2;
        const canvasCenterY = canvasRect.top + canvasRect.height / 2;
        const groundY = slotRect.bottom - Math.max(5, slotRect.height * 0.04);

        return {
            x: slotRect.left + slotRect.width / 2 - canvasCenterX,
            y: canvasCenterY - groundY
        };
    }

    /**
     * Neo VFX vào phía trước mặt mục tiêu theo hướng nhân vật đang nhìn.
     * ENEMY nhìn trái nên VFX nằm ngoài mép trái slot; PLAYER nhìn phải nên
     * VFX nằm ngoài mép phải slot. Tọa độ đổi sang hệ world canvas.
     */
    getTargetFrontPosition(team, instanceId, target) {
        const normalizedTeam = String(team || "").toUpperCase();
        if (!["PLAYER", "ENEMY"].includes(normalizedTeam) || !this.canvas) {
            return target ? {
                x: target.positionX + (normalizedTeam === "ENEMY" ? -36 : 36),
                y: target.positionY
            } : null;
        }

        const container = document.getElementById(
            normalizedTeam === "PLAYER" ? "player-formation" : "enemy-formation"
        );
        const slot = [...(container?.querySelectorAll(".unit-slot") || [])]
            .find(element => element.dataset.instanceId === String(instanceId));

        if (!slot) {
            const fallbackOffset = this.canvas.width * 0.035;
            return target ? {
                x: target.positionX + (normalizedTeam === "ENEMY" ? -fallbackOffset : fallbackOffset),
                y: target.positionY
            } : null;
        }

        const canvasRect = this.canvas.getBoundingClientRect();
        const slotRect = slot.getBoundingClientRect();
        if (!canvasRect.width || !slotRect.width || !slotRect.height) {
            return target ? {
                x: target.positionX,
                y: target.positionY
            } : null;
        }

        const canvasCenterX = canvasRect.left + canvasRect.width / 2;
        const canvasCenterY = canvasRect.top + canvasRect.height / 2;
        const frontX = normalizedTeam === "ENEMY"
            ? slotRect.left - Math.max(8, slotRect.width * 0.08)
            : slotRect.right + Math.max(8, slotRect.width * 0.08);
        const groundY = slotRect.bottom - Math.max(5, slotRect.height * 0.04);

        return {
            x: frontX - canvasCenterX,
            y: canvasCenterY - groundY
        };
    }

    async playEffect(config, event) {
        const generation = this.generation;

        if (!config?.skeleton || !config?.atlas || !Array.isArray(config.textures)) {
            console.warn("[BattleEffectManager] Cấu hình effect thiếu skeleton/atlas/textures.", config);
            return;
        }

        const source = this.getCharacter(
            event.sourceTeam || event.team,
            event.sourceInstanceId ?? event.instanceId
        );
        const target = this.getCharacter(
            event.targetTeam,
            event.targetInstanceId
        );

        const anchor = String(config.anchor || "source").toLowerCase();
        const sourceTeam = event.sourceTeam || event.team;
        const enemyTeam = event.targetTeam ||
            (sourceTeam === "PLAYER" ? "ENEMY" : sourceTeam === "ENEMY" ? "PLAYER" : null);

        let basePosition;

        if (anchor === "target_front") {
            basePosition = this.getTargetFrontPosition(
                event.targetTeam,
                event.targetInstanceId,
                target
            );
        } else if (anchor === "enemy_formation" || anchor === "target_formation" || anchor === "formation") {
            basePosition = this.getFormationCenter(
                anchor === "formation" ? (config.team || enemyTeam) : enemyTeam
            );

            // Nếu đội hình không còn nhân vật, fallback về mục tiêu cụ thể.
            if (!basePosition && target) {
                basePosition = {
                    x: target.positionX,
                    y: target.positionY
                };
            }
        } else if (anchor === "screen_center") {
            // Gốc tọa độ world của canvas là tâm màn hình.
            basePosition = { x: 0, y: 0 };
        } else if (anchor === "center") {
            // Anchor center giữ nguyên hành vi cũ: tâm đội hình địch.
            basePosition = this.getFormationCenter(enemyTeam) || { x: 0, y: 0 };
        } else {
            const anchorCharacter = anchor === "target" ? target : source;

            if (anchor === "target") {
                // Luôn ưu tiên slot DOM hiện tại cho impact target. Đây là cùng
                // phép đổi hệ tọa độ mà BattleSpineIntegration dùng để đặt rig,
                // nên không phụ thuộc positionX/Y có bị stale sau layout/reflow.
                basePosition = this.getTargetSlotPosition(
                    event.targetTeam,
                    event.targetInstanceId
                );
            }

            // Fallback cho màn test không có DOM slot hoặc anchor source.
            if (!basePosition && anchorCharacter) {
                basePosition = {
                    x: anchorCharacter.positionX,
                    y: anchorCharacter.positionY
                };
            }

            if (!basePosition) {
                console.warn("[BattleEffectManager] Không tìm được tọa độ anchor:", {
                    anchor,
                    targetTeam: event.targetTeam,
                    targetInstanceId: event.targetInstanceId,
                    targetFound: Boolean(target),
                    sourceFound: Boolean(source)
                });
                return;
            }
        }

        if (!basePosition) {
            return;
        }

        const effect = await this.manager.createEffect({
            ...config,
            code: config.code || event.skillCode || event.effectCode || "battle-effect"
        });

        if (generation !== this.generation) {
            this.manager.removeEffect(effect);
            return;
        }

        effect.onRemoved = () => this.activeEffects.delete(effect);
        this.activeEffects.add(effect);

        const baseX = basePosition.x;
        const baseY = basePosition.y;

        const mirrorForEnemy = config.mirrorForEnemy !== false;
        effect.setFacingLeft(
            mirrorForEnemy && sourceTeam === "ENEMY"
        );

        // Đặt scale/hướng trước, rồi mới gán vị trí cuối cùng để mọi thao tác
        // căn skeleton không thể ghi đè tọa độ anchor của ô số 5.
        effect.setScale(Number(config.displayScale ?? config.scale ?? 1));
        effect.setPosition(
            baseX + (Number(config.offsetX) || 0),
            baseY + (Number(config.offsetY) || 0)
        );
        effect.animationController.setSpeed(
            Number(window.BattleScreen?.speed) || Number(config.speed) || 1
        );

        return effect;
    }

    setSpeed(speed) {
        const normalized = Math.max(0.1, Math.min(4, Number(speed) || 1));
        this.activeEffects.forEach(effect => {
            effect.animationController?.setSpeed(normalized);
        });
    }

    clear() {
        this.generation++;
        [...this.activeEffects].forEach(effect => {
            this.manager?.removeEffect(effect);
        });
        this.activeEffects.clear();
    }
}
