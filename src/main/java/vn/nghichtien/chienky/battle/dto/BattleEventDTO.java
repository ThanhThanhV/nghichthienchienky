
package vn.nghichtien.chienky.battle.dto;

import vn.nghichtien.chienky.battle.domain.BattleEventType;

import java.math.BigDecimal;

/**
 * Dữ liệu mô tả một sự kiện xảy ra trong trận chiến.
 *
 * @param eventId ID sự kiện.
 * @param sequence Thứ tự xử lý sự kiện.
 * @param type Loại sự kiện.
 * @param sourceTeam Đội tạo ra sự kiện.
 * @param sourceInstanceId ID nhân vật tạo ra sự kiện.
 * @param targetTeam Đội chịu tác động.
 * @param targetInstanceId ID nhân vật chịu tác động.
 * @param skillCode Mã kỹ năng.
 * @param amount Giá trị sát thương hoặc hồi máu.
 * @param critical Có chí mạng hay không.
 * @param currentHp HP hiện tại sau xử lý.
 * @param maxHp HP tối đa.
 * @param alive Nhân vật còn sống hay không.
 * @param effectCode Mã hiệu ứng.
 * @param effectCategory Nhóm hiệu ứng.
 * @param duration Thời hạn hiệu ứng.
 * @param message Nội dung mô tả.
 */
public record BattleEventDTO(
        long eventId,
        int sequence,
        BattleEventType type,
        String sourceTeam,
        Long sourceInstanceId,
        String targetTeam,
        Long targetInstanceId,
        String skillCode,
        BigDecimal amount,
        Boolean critical,
        BigDecimal currentHp,
        BigDecimal maxHp,
        Boolean alive,
        String effectCode,
        String effectCategory,
        Integer duration,
        String message
) {
}
