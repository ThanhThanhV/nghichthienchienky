document.addEventListener("DOMContentLoaded", async () => {
    const $ = id => document.getElementById(id);
    const canvas = $("heroCanvas");
    const heroList = $("heroList");
    const heroSearch = $("heroSearch");
    const assetSelect = $("assetSelect");
    const animationList = $("animationList");
    const status = $("previewStatus");
    const placeholder = $("previewPlaceholder");
    let heroes = [];
    let selectedHero = null;
    let selectedAsset = null;
    let animations = [];
    let selectedAnimation = null;
    let manager = null;
    let character = null;
    let generation = 0;

    const naturalCompare = (a, b) => a.localeCompare(b, "en", {numeric:true, sensitivity:"base"});
    const setStatus = (message, error = false) => {
        status.textContent = message;
        status.classList.toggle("error", error);
    };
    const controls = enabled => {
        ["playButton","pauseButton","restartButton","centerButton","loopInput","speedInput","scaleInput","debugInput"]
            .forEach(id => $(id).disabled = !enabled);
    };
    const disposeManager = () => {
        if (manager) manager.dispose();
        manager = null;
        character = null;
    };
    const parseAtlasPages = atlas => {
        const pages = [];
        atlas.split(/\r?\n/).forEach(line => {
            const value = line.trim();
            if (/^[^:]+\.(png|jpe?g)$/i.test(value) && !pages.includes(value)) pages.push(value);
        });
        return pages;
    };

    function renderHeroes() {
        const keyword = heroSearch.value.trim().toLowerCase();
        const visible = heroes.filter(hero => hero.code.toLowerCase().includes(keyword));
        $("heroCount").textContent = visible.length + " / " + heroes.length;
        heroList.innerHTML = "";
        visible.forEach(hero => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "hero-item" + (selectedHero?.code === hero.code ? " active" : "");
            const name = document.createElement("strong");
            name.textContent = hero.code;
            const count = document.createElement("small");
            count.textContent = hero.assets.length + " bộ";
            button.append(name, count);
            button.addEventListener("click", () => selectHero(hero));
            heroList.appendChild(button);
        });
    }

    function selectHero(hero) {
        selectedHero = hero;
        $("selectedHero").textContent = hero.code;
        assetSelect.innerHTML = "";
        hero.assets.forEach(asset => {
            const option = document.createElement("option");
            option.value = asset.id;
            option.textContent = asset.id;
            assetSelect.appendChild(option);
        });
        assetSelect.disabled = hero.assets.length < 2;
        renderHeroes();
        loadAsset(hero.assets[0]);
    }

    async function selectAssetById(id) {
        const asset = selectedHero?.assets.find(item => item.id === id);
        if (asset) await loadAsset(asset);
    }

    function renderAnimations() {
        animationList.innerHTML = "";
        $("animationCount").textContent = animations.length + " animation";
        if (!animations.length) {
            animationList.innerHTML = '<div class="empty-state">Skeleton này không có animation.</div>';
            return;
        }
        animations.forEach((animation, index) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "animation-item" + (animation.name === selectedAnimation ? " active" : "");
            const number = document.createElement("span");
            number.className = "animation-number";
            number.textContent = String(index + 1);
            const name = document.createElement("span");
            name.className = "animation-name";
            name.textContent = animation.name;
            button.append(number, name);
            button.addEventListener("click", () => playAnimation(animation.name));
            animationList.appendChild(button);
        });
    }

    function playAnimation(name) {
        if (!character || !name) return;
        try {
            character.animationController.play(name, $("loopInput").checked, false);
            selectedAnimation = name;
            renderAnimations();
            setStatus(selectedHero.code + " / " + selectedAsset.id + " / #" +
                (animations.findIndex(item => item.name === name) + 1) + " " + name);
        } catch (error) {
            setStatus(error.message, true);
        }
    }

    async function loadAsset(asset) {
        const requestId = ++generation;
        selectedAsset = asset;
        selectedAnimation = null;
        controls(false);
        animationList.innerHTML = '<div class="empty-state">Đang đọc danh sách animation...</div>';
        $("selectedAsset").textContent = asset.id;
        placeholder.hidden = false;
        setStatus("Đang tải " + asset.id + "...");
        disposeManager();

        let loadingManager = null;
        try {
            const [jsonResponse, atlasResponse] = await Promise.all([
                fetch(asset.skeleton), fetch(asset.atlas)
            ]);
            if (!jsonResponse.ok) throw new Error("Không tải được skeleton JSON (HTTP " + jsonResponse.status + ")");
            if (!atlasResponse.ok) throw new Error("Không tải được atlas (HTTP " + atlasResponse.status + ")");
            const json = await jsonResponse.json();
            const atlas = await atlasResponse.text();
            if (requestId !== generation) return;

            animations = Object.keys(json.animations || {}).sort(naturalCompare).map(name => ({name}));
            renderAnimations();

            const pages = parseAtlasPages(atlas);
            if (!pages.length) throw new Error("Atlas không khai báo texture page.");
            const directory = asset.skeleton.substring(0, asset.skeleton.lastIndexOf("/") + 1);
            loadingManager = new SpineManager(canvas);
            loadingManager.initialize();
            const loadedCharacter = await loadingManager.createCharacter({
                code: selectedHero.code + "_" + asset.id,
                skeleton: asset.skeleton,
                atlas: asset.atlas,
                textures: pages.map(page => directory + page),
                scale: Number($("scaleInput").value),
                offsetX: 0,
                offsetY: 0
            });
            if (requestId !== generation) {
                loadingManager.dispose();
                return;
            }
            manager = loadingManager;
            character = loadedCharacter;
            loadingManager = null;
            character.setScale(Number($("scaleInput").value));
            character.center();
            character.animationController.setSpeed(Number($("speedInput").value));
            manager.start();
            placeholder.hidden = true;
            controls(true);
            if (animations.length) playAnimation(animations[0].name);
            else setStatus("Đã tải " + asset.id + " nhưng skeleton không có animation.");
        } catch (error) {
            loadingManager?.dispose();
            if (requestId === generation) {
                disposeManager();
                controls(false);
                animationList.innerHTML = '<div class="empty-state">Không thể tải animation.</div>';
                setStatus(error.message || "Lỗi tải Spine asset.", true);
            }
        }
    }

    $("assetSelect").addEventListener("change", event => selectAssetById(event.target.value));
    heroSearch.addEventListener("input", renderHeroes);
    $("playButton").addEventListener("click", () => selectedAnimation && playAnimation(selectedAnimation));
    $("pauseButton").addEventListener("click", () => character?.animationController.pause());
    $("restartButton").addEventListener("click", () => character?.animationController.restart());
    $("centerButton").addEventListener("click", () => character?.center());
    $("loopInput").addEventListener("change", () => selectedAnimation && playAnimation(selectedAnimation));
    $("speedInput").addEventListener("input", event => {
        const value = Number(event.target.value);
        $("speedValue").textContent = value.toFixed(1) + "x";
        character?.animationController.setSpeed(value);
    });
    $("scaleInput").addEventListener("input", event => {
        const value = Number(event.target.value);
        $("scaleValue").textContent = value.toFixed(2);
        character?.setScale(value);
        character?.center();
    });
    $("debugInput").addEventListener("change", event => manager?.setDebug(event.target.checked));
    window.addEventListener("resize", () => manager?.resize());
    window.addEventListener("beforeunload", disposeManager);

    try {
        const response = await fetch("/admin/api/spine-animations");
        if (!response.ok) throw new Error("Không lấy được danh sách heroes (HTTP " + response.status + ")");
        heroes = await response.json();
        renderHeroes();
        if (heroes.length) selectHero(heroes[0]);
        else setStatus("Không tìm thấy bộ skeleton JSON + atlas trong thư mục heroes.", true);
    } catch (error) {
        $("heroCount").textContent = "Lỗi";
        setStatus(error.message, true);
    }
});