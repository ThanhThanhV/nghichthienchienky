document.addEventListener("DOMContentLoaded", async () => {
    const canvas = document.getElementById("spineCanvas");
    const statusElement = document.getElementById("spineStatus");
    const animationContainer = document.getElementById("animationButtons");

    const manager = new SpineManager(canvas);

    let character = null;

    /**
     * Hiển thị trạng thái xử lý trên giao diện.
     *
     * @param {string} message nội dung trạng thái
     */
    function setStatus(message) {
        statusElement.textContent = message;
    }

    /**
     * Bật hoặc tắt các nút điều khiển.
     *
     * @param {boolean} enabled trạng thái cho phép
     */
    function setControlsEnabled(enabled) {
        document.querySelectorAll("[data-spine-control]").forEach(element => {
            element.disabled = !enabled;
        });
    }

    /**
     * Tạo nút điều khiển tương ứng với animation thực tế của skeleton.
     *
     * @param {string[]} animationNames danh sách animation
     */
    function renderAnimationButtons(animationNames) {
        animationContainer.innerHTML = "";

        animationNames.forEach(name => {
            const button = document.createElement("button");

            button.type = "button";
            button.textContent = name;
            button.dataset.animation = name;
            button.dataset.spineControl = "";
            button.disabled = false;

            animationContainer.appendChild(button);
        });
    }

    /**
     * Phát animation và cập nhật trạng thái nút đang chọn.
     *
     * @param {string} name tên animation
     */
    function playAnimation(name) {
        if (!character) {
            return;
        }

        try {
            const loop = document.getElementById("loopCheckbox").checked;

            character.animationController.play(name, loop);

            animationContainer.querySelectorAll("[data-animation]").forEach(button => {
                button.classList.toggle(
                    "active",
                    button.dataset.animation === name
                );
            });

            setStatus("Đang phát animation: " + name);
        } catch (error) {
            setStatus(error.message);
        }
    }

    /**
     * Khởi tạo nhân vật từ cấu hình và tải asset Spine.
     */
    async function initializeCharacter() {
        try {
            setStatus("Đang khởi tạo Spine WebGL...");

            manager.initialize();

            setStatus("Đang tải skeleton và texture...");

            character = await manager.createCharacter(
                SPINE_CONFIG.character
            );

            const animationNames = character.getAnimationNames();

            if (animationNames.length === 0) {
                throw new Error("Skeleton không có animation nào.");
            }

            renderAnimationButtons(animationNames);

            manager.start();

            setControlsEnabled(true);

            playAnimation(animationNames[0]);

            setStatus(
                "Tải nhân vật thành công.\n" +
                "Character: " + SPINE_CONFIG.character.code + "\n" +
                "Animations: " + animationNames.join(", ")
            );

        } catch (error) {
            console.error("Spine initialization error:", error);

            setControlsEnabled(false);

            setStatus(
                "Không thể tải nhân vật.\n" +
                error.message
            );
        }
    }

    // Chọn animation.
    animationContainer.addEventListener("click", event => {
        const button = event.target.closest("[data-animation]");

        if (!button || !character) {
            return;
        }

        playAnimation(button.dataset.animation);
    });

    // Pause.
    document.getElementById("pauseButton").addEventListener("click", () => {
        if (!character) return;

        character.animationController.pause();

        setStatus("Đã tạm dừng animation.");
    });

    // Resume.
    document.getElementById("resumeButton").addEventListener("click", () => {
        if (!character) return;

        character.animationController.resume();

        setStatus("Đã tiếp tục animation.");
    });

    // Restart.
    document.getElementById("restartButton").addEventListener("click", () => {
        if (!character) return;

        character.animationController.restart();
    });

    // Clear animation.
    document.getElementById("clearButton").addEventListener("click", () => {
        if (!character) return;

        character.animationController.clear();

        animationContainer.querySelectorAll("[data-animation]").forEach(button => {
            button.classList.remove("active");
        });

        setStatus("Đã xóa animation track.");
    });

    // Center.
    document.getElementById("centerButton").addEventListener("click", () => {
        if (!character) return;

        character.center();
    });

    // Animation speed.
    document.getElementById("speedInput").addEventListener("input", event => {
        if (!character) return;

        const speed = Number(event.target.value);

        character.animationController.setSpeed(speed);

        document.getElementById("speedValue").textContent =
            speed.toFixed(1) + "x";
    });

    // Zoom.
    document.getElementById("zoomInput").addEventListener("input", event => {
        const zoom = Number(event.target.value);

        manager.setZoom(zoom);

        document.getElementById("zoomValue").textContent =
            zoom.toFixed(1) + "x";
    });

    // Debug.
    document.getElementById("debugCheckbox").addEventListener("change", event => {
        manager.setDebug(event.target.checked);
    });

    // Loop.
    document.getElementById("loopCheckbox").addEventListener("change", event => {
        if (!character || !character.animationController.currentAnimation) {
            return;
        }

        playAnimation(
            character.animationController.currentAnimation
        );
    });

    // Resize.
    window.addEventListener("resize", () => {
        manager.resize();
    });

    // Dừng render khi rời trang.
    window.addEventListener("beforeunload", () => {
        manager.dispose();
    });

    await initializeCharacter();
});