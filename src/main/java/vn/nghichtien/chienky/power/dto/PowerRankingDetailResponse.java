package vn.nghichtien.chienky.power.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Hồ sơ lực chiến và đội hình chính công khai của một người chơi. */
public record PowerRankingDetailResponse(
        Long playerId,
        String nickname,
        Integer playerLevel,
        BigDecimal totalPower,
        List<FormationHero> formation
) {
    public record FormationHero(
            Long instanceId,
            String heroCode,
            String heroName,
            String rarity,
            String role,
            String avatarCode,
            Integer heroLevel,
            Integer starLevel,
            Integer breakthroughLevel,
            int row,
            int column,
            BigDecimal power,
            Map<String, BigDecimal> stats
    ) {}
}