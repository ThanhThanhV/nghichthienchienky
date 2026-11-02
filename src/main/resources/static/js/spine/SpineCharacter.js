class SpineCharacter {

    constructor(config, manager) {
        this.config = config;
        this.manager = manager;

        this.textureAtlas = null;
        this.skeletonData = null;
        this.skeleton = null;
        this.animationState = null;

        this.loaded = false;
        this.animationController = null;

        this.centerOffsetX = 0;
        this.centerOffsetY = 0;
        this.positionX = 0;
        this.positionY = 0;
        this.hasPosition = false;
    }

    /**
     * Tải JSON, atlas, texture và khởi tạo skeleton nhân vật.
     *
     * @returns {Promise<SpineCharacter>} nhân vật đã tải
     */
    async load() {
        const { jsonData, atlasText } =
            await this.manager.loadSpineAssetData(this.config);

        if (!jsonData.skeleton || !jsonData.bones) {
            throw new Error("Skeleton JSON không hợp lệ.");
        }

        await this.manager.loadTextures(this.config.textures);

        this.textureAtlas = new spine.TextureAtlas(
            atlasText,
            atlasPath => this.manager.getTexture(atlasPath)
        );

        const atlasLoader = new spine.AtlasAttachmentLoader(
            this.textureAtlas
        );

        const skeletonJson = new spine.SkeletonJson(atlasLoader);

        skeletonJson.scale = this.config.scale || 1;

        this.skeletonData = skeletonJson.readSkeletonData(jsonData);

        this.skeleton = new spine.Skeleton(this.skeletonData);

        const stateData = new spine.AnimationStateData(
            this.skeleton.data
        );

        this.animationState = new spine.AnimationState(stateData);

        this.skeleton.setToSetupPose();
        this.skeleton.updateWorldTransform();

        this.animationController = new SpineAnimationController(this);

        this.loaded = true;

        this.center();

        return this;
    }

    /**
     * Căn giữa skeleton và áp dụng độ lệch vị trí cấu hình.
     */
    center() {
        if (!this.skeleton) {
            return;
        }

        const offset = new spine.Vector2();
        const size = new spine.Vector2();

        this.skeleton.setToSetupPose();
        this.skeleton.updateWorldTransform();

        this.skeleton.getBounds(offset, size, []);

        // Spine character placement is anchored at the feet, not at the
        // visual bounding-box center. This keeps the character standing on
        // the same baseline as its slot/HP bar in battle and preview screens.
        this.centerOffsetX = -(
            offset.x + size.x / 2
        ) + (this.config.offsetX || 0);

        this.centerOffsetY = -offset.y + (this.config.offsetY || 0);

        this.applyPosition();
    }

    /**
     * Áp dụng tọa độ slot cộng độ lệch căn giữa, có tính đến display scale.
     */
    applyPosition() {
        if (!this.skeleton) {
            return;
        }

        const scaleX = Number(this.skeleton.scaleX) || 1;
        const scaleY = Number(this.skeleton.scaleY) || 1;
        const baseX = this.hasPosition ? this.positionX : 0;
        const baseY = this.hasPosition ? this.positionY : 0;

        this.skeleton.x = baseX + this.centerOffsetX * scaleX;
        this.skeleton.y = baseY + this.centerOffsetY * scaleY;
        this.skeleton.updateWorldTransform();
    }

    /**
     * Cập nhật animation và transform của nhân vật.
     *
     * @param {number} delta thời gian giữa hai frame
     */
    update(delta) {
        if (!this.loaded) {
            return;
        }

        this.animationController.update(delta);

        // A one-shot SpineEffect may dispose itself from an animation-complete
        // listener during update; do not access its released state afterwards.
        if (!this.loaded || !this.animationState || !this.skeleton) {
            return;
        }

        this.animationState.apply(this.skeleton);
        this.skeleton.updateWorldTransform();
    }

    /**
     * Vẽ nhân vật lên renderer.
     *
     * @param {boolean} debug bật chế độ debug
     */
    draw(debug = false) {
        if (!this.loaded) {
            return;
        }

        this.manager.renderer.drawSkeleton(this.skeleton, false);

        if (debug) {
            this.manager.renderer.drawSkeletonDebug(this.skeleton);
        }
    }

    /**
     * Trả về danh sách animation của nhân vật.
     *
     * @returns {string[]} danh sách tên animation
     */
    getAnimationNames() {
        if (!this.skeletonData) {
            return [];
        }

        return this.skeletonData.animations.map(
            animation => animation.name
        );
    }

    /**
     * Giải phóng trạng thái của nhân vật.
     */
    dispose() {
        this.loaded = false;

        this.animationController?.dispose();
        this.animationController = null;
        this.animationState = null;
        this.skeleton = null;
        this.skeletonData = null;
        this.textureAtlas = null;
    }

    /**
     * Đặt vị trí nhân vật trong hệ tọa độ world của Spine.
     *
     * @param {number} x tọa độ ngang
     * @param {number} y tọa độ dọc
     */
    setPosition(x, y) {
        if (!this.skeleton) {
            return;
        }

        this.positionX = Number(x) || 0;
        this.positionY = Number(y) || 0;
        this.hasPosition = true;

        this.applyPosition();
    }

    /**
     * Đặt hướng nhìn của nhân vật trong chiến trường.
     *
     * @param {boolean} facingLeft true nếu nhân vật quay sang trái
     */
    setFacingLeft(facingLeft) {
        if (!this.skeleton) {
            return;
        }

        this.skeleton.flipX = Boolean(facingLeft);
        this.skeleton.updateWorldTransform();
    }

    /**
     * Thay đổi kích thước hiển thị của nhân vật.
     *
     * @param {number} scale hệ số kích thước
     */
    setScale(scale) {
        if (!this.skeleton) {
            return;
        }

        const normalizedScale = Math.max(0.01, Number(scale) || 1);
        this.skeleton.scaleX = normalizedScale;
        this.skeleton.scaleY = normalizedScale;

        this.applyPosition();
    }
}

