class SpineFormation {

    constructor(manager) {
        this.manager = manager;

        this.teams = {
            PLAYER: new Map(),
            ENEMY: new Map()
        };

        this.slotPositions = {
            PLAYER: [
                { row: 0, col: 0 },
                { row: 0, col: 1 },
                { row: 0, col: 2 },
                { row: 1, col: 0 },
                { row: 1, col: 1 },
                { row: 1, col: 2 },
                { row: 2, col: 0 },
                { row: 2, col: 1 },
                { row: 2, col: 2 }
            ],

            ENEMY: [
                { row: 0, col: 2 },
                { row: 0, col: 1 },
                { row: 0, col: 0 },
                { row: 1, col: 2 },
                { row: 1, col: 1 },
                { row: 1, col: 0 },
                { row: 2, col: 2 },
                { row: 2, col: 1 },
                { row: 2, col: 0 }
            ]
        };

        this.maxCharactersPerTeam = 5;

        this.layout = {
            playerX: -180,
            enemyX: 180,
            rowSpacing: 110,
            columnSpacing: 100,
            verticalOffset: 0,
            playerYOffset: 0,
            enemyYOffset: 0
        };
    }

    /**
     * Thêm nhân vật vào đội hình và gán vào slot.
     *
     * @param {string} team PLAYER hoặc ENEMY
     * @param {number|string} instanceId ID nhân vật
     * @param {SpineCharacter} character nhân vật Spine
     * @param {number} slotIndex vị trí slot từ 0 đến 8
     */
    addCharacter(team, instanceId, character, slotIndex) {
        this.validateTeam(team);

        if (!character || !character.loaded) {
            throw new Error("Nhân vật Spine chưa được tải.");
        }

        const teamCharacters = this.teams[team];

        if (teamCharacters.size >= this.maxCharactersPerTeam) {
            throw new Error("Đội hình đã đạt giới hạn 5 nhân vật.");
        }

        if (teamCharacters.has(String(instanceId))) {
            throw new Error("Nhân vật đã tồn tại trong đội hình.");
        }

        if (this.findCharacter(instanceId)) {
            throw new Error("Nhân vật đã được sử dụng ở đội hình khác.");
        }

        if (!Number.isInteger(slotIndex) || slotIndex < 0 || slotIndex > 8) {
            throw new Error("Slot phải nằm trong khoảng 0 đến 8.");
        }

        if (this.isSlotOccupied(team, slotIndex)) {
            throw new Error("Slot này đã có nhân vật.");
        }

        teamCharacters.set(String(instanceId), {
            instanceId: String(instanceId),
            character,
            slotIndex,
            team
        });

        this.manager.addCharacter(character);

        this.updatePositions();

        return character;
    }

    /**
     * Xóa nhân vật khỏi đội hình.
     *
     * @param {string} team PLAYER hoặc ENEMY
     * @param {number|string} instanceId ID nhân vật
     */
    removeCharacter(team, instanceId) {
        this.validateTeam(team);

        const key = String(instanceId);
        const entry = this.teams[team].get(key);

        if (!entry) {
            return false;
        }

        this.teams[team].delete(key);

        this.manager.removeCharacter(entry.character);

        return true;
    }

    /**
     * Thay đổi slot của một nhân vật.
     *
     * @param {string} team PLAYER hoặc ENEMY
     * @param {number|string} instanceId ID nhân vật
     * @param {number} newSlotIndex slot mới
     */
    moveCharacter(team, instanceId, newSlotIndex) {
        this.validateTeam(team);

        const entry = this.teams[team].get(String(instanceId));

        if (!entry) {
            throw new Error("Không tìm thấy nhân vật trong đội hình.");
        }

        if (!Number.isInteger(newSlotIndex) || newSlotIndex < 0 || newSlotIndex > 8) {
            throw new Error("Slot không hợp lệ.");
        }

        if (this.isSlotOccupied(team, newSlotIndex)) {
            throw new Error("Slot mới đã có nhân vật.");
        }

        entry.slotIndex = newSlotIndex;

        this.updatePositions();
    }

    /**
     * Kiểm tra slot đã có nhân vật hay chưa.
     *
     * @param {string} team đội hình
     * @param {number} slotIndex slot cần kiểm tra
     * @returns {boolean} trạng thái slot
     */
    isSlotOccupied(team, slotIndex) {
        return [...this.teams[team].values()].some(
            entry => entry.slotIndex === slotIndex
        );
    }

    /**
     * Tìm nhân vật theo instance ID trong cả hai đội.
     *
     * @param {number|string} instanceId ID nhân vật
     * @returns {Object|null} dữ liệu nhân vật
     */
    findCharacter(instanceId) {
        const key = String(instanceId);

        return this.teams.PLAYER.get(key)
            || this.teams.ENEMY.get(key)
            || null;
    }

    /**
     * Lấy nhân vật trong đội hình.
     *
     * @param {string} team đội hình
     * @param {number|string} instanceId ID nhân vật
     * @returns {SpineCharacter|null} nhân vật
     */
    getCharacter(team, instanceId) {
        this.validateTeam(team);

        return this.teams[team].get(String(instanceId))?.character || null;
    }

    /**
     * Phát animation cho một nhân vật.
     *
     * @param {string} team đội hình
     * @param {number|string} instanceId ID nhân vật
     * @param {string} animationName tên animation
     * @param {boolean} loop có lặp hay không
     */
    playAnimation(team, instanceId, animationName, loop = false) {
        const character = this.getCharacter(team, instanceId);

        if (!character) {
            throw new Error("Không tìm thấy nhân vật cần phát animation.");
        }

        character.animationController.play(animationName, loop);
    }

    /**
     * Cập nhật vị trí tất cả nhân vật theo slot.
     */
    updatePositions() {
        for (const team of ["PLAYER", "ENEMY"]) {
            for (const entry of this.teams[team].values()) {
                const position = this.calculatePosition(
                    team,
                    entry.slotIndex
                );

                entry.character.setPosition(
                    position.x,
                    position.y
                );
            }
        }
    }

    /**
     * Tính tọa độ world cho nhân vật theo đội và slot.
     *
     * @param {string} team đội hình
     * @param {number} slotIndex slot
     * @returns {{x:number,y:number}} tọa độ
     */
    calculatePosition(team, slotIndex) {
        const slot = this.slotPositions[team][slotIndex];

        const baseX = team === "PLAYER"
            ? this.layout.playerX
            : this.layout.enemyX;

        const direction = team === "PLAYER" ? 1 : -1;

        const teamYOffset = team === "PLAYER"
            ? this.layout.playerYOffset
            : this.layout.enemyYOffset;

        return {
            x: baseX + direction * slot.col * this.layout.columnSpacing,
            y: teamYOffset + this.layout.verticalOffset
                - slot.row * this.layout.rowSpacing
                + this.layout.rowSpacing
        };
    }

    /**
     * Thay đổi khoảng cách giữa các nhân vật.
     *
     * @param {Object} options cấu hình layout
     */
    setLayout(options = {}) {
        this.layout = {
            ...this.layout,
            ...options
        };

        this.updatePositions();
    }

    /**
     * Kiểm tra tên đội hình hợp lệ.
     *
     * @param {string} team tên đội
     */
    validateTeam(team) {
        if (!["PLAYER", "ENEMY"].includes(team)) {
            throw new Error("Team chỉ nhận PLAYER hoặc ENEMY.");
        }
    }

    /**
     * Lấy danh sách nhân vật của một đội.
     *
     * @param {string} team tên đội
     * @returns {Object[]} danh sách nhân vật
     */
    getTeamCharacters(team) {
        this.validateTeam(team);

        return [...this.teams[team].values()];
    }

    /**
     * Xóa toàn bộ nhân vật trong cả hai đội.
     */
    clear() {
        for (const team of ["PLAYER", "ENEMY"]) {
            for (const entry of this.teams[team].values()) {
                this.manager.removeCharacter(entry.character);
            }

            this.teams[team].clear();
        }
    }
}