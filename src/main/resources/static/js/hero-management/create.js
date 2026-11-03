document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("hero-form");
    const skeletonInput = document.getElementById("skeleton-file");
    const atlasInput = document.getElementById("atlas-file");
    const textureInput = document.getElementById("texture-files");
    const canvas = document.getElementById("spine-preview");
    const message = document.getElementById("page-message");
    const animationSelect = document.getElementById("animation-select");
    const skillEditor = document.getElementById("skill-editor");
    const saveButton = document.getElementById("save-hero");
    const skillTypes = [
        ["BASIC", "Đánh thường"],
        ["ACTIVE", "Kỹ năng chủ động"],
        ["ULTIMATE", "Tuyệt kỹ"],
        ["PASSIVE", "Nội tại 1"],
        ["PASSIVE", "Nội tại 2"]
    ];
    const effectOptions = ["DAMAGE", "HEAL", "DOT", "HOT"];
    const targetOptions = [
        ["SELF", "Bản thân người thi triển"],
        ["RANDOM", "Ngẫu nhiên 1 mục tiêu"],
        ["RANDOM_2", "Ngẫu nhiên tối đa 2 mục tiêu"],
        ["RANDOM_3", "Ngẫu nhiên tối đa 3 mục tiêu"],
        ["FRONT", "Toàn bộ hàng trước"],
        ["BACK", "Toàn bộ hàng sau"],
        ["LOWEST_HP", "HP hiện tại thấp nhất"],
        ["LOWEST_HP_PERCENT", "% HP thấp nhất"],
        ["HIGHEST_HP_PERCENT", "% HP cao nhất"],
        ["HIGHEST_ATK", "ATK cao nhất"],
        ["LOWEST_ATK", "ATK thấp nhất"],
        ["ALL_ENEMIES", "Toàn bộ mục tiêu trong phe được chọn"],
        ["ALL_ALLIES", "Toàn bộ mục tiêu trong phe được chọn (alias)"]
    ];

    let manager = null;
    let character = null;
    let selectedAnimation = "";
    let objectUrls = [];
    let previewReady = false;

    skillEditor.innerHTML = skillTypes.map(([type, label], index) => {
        const number = index + 1;
        const defaultEffect = index >= 3 ? "HEAL" : "DAMAGE";
        const defaultTarget = index === 0 ? "FRONT" : index <= 2 ? "ALL_ENEMIES" : "SELF";
        return `
            <article class="skill-card">
                <h3>${String(number).padStart(2, "0")} · ${label} (${type})</h3>
                <div class="skill-fields">
                    <label>Tên kỹ năng *<input name="skillName${number}" required maxlength="100" placeholder="Tên kỹ năng"></label>
                    <label>Animation Spine *<select name="skillAnimation${number}" required disabled><option value="">Tải assets trước</option></select></label>
                    <label>Loại hiệu ứng *<select name="skillEffectType${number}" required>${effectOptions.map(value => `<option value="${value}" ${value === defaultEffect ? "selected" : ""}>${value}</option>`).join("")}</select></label>
                    <label>Mục tiêu *<select name="skillTargetType${number}" required>${targetOptions.map(([value, label]) => `<option value="${value}" ${value === defaultTarget ? "selected" : ""}>${label}</option>`).join("")}</select><small class="skill-note">Sát thương chọn địch; hồi máu chọn đồng minh. RANDOM_2/3 chọn tối đa số mục tiêu còn sống.</small></label>
                    <label>Hệ số tác động *<input name="skillMultiplier${number}" type="number" min="0.000001" step="any" value="1" required></label>
                    <label>Xác suất (0–1) *<input name="skillChance${number}" type="number" min="0" max="1" step="0.01" value="1" required></label>
                    <label>Duration hiệu ứng *<input name="skillDuration${number}" type="number" min="0" step="1" value="0" required></label>
                    <label>Thời lượng animation (ms) *<input name="skillAnimationDuration${number}" type="number" min="1" step="1" value="1000" required></label>
                    <label>Thời điểm tác động (ms) *<input name="skillImpactTime${number}" type="number" min="0" step="1" value="500" required></label>
                    <label class="wide">Mô tả *<textarea name="skillDescription${number}" rows="2" required maxlength="1000" placeholder="Mô tả kỹ năng"></textarea></label>
                </div>
                <p class="skill-note">Với DOT/HOT, Duration phải lớn hơn 0. Thời điểm tác động không được vượt quá thời lượng animation.</p>
            </article>`;
    }).join("");

    function field(name) {
        return form.querySelector(`[name="${name}"]`);
    }

    function value(name) {
        return field(name).value.trim();
    }

    function number(name) {
        return Number(value(name));
    }

    function showMessage(text, success = false) {
        message.hidden = false;
        message.textContent = text;
        message.classList.toggle("success", success);
    }

    function setCheck(id, text, ok) {
        const element = document.getElementById(id);
        element.textContent = text;
        element.classList.toggle("ok", Boolean(ok));
    }

    function setSaveEnabled(enabled) {
        saveButton.disabled = !enabled;
        saveButton.title = enabled ? "" : "Tải và preview bộ Spine trước khi lưu";
    }

    function clearPreview() {
        previewReady = false;
        setSaveEnabled(false);
        if (manager) {
            manager.dispose();
            manager = null;
            character = null;
        }
        objectUrls.forEach(url => URL.revokeObjectURL(url));
        objectUrls = [];
        document.getElementById("stage-placeholder").hidden = false;
        document.getElementById("stage-placeholder").textContent = "Tải bộ Spine để xem nhân vật";
        animationSelect.disabled = true;
        ["play-animation", "pause-animation", "restart-animation", "preview-scale", "preview-speed"]
            .forEach(id => document.getElementById(id).disabled = true);
        document.querySelectorAll('[name^="skillAnimation"]').forEach(select => {
            select.disabled = true;
            select.innerHTML = '<option value="">Tải assets trước</option>';
        });
        setCheck("check-animation", "Animation: chưa đọc", false);
    }

    [skeletonInput, atlasInput, textureInput].forEach(input => input.addEventListener("change", () => {
        clearPreview();
        const file = input.files?.[0];
        if (input === skeletonInput) document.getElementById("skeleton-name").textContent = file?.name || "Chọn file .json";
        if (input === atlasInput) document.getElementById("atlas-name").textContent = file?.name || "Chọn file .atlas";
        if (input === textureInput) document.getElementById("texture-names").textContent =
            [...input.files].map(item => item.name).join(", ") || "Chọn một hoặc nhiều file .png";
        setCheck("check-json", "Skeleton JSON: " + (skeletonInput.files[0] ? "đã chọn" : "chưa có"), Boolean(skeletonInput.files[0]));
        setCheck("check-atlas", "Atlas: " + (atlasInput.files[0] ? "đã chọn" : "chưa có"), Boolean(atlasInput.files[0]));
        setCheck("check-textures", "Texture: " + (textureInput.files.length ? textureInput.files.length + " file" : "chưa có"), textureInput.files.length > 0);
    }));

    function extractAtlasPages(atlasText) {
        return atlasText.split(/\r?\n/).map(line => line.trim())
            .filter(line => /\.png(?:\s|$)/i.test(line))
            .map(line => line.split(/\s+/)[0].split(/[\\/]/).pop());
    }

    async function loadPreview() {
        clearPreview();
        message.hidden = true;

        const jsonFile = skeletonInput.files[0];
        const atlasFile = atlasInput.files[0];
        const textureFiles = [...textureInput.files];

        if (!jsonFile || !atlasFile || textureFiles.length === 0) {
            showMessage("Cần chọn đủ Skeleton JSON, Atlas và ít nhất một Texture PNG.");
            return;
        }

        const loadButton = document.getElementById("load-preview");
        loadButton.disabled = true;
        loadButton.textContent = "ĐANG ĐỌC ASSETS...";

        try {
            const jsonData = JSON.parse(await jsonFile.text());
            if (!jsonData.skeleton || !Array.isArray(jsonData.bones)
                    || !jsonData.animations || typeof jsonData.animations !== "object"
                    || Array.isArray(jsonData.animations)) {
                throw new Error("Skeleton JSON không có cấu trúc Spine JSON hợp lệ.");
            }

            const atlasText = await atlasFile.text();
            const pages = extractAtlasPages(atlasText);
            const textureByName = new Map(textureFiles.map(file => [file.name.toLowerCase(), file]));
            const missingPages = pages.filter(page => !textureByName.has(page.toLowerCase()));
            if (pages.length === 0) throw new Error("Không tìm thấy tên texture PNG trong file Atlas.");
            if (missingPages.length) throw new Error("Thiếu texture được Atlas tham chiếu: " + missingPages.join(", "));

            const jsonUrl = URL.createObjectURL(jsonFile);
            const atlasUrl = URL.createObjectURL(atlasFile);
            const textureEntries = textureFiles.map(file => ({
                name: file.name,
                url: URL.createObjectURL(file)
            }));
            objectUrls.push(jsonUrl, atlasUrl, ...textureEntries.map(entry => entry.url));

            manager = new SpineManager(canvas);
            manager.initialize();
            character = await manager.createCharacter({
                skeleton: jsonUrl,
                atlas: atlasUrl,
                textures: textureEntries,
                scale: 0.2,
                offsetX: 0,
                offsetY: 0
            });
            manager.start();

            const names = character.getAnimationNames();
            if (!names.length) throw new Error("Skeleton không có animation nào.");

            animationSelect.innerHTML = names.map(name =>
                `<option value="${escapeHtml(name)}">${escapeHtml(name)}</option>`).join("");
            animationSelect.disabled = false;
            document.querySelectorAll('[name^="skillAnimation"]').forEach(select => {
                select.innerHTML = '<option value="">Chọn animation</option>' +
                    names.map(name => `<option value="${escapeHtml(name)}">${escapeHtml(name)}</option>`).join("");
                select.disabled = false;
            });

            selectedAnimation = names.find(name => /idle|stand|wait|holdon/i.test(name)) || names[0];
            animationSelect.value = selectedAnimation;
            character.animationController.play(selectedAnimation, true);
            renderAnimationChips(names);
            document.getElementById("stage-placeholder").hidden = true;
            document.getElementById("preview-status").textContent = "Đã tải · " + names.length + " animation";
            ["play-animation", "pause-animation", "restart-animation", "preview-scale", "preview-speed"]
                .forEach(id => document.getElementById(id).disabled = false);
            setCheck("check-json", "Skeleton JSON: hợp lệ", true);
            setCheck("check-atlas", "Atlas: đọc được " + pages.length + " page", true);
            setCheck("check-textures", "Texture: đủ " + textureFiles.length + " file", true);
            setCheck("check-animation", "Animation: tìm thấy " + names.length + " animation", true);
            previewReady = true;
            setSaveEnabled(true);
        } catch (error) {
            clearPreview();
            showMessage(error.message || "Không thể tải bộ Spine.");
            document.getElementById("preview-status").textContent = "Tải thất bại";
        } finally {
            loadButton.disabled = false;
            loadButton.textContent = "TẢI ASSETS & ĐỌC ANIMATION";
        }
    }

    function escapeHtml(input) {
        return String(input).replace(/[&<>"']/g, char => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
        })[char]);
    }

    function renderAnimationChips(names) {
        const list = document.getElementById("animation-list");
        list.innerHTML = '<div class="animation-chips">' + names.map(name =>
            `<button class="animation-chip" type="button" data-animation="${escapeHtml(name)}">${escapeHtml(name)}</button>`
        ).join("") + '</div>';
        list.querySelectorAll("[data-animation]").forEach(button => {
            button.addEventListener("click", () => playAnimation(button.dataset.animation));
        });
        updateActiveChip();
    }

    function updateActiveChip() {
        document.querySelectorAll(".animation-chip").forEach(button =>
            button.classList.toggle("active", button.dataset.animation === selectedAnimation));
    }

    function playAnimation(name) {
        if (!character || !name) return;
        selectedAnimation = name;
        animationSelect.value = name;
        character.animationController.play(name, true);
        updateActiveChip();
    }

    function collectStats(prefix) {
        return {
            hp: number(prefix + "Hp"),
            atk: number(prefix + "Atk"),
            def: number(prefix + "Def"),
            speed: number(prefix + "Speed"),
            crit: number(prefix + "Crit"),
            critDamage: number(prefix + "CritDamage"),
            accuracy: number(prefix + "Accuracy"),
            resistance: number(prefix + "Resistance"),
            penFlat: number(prefix + "PenFlat"),
            penPercent: number(prefix + "PenPercent"),
            lifesteal: number(prefix + "Lifesteal")
        };
    }

    function collectRequest() {
        return {
            heroCode: value("heroCode"),
            heroName: value("heroName"),
            rarity: value("rarity"),
            role: value("role"),
            faction: value("faction"),
            avatarCode: value("avatarCode"),
            description: value("description"),
            baseStats: collectStats("base"),
            growthStats: collectStats("growth"),
            skills: skillTypes.map((skillType, index) => {
                const n = index + 1;
                return {
                    name: value("skillName" + n),
                    description: value("skillDescription" + n),
                    animation: value("skillAnimation" + n),
                    effectType: value("skillEffectType" + n),
                    targetType: value("skillTargetType" + n),
                    multiplier: number("skillMultiplier" + n),
                    chance: number("skillChance" + n),
                    duration: number("skillDuration" + n),
                    animationDurationMs: number("skillAnimationDuration" + n),
                    impactTimeMs: number("skillImpactTime" + n)
                };
            })
        };
    }

    async function saveHero() {
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }
        if (!previewReady || !character) {
            showMessage("Hãy tải và preview bộ Spine trước khi lưu.");
            return;
        }

        const request = collectRequest();
        for (let index = 0; index < request.skills.length; index++) {
            const skill = request.skills[index];
            if (skill.impactTimeMs > skill.animationDurationMs) {
                showMessage("Thời điểm tác động không được vượt thời lượng animation ở kỹ năng " + (index + 1));
                field("skillImpactTime" + (index + 1)).focus();
                return;
            }
            if ((skill.effectType === "DOT" || skill.effectType === "HOT") && skill.duration <= 0) {
                showMessage("DOT/HOT cần Duration lớn hơn 0 ở kỹ năng " + (index + 1));
                field("skillDuration" + (index + 1)).focus();
                return;
            }
        }

        const formData = new FormData();
        formData.append("request", new Blob([JSON.stringify(request)], { type: "application/json" }));
        formData.append("skeleton", skeletonInput.files[0]);
        formData.append("atlas", atlasInput.files[0]);
        [...textureInput.files].forEach(file => formData.append("textures", file));

        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        const headers = {};
        if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

        saveButton.disabled = true;
        saveButton.textContent = "ĐANG LƯU...";
        message.hidden = true;

        try {
            const response = await fetch("/api/hero-management", {
                method: "POST",
                credentials: "same-origin",
                headers,
                body: formData
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) {
                throw new Error(result.detail || result.message || "Lưu tướng thất bại (HTTP " + response.status + ").");
            }

            showMessage(
                "Đã tạo tướng " + result.heroName + " (" + result.heroCode + "). Assets đã lưu tại " +
                result.spineSkeleton + " · " + result.animations.length + " animation.",
                true
            );
            saveButton.textContent = "ĐÃ LƯU TƯỚNG";
            saveButton.disabled = true;
            form.querySelectorAll("input, select, textarea").forEach(input => input.disabled = true);
            ["load-preview", "play-animation", "pause-animation", "restart-animation", "preview-scale", "preview-speed"]
                .forEach(id => document.getElementById(id).disabled = true);
        } catch (error) {
            showMessage(error.message || "Không thể lưu tướng.");
            saveButton.disabled = false;
            saveButton.textContent = "LƯU TƯỚNG";
        }
    }

    document.getElementById("load-preview").addEventListener("click", loadPreview);
    animationSelect.addEventListener("change", () => playAnimation(animationSelect.value));
    document.getElementById("play-animation").addEventListener("click", () => character?.animationController.resume());
    document.getElementById("pause-animation").addEventListener("click", () => character?.animationController.pause());
    document.getElementById("restart-animation").addEventListener("click", () => character?.animationController.restart());
    document.getElementById("preview-scale").addEventListener("input", event => character?.setScale(Number(event.target.value)));
    document.getElementById("preview-speed").addEventListener("input", event => character?.animationController.setSpeed(Number(event.target.value)));
    form.addEventListener("submit", event => {
        event.preventDefault();
        saveHero();
    });

    window.addEventListener("beforeunload", clearPreview);
});
