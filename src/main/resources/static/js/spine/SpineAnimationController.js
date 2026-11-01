class SpineAnimationController {

    constructor(character) {
        this.character = character;
        this.currentAnimation = null;
        this.paused = false;
        this.loop = true;
        this.speed = 1;
        this.returnToIdleAfterCompletion = false;

        // Spine 3.5 reports completion for non-looping tracks through this listener.
        // Only return to idle when the completed track is still the active track.
        this.stateListener = {
            complete: entry => {
                if (
                    !this.returnToIdleAfterCompletion ||
                    !this.character?.loaded ||
                    entry.animation?.name !== this.currentAnimation ||
                    this.character.animationState.getCurrent(0) !== entry
                ) {
                    return;
                }

                const idleAnimation = this.resolveIdleAnimation();
                this.returnToIdleAfterCompletion = false;

                if (idleAnimation && idleAnimation !== this.currentAnimation) {
                    this.play(idleAnimation, true, false);
                }
            }
        };

        this.character?.animationState?.addListener(this.stateListener);
    }

    /**
     * Phát animation theo tên.
     *
     * @param {string} name tên animation
     * @param {boolean} loop có lặp lại hay không
     * @param {boolean} returnToIdle tự trở về idle khi animation kết thúc
     */
    play(name, loop = this.loop, returnToIdle = !loop && !/(death|dead|die)/i.test(name)) {
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
        this.returnToIdleAfterCompletion = Boolean(returnToIdle);

        character.animationState.setAnimation(0, name, loop);
    }

    /**
     * Tìm animation chờ của skeleton theo cấu hình hoặc tên phổ biến.
     * Không chọn animation tấn công, nhận sát thương, di chuyển hoặc chết làm idle.
     */
    resolveIdleAnimation() {
        const character = this.character;
        if (!character?.skeletonData) return null;

        const names = character.skeletonData.animations.map(animation => animation.name);
        const preferred = [
            character.config?.idleAnimation,
            "holdon",
            "idle",
            "stand",
            "wait",
            "waiting"
        ].filter(Boolean);

        for (const name of preferred) {
            if (names.includes(name)) return name;
        }

        const excluded = /(attack|^att$|skill|ultimate|behit|hit|death|dead|die|move|run|hurt)/i;
        return names.find(name => !excluded.test(name)) || null;
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

        this.play(
            this.currentAnimation,
            this.loop,
            this.returnToIdleAfterCompletion
        );
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
        this.returnToIdleAfterCompletion = false;
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

    /**
     * Gỡ listener trước khi SpineCharacter giải phóng AnimationState.
     */
    dispose() {
        if (this.character?.animationState && this.stateListener) {
            this.character.animationState.removeListener(this.stateListener);
        }

        this.stateListener = null;
        this.character = null;
    }
}
