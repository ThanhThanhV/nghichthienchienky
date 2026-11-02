document.addEventListener("DOMContentLoaded", () => {
    const ROOT = "/assets/spine/seffects/";
    const canvas = document.getElementById("effectCanvas");
    const status = document.getElementById("effectStatus");
    const list = document.getElementById("effectList");
    const search = document.getElementById("effectSearch");
    const groupSelect = document.getElementById("effectGroup");
    const count = document.getElementById("effectCount");
    const animationList = document.getElementById("effectAnimations");
    const loopInput = document.getElementById("effectLoop");
    const speedInput = document.getElementById("effectSpeed");
    const speedValue = document.getElementById("effectSpeedValue");
    const scaleInput = document.getElementById("effectScale");
    const scaleValue = document.getElementById("effectScaleValue");
    const debugInput = document.getElementById("effectDebug");
    const pauseButton = document.getElementById("effectPause");
    const resumeButton = document.getElementById("effectResume");
    const restartButton = document.getElementById("effectRestart");
    const centerButton = document.getElementById("effectCenter");

    let manager = null;
    let character = null;
    let selectedId = null;
    let selectedAnimation = null;
    let generation = 0;

    const groups = new Map();
    window.SPINE_EFFECT_CATALOG.forEach(item => {
        if (!groups.has(item.family)) {
            groups.set(item.family, item.familyLabel);
        }
    });

    function setStatus(message, isError = false) {
        status.textContent = message;
        status.classList.toggle("error", isError);
    }

    function setControls(enabled) {
        [pauseButton, resumeButton, restartButton, centerButton,
            speedInput, scaleInput, loopInput, debugInput]
            .forEach(element => element.disabled = !enabled);
    }

    function renderGroupOptions() {
        groupSelect.innerHTML = "";
        const all = document.createElement("option");
        all.value = "all";
        all.textContent = "Tất cả nhóm";
        groupSelect.appendChild(all);

        [...groups.entries()]
            .sort((a, b) => a[1].localeCompare(b[1], "vi"))
            .forEach(([key, label]) => {
                const option = document.createElement("option");
                option.value = key;
                option.textContent = label;
                groupSelect.appendChild(option);
            });
    }

    function renderEffectList() {
        const keyword = search.value.trim().toLowerCase();
        const group = groupSelect.value;
        const filtered = window.SPINE_EFFECT_CATALOG.filter(item => {
            const matchesGroup = group === "all" || item.family === group;
            const matchesKeyword = !keyword ||
                item.id.toLowerCase().includes(keyword) ||
                item.familyLabel.toLowerCase().includes(keyword) ||
                item.heroes.some(hero => hero.includes(keyword));
            return matchesGroup && matchesKeyword;
        });

        count.textContent = filtered.length + " / " +
            window.SPINE_EFFECT_CATALOG.length + " effect";
        list.innerHTML = "";

        filtered.forEach(item => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "effect-item" +
                (item.id === selectedId ? " active" : "");
            button.dataset.effectId = item.id;

            const title = document.createElement("strong");
            title.textContent = item.id;
            const subtitle = document.createElement("span");
            subtitle.textContent = item.familyLabel;
            const marker = document.createElement("small");
            marker.textContent = item.ambiguous
                ? "Nhóm dùng chung nhiều biến thể"
                : item.family === "other"
                    ? "Chưa xác định tướng"
                    : "Khớp ID asset tướng";

            button.append(title, subtitle, marker);
            list.appendChild(button);
        });
    }

    function parseAtlasPages(atlasText) {
        const pages = [];
        atlasText.split(/\r?\n/).forEach(line => {
            const value = line.trim();
            if (/^[^:]+\.(png|jpe?g)$/i.test(value) && !pages.includes(value)) {
                pages.push(value);
            }
        });
        return pages;
    }

    function renderAnimationButtons(animationMap) {
        animationList.innerHTML = "";
        const entries = Object.entries(animationMap || {});
        if (!entries.length) {
            animationList.textContent = "Không tìm thấy animation.";
            return;
        }

        entries.forEach(([name, data], index) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "animation-item" + (index === 0 ? " active" : "");
            button.dataset.animation = name;

            const title = document.createElement("strong");
            title.textContent = name;

            const events = Array.isArray(data?.events) ? data.events : [];
            const eventText = document.createElement("small");
            eventText.textContent = events.length
                ? events.map(event => event.name + " @" +
                    Number(event.time || 0).toFixed(2) + "s").join(" · ")
                : "Không có event marker";

            button.append(title, eventText);
            animationList.appendChild(button);
        });
    }

    function playAnimation(name) {
        if (!character || !name) return;
        try {
            character.animationController.play(name, loopInput.checked, false);
            selectedAnimation = name;
            animationList.querySelectorAll("[data-animation]").forEach(button => {
                button.classList.toggle("active", button.dataset.animation === name);
            });
            setStatus("Đang xem " + selectedId + " / " + name);
        } catch (error) {
            setStatus(error.message, true);
        }
    }

    async function disposeCurrent() {
        const oldManager = manager;
        manager = null;
        character = null;
        if (oldManager) oldManager.dispose();
    }

    async function loadEffect(id) {
        const item = window.SPINE_EFFECT_CATALOG.find(entry => entry.id === id);
        if (!item) return;

        const currentGeneration = ++generation;
        selectedId = id;
        renderEffectList();
        setControls(false);
        animationList.innerHTML = "";
        setStatus("Đang tải " + id + "...");
        await disposeCurrent();

        try {
            const [jsonResponse, atlasResponse] = await Promise.all([
                fetch(ROOT + id + ".json"),
                fetch(ROOT + id + ".atlas")
            ]);
            if (!jsonResponse.ok) throw new Error("Không tải được " + id + ".json (HTTP " + jsonResponse.status + ")");
            if (!atlasResponse.ok) throw new Error("Không tải được " + id + ".atlas (HTTP " + atlasResponse.status + ")");

            const jsonData = await jsonResponse.json();
            const atlasText = await atlasResponse.text();
            const pageNames = parseAtlasPages(atlasText);
            if (!pageNames.length) throw new Error("Không tìm thấy texture page trong atlas.");

            const animationMap = jsonData.animations || {};
            renderAnimationButtons(animationMap);

            if (currentGeneration !== generation) return;

            manager = new SpineManager(canvas);
            manager.initialize();

            const textures = pageNames.map(name => ROOT + name);
            character = await manager.createCharacter({
                code: id,
                skeleton: ROOT + id + ".json",
                atlas: ROOT + id + ".atlas",
                textures,
                scale: 0.2,
                offsetX: 0,
                offsetY: 0
            });

            if (currentGeneration !== generation) {
                await disposeCurrent();
                return;
            }

            character.setScale(Number(scaleInput.value) || 0.2);
            character.animationController.setSpeed(Number(speedInput.value) || 1);
            manager.start();
            setControls(true);

            const firstAnimation = Object.keys(animationMap)[0];
            if (firstAnimation) playAnimation(firstAnimation);

            const events = Object.values(animationMap)
                .reduce((total, animation) => total +
                    (Array.isArray(animation.events) ? animation.events.length : 0), 0);
            setStatus(
                item.id + " · " + item.familyLabel + " · " +
                Object.keys(animationMap).length + " animation · " +
                events + " event marker · " + pageNames.length + " texture page"
            );
        } catch (error) {
            console.error("[SpineEffectLab]", error);
            await disposeCurrent();
            setControls(false);
            setStatus(error.message || "Không thể tải effect.", true);
        }
    }

    list.addEventListener("click", event => {
        const button = event.target.closest("[data-effect-id]");
        if (button) loadEffect(button.dataset.effectId);
    });

    animationList.addEventListener("click", event => {
        const button = event.target.closest("[data-animation]");
        if (button) playAnimation(button.dataset.animation);
    });

    search.addEventListener("input", renderEffectList);
    groupSelect.addEventListener("change", renderEffectList);

    pauseButton.addEventListener("click", () => character?.animationController.pause());
    resumeButton.addEventListener("click", () => character?.animationController.resume());
    restartButton.addEventListener("click", () => character?.animationController.restart());
    centerButton.addEventListener("click", () => character?.center());

    speedInput.addEventListener("input", () => {
        const speed = Number(speedInput.value);
        speedValue.textContent = speed.toFixed(1) + "x";
        character?.animationController.setSpeed(speed);
    });

    scaleInput.addEventListener("input", () => {
        const scale = Number(scaleInput.value);
        scaleValue.textContent = scale.toFixed(2);
        character?.setScale(scale);
    });

    loopInput.addEventListener("change", () => {
        if (selectedAnimation) playAnimation(selectedAnimation);
    });

    debugInput.addEventListener("change", () => manager?.setDebug(debugInput.checked));

    window.addEventListener("resize", () => manager?.resize());
    window.addEventListener("beforeunload", () => manager?.dispose());

    renderGroupOptions();
    renderEffectList();
    setControls(false);
    setStatus("Đã nạp danh mục " + window.SPINE_EFFECT_CATALOG.length + " effect. Chọn một effect để xem.");
});
