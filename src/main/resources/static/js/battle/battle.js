(function () {
    "use strict";

    const BattleScreen = {
        speed: 1,
        auto: false,
        state: null,
        battleId: null,
        busy: false,
        autoTimer: null,
        activeEffects: new Map(),

        init() {
            this.bindControls();
            this.updateControls();
            this.renderEmptyTeams();
            this.setStatus("CHƯA VÀO TRẬN");
            this.setSummary("Đội hình sẽ được tải từ dữ liệu tài khoản của bạn. Nhấn Bắt đầu chiến đấu để tạo một trận PvE.");
            this.addLog("Sẵn sàng tiến vào chiến trường.", "skill");
        },

        bindControls() {
            document.getElementById("speed-button")?.addEventListener("click", () => {
                this.speed = this.speed === 1 ? 2 : this.speed === 2 ? 4 : 1;
                this.updateControls();
            });

            document.getElementById("auto-button")?.addEventListener("click", () => {
                this.auto = !this.auto;
                this.updateControls();
                if (this.auto) {
                    this.runBattleStep();
                } else {
                    this.stopAuto();
                }
            });

            document.getElementById("start-button")?.addEventListener("click", () => {
                this.runBattleStep();
            });

            document.getElementById("reset-button")?.addEventListener("click", () => {
                this.reset();
            });

            document.getElementById("clear-log-button")?.addEventListener("click", () => {
                this.clearLog();
            });
        },

        getCsrfHeaders() {
            const token = document.querySelector('meta[name="_csrf"]')?.content;
            const header = document.querySelector('meta[name="_csrf_header"]')?.content;
            const headers = { "Content-Type": "application/json" };
            if (token && header) headers[header] = token;
            return headers;
        },

        async postJson(url) {
            const response = await fetch(url, {
                method: "POST",
                credentials: "same-origin",
                headers: { ...this.getCsrfHeaders(), "Accept": "application/json" }
            });

            let payload = null;
            const contentType = response.headers.get("content-type") || "";
            if (contentType.includes("application/json")) {
                payload = await response.json();
            } else {
                const text = await response.text();
                payload = text ? { detail: text } : null;
            }

            if (!response.ok) {
                const message = payload?.detail || payload?.message ||
                    (response.status === 401 || response.status === 403
                        ? "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
                        : "Không thể xử lý trận đấu (HTTP " + response.status + ").");
                throw new Error(message);
            }
            return payload;
        },

        async runBattleStep() {
            if (this.busy) return;
            this.stopAuto();
            this.busy = true;
            this.updateControls();

            try {
                if (!this.battleId || this.state?.finished) {
                    if (this.battleId && this.state?.finished) {
                        await this.releaseBattleSession();
                    }
                    this.battleId = null;
                    this.state = null;
                    await this.startBattle();
                }
                if (this.battleId && !this.state?.finished) {
                    await this.advanceRound();
                }
            } catch (error) {
                this.setStatus("CÓ LỖI");
                this.setSummary(error.message || "Không thể bắt đầu trận chiến.");
                this.addLog(error.message || "Không thể bắt đầu trận chiến.", "damage");
                this.auto = false;
            } finally {
                this.busy = false;
                this.updateControls();
                if (this.auto && !this.state?.finished) {
                    this.scheduleAuto();
                }
            }
        },

        async startBattle() {
            this.clearLog();
            this.setStatus("ĐANG TẠO TRẬN");
            this.setSummary("Đang tải đội hình của bạn và chuẩn bị đối thủ PvE từ danh mục nhân vật...");
            const payload = await this.postJson("/api/battle/start");
            if (!payload?.screen) {
                throw new Error("Dữ liệu khởi tạo trận đấu không hợp lệ.");
            }

            this.battleId = payload.screen.battleId;
            this.activeEffects.clear();
            this.applyScreen(payload.screen, true);
            this.setStatus("SẴN SÀNG");
            this.addLog("Đã tạo trận PvE từ đội hình và dữ liệu nhân vật trong database.", "skill");
            this.setSummary("Hai đội đã vào vị trí. Nhấn Đánh tiếp hoặc bật AUTO để xử lý từng round bằng battle engine.");
        },

        async advanceRound() {
            const payload = await this.postJson(
                "/api/battle/" + encodeURIComponent(this.battleId) + "/round"
            );
            if (!payload?.screen) {
                throw new Error("Không nhận được trạng thái trận đấu từ máy chủ.");
            }

            this.setStatus("ĐANG GIAO CHIẾN");
            const events = Array.isArray(payload.events) ? payload.events : [];
            for (const event of events) {
                await this.playEvent(event);
            }

            this.applyScreen(payload.screen, false);
            if (payload.screen.finished) {
                this.state.finished = true;
                this.state.winner = payload.screen.winner;
                this.auto = false;
                this.stopAuto();
                this.setStatus(payload.screen.winner === "PLAYER" ? "CHIẾN THẮNG" : "THẤT BẠI");
                this.setSummary(payload.screen.winner === "PLAYER"
                    ? "Tiên Minh đã chiến thắng trận chiến PvE!"
                    : "Đội hình của bạn đã thất thủ. Hãy điều chỉnh đội hình và thử lại.");
                this.addLog(payload.screen.winner === "PLAYER"
                    ? "CHIẾN THẮNG! Địch quân đã bị đánh bại."
                    : "THẤT BẠI! Toàn đội đã bị áp đảo.", "skill");
            } else {
                this.setStatus("ĐANG CHỜ LƯỢT");
                this.setSummary("Round " + payload.screen.round + " đã hoàn tất. Trạng thái HP và hiệu ứng được đồng bộ từ battle engine.");
            }
        },

        async playEvent(event) {
            if (!event || !event.type) return;

            switch (event.type) {
                case "BATTLE_START":
                    this.addLog("Trận chiến bắt đầu!", "skill");
                    break;
                case "ROUND_START":
                    this.setRound(event.message?.match(/\d+/)?.[0] || (this.state.round + 1));
                    this.addLog("━━ " + (event.message || "Bắt đầu round mới") + " ━━", "skill");
                    break;
                case "ACTION_START":
                    this.animateUnit(event.sourceTeam, event.sourceInstanceId, "attacking");
                    this.addLog(this.unitName(event.sourceTeam, event.sourceInstanceId) + " chuẩn bị hành động.");
                    await this.delay(180);
                    break;
                case "BASIC_ATTACK":
                case "ACTIVE_SKILL":
                case "ULTIMATE_SKILL":
                case "PASSIVE": {
                    const actionClass = event.type === "ULTIMATE_SKILL"
                        ? "ultimate-casting"
                        : event.type === "ACTIVE_SKILL"
                            ? "skill-casting"
                            : event.type === "PASSIVE" ? "passive-triggered" : "attacking";
                    this.animateUnit(event.sourceTeam, event.sourceInstanceId, actionClass);
                    this.showSkillLabel(event);
                    this.addLog(
                        event.message || this.unitName(event.sourceTeam, event.sourceInstanceId) +
                        " kích hoạt " + (event.skillCode || "kỹ năng") + ".",
                        "skill"
                    );
                    await this.delay(event.type === "ULTIMATE_SKILL" ? 420 : 260);
                    break;
                }
                case "DAMAGE":
                    this.applyEventHp(event, "damage");
                    this.animateUnit(event.targetTeam, event.targetInstanceId, "damaged");
                    this.showFloatingNumber(event.targetTeam, event.targetInstanceId, event.amount, "damage");
                    this.addLog(
                        this.unitName(event.sourceTeam, event.sourceInstanceId) + " gây " +
                        this.formatNumber(event.amount) + " sát thương lên " +
                        this.unitName(event.targetTeam, event.targetInstanceId) +
                        (event.critical ? " (CHÍ MẠNG)" : "") + ".",
                        "damage"
                    );
                    await this.delay(300);
                    break;
                case "HEAL":
                    this.applyEventHp(event, "heal");
                    this.showFloatingNumber(event.targetTeam, event.targetInstanceId, event.amount, "heal");
                    this.addLog(
                        this.unitName(event.targetTeam, event.targetInstanceId) +
                        " hồi " + this.formatNumber(event.amount) + " HP.",
                        "heal"
                    );
                    await this.delay(240);
                    break;
                case "DEATH":
                    this.applyEventHp(event, "damage");
                    this.markUnitDead(event.targetTeam, event.targetInstanceId);
                    this.addLog(this.unitName(event.targetTeam, event.targetInstanceId) + " đã gục ngã!", "damage");
                    await this.delay(260);
                    break;
                case "SHIELD":
                case "BUFF":
                case "DEBUFF":
                case "CC":
                case "DISPEL":
                case "IMMUNITY":
                case "EVASION":
                case "BLOCK":
                case "REVIVE":
                case "COUNTER":
                case "EXTRA_ACTION":
                    if (event.currentHp != null) this.applyEventHp(event, "neutral");
                    if (event.type === "DISPEL") {
                        this.removeDispelledEffects(event);
                    } else if (["SHIELD", "BUFF", "DEBUFF", "CC", "IMMUNITY", "EVASION", "BLOCK"].includes(event.type)) {
                        this.addActiveEffect(event);
                    }
                    this.addLog(event.message || (event.type + " · " + this.unitName(event.targetTeam, event.targetInstanceId)), "skill");
                    await this.delay(150);
                    break;
                case "ROUND_END":
                    this.addLog("━━ " + (event.message || "Kết thúc round") + " ━━", "skill");
                    break;
                case "BATTLE_END":
                    this.addLog(event.message || "Trận chiến kết thúc.", "skill");
                    break;
                default:
                    if (event.message) this.addLog(event.message);
            }
        },

        applyScreen(screen, replace) {
            const previous = this.state;
            this.state = {
                battleId: screen.battleId,
                round: screen.round,
                finished: screen.finished,
                winner: screen.winner,
                playerUnits: (screen.playerUnits || []).map(unit => ({ ...unit })),
                enemyUnits: (screen.enemyUnits || []).map(unit => ({ ...unit }))
            };

            this.setRound(screen.round || 1);
            this.updateTeamCount("player-team", this.state.playerUnits.length);
            this.updateTeamCount("enemy-team", this.state.enemyUnits.length);

            if (replace || !previous) {
                this.renderTeam("player-formation", this.state.playerUnits, "PLAYER");
                this.renderTeam("enemy-formation", this.state.enemyUnits, "ENEMY");
            } else {
                this.state.playerUnits.concat(this.state.enemyUnits).forEach(unit => {
                    this.updateUnitCard(unit);
                });
            }
            this.renderTurnOrder();
            this.updateControls();
        },

        updateTeamCount(teamClass, count) {
            const label = document.querySelector("." + teamClass + " .team-side");
            if (label) label.textContent = (teamClass === "player-team" ? "PLAYER" : "ENEMY") + " · " + count + "/5";
        },

        renderEmptyTeams() {
            this.renderTeam("player-formation", [], "PLAYER");
            this.renderTeam("enemy-formation", [], "ENEMY");
            this.updateTeamCount("player-team", 0);
            this.updateTeamCount("enemy-team", 0);
        },

        renderTeam(containerId, units, team) {
            const container = document.getElementById(containerId);
            if (!container) return;
            container.replaceChildren();

            const unitMap = new Map();
            (units || []).forEach(unit => {
                const row = Number(unit.row);
                const column = Number(unit.column);
                if (row < 0 || row > 2 || column < 0 || column > 2) return;
                unitMap.set(row + "-" + column, unit);
            });

            for (let row = 0; row < 3; row++) {
                for (let column = 0; column < 3; column++) {
                    const unit = unitMap.get(row + "-" + column);
                    const slot = document.createElement("div");
                    slot.className = unit ? "unit-slot" : "unit-slot empty";
                    slot.style.gridRow = String(row + 1);
                    slot.style.gridColumn = String(column + 1);
                    if (unit) this.renderUnit(slot, unit, team);
                    container.appendChild(slot);
                }
            }
        },

        renderUnit(slot, unit, team) {
            slot.dataset.instanceId = String(unit.instanceId);
            slot.dataset.team = team;
            if (unit.alive === false) slot.classList.add("unit-dead");

            const level = document.createElement("span");
            level.className = "unit-level";
            level.textContent = "Lv." + (unit.level || 1);

            const visual = document.createElement("div");
            visual.className = "unit-visual";
            visual.textContent = this.avatarText(unit.heroName || unit.heroCode);

            const effects = document.createElement("div");
            effects.className = "unit-effects";
            effects.setAttribute("aria-label", "Hiệu ứng đang có");

            const info = document.createElement("div");
            info.className = "unit-info";

            const name = document.createElement("div");
            name.className = "unit-name";
            name.textContent = unit.heroName || unit.heroCode || "Unknown";

            const role = document.createElement("div");
            role.className = "unit-role";
            role.textContent = unit.role || "TU SĨ";

            const hpBar = document.createElement("div");
            hpBar.className = "hp-bar";
            const hpFill = document.createElement("div");
            hpFill.className = "hp-bar-fill";
            hpFill.style.width = Math.max(0, Math.min(100, Number(unit.hpPercent) || 0)) + "%";
            if (Number(unit.hpPercent) <= 25) hpFill.classList.add("hp-critical");
            hpBar.appendChild(hpFill);

            const resourceBar = document.createElement("div");
            resourceBar.className = "unit-resource-bar";
            const resourceFill = document.createElement("div");
            resourceFill.className = "unit-resource-fill";
            resourceFill.style.width = "0%";
            resourceBar.appendChild(resourceFill);

            const hpText = document.createElement("div");
            hpText.className = "hp-text";
            hpText.textContent = this.formatNumber(unit.currentHp) + " / " + this.formatNumber(unit.maxHp);

            info.append(name, role, hpBar, resourceBar, hpText);
            slot.append(level, visual, effects, info);
            this.renderEffectBadges(team, unit.instanceId);
        },

        updateUnitCard(unit) {
            const team = unit.instanceId < 0 ? "ENEMY" : "PLAYER";
            const container = document.getElementById(team === "PLAYER" ? "player-formation" : "enemy-formation");
            const card = container?.querySelector('[data-instance-id="' + unit.instanceId + '"]');
            if (!card) return;

            const percent = Math.max(0, Math.min(100, Number(unit.hpPercent) || 0));
            const fill = card.querySelector(".hp-bar-fill");
            if (fill) {
                fill.style.width = percent + "%";
                fill.classList.toggle("hp-critical", percent <= 25);
            }
            const hpText = card.querySelector(".hp-text");
            if (hpText) hpText.textContent = this.formatNumber(unit.currentHp) + " / " + this.formatNumber(unit.maxHp);
            card.classList.toggle("unit-dead", unit.alive === false);
        },

        renderTurnOrder() {
            const container = document.getElementById("turn-order");
            if (!container || !this.state) return;
            container.replaceChildren();

            const order = this.state.playerUnits.map(unit => ({ unit, team: "PLAYER" }))
                .concat(this.state.enemyUnits.map(unit => ({ unit, team: "ENEMY" })))
                .filter(entry => entry.unit.alive)
                .sort((a, b) => Number(b.unit.speed || 0) - Number(a.unit.speed || 0));

            order.forEach(entry => {
                const item = document.createElement("div");
                item.className = "turn-order-item" + (entry.team === "ENEMY" ? " enemy-team" : "");
                const dot = document.createElement("span");
                dot.className = "turn-order-dot";
                const label = document.createElement("span");
                label.textContent = entry.unit.heroName || entry.unit.heroCode;
                item.append(dot, label);
                container.appendChild(item);
            });

            if (!order.length) {
                const empty = document.createElement("span");
                empty.className = "empty-message";
                empty.textContent = "Không còn nhân vật có thể hành động.";
                container.appendChild(empty);
            }
        },

        effectUnitKey(team, instanceId) {
            return String(team || "") + ":" + String(instanceId);
        },

        addActiveEffect(event) {
            if (!event.targetTeam || event.targetInstanceId == null) return;
            const category = String(event.effectCategory || event.type).toUpperCase();
            const code = String(event.effectCode || event.skillCode || event.type);
            const key = this.effectUnitKey(event.targetTeam, event.targetInstanceId);
            const effects = this.activeEffects.get(key) || new Map();
            effects.set(category + ":" + code, {
                category,
                code,
                duration: Number.isFinite(Number(event.duration)) ? Number(event.duration) : 0
            });
            this.activeEffects.set(key, effects);
            this.renderEffectBadges(event.targetTeam, event.targetInstanceId);
        },

        removeDispelledEffects(event) {
            if (!event.targetTeam || event.targetInstanceId == null) return;
            const match = String(event.message || "").match(/DISPEL applied:\\s*(BUFF|DEBUFF|CC)/i);
            if (!match) return;
            const category = match[1].toUpperCase();
            const key = this.effectUnitKey(event.targetTeam, event.targetInstanceId);
            const effects = this.activeEffects.get(key);
            if (!effects) return;
            for (const [effectKey, effect] of effects) {
                if (category === "CC" ? effect.category === "CC" : effect.category === category) {
                    effects.delete(effectKey);
                }
            }
            if (effects.size === 0) this.activeEffects.delete(key);
            this.renderEffectBadges(event.targetTeam, event.targetInstanceId);
        },

        decreaseActiveEffectDurations() {
            for (const [unitKey, effects] of this.activeEffects) {
                for (const [effectKey, effect] of effects) {
                    if (effect.duration > 0) {
                        effect.duration -= 1;
                        if (effect.duration <= 0) effects.delete(effectKey);
                    }
                }
                if (effects.size === 0) this.activeEffects.delete(unitKey);
                const separator = unitKey.indexOf(":");
                const team = unitKey.slice(0, separator);
                const instanceId = unitKey.slice(separator + 1);
                this.renderEffectBadges(team, instanceId);
            }
        },

        renderEffectBadges(team, instanceId) {
            const container = document.getElementById(team === "PLAYER" ? "player-formation" : "enemy-formation");
            const card = container?.querySelector('[data-instance-id="' + instanceId + '"]');
            const badgeContainer = card?.querySelector(".unit-effects");
            if (!badgeContainer) return;
            badgeContainer.replaceChildren();

            const effects = this.activeEffects.get(this.effectUnitKey(team, instanceId));
            if (!effects) return;

            for (const effect of effects.values()) {
                const badge = document.createElement("span");
                badge.className = "unit-effect-badge " + effect.category.toLowerCase();
                const durationText = effect.duration === 0 ? "∞" : String(effect.duration);
                badge.textContent = effect.code.replace(/[_-]+/g, " ") + " · " + durationText;
                badge.title = effect.code + " (" + (effect.duration === 0 ? "vĩnh viễn" : effect.duration + " round") + ")";
                badgeContainer.appendChild(badge);
            }
        },

        applyEventHp(event, kind) {
            if (event.targetInstanceId == null || event.currentHp == null) return;
            const team = event.targetTeam;
            const units = team === "PLAYER" ? this.state.playerUnits : this.state.enemyUnits;
            const unit = units.find(item => String(item.instanceId) === String(event.targetInstanceId));
            if (!unit) return;

            unit.currentHp = Number(event.currentHp);
            if (event.maxHp != null) unit.maxHp = Number(event.maxHp);
            unit.hpPercent = unit.maxHp > 0 ? unit.currentHp / unit.maxHp * 100 : 0;
            if (event.alive != null) unit.alive = event.alive;
            this.updateUnitCard(unit);
            if (kind !== "neutral") this.renderTurnOrder();
        },

        markUnitDead(team, instanceId) {
            const units = team === "PLAYER" ? this.state.playerUnits : this.state.enemyUnits;
            const unit = units.find(item => String(item.instanceId) === String(instanceId));
            if (unit) {
                unit.alive = false;
                unit.currentHp = 0;
                unit.hpPercent = 0;
                this.updateUnitCard(unit);
                this.renderTurnOrder();
            }
        },

        animateUnit(team, instanceId, className) {
            if (!team || instanceId == null) return;
            const container = document.getElementById(team === "PLAYER" ? "player-formation" : "enemy-formation");
            const card = container?.querySelector('[data-instance-id="' + instanceId + '"]');
            if (!card) return;
            card.classList.remove(className);
            void card.offsetWidth;
            card.classList.add(className);
            window.setTimeout(() => card.classList.remove(className), 520);
        },

        showFloatingNumber(team, instanceId, amount, kind) {
            if (!team || instanceId == null || amount == null) return;
            const container = document.getElementById(team === "PLAYER" ? "player-formation" : "enemy-formation");
            const card = container?.querySelector('[data-instance-id="' + instanceId + '"]');
            if (!card) return;
            const number = document.createElement("div");
            number.className = "damage-number" + (kind === "heal" ? " heal" : "");
            number.textContent = (kind === "heal" ? "+" : "-") + this.formatNumber(amount);
            card.appendChild(number);
            window.setTimeout(() => number.remove(), 950);
        },

        showSkillLabel(event) {
            const team = event.sourceTeam;
            const instanceId = event.sourceInstanceId;
            if (!team || instanceId == null) return;

            const container = document.getElementById(team === "PLAYER" ? "player-formation" : "enemy-formation");
            const card = container?.querySelector('[data-instance-id="' + instanceId + '"]');
            if (!card) return;

            const label = document.createElement("div");
            const isUltimate = event.type === "ULTIMATE_SKILL";
            label.className = "skill-effect-label" + (isUltimate ? " ultimate" : "");
            const code = event.skillCode || (event.type === "PASSIVE" ? "PASSIVE" : "SKILL");
            label.textContent = code.replace(/[_-]+/g, " ");
            card.appendChild(label);
            window.setTimeout(() => label.remove(), isUltimate ? 1350 : 900);

            if (event.effectCode || event.effectCategory) {
                const effect = document.createElement("div");
                effect.className = "effect-code-label";
                effect.textContent = [event.effectCode, event.effectCategory]
                    .filter(Boolean)
                    .join(" · ")
                    .replace(/[_-]+/g, " ");
                card.appendChild(effect);
                window.setTimeout(() => effect.remove(), 1250);
            }
        },

        unitName(team, instanceId) {
            if (!team || instanceId == null || !this.state) return "Mục tiêu";
            const units = team === "PLAYER" ? this.state.playerUnits : this.state.enemyUnits;
            return units.find(unit => String(unit.instanceId) === String(instanceId))?.heroName || "Mục tiêu";
        },

        avatarText(name) {
            const value = String(name || "?").trim();
            return value ? value.charAt(0) : "?";
        },

        setRound(value) {
            const element = document.getElementById("battle-round");
            if (element) element.textContent = String(value || 0).padStart(2, "0");
        },

        updateControls() {
            const speedButton = document.getElementById("speed-button");
            const autoButton = document.getElementById("auto-button");
            const startButton = document.getElementById("start-button");
            if (speedButton) speedButton.textContent = "x" + this.speed;
            if (autoButton) {
                autoButton.textContent = "AUTO: " + (this.auto ? "ON" : "OFF");
                autoButton.classList.toggle("active", this.auto);
                autoButton.setAttribute("aria-pressed", String(this.auto));
            }
            if (startButton) {
                startButton.disabled = this.busy;
                startButton.textContent = this.busy
                    ? "ĐANG XỬ LÝ..."
                    : (this.battleId ? (this.state?.finished ? "↻ CHƠI LẠI" : "⚔ ĐÁNH TIẾP") : "⚔ BẮT ĐẦU CHIẾN ĐẤU");
            }
        },

        scheduleAuto() {
            this.stopAuto();
            if (!this.auto || this.state?.finished) return;
            this.autoTimer = window.setTimeout(() => this.runBattleStep(), 700 / this.speed);
        },

        stopAuto() {
            if (this.autoTimer != null) {
                window.clearTimeout(this.autoTimer);
                this.autoTimer = null;
            }
        },

        async releaseBattleSession() {
            if (!this.battleId) return;
            try {
                await fetch("/api/battle/" + encodeURIComponent(this.battleId), {
                    method: "DELETE",
                    credentials: "same-origin",
                    headers: this.getCsrfHeaders()
                });
            } catch (error) {
                // Phiên có thể đã hết hạn ở server; không chặn thao tác chơi lại.
            }
        },

        reset() {
            this.stopAuto();
            this.releaseBattleSession();
            this.auto = false;
            this.busy = false;
            this.battleId = null;
            this.state = null;
            this.activeEffects.clear();
            this.renderEmptyTeams();
            this.clearLog();
            this.setRound(0);
            this.setStatus("CHƯA VÀO TRẬN");
            this.setSummary("Đội hình sẽ được tải từ dữ liệu tài khoản của bạn. Nhấn Bắt đầu chiến đấu để tạo một trận PvE mới.");
            this.addLog("Đã đặt lại màn hình chiến đấu.", "skill");
            this.updateControls();
        },

        setStatus(value) {
            const element = document.getElementById("battle-status");
            if (element) element.textContent = value;
        },

        setSummary(value) {
            const element = document.getElementById("battle-summary");
            if (element) element.textContent = value;
        },

        addLog(message, type) {
            const container = document.getElementById("battle-log");
            if (!container) return;
            container.querySelector(".empty-message")?.remove();
            const item = document.createElement("div");
            item.className = "battle-log-item " + (type || "");
            item.textContent = message;
            container.appendChild(item);
            while (container.children.length > 40) container.firstElementChild.remove();
            container.scrollTop = container.scrollHeight;
        },

        clearLog() {
            const container = document.getElementById("battle-log");
            if (!container) return;
            container.replaceChildren();
            const empty = document.createElement("div");
            empty.className = "empty-message";
            empty.textContent = "Nhật ký chiến đấu sẽ xuất hiện tại đây.";
            container.appendChild(empty);
        },

        delay(baseMilliseconds) {
            return new Promise(resolve => window.setTimeout(resolve, Math.max(45, baseMilliseconds / this.speed)));
        },

        formatNumber(value) {
            return Math.round(Number(value) || 0).toLocaleString("vi-VN");
        }
    };

    document.addEventListener("DOMContentLoaded", () => {
        BattleScreen.init();
        window.BattleScreen = BattleScreen;
    });
})();