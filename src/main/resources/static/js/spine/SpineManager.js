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
            { alpha: true }
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

    normalizePath(path) {
        return String(path || "").replace(/\\/g, "/").replace(/^\/+/, "").toLowerCase();
    }

    async loadTextures(paths) {
        this.initialize();
        const newPaths = paths.filter(path => !this.textureUrls.has(this.normalizePath(path)));
        if (newPaths.length === 0) return;
        for (const path of newPaths) {
            this.textureUrls.set(this.normalizePath(path), path);
            this.assetManager.loadTexture(path);
        }
        await this.waitForAssets();
    }

    waitForAssets() {
        return new Promise((resolve, reject) => {
            const check = () => {
                if (this.assetManager.hasErrors()) {
                    reject(new Error("Không tải được texture Spine."));
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

    getTexture(atlasPath) {
        const normalized = this.normalizePath(atlasPath);
        let textureUrl = this.textureUrls.get(normalized);
        if (!textureUrl) {
            const fileName = normalized.split("/").pop();
            const matched = [...this.textureUrls.entries()].find(([path]) => path.split("/").pop() === fileName);
            if (matched) textureUrl = matched[1];
        }
        if (!textureUrl) throw new Error("Không tìm thấy texture trong atlas: " + atlasPath);
        return this.assetManager.get(textureUrl);
    }

    async createCharacter(config) {
        this.initialize();
        const character = new SpineCharacter(config, this);
        await character.load();
        this.addCharacter(character);
        return character;
    }

    resize() {
        const rect = this.canvas.getBoundingClientRect();
        this.canvas.width = Math.max(1, Math.floor(rect.width));
        this.canvas.height = Math.max(1, Math.floor(rect.height));

        // Expand makes Spine world coordinates match the canvas CSS-pixel size.
        if (this.renderer) {
            this.renderer.resize(spine.webgl.ResizeMode.Expand);
        }
    }

    setZoom(zoom) {
        this.zoom = Math.max(0.2, Math.min(2, Number(zoom) || 1));
        this.canvas.style.transform = `scale(${this.zoom})`;
        this.canvas.style.transformOrigin = "center center";
    }

    setDebug(enabled) { this.debug = Boolean(enabled); }

    start() {
        if (this.running) return;
        this.running = true;
        this.lastTime = 0;
        const render = () => {
            if (!this.running) return;
            this.animationFrame = requestAnimationFrame(render);
            if (!this.renderer || !this.timeKeeper) return;
            this.timeKeeper.update();
            const delta = this.timeKeeper.delta;
            const gl = this.context.gl;
            gl.clearColor(0, 0, 0, 0);
            gl.clear(gl.COLOR_BUFFER_BIT);
            this.renderer.begin();
            this.characters.forEach(character => {
                character.update(delta);
                character.draw(this.debug);
            });
            this.renderer.end();
        };
        render();
    }

    stop() {
        this.running = false;
        if (this.animationFrame) {
            cancelAnimationFrame(this.animationFrame);
            this.animationFrame = null;
        }
    }

    dispose() {
        this.stop();
        this.characters.forEach(character => character.dispose());
        this.characters = [];
        this.renderer?.dispose();
        this.renderer = null;
        this.assetManager = null;
        this.timeKeeper = null;
        this.context = null;
        this.initialized = false;
    }

    addCharacter(character) {
        if (!character || !character.loaded) throw new Error("Nhân vật Spine chưa sẵn sàng.");
        if (this.characters.includes(character)) return;
        this.characters.push(character);
    }

    removeCharacter(character) {
        const index = this.characters.indexOf(character);
        if (index < 0) return;
        this.characters.splice(index, 1);
    }
}