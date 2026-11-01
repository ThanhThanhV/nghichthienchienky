package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleActionType;
import vn.nghichtien.chienky.battle.domain.BattleEventType;
import vn.nghichtien.chienky.battle.domain.BattlePassiveTrigger;
import vn.nghichtien.chienky.battle.domain.BattleState;
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

        initializeBattlePassives(battle);

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

        executePassivesForAllAlive(battle, BattlePassiveTrigger.ROUND_START);

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

            // SILENCE chỉ chặn Active và Ultimate.
            if (statusManager.isSilenced()
                    && actionType != BattleActionType.BASIC_ATTACK) {
                actionType = BattleActionType.BASIC_ATTACK;
            }

            int eventStartIndex = collector.getEvents().size();
            skillExecutor.execute(battle, unit, actionType);

            // Chỉ dispatch dựa trên event do action chính vừa tạo.
            // Event do passive tạo ra không tự kích hoạt dây chuyền trong cùng lượt.
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

            if (event.type() == BattleEventType.BASIC_ATTACK
                    || event.type() == BattleEventType.ACTIVE_SKILL
                    || event.type() == BattleEventType.ULTIMATE_SKILL) {
                BattleUnitState attacker = battle.findUnit(
                        event.sourceInstanceId()
                );
                if (attacker != null && attacker.isAlive()) {
                    skillExecutor.executePassives(
                            battle, attacker, BattlePassiveTrigger.ON_ATTACK
                    );
                }
            }

            if (event.type() == BattleEventType.DAMAGE) {
                BattleUnitState damaged = battle.findUnit(
                        event.targetInstanceId()
                );
                if (damaged != null && damaged.isAlive()) {
                    skillExecutor.executePassives(
                            battle, damaged, BattlePassiveTrigger.ON_DAMAGED
                    );
                }
            }

            if (event.type() == BattleEventType.DEATH) {
                BattleUnitState killer = battle.findUnit(
                        event.sourceInstanceId()
                );
                if (killer != null && killer.isAlive()) {
                    skillExecutor.executePassives(
                            battle, killer, BattlePassiveTrigger.ON_KILL
                    );
                }
            }
        }
    }
}
