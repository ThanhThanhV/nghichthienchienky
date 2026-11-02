package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleActionType;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTeam;
import vn.nghichtien.chienky.battle.domain.BattleTargetMode;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Quyết định hành động cho đội ENEMY trong các trận PvE.
 *
 * AI chỉ chọn loại kỹ năng; DatabaseSkillExecutor vẫn chịu trách nhiệm
 * tải kỹ năng, áp dụng hiệu ứng và tính toán kết quả trận đấu.
 */
@Component
public class PveBattleAi {

    private static final BigDecimal AOE_OPPORTUNITY_HP =
            new BigDecimal("0.75");
    private static final BigDecimal ULTIMATE_FINISHER_HP =
            new BigDecimal("0.50");
    private static final BigDecimal BASIC_FINISHER_HP =
            new BigDecimal("0.25");

    private final BattleTargetSelector targetSelector;

    public PveBattleAi(BattleTargetSelector targetSelector) {
        this.targetSelector = targetSelector;
    }

    /**
     * Chọn mục tiêu theo vai trò cho các đòn đánh PVE có cấu hình FRONT/RANDOM.
     * Các target mode tường minh như SELF, ALL_ENEMIES, LOWEST_HP và
     * HIGHEST_ATK vẫn được giữ nguyên theo cấu hình kỹ năng trong DB.
     */
    public List<BattleUnitState> selectTargets(
            BattleState battle,
            BattleUnitState actor,
            BattleTargetMode configuredMode
    ) {
        if (actor.getTeam() != BattleTeam.ENEMY
                || (configuredMode != BattleTargetMode.FRONT
                && configuredMode != BattleTargetMode.RANDOM)) {
            return targetSelector.selectTargets(battle, actor, configuredMode);
        }

        BattleTargetMode tacticalMode = switch (
                actor.getRole() == null ? "" : actor.getRole().trim().toUpperCase(Locale.ROOT)
        ) {
            case "ASSASSIN" -> BattleTargetMode.LOWEST_HP;
            case "MAGE", "SUPPORT" -> BattleTargetMode.HIGHEST_ATK;
            case "TANK", "WARRIOR" -> BattleTargetMode.FRONT;
            default -> configuredMode;
        };

        return targetSelector.selectTargets(battle, actor, tacticalMode);
    }

    public BattleActionType chooseAction(
            BattleState battle,
            BattleUnitState actor
    ) {
        if (battle == null || actor == null) {
            throw new IllegalArgumentException(
                    "Battle and actor must not be null"
            );
        }

        if (actor.getTeam() != BattleTeam.ENEMY) {
            return defaultAction(actor.getActionCount());
        }

        List<BattleUnitState> livingTargets = battle.getUnits().stream()
                .filter(BattleUnitState::isAlive)
                .filter(unit -> unit.getTeam() == BattleTeam.PLAYER)
                .toList();

        if (livingTargets.isEmpty()) {
            return BattleActionType.BASIC_ATTACK;
        }

        BattleUnitState weakestTarget = livingTargets.stream()
                .min(Comparator.comparing(BattleUnitState::getCurrentHp))
                .orElseThrow();

        BigDecimal weakestHpRatio = weakestTarget.getCurrentHp()
                .divide(weakestTarget.getMaxHp(), 4, RoundingMode.HALF_UP);

        return switch (actor.getActionCount() % 3) {
            // Ưu tiên tuyệt kỹ sớm khi có nhiều mục tiêu và đội hình địch đã suy yếu.
            case 0 -> livingTargets.size() >= 3
                    && weakestHpRatio.compareTo(AOE_OPPORTUNITY_HP) <= 0
                    ? BattleActionType.ULTIMATE_SKILL
                    : BattleActionType.ACTIVE_SKILL;

            // Dùng tuyệt kỹ để kết liễu mục tiêu yếu; nếu chưa có cơ hội thì đánh thường.
            case 1 -> weakestHpRatio.compareTo(ULTIMATE_FINISHER_HP) <= 0
                    ? BattleActionType.ULTIMATE_SKILL
                    : BattleActionType.BASIC_ATTACK;

            // Không lãng phí kỹ năng mạnh vào mục tiêu sắp bị hạ bằng đòn thường.
            default -> weakestTarget.getCurrentHp()
                    .compareTo(actor.getAttack()) <= 0
                    || weakestHpRatio.compareTo(BASIC_FINISHER_HP) <= 0
                    ? BattleActionType.BASIC_ATTACK
                    : BattleActionType.ACTIVE_SKILL;
        };
    }

    private BattleActionType defaultAction(int actionCount) {
        return switch (actionCount % 3) {
            case 0 -> BattleActionType.ACTIVE_SKILL;
            case 1 -> BattleActionType.ULTIMATE_SKILL;
            default -> BattleActionType.BASIC_ATTACK;
        };
    }
}
