document.addEventListener("DOMContentLoaded", async () => {
    const canvas = document.getElementById("battleCanvas");
    const status = document.getElementById("battleStatus");

    const manager = new SpineManager(canvas);
    const formation = new SpineFormation(manager);

    const config = SPINE_CONFIG.character;

    /**
     * Hiển thị trạng thái đội hình.
     *
     * @param {string} message nội dung
     */
    function setStatus(message) {
        status.textContent = message;
    }

    /**
     * Tạo nhân vật thử nghiệm và đưa vào đội hình.
     *
     * @param {string} team đội hình
     * @param {number} id ID nhân vật
     * @param {number} slot slot
     * @param {number} scale kích thước
     */
    async function addTestCharacter(team, id, slot, scale) {
        const character = await manager.createCharacter({
            ...config,
            code: `${config.code}-${id}`
        });

        character.setScale(scale);

        formation.addCharacter(
            team,
            id,
            character,
            slot
        );

        const animations = character.getAnimationNames();

        if (animations.length > 0) {
            character.animationController.play(
                animations[0],
                true
            );
        }

        return character;
    }

    try {
        setStatus("Đang khởi tạo WebGL...");

        manager.initialize();

        // Tạo 3 nhân vật đội người chơi.
        await addTestCharacter("PLAYER", 101, 0, 1);
        await addTestCharacter("PLAYER", 102, 1, 1);
        await addTestCharacter("PLAYER", 103, 2, 1);

        // Tạo 3 nhân vật đội đối thủ.
        await addTestCharacter("ENEMY", 201, 0, 1);
        await addTestCharacter("ENEMY", 202, 1, 1);
        await addTestCharacter("ENEMY", 203, 2, 1);

        manager.start();

        setStatus(
            "Đội hình đã tải thành công.\n" +
            "Player: " + formation.getTeamCharacters("PLAYER").length + "\n" +
            "Enemy: " + formation.getTeamCharacters("ENEMY").length
        );

        // Demo phát animation tấn công cho nhân vật.
        document.getElementById("attackDemo").addEventListener("click", () => {
            const player = formation.getTeamCharacters("PLAYER")[0];

            const animation = player.character.getAnimationNames()
                .find(name => name === "att");

            if (!animation) {
                setStatus("Nhân vật không có animation att.");
                return;
            }

            formation.playAnimation(
                "PLAYER",
                player.instanceId,
                animation,
                false
            );

            setStatus("Nhân vật " + player.instanceId + " đang tấn công.");
        });

        // Demo thay đổi khoảng cách đội hình.
        document.getElementById("spreadDemo").addEventListener("click", () => {
            formation.setLayout({
                playerX: -250,
                enemyX: 250,
                columnSpacing: 130,
                rowSpacing: 140
            });

            setStatus("Đã thay đổi khoảng cách đội hình.");
        });

    } catch (error) {
        console.error(error);
        setStatus("Lỗi khởi tạo đội hình: " + error.message);
    }

    window.addEventListener("resize", () => manager.resize());

    window.addEventListener("beforeunload", () => {
        formation.clear();
        manager.dispose();
    });
});