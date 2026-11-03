package vn.nghichtien.chienky.power.dto;

import java.math.BigDecimal;

/** Một dòng trong bảng xếp hạng lực chiến. */
public record PowerRankingEntryResponse(
        int rank,
        Long playerId,
        String nickname,
        Integer playerLevel,
        BigDecimal totalPower,
        int teamSize
) {
}