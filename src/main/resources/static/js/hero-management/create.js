document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("hero-form");
    const skeletonInput = document.getElementById("skeleton-file");
    const atlasInput = document.getElementById("atlas-file");
    const textureInput = document.getElementById("texture-files");
    const canvas = document.getElementById("spine-preview");
    const message = document.getElementById("page-message");
    const animationSelect = document.getElementById("animation-select");
    const skillEditor = document.getElementById("skill-editor");
    const skillTypes = [
        ["BASIC", "Đánh thường"],
        ["ACTIVE", "Kỹ năng chủ động"],
        ["ULTIMATE", "Tuyệt kỹ"],
        ["PASSIVE", "Nội tại 1"],
        ["PASSIVE", "Nội tại 2"]
    ];

    let manager = null;
    let character = null;
    let selectedAnimation = "";
    let objectUrls = [];

    skillEditor.innerHTML = skillTypes.map(([type, label], index) => `
        <article class="skill-card">
            <h3>${String(index + 1).padStart(2, "0")} · ${label} (${type})</h3>
            <div class="skill-fields">
                <label>Tên kỹ năng *<input name="skillName${index + 1}" required maxlength="100" placeholder="Tên kỹ năng"></label>
                <label>Animation Spine *<select name="skillAnimation${index + 1}" required disabled><option value="">Tải assets trước</option></select></label>
                <label class="wide">Mô tả *<textarea name="skillDescription${index + 1}" rows="2" required maxlength="1000" placeholder="Mô tả kỹ năng"></textarea></label>
            </div>
        </article>`).join("");

    function showMessage(text) {
        message.hidden = false;
        message.textContent = text;
    }

    function setCheck(id, text, ok) {
        const element = document.getElementById(id);
        element.textContent = text;
        element.classList.toggle("ok", Boolean(ok));
    }

    function clearPreview() {
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
            .map(line => line.split(/\s+/)[0]);
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
            if (!jsonData.skeleton || !Array.isArray(jsonData.bones) || !Array.isArray(jsonData.animations)) {
                throw new Error("Skeleton JSON không có cấu trúc Spine JSON hợp lệ.");
            }

            const atlasText = await atlasFile.text();
            const pages = extractAtlasPages(atlasText);
            const textureByName = new Map(textureFiles.map(file => [file.name.toLowerCase(), file]));
            const missingPages = pages.filter(page => !textureByName.has(page.toLowerCase()));
            if (pages.length === 0) {
                throw new Error("Không tìm thấy tên texture PNG trong file Atlas.");
            }
            if (missingPages.length) {
                throw new Error("Thiếu texture được Atlas tham chiếu: " + missingPages.join(", "));
            }

            const jsonUrl = URL.createObjectURL(jsonFile);
            const atlasUrl = URL.createObjectURL(atlasFile);
            const textureUrls = textureFiles.map(file => URL.createObjectURL(file));
            objectUrls.push(jsonUrl, atlasUrl, ...textureUrls);

            manager = new SpineManager(canvas);
            manager.initialize();
            character = await manager.createCharacter({
                skeleton: jsonUrl,
                atlas: atlasUrl,
                textures: textureUrls,
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
        } catch (error) {
            clearPreview();
            showMessage(error.message || "Không thể tải bộ Spine.");
            document.getElementById("preview-status").textContent = "Tải thất bại";
        } finally {
            loadButton.disabled = false;
            loadButton.textContent = "TẢI ASSETS & ĐỌC ANIMATION";
        }
    }

    function escapeHtml(value) {
        return String(value).replace(/[&<>"']/g, char => ({
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

    document.getElementById("load-preview").addEventListener("click", loadPreview);
    animationSelect.addEventListener("change", () => playAnimation(animationSelect.value));
    document.getElementById("play-animation").addEventListener("click", () => character?.animationController.resume());
    document.getElementById("pause-animation").addEventListener("click", () => character?.animationController.pause());
    document.getElementById("restart-animation").addEventListener("click", () => character?.animationController.restart());
    document.getElementById("preview-scale").addEventListener("input", event => character?.setScale(Number(event.target.value)));
    document.getElementById("preview-speed").addEventListener("input", event => character?.animationController.setSpeed(Number(event.target.value)));

    form.addEventListener("submit", event => {
        event.preventDefault();
        form.reportValidity();
    });

    window.addEventListener("beforeunload", clearPreview);
});
