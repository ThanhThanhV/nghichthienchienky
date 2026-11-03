package vn.nghichtien.chienky.hero.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload cập nhật dữ liệu tướng từ khu vực quản trị.
 */
public record HeroAdminUpdateRequest(
        String heroName,
        String rarity,
        String role,
        String faction,
        String avatarCode,
        String description,
        Boolean enabled,
        Integer sortOrder,
        StatsRequest baseStats,
        StatsRequest growthStats,
        List<SkillRequest> skills
) {
    public record StatsRequest(
            BigDecimal hp,
            BigDecimal atk,
            BigDecimal def,
            BigDecimal speed,
            BigDecimal crit,
            BigDecimal critDamage,
            BigDecimal accuracy,
            BigDecimal resistance,
            BigDecimal penFlat,
            BigDecimal penPercent,
            BigDecimal lifesteal
    ) {
    }

    public record SkillRequest(
            Long id,
            Integer skillOrder,
            String skillName,
            String description,
            String iconCode,
            Boolean enabled,
            List<EffectRequest> effects
    ) {
    }

    public record EffectRequest(
            Long id,
            Integer effectOrder,
            String effectType,
            String targetType,
            BigDecimal multiplier,
            BigDecimal chance,
            Integer duration,
            String configJson,
            Boolean enabled
    ) {
    }
}
