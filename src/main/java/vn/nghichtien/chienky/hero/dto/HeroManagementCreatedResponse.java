package vn.nghichtien.chienky.hero.dto;

import java.util.List;

/**
 * Kết quả tạo tướng và vị trí tài nguyên Spine đã lưu.
 */
public record HeroManagementCreatedResponse(
        String heroCode,
        String heroName,
        String spineSkeleton,
        String spineAtlas,
        List<String> spineTextures,
        List<String> animations
) {
}
