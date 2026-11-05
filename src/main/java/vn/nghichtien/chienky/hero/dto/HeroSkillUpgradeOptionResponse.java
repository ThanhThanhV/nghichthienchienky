package vn.nghichtien.chienky.hero.dto;

import java.util.List;

/**
 * Điều kiện và chi phí nâng cấp cấp độ tiếp theo của kỹ năng, định danh bằng skillCode.
 */
public record HeroSkillUpgradeOptionResponse(
        String skillCode,
        String skillName,
        Integer currentSkillLevel,
        Integer targetSkillLevel,
        Integer requiredHeroLevel,
        boolean configured,
        boolean canUpgrade,
        String unavailableReason,
        List<HeroResourceCostResponse> costs
) {
    public HeroSkillUpgradeOptionResponse {
        costs = costs == null ? List.of() : List.copyOf(costs);
    }
}
