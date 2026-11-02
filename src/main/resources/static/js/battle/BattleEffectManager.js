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
                effectCode
            });
            if (resolved) {
                return (Array.isArray(resolved) ? resolved : [resolved])
                    .filter(config => !config.phase || config.phase === phase);
            }
        }

        const selected = candidates[0];
        if (!selected) {
            return [];
        }

        return (Array.isArray(selected) ? selected : [selected])
            .filter(config => !config.phase || config.phase === phase);
    }

    /**
     * Kiểm tra impact của event có phải effect neo vào đội hình hay không.
     * Các effect này phải chờ ACTION_END để tránh lặp trên từng DAMAGE.
     */
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
     * Lấy tọa độ world của ô số 5 trong đội hình.
     *
     * Dùng cùng nguồn tọa độ với SpineFormation.updatePositions(), không
     * chuyển đổi DOMRect sang canvas vì canvas có thể có transform/zoom riêng.
     * Slot index 4 là ô giữa của lưới 3x3, kể cả khi ô đó đang trống.
     */
    getFormationCenter(team) {
        if (!team || !this.formation ||
            typeof this.formation.calculatePosition !== "function") {
            return null;
        }

        const normalizedTeam = String(team).toUpperCase();
        if (!["PLAYER", "ENEMY"].includes(normalizedTeam)) {
            return null;
        }

        return this.formation.calculatePosition(normalizedTeam, 4);
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

        if (anchor === "enemy_formation" || anchor === "target_formation" || anchor === "formation") {
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
        } else if (anchor === "center") {
            // Tọa độ world (0, 0) không phải tâm màn hình đáng tin cậy.
            // Với anchor center, dùng tâm đội hình địch nếu xác định được.
            basePosition = this.getFormationCenter(enemyTeam) || { x: 0, y: 0 };
        } else {
            const anchorCharacter = anchor === "target" ? target : source;
            if (!anchorCharacter) {
                return;
            }
            basePosition = {
                x: anchorCharacter.positionX,
                y: anchorCharacter.positionY
            };
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
