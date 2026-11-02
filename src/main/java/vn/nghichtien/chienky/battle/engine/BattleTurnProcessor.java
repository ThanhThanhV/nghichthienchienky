package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleActionType;
import vn.nghichtien.chienky.battle.domain.BattleEventType;
import vn.nghichtien.chienky.battle.domain.BattlePassiveTrigger;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTeam;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;
import vn.nghichtien.chienky.battle.dto.BattleEventDTO;

import java.util.List;

/**
 * Điều phối thứ tự hành động của các nhân vật trong từng round
 * và tự động gọi nội tại theo các sự kiện chiến đấu.
 */
@Component
public class BattleTurnProcessor {

    private final DatabaseSkillExecutor skillExecutor;

    public BattleTurnProcessor(DatabaseSkillExecutor skillExecutor) {
        this.skillExecutor = skillExecutor;
    }

    public BattleActionType getNextActionType(int actionCount) {
        return switch (actionCount % 3) {
            case 0 -> BattleActionType.ACTIVE_SKILL;
            case 1 -> BattleActionType.ULTIMATE_SKILL;
            default -> BattleActionType.BASIC_ATTACK;
        };
    }

    public void processRound(BattleState battle) {
        if (battle == null) {
            throw new IllegalArgumentException("Battle must not be null");
        }
        if (battle.isFinished()) {
            return;
        }

        int round = battle.nextRound();
        var collector = battle.getEventCollector();

        // Tick hiệu ứng cũ trước khi áp dụng nội tại đầu trận.
        for (BattleUnitState unit : battle.getUnits()) {
            unit.getStatusEffectManager().decreaseDurations();
            unit.getImmunityManager().decreaseDurations();
            unit.getShieldManager().decreaseDurations();
            unit.getDefenseEffectManager().decreaseDurations();
        }

        initializeBattlePassives(battle);

        collector.record(
                BattleEventType.ROUND_START,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                "Round " + round + " started"
        );

        executePassivesForAllAlive(battle, BattlePassiveTrigger.ROUND_START);

        List<BattleUnitState> actionOrder = battle.getActionOrder();

        for (BattleUnitState unit : actionOrder) {
            if (battle.checkFinished()) {
                break;
            }
            if (!unit.isAlive()) {
                continue;
            }

            BattleActionType actionType = getNextActionType(unit.getActionCount());
            unit.incrementActionCount();

            collector.record(
                    BattleEventType.ACTION_START,
                    unit.getTeam().name(),
                    unit.getInstanceId(),
                    null, null, null, null,
                    null, null, null, null, null, null, null,
                    "Character action started"
            );

            skillExecutor.executePassives(
                    battle, unit, BattlePassiveTrigger.BEFORE_ACTION
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
                skillExecutor.executePassives(
                        battle, unit, BattlePassiveTrigger.AFTER_ACTION
                );
                continue;
            }

            if (statusManager.isSilenced()
                    && actionType != BattleActionType.BASIC_ATTACK) {
                actionType = BattleActionType.BASIC_ATTACK;
            }

            // ON_ATTACK chạy trước effect chính để buff nội tại có tác dụng ngay đòn này.
            skillExecutor.executePassives(
                    battle, unit, BattlePassiveTrigger.ON_ATTACK
            );

            int eventStartIndex = collector.getEvents().size();
            skillExecutor.execute(battle, unit, actionType);

            // Chỉ dispatch event do action chính tạo ra, không tạo chuỗi passive vô hạn.
            List<BattleEventDTO> actionEvents = collector.getEvents()
                    .subList(eventStartIndex, collector.getEvents().size());
            dispatchActionEventPassives(battle, actionEvents);

            skillExecutor.executePassives(
                    battle, unit, BattlePassiveTrigger.AFTER_ACTION
            );

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

    private void initializeBattlePassives(BattleState battle) {
        if (!battle.beginPassiveInitialization()) {
            return;
        }

        battle.getEventCollector().record(
                BattleEventType.BATTLE_START,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                "Battle started"
        );

        executePassivesForAllAlive(battle, BattlePassiveTrigger.BATTLE_START);
    }

    private void executePassivesForAllAlive(
            BattleState battle,
            BattlePassiveTrigger trigger
    ) {
        for (BattleUnitState unit : battle.getUnits()) {
            if (battle.isFinished()) {
                return;
            }
            if (unit.isAlive()) {
                skillExecutor.executePassives(battle, unit, trigger);
            }
        }
    }

    private void dispatchActionEventPassives(
            BattleState battle,
            List<BattleEventDTO> events
    ) {
        for (BattleEventDTO event : events) {
            if (battle.isFinished()) {
                return;
            }

            if (event.type() == BattleEventType.DAMAGE) {
                BattleUnitState damaged = battle.findUnit(event.targetInstanceId());
                if (damaged != null && damaged.isAlive()) {
                    skillExecutor.executePassives(
                            battle, damaged, BattlePassiveTrigger.ON_DAMAGED
                    );
                }
            }

            if (event.type() == BattleEventType.DEATH) {
                BattleUnitState killer = battle.findUnit(event.sourceInstanceId());
                if (killer != null && killer.isAlive()) {
                    skillExecutor.executePassives(
                            battle, killer, BattlePassiveTrigger.ON_KILL
                    );
                }
            }
        }
    }
}
