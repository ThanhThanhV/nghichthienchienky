class SpineManager {

    constructor(canvas) {
        this.canvas = canvas;

        this.context = null;
        this.renderer = null;
        this.assetManager = null;
        this.timeKeeper = null;

        this.characters = [];

        this.textureUrls = new Map();
        this.loadingTextures = new Map();

        this.zoom = 1;
        this.debug = false;
        this.initialized = false;

        this.running = false;
        this.animationFrame = null;
        this.lastTime = 0;

        this.characters = [];
    }

    /**
     * Khởi tạo WebGL context và renderer Spine.
     */
    initialize() {
        if (!window.spine || !spine.webgl) {
            throw new Error("Không tìm thấy Spine WebGL runtime.");
        }

        if (this.initialized) {
            return;
        }

        this.context = new spine.webgl.ManagedWebGLRenderingContext(
            this.canvas,
            { alpha: false }
        );

        this.renderer = new spine.webgl.SceneRenderer(
            this.canvas,
            this.context
        );

        this.timeKeeper = new spine.TimeKeeper();

        this.assetManager = new spine.webgl.AssetManager(
            this.context,
            ""
        );

        this.resize();

        this.initialized = true;
    }

    /**
     * Chuẩn hóa đường dẫn texture để đối chiếu với atlas.
     *
     * @param {string} path đường dẫn texture
     * @returns {string} đường dẫn đã chuẩn hóa
     */
    normalizePath(path) {
        return String(path || "")
            .replace(/\\/g, "/")
            .replace(/^\/+/, "")
            .toLowerCase();
    }

    /**
     * Tải texture nếu chưa được tải trước đó.
     *
     * @param {string[]} paths danh sách đường dẫn PNG
     */
    async loadTextures(paths) {
        this.initialize();

        const newPaths = paths.filter(path => !this.textureUrls.has(path));

        if (newPaths.length === 0) {
            return;
        }

        for (const path of newPaths) {
            this.textureUrls.set(
                this.normalizePath(path),
                path
            );

            this.assetManager.loadTexture(path);
        }

        await this.waitForAssets();
    }

    /**
     * Chờ AssetManager hoàn thành việc tải texture.
     */
    waitForAssets() {
        return new Promise((resolve, reject) => {
            const check = () => {
                if (this.assetManager.hasErrors()) {
                    reject(
                        new Error("Không tải được texture Spine.")
                    );
                    return;
                }

                if (this.assetManager.isLoadingComplete()) {
                    resolve();
                    return;
                }

                requestAnimationFrame(check);
            };

            check();
        });
    }

    /**
     * Lấy texture tương ứng với tên page trong atlas.
     *
     * @param {string} atlasPath tên texture trong atlas
     * @returns {*} texture của Spine AssetManager
     */
    getTexture(atlasPath) {
        const normalized = this.normalizePath(atlasPath);

        let textureUrl = this.textureUrls.get(normalized);

        if (!textureUrl) {
            const fileName = normalized.split("/").pop();

            const matched = [...this.textureUrls.entries()].find(
                ([path]) => path.split("/").pop() === fileName
            );

            if (matched) {
                textureUrl = matched[1];
            }
        }

        if (!textureUrl) {
            throw new Error(
                "Không tìm thấy texture trong atlas: " + atlasPath
            );
        }

        return this.assetManager.get(textureUrl);
    }

    /**
     * Tạo và tải nhân vật Spine, sau đó đăng ký vào renderer.
     *
     * @param {Object} config cấu hình nhân vật
     * @returns {Promise<SpineCharacter>} nhân vật đã tải
     */
    async createCharacter(config) {
        this.initialize();

        const character = new SpineCharacter(config, this);

        await character.load();

        this.addCharacter(character);

        return character;
    }

    /**
     * Thay đổi kích thước canvas theo kích thước vùng hiển thị.
     */
    resize() {
        const rect = this.canvas.getBoundingClientRect();
        const dpr = window.devicePixelRatio || 1;

        this.canvas.width = Math.floor(rect.width * dpr);
        this.canvas.height = Math.floor(rect.height * dpr);

        if (this.renderer) {
            this.renderer.resize(spine.webgl.ResizeMode.Fit);
        }

        this.characters.forEach(character => character.center());
    }

    /**
     * Thay đổi zoom hiển thị.
     *
     * @param {number} zoom hệ số phóng to
     */
    setZoom(zoom) {
        this.zoom = Math.max(0.2, Math.min(2, Number(zoom) || 1));

        this.canvas.style.transform = `scale(${this.zoom})`;
        this.canvas.style.transformOrigin = "center center";
    }

    /**
     * Bật hoặc tắt hiển thị debug skeleton.
     *
     * @param {boolean} enabled trạng thái debug
     */
    setDebug(enabled) {
        this.debug = Boolean(enabled);
    }

    /**
     * Bắt đầu vòng lặp render.
     */
    start() {
        if (this.running) {
            return;
        }

        this.running = true;
        this.lastTime = 0;

        const render = () => {
            if (!this.running) {
                return;
            }

            this.animationFrame = requestAnimationFrame(render);

            if (!this.renderer || !this.timeKeeper) {
                return;
            }

            this.timeKeeper.update();

            const delta = this.timeKeeper.delta;

            this.renderer.begin();

            this.characters.forEach(character => {
                character.update(delta);
                character.draw(this.debug);
            });

            this.renderer.end();
        };

        render();
    }

    /**
     * Dừng vòng lặp render.
     */
    stop() {
        this.running = false;

        if (this.animationFrame) {
            cancelAnimationFrame(this.animationFrame);
            this.animationFrame = null;
        }
    }

    /**
     * Giải phóng các nhân vật và dừng renderer.
     */
    dispose() {
        this.stop();

        this.characters.forEach(character => character.dispose());

        this.characters = [];
        this.initialized = false;
    }

    /**
     * Đăng ký nhân vật vào danh sách render chung.
     *
     * @param {SpineCharacter} character nhân vật cần thêm
     */
    addCharacter(character) {
        if (!character || !character.loaded) {
            throw new Error("Nhân vật Spine chưa sẵn sàng.");
        }

        if (this.characters.includes(character)) {
            return;
        }

        this.characters.push(character);
    }

    /**
     * Xóa nhân vật khỏi danh sách render chung.
     *
     * @param {SpineCharacter} character nhân vật cần xóa
     */
    removeCharacter(character) {
        const index = this.characters.indexOf(character);

        if (index < 0) {
            return;
        }

        this.characters.splice(index, 1);
    }
}