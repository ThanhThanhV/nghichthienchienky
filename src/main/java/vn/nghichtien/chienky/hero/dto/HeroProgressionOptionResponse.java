package vn.nghichtien.chienky.hero.dto;

import java.util.List;

/**
 * Điều kiện và chi phí cho cấp nâng sao hoặc đột phá kế tiếp.
 */
public record HeroProgressionOptionResponse(
        String progressionType,
        Integer currentLevel,
        Integer targetLevel,
        Integer requiredHeroLevel,
        Integer requiredStarLevel,
        boolean configured,
        boolean canUpgrade,
        String unavailableReason,
        List<HeroResourceCostResponse> costs
) {
    public HeroProgressionOptionResponse {
        costs = costs == null ? List.of() : List.copyOf(costs);
    }
}
