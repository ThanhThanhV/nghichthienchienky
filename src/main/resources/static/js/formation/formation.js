document.addEventListener("DOMContentLoaded", () => {
    window.playerFormationScreen = new PlayerFormationScreen();
    window.playerFormationScreen.initialize();
});

class PlayerFormationScreen {
    constructor() {
        this.heroes = [];
        this.slots = Array(9).fill(null);
        this.selectedHeroId = null;
        this.spinePreview = null;
        this.messageElement = document.getElementById("formation-message");
        this.gridElement = document.getElementById("formation-grid");
        this.rosterElement = document.getElementById("owned-heroes");
        this.saveButton = document.getElementById("save-formation");
        this.csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        this.csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "X-CSRF-TOKEN";
    }

    async initialize() {
        this.saveButton.addEventListener("click", () => this.saveFormation());
        this.spinePreview = new FormationSpinePreview(
            document.getElementById("formation-spine-canvas"),
            document.getElementById("spine-preview-status")
        );
        this.renderSlots();
        await this.loadFormation();
    }

    async loadFormation() {
        try {
            const response = await fetch("/api/player/formation", {
                method: "GET",
                headers: { "Accept": "application/json" },
                credentials: "same-origin"
            });
            const data = await this.readResponse(response);
            this.heroes = data.heroes || [];
            this.slots = Array(9).fill(null);

            (data.slots || []).forEach(slot => {
                const index = Number(slot.row) * 3 + Number(slot.column);
                if (index >= 0 && index < 9) {
                    this.slots[index] = {
                        instanceId: Number(slot.instanceId),
                        row: Number(slot.row),
                        column: Number(slot.column)
                    };
                }
            });

            this.render();
            await this.spinePreview.sync(this.heroes, this.getSelectedSlots());
            this.showMessage("Đã tải đội hình hiện tại.", "success");
        } catch (error) {
            console.error("Load formation failed:", error);
            this.rosterElement.innerHTML = '<div class="empty-state">Không thể tải danh sách nhân vật.</div>';
            this.showMessage(error.message || "Không thể tải đội hình.", "error");
        }
    }

    async readResponse(response) {
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            throw new Error(data.message || data.error || ("HTTP " + response.status));
        }
        return data;
    }

    render() {
        this.renderSlots();
        this.renderHeroes();
        const count = this.getSelectedSlots().length;
        document.getElementById("formation-count").textContent = count + " / 5 ĐẠI TƯỚNG";
        document.getElementById("hero-total").textContent = String(this.heroes.length);
        this.saveButton.disabled = count === 0;
        this.saveButton.style.opacity = count === 0 ? ".5" : "1";
    }

    renderSlots() {
        this.gridElement.replaceChildren();

        for (let index = 0; index < 9; index++) {
            const row = Math.floor(index / 3);
            const column = index % 3;
            const slot = this.slots[index];
            const hero = slot ? this.findHero(slot.instanceId) : null;

            const cell = document.createElement("div");
            cell.className = "formation-slot" + (hero ? " filled" : "");

            const number = document.createElement("span");
            number.className = "slot-number";
            number.textContent = "VỊ TRÍ " + (index + 1);
            cell.append(number);

            const main = document.createElement("button");
            main.type = "button";
            main.className = "slot-main";
            main.setAttribute("aria-label", hero
                ? "Vị trí " + (index + 1) + ": " + hero.heroName
                : "Chọn nhân vật cho hàng " + (row + 1) + ", cột " + (column + 1));

            if (hero) {
                const avatar = document.createElement("span");
                avatar.className = "slot-avatar";
                avatar.textContent = this.initialOf(hero.heroName);
                const name = document.createElement("span");
                name.className = "slot-name";
                name.textContent = hero.heroName;
                const position = document.createElement("span");
                position.className = "slot-position";
                position.textContent = "HÀNG " + (row + 1) + " · CỘT " + (column + 1);
                main.append(avatar, name, position);
            } else {
                const empty = document.createElement("span");
                empty.className = "slot-empty";
                empty.textContent = this.selectedHeroId
                    ? "ĐẶT NHÂN VẬT VÀO ĐÂY"
                    : "＋ CHỌN NHÂN VẬT";
                main.append(empty);
            }

            main.addEventListener("click", () => this.handleSlotClick(index));
            cell.append(main);

            if (hero) {
                const remove = document.createElement("button");
                remove.type = "button";
                remove.className = "slot-remove";
                remove.textContent = "×";
                remove.title = "Bỏ nhân vật khỏi đội hình";
                remove.setAttribute("aria-label", "Bỏ " + hero.heroName + " khỏi đội hình");
                remove.addEventListener("click", () => this.removeFromSlot(index));
                cell.append(remove);
            }

            this.gridElement.append(cell);
        }
    }

    renderHeroes() {
        this.rosterElement.replaceChildren();
        if (this.heroes.length === 0) {
            const empty = document.createElement("div");
            empty.className = "empty-state";
            empty.textContent = "Ngươi chưa sở hữu nhân vật nào.";
            this.rosterElement.append(empty);
            return;
        }

        const selectedIds = new Set(this.getSelectedSlots().map(slot => slot.instanceId));

        this.heroes.forEach(hero => {
            const card = document.createElement("button");
            card.type = "button";
            card.className = "hero-card";
            if (Number(hero.instanceId) === Number(this.selectedHeroId)) card.classList.add("selected");
            if (selectedIds.has(Number(hero.instanceId))) card.classList.add("in-team");

            const avatar = document.createElement("span");
            avatar.className = "hero-card-avatar";
            avatar.textContent = this.initialOf(hero.heroName);

            const info = document.createElement("span");
            info.className = "hero-card-info";
            const name = document.createElement("span");
            name.className = "hero-card-name";
            name.textContent = hero.heroName || hero.heroCode;
            const meta = document.createElement("span");
            meta.className = "hero-card-meta";
            meta.textContent = "Cấp " + (hero.heroLevel ?? 1) + " · " + (hero.role || "Chưa phân vai");

            const tags = document.createElement("span");
            tags.className = "hero-card-tags";
            [hero.rarity, hero.faction].filter(Boolean).forEach(value => {
                const tag = document.createElement("span");
                tag.className = "hero-tag";
                tag.textContent = value;
                tags.append(tag);
            });
            if (hero.spineSkeleton) {
                const spineTag = document.createElement("span");
                spineTag.className = "hero-tag spine";
                spineTag.textContent = "SPINE";
                tags.append(spineTag);
            }
            info.append(name, meta, tags);

            const state = document.createElement("span");
            state.className = "hero-card-state";
            state.textContent = selectedIds.has(Number(hero.instanceId)) ? "ĐÃ XẾP" : "CHỌN";

            card.append(avatar, info, state);
            card.addEventListener("click", () => this.selectHero(hero.instanceId));
            this.rosterElement.append(card);
        });
    }

    selectHero(instanceId) {
        this.selectedHeroId = Number(this.selectedHeroId) === Number(instanceId)
            ? null : Number(instanceId);
        this.render();
    }

    handleSlotClick(index) {
        const current = this.slots[index];
        if (this.selectedHeroId == null) {
            if (current) {
                this.selectedHeroId = current.instanceId;
                this.render();
            } else {
                this.showMessage("Hãy chọn một nhân vật trong danh sách trước.", "error");
            }
            return;
        }
        this.placeSelectedHero(index);
    }

    placeSelectedHero(targetIndex) {
        const heroId = Number(this.selectedHeroId);
        const previousIndex = this.slots.findIndex(
            slot => slot && Number(slot.instanceId) === heroId
        );
        if (previousIndex === targetIndex) {
            this.selectedHeroId = null;
            this.render();
            return;
        }

        const targetSlot = this.slots[targetIndex];
        const targetRow = Math.floor(targetIndex / 3);
        const targetColumn = targetIndex % 3;

        if (previousIndex >= 0) {
            if (targetSlot) {
                const previousRow = Math.floor(previousIndex / 3);
                const previousColumn = previousIndex % 3;
                this.slots[previousIndex] = {
                    instanceId: targetSlot.instanceId,
                    row: previousRow,
                    column: previousColumn
                };
            } else {
                this.slots[previousIndex] = null;
            }
        }

        this.slots[targetIndex] = {
            instanceId: heroId,
            row: targetRow,
            column: targetColumn
        };
        this.selectedHeroId = null;
        this.render();
        this.previewCurrentFormation();
    }

    removeFromSlot(index) {
        this.slots[index] = null;
        this.render();
        this.previewCurrentFormation();
    }

    getSelectedSlots() {
        return this.slots.map((slot, index) => slot ? {
            instanceId: Number(slot.instanceId),
            row: Math.floor(index / 3),
            column: index % 3
        } : null).filter(Boolean);
    }

    findHero(instanceId) {
        return this.heroes.find(hero => Number(hero.instanceId) === Number(instanceId));
    }

    async previewCurrentFormation() {
        if (this.spinePreview) {
            await this.spinePreview.sync(this.heroes, this.getSelectedSlots());
        }
    }

    async saveFormation() {
        const slots = this.getSelectedSlots();
        if (slots.length === 0 || slots.length > 5) {
            this.showMessage("Đội hình phải có từ 1 đến 5 nhân vật.", "error");
            return;
        }

        this.saveButton.disabled = true;
        this.saveButton.textContent = "ĐANG LƯU...";
        try {
            const headers = {
                "Content-Type": "application/json",
                "Accept": "application/json"
            };
            if (this.csrfToken) headers[this.csrfHeader] = this.csrfToken;

            const response = await fetch("/api/player/formation", {
                method: "PUT",
                headers,
                credentials: "same-origin",
                body: JSON.stringify({ slots })
            });
            const data = await this.readResponse(response);
            this.heroes = data.heroes || this.heroes;
            this.slots = Array(9).fill(null);

            (data.slots || []).forEach(slot => {
                const index = Number(slot.row) * 3 + Number(slot.column);
                if (index >= 0 && index < 9) {
                    this.slots[index] = {
                        instanceId: Number(slot.instanceId),
                        row: Number(slot.row),
                        column: Number(slot.column)
                    };
                }
            });

            this.selectedHeroId = null;
            this.render();
            await this.previewCurrentFormation();
            this.showMessage("Đã lưu đội hình thành công. Có thể vào chiến đấu.", "success");
        } catch (error) {
            console.error("Save formation failed:", error);
            this.showMessage(error.message || "Không thể lưu đội hình.", "error");
        } finally {
            this.saveButton.disabled = false;
            this.saveButton.textContent = "LƯU ĐỘI HÌNH";
        }
    }

    initialOf(value) {
        return String(value || "?").trim().charAt(0).toUpperCase() || "?";
    }

    showMessage(message, type) {
        this.messageElement.textContent = message;
        this.messageElement.className = "formation-message " + (type || "");
        this.messageElement.hidden = false;
    }
}