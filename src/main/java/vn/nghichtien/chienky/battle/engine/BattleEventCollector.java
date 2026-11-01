
package vn.nghichtien.chienky.battle.engine;


import vn.nghichtien.chienky.battle.domain.BattleEventType;
import vn.nghichtien.chienky.battle.dto.BattleEventDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thu thập và quản lý thứ tự các sự kiện trong một trận chiến.
 */

public class BattleEventCollector {

    private final AtomicLong eventSequence = new AtomicLong();

    private final List<BattleEventDTO> events = new ArrayList<>();

    /**
     * Ghi nhận một sự kiện chiến đấu.
     *
     * @param type Loại sự kiện.
     * @param sourceTeam Đội tạo sự kiện.
     * @param sourceInstanceId ID nhân vật nguồn.
     * @param targetTeam Đội mục tiêu.
     * @param targetInstanceId ID nhân vật mục tiêu.
     * @param skillCode Mã kỹ năng.
     * @param amount Giá trị sát thương hoặc hồi máu.
     * @param critical Trạng thái chí mạng.
     * @param currentHp HP hiện tại.
     * @param maxHp HP tối đa.
     * @param alive Trạng thái sống.
     * @param effectCode Mã hiệu ứng.
     * @param effectCategory Nhóm hiệu ứng.
     * @param duration Thời hạn hiệu ứng.
     * @param message Mô tả sự kiện.
     * @return Sự kiện vừa được ghi nhận.
     */
    public BattleEventDTO record(
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
        long sequence = eventSequence.incrementAndGet();

        BattleEventDTO event = new BattleEventDTO(
                sequence,
                (int) sequence,
                type,
                sourceTeam,
                sourceInstanceId,
                targetTeam,
                targetInstanceId,
                skillCode,
                amount,
                critical,
                currentHp,
                maxHp,
                alive,
                effectCode,
                effectCategory,
                duration,
                message
        );

        events.add(event);
        return event;
    }

    /**
     * Trả về danh sách sự kiện đã ghi nhận.
     *
     * @return Danh sách sự kiện theo thứ tự.
     */
    public List<BattleEventDTO> getEvents() {
        return List.copyOf(events);
    }

    /**
     * Xóa dữ liệu sự kiện để chuẩn bị cho trận đấu mới.
     */
    public void clear() {
        events.clear();
        eventSequence.set(0);
    }
}
