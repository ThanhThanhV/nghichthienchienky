document.addEventListener("DOMContentLoaded", () => {
    const ROOT = "/assets/spine/seffects/";
    const DRAFT_KEY = "spine-effect-lab-draft-v1";
    const $ = id => document.getElementById(id);
    const canvas = $("effectCanvas"), status = $("effectStatus");
    const list = $("effectList"), search = $("effectSearch");
    const groupSelect = $("effectGroup"), count = $("effectCount");
    const animationList = $("effectAnimations"), loopInput = $("effectLoop");
    const speedInput = $("effectSpeed"), speedValue = $("effectSpeedValue");
    const scaleInput = $("effectScale"), scaleValue = $("effectScaleValue");
    const debugInput = $("effectDebug"), pauseButton = $("effectPause");
    const resumeButton = $("effectResume"), restartButton = $("effectRestart");
    const centerButton = $("effectCenter");
    const heroSelect = $("mappingHero"), skillSelect = $("mappingSkill");
    const progress = $("mappingProgress"), skillInfo = $("mappingSkillInfo");
    const selectedAssetLabel = $("selectedAssetLabel");
    const phaseSelect = $("mappingPhase"), animationSelect = $("mappingEffectAnimation");
    const anchorSelect = $("mappingAnchor"), triggerSelect = $("mappingTrigger");
    const eventsInput = $("mappingEvents"), addButton = $("addEffectMapping");
    const currentMappings = $("currentSkillMappings"), draftMappings = $("draftSkillMappings");
    const exportButton = $("exportEffectDraft");

    let manager = null, character = null, selectedId = null, selectedAnimation = null;
    let generation = 0, heroes = [], selectedHero = null, selectedSkill = null;
    let heroDetail = null, mappingCatalog = {}, draft = readDraft();
    const groups = new Map();
    (window.SPINE_EFFECT_CATALOG || []).forEach(item => {
        if (!groups.has(item.family)) groups.set(item.family, item.familyLabel);
    });

    function readDraft() {
        try {
            const value = JSON.parse(localStorage.getItem(DRAFT_KEY) || "{}");
            return value && typeof value === "object" ? value : {};
        } catch (_) { return {}; }
    }
    function persistDraft() {
        localStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
        exportButton.disabled = !Object.keys(draft).length;
    }
    function setStatus(message, isError = false) {
        status.textContent = message;
        status.classList.toggle("error", isError);
    }
    function setControls(enabled) {
        [pauseButton, resumeButton, restartButton, centerButton, speedInput,
            scaleInput, loopInput, debugInput].forEach(element => element.disabled = !enabled);
    }
    function renderGroupOptions() {
        groupSelect.innerHTML = "";
        const all = document.createElement("option");
        all.value = "all"; all.textContent = "Tất cả nhóm";
        groupSelect.appendChild(all);
        [...groups.entries()].sort((a,b) => a[1].localeCompare(b[1],"vi")).forEach(([key,label]) => {
            const option = document.createElement("option");
            option.value = key; option.textContent = label; groupSelect.appendChild(option);
        });
    }
    function renderEffectList() {
        const catalog = window.SPINE_EFFECT_CATALOG || [];
        const keyword = search.value.trim().toLowerCase(), group = groupSelect.value;
        const filtered = catalog.filter(item => {
            const matchesGroup = group === "all" || item.family === group;
            const matchesKeyword = !keyword || item.id.toLowerCase().includes(keyword) ||
                item.familyLabel.toLowerCase().includes(keyword) ||
                (item.heroes || []).some(hero => hero.toLowerCase().includes(keyword));
            return matchesGroup && matchesKeyword;
        });
        count.textContent = filtered.length + " / " + catalog.length + " effect";
        list.innerHTML = "";
        filtered.forEach(item => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "effect-item" + (item.id === selectedId ? " active" : "");
            button.dataset.effectId = item.id;
            const title = document.createElement("strong"); title.textContent = item.id;
            const subtitle = document.createElement("span"); subtitle.textContent = item.familyLabel;
            const marker = document.createElement("small");
            marker.textContent = item.ambiguous ? "Có thể dùng chung nhiều biến thể" :
                item.family === "other" ? "Chưa xác định nhóm hero" : "Theo nhóm asset";
            button.append(title, subtitle, marker); list.appendChild(button);
        });
    }
    function parseAtlasPages(atlasText) {
        const pages = [];
        atlasText.split(/\r?\n/).forEach(line => {
            const value = line.trim();
            if (/^[^:]+\.(png|jpe?g)$/i.test(value) && !pages.includes(value)) pages.push(value);
        });
        return pages;
    }
    function renderAnimationButtons(animationMap) {
        animationList.innerHTML = "";
        const entries = Object.entries(animationMap || {});
        if (!entries.length) { animationList.textContent = "Không tìm thấy animation."; return; }
        entries.forEach(([name,data],index) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "animation-item" + (index === 0 ? " active" : "");
            button.dataset.animation = name;
            const title = document.createElement("strong"); title.textContent = name;
            const events = Array.isArray(data?.events) ? data.events : [];
            const eventText = document.createElement("small");
            eventText.textContent = events.length ? events.map(event =>
                event.name + " @" + Number(event.time || 0).toFixed(2) + "s").join(" · ") :
                "Không có event marker";
            button.append(title,eventText); animationList.appendChild(button);
        });
        refreshAnimationChoices(animationMap);
    }
    function refreshAnimationChoices(animationMap) {
        animationSelect.innerHTML = "";
        Object.keys(animationMap || {}).forEach(name => {
            const option = document.createElement("option");
            option.value = name; option.textContent = name; animationSelect.appendChild(option);
        });
        animationSelect.disabled = !Object.keys(animationMap || {}).length;
        selectedAnimation = Object.keys(animationMap || {})[0] || null;
        addButton.disabled = !selectedHero || !selectedSkill || !selectedId || !selectedAnimation;
    }
    function playAnimation(name) {
        if (!character || !name) return;
        try {
            character.animationController.play(name, loopInput.checked, false);
            selectedAnimation = name;
            animationList.querySelectorAll("[data-animation]").forEach(button =>
                button.classList.toggle("active", button.dataset.animation === name));
            if (animationSelect.querySelector('option[value="' + CSS.escape(name) + '"]')) {
                animationSelect.value = name;
            }
            setStatus("Đang xem " + selectedId + " / " + name);
        } catch (error) { setStatus(error.message,true); }
    }
    async function disposeCurrent() {
        const oldManager = manager; manager = null; character = null;
        if (oldManager) oldManager.dispose();
    }
    async function loadEffect(id) {
        const item = (window.SPINE_EFFECT_CATALOG || []).find(entry => entry.id === id);
        if (!item) return;
        const currentGeneration = ++generation;
        selectedId = id; selectedAssetLabel.textContent = id;
        renderEffectList(); setControls(false); animationList.innerHTML = "";
        animationSelect.innerHTML = '<option value="">Đang tải...</option>';
        animationSelect.disabled = true; addButton.disabled = true;
        setStatus("Đang tải " + id + "...");
        await disposeCurrent();
        let loadingManager = null;
        try {
            const [jsonResponse,atlasResponse] = await Promise.all([
                fetch(ROOT + id + ".json"),fetch(ROOT + id + ".atlas")
            ]);
            if (!jsonResponse.ok) throw new Error("Không tải được " + id + ".json (HTTP " + jsonResponse.status + ")");
            if (!atlasResponse.ok) throw new Error("Không tải được " + id + ".atlas (HTTP " + atlasResponse.status + ")");
            const jsonData = await jsonResponse.json(), atlasText = await atlasResponse.text();
            const pageNames = parseAtlasPages(atlasText);
            if (!pageNames.length) throw new Error("Không tìm thấy texture page trong atlas.");
            const animationMap = jsonData.animations || {};
            renderAnimationButtons(animationMap);
            if (currentGeneration !== generation) return;
            loadingManager = new SpineManager(canvas); loadingManager.initialize();
            const textures = pageNames.map(name => ROOT + name);
            const loadingCharacter = await loadingManager.createCharacter({
                code:id,skeleton:ROOT+id+".json",atlas:ROOT+id+".atlas",textures,
                scale:0.2,offsetX:0,offsetY:0
            });
            if (currentGeneration !== generation) { loadingManager.dispose(); return; }
            manager = loadingManager; character = loadingCharacter; loadingManager = null;
            character.setScale(Number(scaleInput.value) || 0.2);
            character.animationController.setSpeed(Number(speedInput.value) || 1);
            manager.start(); setControls(true);
            const firstAnimation = Object.keys(animationMap)[0];
            if (firstAnimation) playAnimation(firstAnimation);
            const events = Object.values(animationMap).reduce((total,animation) =>
                total + (Array.isArray(animation.events) ? animation.events.length : 0),0);
            setStatus(item.id + " · " + Object.keys(animationMap).length +
                " animation · " + events + " event marker · " + pageNames.length + " texture page");
        } catch (error) {
            console.error("[SpineEffectLab]",error); loadingManager?.dispose();
            if (currentGeneration === generation) {
                await disposeCurrent(); setControls(false);
                setStatus(error.message || "Không thể tải effect.",true);
            }
        }
        addButton.disabled = !(selectedHero && selectedSkill && selectedId && selectedAnimation);
    }

    function getCurrentSkillMapping() {
        const catalog = heroDetail?.spineSkillAnimations || {};
        const definitions = catalog._SKILL_EFFECTS || {};
        return definitions[selectedSkill?.skillCode] || {};
    }
    function normalizePhase(value) { return Array.isArray(value) ? value : value ? [value] : []; }
    function renderMappingRows(container, phases, removable) {
        container.innerHTML = "";
        const rows = [];
        Object.entries(phases || {}).forEach(([phase,value]) =>
            normalizePhase(value).forEach((entry,index) => rows.push({phase,entry,index})));
        if (!rows.length) {
            container.textContent = removable ? "Chưa có mapping nháp cho chiêu này." :
                "Chưa có Spine VFX mapping được lưu trong DB cho chiêu này.";
            return;
        }
        rows.forEach(({phase,entry,index}) => {
            const row = document.createElement("div"); row.className = "mapping-row";
            const strong = document.createElement("strong");
            strong.textContent = phase.toUpperCase() + " · " + (entry.file || "Chưa có file");
            const detail = document.createElement("span");
            detail.textContent = [entry.animation || "",entry.anchor || "",entry.trigger || "",
                ...(entry.impactEvents || (entry.impactEvent ? [entry.impactEvent] : []))].filter(Boolean).join(" · ");
            row.append(strong,detail);
            if (removable) {
                const remove = document.createElement("button"); remove.type = "button";
                remove.textContent = "Xóa"; remove.dataset.removeDraft = "true";
                remove.dataset.phase = phase; remove.dataset.index = String(index);
                row.appendChild(remove);
            }
            container.appendChild(row);
        });
    }
    function renderSkillMapping() {
        if (!selectedHero || !selectedSkill) return;
        const effects = selectedSkill.effects || [];
        skillInfo.innerHTML = "";
        const title = document.createElement("strong");
        title.textContent = selectedHero.heroName + " · " + selectedSkill.skillName +
            " · " + selectedSkill.skillCode;
        const desc = document.createElement("p"); desc.textContent = selectedSkill.description || "Không có mô tả.";
        const gameplay = document.createElement("div"); gameplay.className = "gameplay-effect-list";
        if (!effects.length) gameplay.textContent = "DB hiện chưa có effect gameplay được cấu hình.";
        effects.forEach(effect => {
            const line = document.createElement("span");
            line.textContent = "#" + effect.effectOrder + " " + effect.effectType + " → " +
                effect.targetType + " · x" + effect.multiplier + " · " +
                (effect.configJson || "{}");
            gameplay.appendChild(line);
        });
        skillInfo.append(title,desc,gameplay);
        renderMappingRows(currentMappings,getCurrentSkillMapping(),false);
        const heroDraft = draft[selectedHero.heroCode] || {};
        renderMappingRows(draftMappings,heroDraft[selectedSkill.skillCode] || {},true);
        addButton.disabled = !selectedId || !selectedAnimation;
    }
    function populateSkills() {
        skillSelect.innerHTML = "";
        (heroDetail?.skills || []).forEach(skill => {
            const option = document.createElement("option");
            option.value = skill.skillCode;
            option.textContent = String(skill.skillOrder).padStart(2,"0") + " · " +
                skill.skillName + " [" + skill.skillType + "]";
            skillSelect.appendChild(option);
        });
        skillSelect.disabled = !(heroDetail?.skills || []).length;
        selectedSkill = heroDetail?.skills?.[0] || null;
        if (selectedSkill) skillSelect.value = selectedSkill.skillCode;
        renderSkillMapping();
    }
    async function loadHero(code) {
        selectedHero = heroes.find(hero => hero.heroCode === code) || null;
        selectedSkill = null; heroDetail = null;
        skillSelect.innerHTML = '<option value="">Đang tải kỹ năng...</option>';
        skillSelect.disabled = true; skillInfo.textContent = "Đang tải dữ liệu kỹ năng...";
        currentMappings.textContent = "Đang tải mapping...";
        try {
            const response = await fetch("/admin/api/heroes/" + encodeURIComponent(code),{
                credentials:"same-origin",headers:{"Accept":"application/json"}
            });
            const detail = await response.json();
            if (!response.ok) throw new Error(detail.detail || detail.message || "Không tải được dữ liệu hero.");
            heroDetail = detail;
            if (!heroDetail.spineSkillAnimations) heroDetail.spineSkillAnimations = {};
            populateSkills();
            progress.textContent = heroes.length + " hero · " +
                heroes.reduce((n,h)=>n+(h.skillsCount||5),0) + " skill slots · đang xem " + code;
        } catch (error) {
            skillInfo.textContent = error.message;
            progress.textContent = "Không tải được dữ liệu hero";
        }
    }
    async function initializeMapping() {
        try {
            const response = await fetch("/admin/api/heroes",{credentials:"same-origin",headers:{"Accept":"application/json"}});
            const data = await response.json();
            if (!response.ok) throw new Error(data.detail || data.message || "Không tải được danh sách hero.");
            heroes = Array.isArray(data) ? data : [];
            heroSelect.innerHTML = "";
            heroes.forEach(hero => {
                const option = document.createElement("option");
                option.value = hero.heroCode;
                option.textContent = hero.heroName + " · " + hero.heroCode;
                heroSelect.appendChild(option);
            });
            if (!heroes.length) throw new Error("DB chưa có hero nào.");
            progress.textContent = heroes.length + " hero đã nạp · 5 kỹ năng/hero theo catalog";
            await loadHero(heroes[0].heroCode);
        } catch (error) {
            progress.textContent = error.message;
            skillInfo.textContent = "Không thể nạp danh sách hero. Hãy đăng nhập tài khoản ADMIN rồi tải lại trang.";
            heroSelect.innerHTML = '<option value="">Không tải được danh sách hero</option>';
        }
    }

    function addDraftMapping() {
        if (!selectedHero || !selectedSkill || !selectedId || !selectedAnimation) return;
        const heroCode = selectedHero.heroCode, skillCode = selectedSkill.skillCode;
        draft[heroCode] ||= {}; draft[heroCode][skillCode] ||= {};
        const phase = phaseSelect.value;
        draft[heroCode][skillCode][phase] ||= [];
        const events = eventsInput.value.split(",").map(x=>x.trim()).filter(Boolean);
        draft[heroCode][skillCode][phase].push({
            file:selectedId,animation:animationSelect.value,anchor:anchorSelect.value,
            ...(triggerSelect.value ? {trigger:triggerSelect.value} : {}),
            ...(events.length ? {impactEvents:events} : {})
        });
        persistDraft(); renderSkillMapping();
    }
    function exportDraft() {
        const payload = {
            version:1,source:"Spine Effect Lab draft",createdAt:new Date().toISOString(),
            note:"Mapping nháp do người dùng xác nhận bằng quan sát preview; cần review trước khi đưa vào runtime.",
            heroes:draft
        };
        const blob = new Blob([JSON.stringify(payload,null,2)],{type:"application/json"});
        const url = URL.createObjectURL(blob), link = document.createElement("a");
        link.href=url; link.download="spine-effect-mapping-draft.json";
        document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
    }

    list.addEventListener("click",event=>{
        const button=event.target.closest("[data-effect-id]");
        if(button) loadEffect(button.dataset.effectId);
    });
    animationList.addEventListener("click",event=>{
        const button=event.target.closest("[data-animation]");
        if(button) { playAnimation(button.dataset.animation); animationSelect.value=button.dataset.animation; }
    });
    draftMappings.addEventListener("click",event=>{
        const button=event.target.closest("[data-remove-draft]");
        if(!button || !selectedHero || !selectedSkill) return;
        const phase=button.dataset.phase,index=Number(button.dataset.index);
        const entries=draft[selectedHero.heroCode]?.[selectedSkill.skillCode]?.[phase] || [];
        entries.splice(index,1);
        if(!entries.length) delete draft[selectedHero.heroCode][selectedSkill.skillCode][phase];
        if(!Object.keys(draft[selectedHero.heroCode][selectedSkill.skillCode]).length)
            delete draft[selectedHero.heroCode][selectedSkill.skillCode];
        if(!Object.keys(draft[selectedHero.heroCode]).length) delete draft[selectedHero.heroCode];
        persistDraft(); renderSkillMapping();
    });
    search.addEventListener("input",renderEffectList);
    groupSelect.addEventListener("change",renderEffectList);
    heroSelect.addEventListener("change",()=>loadHero(heroSelect.value));
    skillSelect.addEventListener("change",()=>{
        selectedSkill=(heroDetail?.skills||[]).find(skill=>skill.skillCode===skillSelect.value)||null;
        renderSkillMapping();
    });
    addButton.addEventListener("click",addDraftMapping);
    exportButton.addEventListener("click",exportDraft);
    pauseButton.addEventListener("click",()=>character?.animationController.pause());
    resumeButton.addEventListener("click",()=>character?.animationController.resume());
    restartButton.addEventListener("click",()=>character?.animationController.restart());
    centerButton.addEventListener("click",()=>character?.center());
    speedInput.addEventListener("input",()=>{
        const speed=Number(speedInput.value);speedValue.textContent=speed.toFixed(1)+"x";
        character?.animationController.setSpeed(speed);
    });
    scaleInput.addEventListener("input",()=>{
        const scale=Number(scaleInput.value);scaleValue.textContent=scale.toFixed(2);
        character?.setScale(scale);
    });
    loopInput.addEventListener("change",()=>{if(selectedAnimation)playAnimation(selectedAnimation);});
    debugInput.addEventListener("change",()=>manager?.setDebug(debugInput.checked));
    window.addEventListener("resize",()=>manager?.resize());
    window.addEventListener("beforeunload",()=>manager?.dispose());

    renderGroupOptions(); renderEffectList(); setControls(false);
    setStatus("Đã nạp " + (window.SPINE_EFFECT_CATALOG||[]).length + " effect asset.");
    persistDraft(); initializeMapping();
});