package vn.nghichtien.chienky.hero.dto;

/**
 * Thông tin vật phẩm EXP được phép sử dụng để tăng cấp nhân vật.
 */
public record HeroExpItemResponse(
        String resourceCode,
        Long expPerItem
) {
}
