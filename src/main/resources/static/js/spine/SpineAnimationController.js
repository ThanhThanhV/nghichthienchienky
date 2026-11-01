class SpineAnimationController {

    constructor(character) {
        this.character = character;
        this.currentAnimation = null;
        this.paused = false;
        this.loop = true;
        this.speed = 1;
    }

    /**
     * Phát animation theo tên.
     *
     * @param {string} name tên animation
     * @param {boolean} loop có lặp lại hay không
     */
    play(name, loop = this.loop) {
        const character = this.character;

        if (!character || !character.loaded) {
            throw new Error("Nhân vật chưa được tải.");
        }

        const exists = character.skeletonData.animations.some(
            animation => animation.name === name
        );

        if (!exists) {
            throw new Error("Không tìm thấy animation: " + name);
        }

        this.currentAnimation = name;
        this.loop = loop;
        this.paused = false;

        character.animationState.setAnimation(0, name, loop);
    }

    /**
     * Tạm dừng animation hiện tại.
     */
    pause() {
        this.paused = true;
    }

    /**
     * Tiếp tục animation đang tạm dừng.
     */
    resume() {
        this.paused = false;
    }

    /**
     * Phát lại animation hiện tại từ đầu.
     */
    restart() {
        if (!this.currentAnimation) {
            return;
        }

        this.play(this.currentAnimation, this.loop);
    }

    /**
     * Xóa toàn bộ animation track và đưa nhân vật về setup pose.
     */
    clear() {
        const character = this.character;

        if (!character || !character.skeleton) {
            return;
        }

        character.animationState.clearTracks();
        character.skeleton.setToSetupPose();
        character.skeleton.updateWorldTransform();

        this.currentAnimation = null;
        this.paused = false;
    }

    /**
     * Thay đổi tốc độ phát animation.
     *
     * @param {number} speed tốc độ phát
     */
    setSpeed(speed) {
        this.speed = Math.max(0.1, Math.min(4, Number(speed) || 1));
    }

    /**
     * Cập nhật animation theo delta time của vòng lặp render.
     *
     * @param {number} delta thời gian giữa hai frame
     */
    update(delta) {
        if (this.paused || !this.character.animationState) {
            return;
        }

        this.character.animationState.update(delta * this.speed);
    }
}