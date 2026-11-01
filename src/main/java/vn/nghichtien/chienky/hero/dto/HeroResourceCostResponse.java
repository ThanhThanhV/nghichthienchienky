package vn.nghichtien.chienky.hero.dto;

import java.math.BigDecimal;

/**
 * Chi phí tài nguyên của một thao tác nâng cấp nhân vật.
 */
public record HeroResourceCostResponse(
        String resourceCode,
        BigDecimal requiredAmount
) {
}
