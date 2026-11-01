
package vn.nghichtien.chienky.battle.dto;

import java.util.List;

/**
 * Kết quả hoàn chỉnh của một trận chiến.
 *
 * @param battleId ID trận đấu.
 * @param totalRounds Tổng số round đã diễn ra.
 * @param winner Đội chiến thắng.
 * @param status Trạng thái trận đấu.
 * @param events Danh sách sự kiện theo thứ tự xử lý.
 */
public record BattleResultDTO(
        String battleId,
        Integer totalRounds,
        String winner,
        String status,
        List<BattleEventDTO> events
) {
}
