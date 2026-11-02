package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleTeam;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;
import vn.nghichtien.chienky.hero.domain.HeroInstance;
import vn.nghichtien.chienky.hero.dto.HeroStatsResponse;
import vn.nghichtien.chienky.hero.service.HeroStatsService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Chuyển nhân vật người chơi và chỉ số đã tính thành trạng thái chiến đấu.
 * Vị trí đội hình được truyền vào để factory không phụ thuộc cấu trúc lưu đội hình.
 */
@Component
public class HeroBattleUnitFactory {

    private final HeroStatsService heroStatsService;

    public HeroBattleUnitFactory(HeroStatsService heroStatsService) {
        this.heroStatsService = heroStatsService;
    }

    /**
     * Tạo BattleUnitState từ HeroInstance, bao gồm chỉ số cấp độ, sao,
     * đột phá và trang bị.
     *
     * @param instance nhân vật người chơi sở hữu
     * @param team đội tham gia trận đấu
     * @param row hàng trong đội hình 3x3, từ 0 đến 2
     * @param column cột trong đội hình 3x3, từ 0 đến 2
     * @return trạng thái nhân vật dùng trong battle engine
     */
    public BattleUnitState create(
            HeroInstance instance,
            BattleTeam team,
            int row,
            int column
    ) {
        Objects.requireNonNull(instance, "Hero instance must not be null");
        Objects.requireNonNull(team, "Battle team must not be null");

        if (instance.getId() == null) {
            throw new IllegalArgumentException(
                    "Hero instance must be persisted before entering battle"
            );
        }

        HeroStatsResponse response = heroStatsService.calculateStats(instance);
        Map<String, BigDecimal> stats = response.getStats();

        BattleUnitState unit = new BattleUnitState(
                instance.getId(),
                team,
                instance.getHeroCode(),
                requiredStat(stats, "HP"),
                requiredStat(stats, "ATK"),
                requiredStat(stats, "DEF"),
                requiredStat(stats, "SPEED"),
                row,
                column,
                instance.getHeroDefinition().getRole()
        );

        unit.setCombatStats(
                statOrZero(stats, "CRIT"),
                statOrZero(stats, "CRIT_DAMAGE"),
                statOrZero(stats, "ACCURACY"),
                statOrZero(stats, "RESISTANCE"),
                statOrZero(stats, "PEN_FLAT"),
                statOrZero(stats, "PEN_PERCENT"),
                statOrZero(stats, "LIFESTEAL")
        );

        return unit;
    }

    /**
     * Lấy chỉ số bắt buộc; thiếu cấu hình phải báo lỗi thay vì tự gán 0.
     */
    private BigDecimal requiredStat(
            Map<String, BigDecimal> stats,
            String statCode
    ) {
        BigDecimal value = stats.get(statCode);

        if (value == null) {
            throw new IllegalStateException(
                    "Missing required hero stat: " + statCode
            );
        }

        return value;
    }

    /**
     * Lấy chỉ số bổ sung; cho phép mặc định 0 để tương thích dữ liệu cũ.
     */
    private BigDecimal statOrZero(
            Map<String, BigDecimal> stats,
            String statCode
    ) {
        return stats.getOrDefault(statCode, BigDecimal.ZERO);
    }
}
