(function () {
    "use strict";

    const BattleScreen = {
        speed: 1,
        auto: false,

        /**
         * Khởi tạo các thành phần giao diện.
         */
        init() {
            this.bindControls();
            this.renderEmptyFormations();
            this.updateControls();
        },

        /**
         * Gắn sự kiện cho các nút điều khiển.
         */
        bindControls() {
            const speedButton =
                document.getElementById("speed-button");

            const autoButton =
                document.getElementById("auto-button");

            const clearLogButton =
                document.getElementById("clear-log-button");

            const startButton =
                document.getElementById("start-button");

            if (speedButton) {
                speedButton.addEventListener("click", () => {
                    this.speed =
                        this.speed === 1 ? 2 :
                            this.speed === 2 ? 4 : 1;

                    this.updateControls();
                });
            }

            if (autoButton) {
                autoButton.addEventListener("click", () => {
                    this.auto = !this.auto;
                    this.updateControls();
                });
            }

            if (clearLogButton) {
                clearLogButton.addEventListener("click", () => {
                    this.clearLog();
                });
            }

            if (startButton) {
                startButton.addEventListener("click", () => {
                    this.addLog(
                        "Giao diện đã sẵn sàng. Chưa kết nối API khởi tạo trận đấu.",
                        "skill"
                    );
                });
            }
        },

        /**
         * Cập nhật trạng thái nút điều khiển.
         */
        updateControls() {
            const speedButton =
                document.getElementById("speed-button");

            const autoButton =
                document.getElementById("auto-button");

            if (speedButton) {
                speedButton.textContent = `x${this.speed}`;
            }

            if (autoButton) {
                autoButton.textContent =
                    `AUTO: ${this.auto ? "ON" : "OFF"}`;

                autoButton.classList.toggle(
                    "primary-button",
                    this.auto
                );
            }
        },

        /**
         * Tạo 9 ô trống cho mỗi đội.
         */
        renderEmptyFormations() {
            this.renderTeam("player-formation", []);
            this.renderTeam("enemy-formation", []);
        },

        /**
         * Render đội hình 3x3.
         *
         * row: 0-2
         * column: 0-2
         */
        renderTeam(containerId, units) {
            const container =
                document.getElementById(containerId);

            if (!container) {
                return;
            }

            container.replaceChildren();

            const unitMap = new Map();

            (units || []).forEach(unit => {
                const row = Number(unit.row);
                const column = Number(unit.column);

                if (
                    row < 0 || row > 2 ||
                    column < 0 || column > 2
                ) {
                    return;
                }

                unitMap.set(`${row}-${column}`, unit);
            });

            for (let row = 0; row < 3; row++) {
                for (let column = 0; column < 3; column++) {
                    const unit =
                        unitMap.get(`${row}-${column}`);

                    const slot =
                        document.createElement("div");

                    slot.className = unit
                        ? "unit-slot"
                        : "unit-slot empty";

                    slot.style.gridRow = String(row + 1);
                    slot.style.gridColumn = String(column + 1);

                    if (unit) {
                        this.renderUnit(slot, unit);
                    }

                    container.appendChild(slot);
                }
            }
        },

        /**
         * Render thông tin một nhân vật.
         */
        renderUnit(slot, unit) {
            slot.dataset.instanceId = unit.instanceId;

            const visual =
                document.createElement("div");

            visual.className = "unit-visual";
            visual.textContent = "✦";

            const info =
                document.createElement("div");

            info.className = "unit-info";

            const name =
                document.createElement("div");

            name.className = "unit-name";
            name.textContent = unit.heroCode || "Unknown";

            const hpBar =
                document.createElement("div");

            hpBar.className = "hp-bar";

            const hpFill =
                document.createElement("div");

            hpFill.className = "hp-bar-fill";

            const hpPercent = Math.max(
                0,
                Math.min(100, Number(unit.hpPercent) || 0)
            );

            hpFill.style.width = `${hpPercent}%`;

            if (hpPercent <= 25) {
                hpFill.classList.add("hp-critical");
            }

            const hpText =
                document.createElement("div");

            hpText.className = "hp-text";
            hpText.textContent =
                `${unit.currentHp ?? 0} / ${unit.maxHp ?? 0}`;

            if (unit.alive === false) {
                slot.classList.add("unit-dead");
                hpFill.style.width = "0%";
            }

            hpBar.appendChild(hpFill);

            info.append(
                name,
                hpBar,
                hpText
            );

            slot.append(
                visual,
                info
            );
        },

        /**
         * Render toàn bộ dữ liệu màn hình trận đấu.
         *
         * Dữ liệu đầu vào là BattleScreenDTO.
         */
        render(data) {
            if (!data) {
                return;
            }

            const round =
                document.getElementById("battle-round");

            const status =
                document.getElementById("battle-status");

            if (round) {
                round.textContent = data.round ?? 0;
            }

            if (status) {
                status.textContent = data.finished
                    ? `KẾT THÚC - ${data.winner || "DRAW"}`
                    : "ĐANG CHIẾN ĐẤU";
            }

            this.renderTeam(
                "player-formation",
                data.playerUnits
            );

            this.renderTeam(
                "enemy-formation",
                data.enemyUnits
            );
        },

        /**
         * Render thứ tự hành động.
         */
        renderTurnOrder(units) {
            const container =
                document.getElementById("turn-order");

            if (!container) {
                return;
            }

            container.replaceChildren();

            (units || []).forEach((unit, index) => {
                const item =
                    document.createElement("div");

                item.className = "turn-order-item";

                if (index === 0) {
                    item.classList.add("active");
                }

                item.textContent =
                    unit.heroCode || `Unit ${index + 1}`;

                container.appendChild(item);
            });
        },

        /**
         * Thêm một dòng nhật ký chiến đấu.
         */
        addLog(message, type) {
            const container =
                document.getElementById("battle-log");

            if (!container) {
                return;
            }

            const empty =
                container.querySelector(".empty-message");

            if (empty) {
                empty.remove();
            }

            const item =
                document.createElement("div");

            item.className =
                `battle-log-item ${type || ""}`;

            item.textContent = message;

            container.appendChild(item);
            container.scrollTop = container.scrollHeight;
        },

        /**
         * Xóa nhật ký chiến đấu.
         */
        clearLog() {
            const container =
                document.getElementById("battle-log");

            if (!container) {
                return;
            }

            container.replaceChildren();

            const empty =
                document.createElement("div");

            empty.className = "empty-message";
            empty.textContent =
                "Nhật ký chiến đấu sẽ xuất hiện tại đây.";

            container.appendChild(empty);
        }
    };

    document.addEventListener("DOMContentLoaded", function () {
        BattleScreen.init();

        // Cho phép các module khác gọi render dữ liệu trận đấu.
        window.BattleScreen = BattleScreen;
    });

})();