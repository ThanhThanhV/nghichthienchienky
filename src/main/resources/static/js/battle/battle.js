(function () {
    "use strict";

    const DEMO_BATTLE = {
        round: 1,
        finished: false,
        winner: null,
        playerUnits: [
            { instanceId: 101, heroCode: "THANH_VAN", heroName: "Thanh Vân", role: "KIẾM TU", avatarText: "劍", level: 68, row: 0, column: 1, currentHp: 9200, maxHp: 9200, hpPercent: 100, alive: true, speed: 128 },
            { instanceId: 102, heroCode: "HAC_Y", heroName: "Hắc Y", role: "SÁT THỦ", avatarText: "影", level: 66, row: 1, column: 0, currentHp: 7600, maxHp: 8200, hpPercent: 93, alive: true, speed: 151 },
            { instanceId: 103, heroCode: "LINH_HO", heroName: "Linh Hồ", role: "LINH SƯ", avatarText: "狐", level: 65, row: 1, column: 1, currentHp: 7100, maxHp: 7100, hpPercent: 100, alive: true, speed: 119 },
            { instanceId: 104, heroCode: "THIEN_AM", heroName: "Thiên Âm", role: "HỖ TRỢ", avatarText: "音", level: 64, row: 1, column: 2, currentHp: 6800, maxHp: 7400, hpPercent: 92, alive: true, speed: 110 },
            { instanceId: 105, heroCode: "SON_HA", heroName: "Sơn Hà", role: "HỘ THỂ", avatarText: "山", level: 63, row: 2, column: 1, currentHp: 11800, maxHp: 11800, hpPercent: 100, alive: true, speed: 86 }
        ],
        enemyUnits: [
            { instanceId: 201, heroCode: "MA_TU", heroName: "Ma Tử", role: "MA KIẾM", avatarText: "魔", level: 69, row: 0, column: 1, currentHp: 9800, maxHp: 9800, hpPercent: 100, alive: true, speed: 132 },
            { instanceId: 202, heroCode: "HUYET_SAT", heroName: "Huyết Sát", role: "SÁT PHÁ", avatarText: "血", level: 67, row: 1, column: 0, currentHp: 8400, maxHp: 8400, hpPercent: 100, alive: true, speed: 145 },
            { instanceId: 203, heroCode: "U_MINH", heroName: "U Minh", role: "PHÁP TU", avatarText: "幽", level: 66, row: 1, column: 1, currentHp: 7900, maxHp: 9000, hpPercent: 88, alive: true, speed: 116 },
            { instanceId: 204, heroCode: "COT_LINH", heroName: "Cốt Linh", role: "TRỢ CHIẾN", avatarText: "骨", level: 65, row: 1, column: 2, currentHp: 7200, maxHp: 7200, hpPercent: 100, alive: true, speed: 103 },
            { instanceId: 205, heroCode: "HAC_LONG", heroName: "Hắc Long", role: "MA TÔN", avatarText: "龍", level: 70, row: 2, column: 1, currentHp: 13200, maxHp: 13200, hpPercent: 100, alive: true, speed: 91 }
        ]
    };

    const BattleScreen = {
        speed: 1,
        auto: false,
        state: null,
        actionCount: 0,
        autoTimer: null,
        init() {
            this.bindControls();
            this.reset();
        },
        bindControls() {
            document.getElementById("speed-button")?.addEventListener("click", () => {
                this.speed = this.speed === 1 ? 2 : this.speed === 2 ? 4 : 1;
                this.updateControls();
                this.addLog("Tốc độ chiến đấu: x" + this.speed, "skill");
            });
            document.getElementById("auto-button")?.addEventListener("click", () => {
                this.auto = !this.auto;
                this.updateControls();
                if (this.auto) {
                    this.runNextAction();
                    this.scheduleAuto();
                } else {
                    this.stopAuto();
                }
            });
            document.getElementById("start-button")?.addEventListener("click", () => {
                if (this.state.finished) this.reset();
                this.runNextAction();
                if (this.auto && !this.state.finished) this.scheduleAuto();
            });
            document.getElementById("reset-button")?.addEventListener("click", () => this.reset());
            document.getElementById("clear-log-button")?.addEventListener("click", () => this.clearLog());
        },
        createInitialState() {
            const cloneUnits = units => units.map(unit => ({ ...unit }));
            return {
                round: DEMO_BATTLE.round,
                finished: false,
                winner: null,
                playerUnits: cloneUnits(DEMO_BATTLE.playerUnits),
                enemyUnits: cloneUnits(DEMO_BATTLE.enemyUnits)
            };
        },
        reset() {
            this.stopAuto();
            this.auto = false;
            this.actionCount = 0;
            this.state = this.createInitialState();
            this.render(this.state);
            this.updateControls();
            this.renderTurnOrder(this.getTurnOrder());
            this.clearLog();
            this.setSummary("Hai phe đã vào vị trí. Đây là dữ liệu xem trước để hoàn thiện giao diện; chưa phải trận đấu lấy từ backend.");
            this.setStatus("SẴN SÀNG");
            this.addLog("Hai đội hình đã tiến vào chiến trường Cửu U.", "skill");
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
            if (startButton) startButton.textContent = this.state?.finished ? "↺ CHƠI LẠI" : "⚔ ĐÁNH TIẾP";
        },
        render(data) {
            if (!data) return;
            const round = document.getElementById("battle-round");
            if (round) round.textContent = String(data.round).padStart(2, "0");
            this.renderTeam("player-formation", data.playerUnits, "PLAYER");
            this.renderTeam("enemy-formation", data.enemyUnits, "ENEMY");
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
            visual.textContent = unit.avatarText || "✦";

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
            const percent = Math.max(0, Math.min(100, Number(unit.hpPercent) || 0));
            hpFill.style.width = percent + "%";
            if (percent <= 25) hpFill.classList.add("hp-critical");
            hpBar.appendChild(hpFill);

            const resourceBar = document.createElement("div");
            resourceBar.className = "unit-resource-bar";
            const resourceFill = document.createElement("div");
            resourceFill.className = "unit-resource-fill";
            resourceFill.style.width = (unit.qiPercent ?? 68) + "%";
            resourceBar.appendChild(resourceFill);

            const hpText = document.createElement("div");
            hpText.className = "hp-text";
            hpText.textContent = this.formatNumber(unit.currentHp) + " / " + this.formatNumber(unit.maxHp);

            info.append(name, role, hpBar, resourceBar, hpText);
            slot.append(level, visual, info);
        },
        renderTurnOrder(units) {
            const container = document.getElementById("turn-order");
            if (!container) return;
            container.replaceChildren();
            (units || []).forEach((entry, index) => {
                const item = document.createElement("div");
                item.className = "turn-order-item" + (index === this.actionCount % Math.max(units.length, 1) ? " active" : "");
                const dot = document.createElement("span");
                dot.className = "turn-order-dot";
                const label = document.createElement("span");
                label.textContent = entry.unit.heroName || entry.unit.heroCode;
                item.append(dot, label);
                container.appendChild(item);
            });
        },
        getTurnOrder() {
            if (!this.state) return [];
            return [
                ...this.state.playerUnits.map(unit => ({ unit, team: "PLAYER" })),
                ...this.state.enemyUnits.map(unit => ({ unit, team: "ENEMY" }))
            ].filter(entry => entry.unit.alive).sort((a, b) => Number(b.unit.speed || 0) - Number(a.unit.speed || 0));
        },
        runNextAction() {
            if (!this.state || this.state.finished) return;
            const order = this.getTurnOrder();
            if (!order.length) return;
            const actorEntry = order[this.actionCount % order.length];
            const targetTeam = actorEntry.team === "PLAYER" ? "ENEMY" : "PLAYER";
            const targets = this.state[targetTeam === "PLAYER" ? "playerUnits" : "enemyUnits"].filter(unit => unit.alive);
            if (!targets.length) {
                this.finishBattle(actorEntry.team);
                return;
            }
            const target = targets[Math.floor(Math.random() * targets.length)];
            const actor = actorEntry.unit;
            const damage = Math.max(180, Math.round(target.maxHp * (0.075 + Math.random() * 0.085)));
            target.currentHp = Math.max(0, target.currentHp - damage);
            target.hpPercent = target.currentHp / target.maxHp * 100;
            if (target.currentHp <= 0) target.alive = false;

            this.actionCount++;
            this.render(this.state);
            this.renderTurnOrder(this.getTurnOrder());
            this.animateAction(actorEntry.team, actor.instanceId, targetTeam, target.instanceId, damage);
            this.addLog(actor.heroName + " tấn công " + target.heroName + ", gây " + this.formatNumber(damage) + " sát thương.", "damage");
            this.setStatus("ĐANG GIAO CHIẾN");
            this.setSummary(actor.heroName + " vừa ra chiêu. " + target.heroName + (target.alive ? " bị tổn thất sinh lực." : " đã gục ngã!"));
            if (target.alive === false) this.addLog(target.heroName + " đã bị đánh bại.", "skill");

            const playerAlive = this.state.playerUnits.some(unit => unit.alive);
            const enemyAlive = this.state.enemyUnits.some(unit => unit.alive);
            if (!playerAlive || !enemyAlive) {
                this.finishBattle(playerAlive ? "PLAYER" : "ENEMY");
            }
            this.updateControls();
        },
        animateAction(sourceTeam, sourceId, targetTeam, targetId, amount) {
            const sourceContainer = document.getElementById(sourceTeam === "PLAYER" ? "player-formation" : "enemy-formation");
            const targetContainer = document.getElementById(targetTeam === "PLAYER" ? "player-formation" : "enemy-formation");
            const source = sourceContainer?.querySelector('[data-instance-id="' + sourceId + '"]');
            const target = targetContainer?.querySelector('[data-instance-id="' + targetId + '"]');
            source?.classList.remove("attacking");
            target?.classList.remove("damaged");
            void source?.offsetWidth;
            source?.classList.add("attacking");
            target?.classList.add("damaged");
            if (target) {
                const number = document.createElement("div");
                number.className = "damage-number";
                number.textContent = "-" + this.formatNumber(amount);
                target.appendChild(number);
                window.setTimeout(() => number.remove(), 950);
            }
        },
        finishBattle(winner) {
            this.state.finished = true;
            this.state.winner = winner;
            this.stopAuto();
            this.auto = false;
            this.setStatus(winner === "PLAYER" ? "CHIẾN THẮNG" : "THẤT BẠI");
            this.setSummary(winner === "PLAYER" ? "Tiên Minh đã chiến thắng trận chiến Cửu U!" : "Đội hình của bạn đã thất thủ. Hãy điều chỉnh chiến thuật rồi thử lại.");
            this.addLog(winner === "PLAYER" ? "CHIẾN THẮNG! Địch quân đã bị đánh bại." : "THẤT BẠI! Toàn đội đã bị áp đảo.", "skill");
            this.updateControls();
        },
        scheduleAuto() {
            this.stopAuto();
            if (!this.auto || this.state?.finished) return;
            this.autoTimer = window.setTimeout(() => {
                this.runNextAction();
                this.scheduleAuto();
            }, 1150 / this.speed);
        },
        stopAuto() {
            if (this.autoTimer != null) {
                window.clearTimeout(this.autoTimer);
                this.autoTimer = null;
            }
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
            while (container.children.length > 30) container.firstElementChild.remove();
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
        formatNumber(value) {
            return Math.round(Number(value) || 0).toLocaleString("vi-VN");
        }
    };

    document.addEventListener("DOMContentLoaded", () => {
        BattleScreen.init();
        window.BattleScreen = BattleScreen;
    });
})();