package vn.nghichtien.chienky.battle.dto;

import java.util.List;

/**
 * Trạng thái mới nhất của trận đấu và các sự kiện phát sinh kể từ lần gọi trước.
 */
public record BattlePlaybackDTO(
        BattleScreenDTO screen,
        List<BattleEventDTO> events
) {
}
