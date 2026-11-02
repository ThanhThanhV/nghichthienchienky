/**
 * Skeleton Spine độc lập dùng cho hiệu ứng kỹ năng.
 * Không đăng ký vào SpineFormation và không chiếm slot nhân vật.
 *
 * Exposes two timing milestones:
 * - impactPromise: resolves at a named Spine event or configured animation time.
 * - completionPromise: resolves when the one-shot animation completes.
 */
class SpineEffect extends SpineCharacter {

    constructor(config, manager) {
        super(config, manager);
        this.layer = String(config.layer || "BACK").toUpperCase();
        this.completionListener = null;
        this.completed = false;
        this.onRemoved = null;
        this.elapsedSeconds = 0;
        this.impactResolved = false;
        this.impactAtSeconds = null;

        this.impactPromise = new Promise(resolve => {
            this.resolveImpact = resolve;
        });
        this.completionPromise = new Promise(resolve => {
            this.resolveCompletion = resolve;
        });
    }

    async load() {
        await super.load();

        const animation = this.config.animation ||
            this.getAnimationNames()[0];

        if (!animation || !this.getAnimationNames().includes(animation)) {
            throw new Error("Không tìm thấy animation hiệu ứng Spine: " + animation);
        }

        const animationData = this.skeletonData.animations.find(
            item => item.name === animation
        );
        const durationSeconds = Math.max(0, Number(animationData?.duration) || 0);
        const configuredImpact = Number(this.config.impactAtSeconds);
        const configuredFallback = Number(this.config.impactFallbackAtSeconds);
        const impactRatio = Math.max(0, Math.min(1,
            Number(this.config.impactRatio ?? 0.55)
        ));
        const impactEventName = String(this.config.impactEvent || "").trim();
        const hasImpactEvent = Boolean(
            impactEventName &&
            (animationData?.events || []).some(event => event.name === impactEventName)
        );

        if (impactEventName && !hasImpactEvent) {
            console.warn(
                "[SpineEffect] Không tìm thấy impactEvent '" + impactEventName +
                "' trong animation '" + animation + "'. Sử dụng impactRatio."
            );
        }

        // Chỉ chờ Spine event nếu event thực sự tồn tại trong animation.
        // Nếu config sai tên hoặc asset không khai báo event, tự fallback theo thời lượng.
        this.impactAtSeconds = Number.isFinite(configuredFallback)
            ? Math.max(0, configuredFallback)
            : hasImpactEvent
                ? null
                : Number.isFinite(configuredImpact)
                    ? Math.max(0, configuredImpact)
                    : durationSeconds * impactRatio;

        this.completionListener = {
            event: (entry, event) => {
                if (
                    this.config.impactEvent &&
                    event?.data?.name === this.config.impactEvent
                ) {
                    this.finishImpact();
                }
            },
            complete: entry => {
                if (
                    this.completed ||
                    entry.animation?.name !== animation ||
                    !this.loaded
                ) {
                    return;
                }

                this.completed = true;
                this.finishImpact();
                this.resolveCompletion();
                this.manager.removeEffect(this);
            }
        };

        this.animationState.addListener(this.completionListener);
        this.animationController.setSpeed(this.config.speed || 1);
        this.animationController.play(animation, false, false);

        // Animation cực ngắn hoặc thiếu duration vẫn phải giải phóng đúng vòng đời.
        if (this.impactAtSeconds === 0) {
            this.finishImpact();
        }

        return this;
    }

    finishImpact() {
        if (this.impactResolved) {
            return;
        }

        this.impactResolved = true;
        this.resolveImpact();
    }

    update(delta) {
        super.update(delta);

        if (
            !this.impactResolved &&
            this.impactAtSeconds != null &&
            this.loaded
        ) {
            this.elapsedSeconds += Math.max(0, Number(delta) || 0) *
                (Number(this.animationController?.speed) || 1);

            if (this.elapsedSeconds >= this.impactAtSeconds) {
                this.finishImpact();
            }
        }
    }

    dispose() {
        this.finishImpact();
        this.resolveCompletion();

        if (this.animationState && this.completionListener) {
            this.animationState.removeListener(this.completionListener);
        }

        this.completionListener = null;
        super.dispose();
    }
}
