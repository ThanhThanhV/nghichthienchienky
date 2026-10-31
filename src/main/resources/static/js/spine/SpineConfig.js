window.SPINE_CONFIG = {
    runtimeUrl: "/lib/spine/spine-runtime-3.5/spine-ts/build/spine-webgl.js",

    character: {
        code: "test-hero",
        skeleton: "/assets/spine/heroes/test-hero/sanim_415141_1.json",
        atlas: "/assets/spine/heroes/test-hero/sanim_415141_1.atlas",
        textures: [
            "/assets/spine/heroes/test-hero/sanim_415141_1.png",
            "/assets/spine/heroes/test-hero/sanim_415141_12.png"
        ],

        scale: 0.2,
        offsetX: 0,
        offsetY: -180
    },

    render: {
        backgroundAlpha: false,
        debug: false,
        zoom: 1,
        speed: 1
    }
};