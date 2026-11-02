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

    async playForEvent(event, phase = "cast") {
        if (!event || !this.manager) {
            return;
        }

        const configs = this.resolveEffectConfigs(event, phase);
        if (!configs.length) {
            return;
        }

        for (const config of configs) {
            try {
                await this.playEffect(config, event);
            } catch (error) {
                console.warn(
                    "[BattleEffectManager] Không phát được hiệu ứng:",
                    config.code || config.skeleton,
                    error
                );
            }
        }
    }

    async playEffect(config, event) {
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
        const anchorCharacter = anchor === "target" ? target : source;

        if (anchor !== "center" && !anchorCharacter) {
            return;
        }

        const effect = await this.manager.createEffect({
            ...config,
            code: config.code || event.skillCode || event.effectCode || "battle-effect"
        });

        this.activeEffects.add(effect);

        const baseX = anchor === "center"
            ? 0
            : anchorCharacter.positionX;
        const baseY = anchor === "center"
            ? 0
            : anchorCharacter.positionY;

        effect.setPosition(
            baseX + (Number(config.offsetX) || 0),
            baseY + (Number(config.offsetY) || 0)
        );
        effect.setScale(Number(config.displayScale ?? config.scale ?? 1));

        const mirrorForEnemy = config.mirrorForEnemy !== false;
        const sourceTeam = event.sourceTeam || event.team;
        effect.setFacingLeft(
            mirrorForEnemy && sourceTeam === "ENEMY"
        );
        effect.animationController.setSpeed(
            Number(config.speed) || Number(window.BattleScreen?.speed) || 1
        );
    }

    setSpeed(speed) {
        const normalized = Math.max(0.1, Math.min(4, Number(speed) || 1));
        this.activeEffects.forEach(effect => {
            effect.animationController?.setSpeed(normalized);
        });
    }

    clear() {
        [...this.activeEffects].forEach(effect => {
            this.manager?.removeEffect(effect);
        });
        this.activeEffects.clear();
    }
}
