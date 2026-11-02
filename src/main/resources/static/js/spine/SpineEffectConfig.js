/**
 * Mapping hiệu ứng Spine theo heroCode.skillCode hoặc effectCode.
 *
 * Khi thêm asset, khai báo tại effects:
 * "gojo.active_01": {
 *     code: "gojo_active_01",
 *     skeleton: "/assets/spine/effects/gojo/active_01/effect.json",
 *     atlas: "/assets/spine/effects/gojo/active_01/effect.atlas",
 *     textures: ["/assets/spine/effects/gojo/active_01/effect.png"],
 *     animation: "animation",
 *     impactEvent: "impact",  // Optional Spine event at the exact hit frame
 *     impactRatio: 0.55,      // Used when no impactEvent is configured
 *     anchor: "target",       // source | target | center
 *     layer: "FRONT",         // BACK | FRONT
 *     displayScale: 1,
 *     offsetX: 0,
 *     offsetY: 0,
 *     phase: "cast"           // cast | impact
 * }
 *
 * Mỗi key có thể nhận một object hoặc một mảng object để phát nhiều effect.
 * Asset phải được export tương thích Spine runtime 3.5 đang dùng trong game.
 */
window.SPINE_EFFECT_CONFIG = {
    effects: {}
};
