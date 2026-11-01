package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleActionType;
import vn.nghichtien.chienky.battle.domain.BattleEventType;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;

import java.util.List;

/**
 * Điều phối thứ tự hành động của các nhân vật trong từng round.
 */
@Component
public class BattleTurnProcessor {

    private final DatabaseSkillExecutor skillExecutor;

    public BattleTurnProcessor(DatabaseSkillExecutor skillExecutor) {
        this.skillExecutor = skillExecutor;
    }

    /**
     * Xác định hành động tiếp theo theo chu kỳ Active, Ultimate, Basic.
     */
    public BattleActionType getNextActionType(int actionCount) {
        return switch (actionCount % 3) {
            case 0 -> BattleActionType.ACTIVE_SKILL;
            case 1 -> BattleActionType.ULTIMATE_SKILL;
            default -> BattleActionType.BASIC_ATTACK;
        };
    }

    /**
     * Xử lý một round chiến đấu.
     */
    public void processRound(BattleState battle) {
        if (battle == null) {
            throw new IllegalArgumentException("Battle must not be null");
        }

        if (battle.isFinished()) {
            return;
        }

        int round = battle.nextRound();
        var collector = battle.getEventCollector();

        // Giảm thời hạn hiệu ứng trước khi bắt đầu round mới.
        for (BattleUnitState unit : battle.getUnits()) {
            unit.getStatusEffectManager().decreaseDurations();
            unit.getImmunityManager().decreaseDurations();
            unit.getShieldManager().decreaseDurations();
            unit.getDefenseEffectManager().decreaseDurations();
        }

        collector.record(
                BattleEventType.ROUND_START,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                "Round " + round + " started"
        );

        List<BattleUnitState> actionOrder = battle.getActionOrder();

        for (BattleUnitState unit : actionOrder) {
            if (battle.checkFinished()) {
                break;
            }

            if (!unit.isAlive()) {
                continue;
            }

            BattleActionType actionType =
                    getNextActionType(unit.getActionCount());

            // Mỗi cơ hội hành động đều tiến chu kỳ, kể cả khi bị khống chế.
            unit.incrementActionCount();

            collector.record(
                    BattleEventType.ACTION_START,
                    unit.getTeam().name(),
                    unit.getInstanceId(),
                    null, null, null, null,
                    null, null, null, null, null, null, null,
                    "Character action started"
            );

            var statusManager = unit.getStatusEffectManager();

            if (statusManager.shouldSkipAction()) {
                collector.record(
                        BattleEventType.ACTION_END,
                        unit.getTeam().name(),
                        unit.getInstanceId(),
                        null, null, null, null,
                        null, null, null, null, null, null, null,
                        "Action skipped: "
                                + statusManager.getCrowdControl().getType()
                );
                continue;
            }

            // SILENCE chỉ chặn Active và Ultimate.
            if (statusManager.isSilenced()
                    && actionType != BattleActionType.BASIC_ATTACK) {
                actionType = BattleActionType.BASIC_ATTACK;
            }

            // DatabaseSkillExecutor là nơi duy nhất ghi event kích hoạt skill.
            skillExecutor.execute(battle, unit, actionType);

            collector.record(
                    BattleEventType.ACTION_END,
                    unit.getTeam().name(),
                    unit.getInstanceId(),
                    null, null, null, null,
                    null, null, null, null, null, null, null,
                    "Character action finished"
            );
        }

        battle.checkFinished();

        collector.record(
                BattleEventType.ROUND_END,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                "Round " + round + " finished"
        );
    }
}
