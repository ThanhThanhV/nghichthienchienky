/**
 * Skeleton Spine độc lập dùng cho hiệu ứng kỹ năng.
 * Không đăng ký vào SpineFormation và không chiếm slot nhân vật.
 */
class SpineEffect extends SpineCharacter {

    constructor(config, manager) {
        super(config, manager);
        this.layer = String(config.layer || "BACK").toUpperCase();
        this.completionListener = null;
        this.completed = false;
        this.onRemoved = null;
    }

    async load() {
        await super.load();

        const animation = this.config.animation ||
            this.getAnimationNames()[0];

        if (!animation || !this.getAnimationNames().includes(animation)) {
            throw new Error("Không tìm thấy animation hiệu ứng Spine: " + animation);
        }

        this.completionListener = {
            complete: entry => {
                if (
                    this.completed ||
                    entry.animation?.name !== animation ||
                    !this.loaded
                ) {
                    return;
                }

                this.completed = true;
                this.manager.removeEffect(this);
            }
        };

        this.animationState.addListener(this.completionListener);
        this.animationController.setSpeed(this.config.speed || 1);
        this.animationController.play(animation, false, false);

        return this;
    }

    dispose() {
        if (this.animationState && this.completionListener) {
            this.animationState.removeListener(this.completionListener);
        }

        this.completionListener = null;
        super.dispose();
    }
}
