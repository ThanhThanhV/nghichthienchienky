document.addEventListener("DOMContentLoaded", () => {
    window.heroManagementScreen = new HeroManagementScreen();
    window.heroManagementScreen.initialize();
});

class HeroManagementScreen {
    constructor() {
        this.heroes = [];
        this.resources = [];
        this.expItems = [];
        this.selectedHero = null;
        this.preview = null;
        this.busy = false;

        this.rosterElement = document.getElementById("hero-roster");
        this.messageElement = document.getElementById("hero-message");
        this.expSelect = document.getElementById("exp-item-select");
        this.quantityInput = document.getElementById("exp-item-quantity");
        this.useExpButton = document.getElementById("use-exp-button");
        this.expInfo = document.getElementById("exp-item-info");

        this.csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        this.csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || "X-CSRF-TOKEN";
    }

    async initialize() {
        this.preview = new FormationSpinePreview(
            document.getElementById("hero-spine-canvas"),
            document.getElementById("spine-status")
        );

        document.getElementById("exp-item-form").addEventListener(
            "submit",
            event => {
                event.preventDefault();
                this.useSelectedExpItems();
            }
        );
        this.expSelect.addEventListener("change", () => this.updateExpItemInfo());

        try {
            const [heroes, profile] = await Promise.all([
                this.request("/api/player/heroes"),
                this.request("/api/player/me")
            ]);

            this.heroes = Array.isArray(heroes) ? heroes : [];
            this.resources = profile.resources || [];

            try {
                this.expItems = await this.request("/api/player/heroes/exp-items");
            } catch (error) {
                this.expItems = [];
                this.showMessage("Không tải được danh mục vật phẩm EXP: " + error.message, "error");
            }

            document.getElementById("hero-count").textContent = String(this.heroes.length);
            this.renderRoster();

            if (this.heroes.length > 0) {
                await this.selectHero(this.heroes[0].instanceId);
            } else {
                this.renderEmptyDetail();
                this.showMessage("Tài khoản chưa sở hữu nhân vật nào.", "error");
            }
        } catch (error) {
            console.error("Hero screen initialization failed:", error);
            this.rosterElement.replaceChildren(this.createMessage("Không thể tải danh sách nhân vật."));
            this.showMessage(error.message || "Không thể tải dữ liệu nhân vật.", "error");
        }
    }

    async request(url, options = {}) {
        const headers = {
            "Accept": "application/json",
            ...(options.headers || {})
        };

        if (options.body != null) {
            headers["Content-Type"] = "application/json";
        }
        if (options.method && options.method !== "GET" && this.csrfToken) {
            headers[this.csrfHeader] = this.csrfToken;
        }

        const response = await fetch(url, {
            credentials: "same-origin",
            ...options,
            headers
        });
        const data = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new Error(
                data.message || data.detail || data.error ||
                ("Không thể xử lý yêu cầu (HTTP " + response.status + ").")
            );
        }
        return data;
    }

    renderRoster() {
        this.rosterElement.replaceChildren();

        if (this.heroes.length === 0) {
            this.rosterElement.append(this.createMessage("Chưa có nhân vật sở hữu."));
            return;
        }

        this.heroes.forEach(hero => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "hero-card" +
                (Number(hero.instanceId) === Number(this.selectedHero?.instanceId) ? " selected" : "");

            const avatar = document.createElement("span");
            avatar.className = "hero-avatar";
            avatar.textContent = this.initialOf(hero.heroName);

            const info = document.createElement("span");
            info.className = "hero-card-info";

            const name = document.createElement("span");
            name.className = "hero-card-name";
            name.textContent = hero.heroName || hero.heroCode || "Nhân vật";

            const meta = document.createElement("span");
            meta.className = "hero-card-meta";
            meta.textContent = "Cấp " + (hero.heroLevel ?? 1) +
                " · " + (hero.rarity || "Chưa phân hạng") +
                " · " + (hero.role || "Chưa phân vai");

            info.append(name, meta);
            button.append(avatar, info);
            button.addEventListener("click", () => this.selectHero(hero.instanceId));
            this.rosterElement.append(button);
        });
    }

    async selectHero(instanceId) {
        const hero = this.heroes.find(item => Number(item.instanceId) === Number(instanceId));
        if (!hero) return;

        this.selectedHero = hero;
        this.renderRoster();
        this.renderDetail();
        await this.previewSelectedHero();
    }

    renderDetail() {
        const hero = this.selectedHero;
        if (!hero) {
            this.renderEmptyDetail();
            return;
        }

        document.getElementById("preview-title").textContent = hero.heroName || hero.heroCode;
        document.getElementById("hero-name").textContent = hero.heroName || hero.heroCode;
        document.getElementById("hero-description").textContent = hero.description || "Chưa có mô tả cho nhân vật này.";
        document.getElementById("hero-instance").textContent = "BẢN SAO #" + hero.instanceId;
        document.getElementById("hero-level").textContent = hero.heroLevel ?? "—";
        document.getElementById("hero-stars").textContent = "★".repeat(Math.max(0, Number(hero.starLevel) || 0)) || "0";
        document.getElementById("hero-breakthrough").textContent = hero.breakthroughLevel ?? 0;
        document.getElementById("hero-exp").textContent = this.formatNumber(hero.experience);

        const tags = document.getElementById("hero-tags");
        tags.replaceChildren();
        [hero.rarity, hero.faction, hero.role].filter(Boolean).forEach((value, index) => {
            const tag = document.createElement("span");
            tag.className = "hero-tag" + (index === 0 ? " rarity" : "");
            tag.textContent = value;
            tags.append(tag);
        });
        if (hero.spineSkeleton) {
            const tag = document.createElement("span");
            tag.className = "hero-tag";
            tag.textContent = "SPINE";
            tags.append(tag);
        }

        this.renderStats(hero.stats?.stats || {});
        this.renderSkills(hero.skills || []);
        this.renderExpItems();
    }

    renderStats(stats) {
        const container = document.getElementById("hero-stats");
        container.replaceChildren();

        const entries = Object.entries(stats || {});
        if (entries.length === 0) {
            container.append(this.createMessage("Máy chủ chưa trả về chỉ số cho nhân vật."));
            return;
        }

        entries.forEach(([key, value]) => {
            const card = document.createElement("div");
            card.className = "stat-card";

            const label = document.createElement("div");
            label.className = "stat-label";
            label.textContent = String(key).replace(/[_-]+/g, " ");

            const number = document.createElement("div");
            number.className = "stat-value";
            number.textContent = this.formatNumber(value);

            card.append(label, number);
            container.append(card);
        });
    }

    renderSkills(skills) {
        const container = document.getElementById("hero-skills");
        container.replaceChildren();

        if (!skills.length) {
            container.append(this.createMessage("Nhân vật chưa có dữ liệu kỹ năng."));
            return;
        }

        skills.forEach(skill => {
            const card = document.createElement("article");
            card.className = "skill-card";

            const top = document.createElement("div");
            top.className = "skill-top";

            const identity = document.createElement("div");
            const name = document.createElement("div");
            name.className = "skill-name";
            name.textContent = skill.skillName || skill.skillCode;

            const type = document.createElement("div");
            type.className = "skill-type";
            type.textContent = (skill.skillType || "SKILL") + " · THỨ TỰ " + (skill.skillOrder ?? "—");
            identity.append(name, type);

            const level = document.createElement("span");
            level.className = "skill-level";
            level.textContent = "CẤP " + (skill.skillLevel ?? 1);

            top.append(identity, level);
            card.append(top);

            if (skill.description) {
                const description = document.createElement("p");
                description.className = "skill-description";
                description.textContent = skill.description;
                card.append(description);
            }

            const effects = document.createElement("div");
            effects.className = "skill-effects";
            (skill.effects || []).forEach(effect => {
                const tag = document.createElement("span");
                tag.className = "effect-tag";
                const parts = [effect.effectType, effect.targetType].filter(Boolean);
                if (effect.multiplier != null) parts.push("×" + effect.multiplier);
                if (effect.chance != null) parts.push(Math.round(Number(effect.chance) * 100) + "%");
                if (effect.duration != null) parts.push(effect.duration + " round");
                tag.textContent = parts.join(" · ");
                effects.append(tag);
            });
            if (effects.childElementCount) card.append(effects);

            container.append(card);
        });
    }

    renderExpItems() {
        this.expSelect.replaceChildren();
        const placeholder = document.createElement("option");
        placeholder.value = "";
        placeholder.textContent = this.expItems.length
            ? "Chọn vật phẩm EXP"
            : "Chưa có vật phẩm EXP được bật";
        this.expSelect.append(placeholder);

        const resourceMap = new Map(
            this.resources.map(resource => [resource.resourceCode, resource])
        );

        this.expItems.forEach(item => {
            const resource = resourceMap.get(item.resourceCode);
            const balance = Number(resource?.balance || 0);
            const option = document.createElement("option");
            option.value = item.resourceCode;
            option.textContent = item.resourceCode + " · " +
                this.formatNumber(item.expPerItem) + " EXP/vật phẩm · Có: " +
                this.formatNumber(balance);
            option.disabled = balance < 1;
            this.expSelect.append(option);
        });

        this.expSelect.disabled = this.expItems.length === 0;
        this.quantityInput.value = "1";
        this.updateExpItemInfo();
    }

    updateExpItemInfo() {
        const selected = this.expItems.find(item => item.resourceCode === this.expSelect.value);
        const resource = selected && this.resources.find(
            item => item.resourceCode === selected.resourceCode
        );
        const balance = Number(resource?.balance || 0);
        const available = Math.max(0, Math.floor(balance));

        this.quantityInput.max = String(available || 1);
        this.quantityInput.disabled = !selected || available < 1;
        this.useExpButton.disabled = this.busy || !selected || available < 1 || !this.selectedHero;

        this.expInfo.textContent = selected
            ? "Số dư: " + this.formatNumber(balance) +
                " · EXP mỗi vật phẩm: " + this.formatNumber(selected.expPerItem)
            : "Chọn vật phẩm để xem số dư và EXP mỗi vật phẩm.";
    }

    async useSelectedExpItems() {
        if (!this.selectedHero || this.busy) return;

        const item = this.expItems.find(
            value => value.resourceCode === this.expSelect.value
        );
        const quantity = Number(this.quantityInput.value);
        const resource = item && this.resources.find(
            value => value.resourceCode === item.resourceCode
        );
        const available = Math.floor(Number(resource?.balance || 0));

        if (!item || !Number.isInteger(quantity) || quantity <= 0 || quantity > available) {
            this.showMessage("Vật phẩm hoặc số lượng sử dụng không hợp lệ.", "error");
            return;
        }

        this.busy = true;
        this.updateExpItemInfo();
        this.useExpButton.textContent = "ĐANG BỒI DƯỠNG...";

        try {
            const updatedHero = await this.request(
                "/api/player/heroes/" + encodeURIComponent(this.selectedHero.instanceId) + "/use-exp-item",
                {
                    method: "POST",
                    body: JSON.stringify({
                        resourceCode: item.resourceCode,
                        quantity,
                        transactionKey: this.createTransactionKey()
                    })
                }
            );

            this.replaceHero(updatedHero);
            this.selectedHero = updatedHero;
            const profile = await this.request("/api/player/me");
            this.resources = profile.resources || [];

            this.renderRoster();
            this.renderDetail();
            await this.previewSelectedHero();
            this.showMessage(
                "Đã sử dụng " + quantity + " vật phẩm " + item.resourceCode +
                ". Cấp độ và chỉ số đã được cập nhật từ máy chủ.",
                "success"
            );
        } catch (error) {
            console.error("Use hero EXP item failed:", error);
            this.showMessage(error.message || "Không thể sử dụng vật phẩm EXP.", "error");
        } finally {
            this.busy = false;
            this.updateExpItemInfo();
            this.useExpButton.textContent = "SỬ DỤNG EXP";
        }
    }

    replaceHero(updatedHero) {
        const index = this.heroes.findIndex(
            hero => Number(hero.instanceId) === Number(updatedHero.instanceId)
        );
        if (index >= 0) this.heroes[index] = updatedHero;
    }

    async previewSelectedHero() {
        if (!this.preview || !this.selectedHero) return;
        await this.preview.sync(
            [this.selectedHero],
            [{ instanceId: this.selectedHero.instanceId, row: 1, column: 1 }]
        );
    }

    renderEmptyDetail() {
        document.getElementById("preview-title").textContent = "Chưa có nhân vật";
        document.getElementById("hero-name").textContent = "—";
        document.getElementById("hero-description").textContent = "Nhân vật sẽ xuất hiện tại đây sau khi được cấp.";
        document.getElementById("hero-tags").replaceChildren();
        document.getElementById("hero-stats").replaceChildren(this.createMessage("Chưa có dữ liệu chỉ số."));
        document.getElementById("hero-skills").replaceChildren(this.createMessage("Chưa có dữ liệu kỹ năng."));
        this.expSelect.replaceChildren();
        this.expSelect.disabled = true;
        this.useExpButton.disabled = true;
        this.preview?.sync([], []);
    }

    createMessage(message) {
        const element = document.createElement("div");
        element.className = "empty-state";
        element.textContent = message;
        return element;
    }

    initialOf(value) {
        return String(value || "?").trim().charAt(0).toUpperCase() || "?";
    }

    formatNumber(value) {
        const number = Number(value);
        return Number.isFinite(number)
            ? number.toLocaleString("vi-VN", { maximumFractionDigits: 2 })
            : "—";
    }

    createTransactionKey() {
        if (window.crypto?.randomUUID) return window.crypto.randomUUID();
        return "hero-exp-" + Date.now() + "-" + Math.random().toString(36).slice(2);
    }

    showMessage(message, type) {
        this.messageElement.textContent = message;
        this.messageElement.className = "hero-message " + (type || "");
        this.messageElement.hidden = false;
    }

    dispose() {
        this.preview?.dispose();
    }
}

window.addEventListener("beforeunload", () => {
    window.heroManagementScreen?.dispose();
}, { once: true });
