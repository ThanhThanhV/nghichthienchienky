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
    }

    /**
     * Tải JSON, atlas, texture và khởi tạo skeleton nhân vật.
     *
     * @returns {Promise<SpineCharacter>} nhân vật đã tải
     */
    async load() {
        const [jsonResponse, atlasResponse] = await Promise.all([
            fetch(this.config.skeleton),
            fetch(this.config.atlas)
        ]);

        if (!jsonResponse.ok) {
            throw new Error("Không tải được skeleton JSON.");
        }

        if (!atlasResponse.ok) {
            throw new Error("Không tải được atlas.");
        }

        const jsonData = await jsonResponse.json();
        const atlasText = await atlasResponse.text();

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

        this.skeleton.x = -(
            offset.x + size.x / 2
        ) + (this.config.offsetX || 0);

        this.skeleton.y = -(
            offset.y + size.y / 2
        ) + (this.config.offsetY || 0);

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

        this.skeleton.x = x;
        this.skeleton.y = y;

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

        this.skeleton.scaleX = scale;
        this.skeleton.scaleY = scale;

        this.skeleton.updateWorldTransform();
    }
}

