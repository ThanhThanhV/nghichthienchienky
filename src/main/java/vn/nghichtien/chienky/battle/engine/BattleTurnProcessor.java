package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleActionType;
import vn.nghichtien.chienky.battle.domain.BattleEventType;
import vn.nghichtien.chienky.battle.domain.BattlePassiveTrigger;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTeam;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;
import vn.nghichtien.chienky.battle.dto.BattleEventDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Điều phối thứ tự hành động của các nhân vật trong từng round
 * và tự động gọi nội tại theo các sự kiện chiến đấu.
 */
@Component
public class BattleTurnProcessor {

    private final DatabaseSkillExecutor skillExecutor;
    private final BattleEffectProcessor effectProcessor;

    public BattleTurnProcessor(
            DatabaseSkillExecutor skillExecutor,
            BattleEffectProcessor effectProcessor
    ) {
        this.skillExecutor = skillExecutor;
        this.effectProcessor = effectProcessor;
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
            unit.resetExtraActionForRound();
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

        // Nội tại BATTLE_START có thể kết thúc trận; không tick DOT/HOT
        // hoặc phát sinh thêm hành động sau khi kết quả đã được xác định.
        if (battle.checkFinished()) {
            collector.record(
                    BattleEventType.ROUND_END,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    "Round " + round + " finished"
            );
            return;
        }

        // DOT/HOT tick một lần sau ROUND_START và trước hành động của round.
        int periodicEventStartIndex = collector.getEvents().size();
        effectProcessor.tickPeriodicEffects(battle);
        List<BattleEventDTO> periodicEvents = List.copyOf(collector.getEvents()
                .subList(periodicEventStartIndex, collector.getEvents().size()));
        dispatchActionEventPassives(battle, periodicEvents);

        if (battle.checkFinished()) {
            collector.record(
                    BattleEventType.ROUND_END,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    "Round " + round + " finished"
            );
            return;
        }

        executePassivesForAllAlive(battle, BattlePassiveTrigger.ROUND_START);

        // Nội tại đầu round có thể kết liễu đội đối phương; không cho đơn vị
        // tiếp tục hành động sau khi điều kiện kết thúc đã được thỏa mãn.
        if (battle.checkFinished()) {
            collector.record(
                    BattleEventType.ROUND_END,
                    null, null, null, null, null, null,
                    null, null, null, null, null, null, null,
                    "Round " + round + " finished"
            );
            return;
        }

        List<BattleUnitState> actionOrder = new ArrayList<>(battle.getActionOrder());
        if (round == 1) {
            // Gojo được ưu tiên hành động mở màn ở round đầu:
            // PLAYER Gojo -> ENEMY Gojo -> các đơn vị còn lại theo SPEED.
            // sort ổn định nên thứ tự SPEED hiện tại vẫn được giữ trong từng nhóm.
            actionOrder.sort(java.util.Comparator.comparingInt(
                    this::getRoundOneActionPriority
            ));
        }

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

            if (battle.checkFinished()) {
                collector.record(
                        BattleEventType.ACTION_END,
                        unit.getTeam().name(),
                        unit.getInstanceId(),
                        null, null, null, null,
                        null, null, null, null, null, null, null,
                        "Action ended because the battle finished"
                );
                break;
            }

            var statusManager = unit.getStatusEffectManager();

            if (statusManager.shouldSkipAction()) {
                // Lưu loại khống chế trước passive AFTER_ACTION vì passive có thể
                // giải trừ CC và làm crowdControl trở thành null.
                String crowdControlType =
                        statusManager.getCrowdControl().getType().name();

                skillExecutor.executePassives(
                        battle, unit, BattlePassiveTrigger.AFTER_ACTION
                );
                collector.record(
                        BattleEventType.ACTION_END,
                        unit.getTeam().name(),
                        unit.getInstanceId(),
                        null, null, null, null,
                        null, null, null, null, null, null, null,
                        "Action skipped: " + crowdControlType
                );
                executePendingExtraAction(battle, unit);
                if (battle.checkFinished()) {
                    break;
                }
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

            if (battle.checkFinished()) {
                collector.record(
                        BattleEventType.ACTION_END,
                        unit.getTeam().name(),
                        unit.getInstanceId(),
                        null, null, null, null,
                        null, null, null, null, null, null, null,
                        "Action ended because the battle finished"
                );
                break;
            }

            int eventStartIndex = collector.getEvents().size();
            skillExecutor.execute(battle, unit, actionType);

            // Chỉ dispatch event do action chính tạo ra, không tạo chuỗi passive vô hạn.
            List<BattleEventDTO> actionEvents = List.copyOf(collector.getEvents()
                    .subList(eventStartIndex, collector.getEvents().size()));
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

            // EXTRA_ACTION phát sinh từ AFTER_ACTION hoặc nội tại đầu round.
            executePendingExtraAction(battle, unit);

            // Chỉ chốt kết quả sau khi toàn bộ effect và passive của hành động
            // hiện tại đã chạy xong (cho phép REVIVE trong cùng chuỗi effect).
            if (battle.checkFinished()) {
                break;
            }
        }

        battle.checkFinished();

        collector.record(
                BattleEventType.ROUND_END,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                "Round " + round + " finished"
        );
    }

    /**
     * Thứ tự ưu tiên riêng ở round 1.
     * Các đơn vị không phải Gojo cùng nằm trong nhóm cuối và vẫn giữ
     * thứ tự SPEED đã được BattleState.getActionOrder() sắp xếp.
     */
    private int getRoundOneActionPriority(BattleUnitState unit) {
        if (!"gojo".equalsIgnoreCase(unit.getHeroCode())) {
            return 2;
        }
        return unit.getTeam() == BattleTeam.PLAYER ? 0 : 1;
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

    /**
     * Thực thi một hành động bổ sung ngay khi EXTRA_ACTION được cấp.
     * Hành động vẫn đi theo chu kỳ ACTIVE -> ULTIMATE -> BASIC.
     */
    private void executePendingExtraAction(
            BattleState battle,
            BattleUnitState unit
    ) {
        if (battle.isFinished() || !unit.consumeExtraAction()) {
            return;
        }

        BattleActionType actionType = getNextActionType(unit.getActionCount());
        unit.incrementActionCount();

        var collector = battle.getEventCollector();
        collector.record(
                BattleEventType.ACTION_START,
                unit.getTeam().name(),
                unit.getInstanceId(),
                null, null, null, null,
                null, null, null, null, null, null, null,
                "Extra action started"
        );

        skillExecutor.executePassives(
                battle, unit, BattlePassiveTrigger.BEFORE_ACTION
        );

        if (battle.isFinished()) {
            collector.record(
                    BattleEventType.ACTION_END,
                    unit.getTeam().name(),
                    unit.getInstanceId(),
                    null, null, null, null,
                    null, null, null, null, null, null, null,
                    "Extra action ended because the battle finished"
            );
            return;
        }

        var statusManager = unit.getStatusEffectManager();
        if (statusManager.shouldSkipAction()) {
            String crowdControlType =
                    statusManager.getCrowdControl().getType().name();
            skillExecutor.executePassives(
                    battle, unit, BattlePassiveTrigger.AFTER_ACTION
            );
            collector.record(
                    BattleEventType.ACTION_END,
                    unit.getTeam().name(),
                    unit.getInstanceId(),
                    null, null, null, null,
                    null, null, null, null, null, null, null,
                    "Extra action skipped: " + crowdControlType
            );
            return;
        }

        if (statusManager.isSilenced()
                && actionType != BattleActionType.BASIC_ATTACK) {
            actionType = BattleActionType.BASIC_ATTACK;
        }

        skillExecutor.executePassives(
                battle, unit, BattlePassiveTrigger.ON_ATTACK
        );

        if (!battle.isFinished() && unit.isAlive()) {
            int eventStartIndex = collector.getEvents().size();
            skillExecutor.execute(battle, unit, actionType);
            List<BattleEventDTO> actionEvents = List.copyOf(collector.getEvents()
                    .subList(eventStartIndex, collector.getEvents().size()));
            dispatchActionEventPassives(battle, actionEvents);
        }

        skillExecutor.executePassives(
                battle, unit, BattlePassiveTrigger.AFTER_ACTION
        );

        collector.record(
                BattleEventType.ACTION_END,
                unit.getTeam().name(),
                unit.getInstanceId(),
                null, null, null, null,
                null, null, null, null, null, null, null,
                "Extra action finished"
        );
    }

    private void dispatchActionEventPassives(
            BattleState battle,
            List<BattleEventDTO> events
    ) {
        List<BattleUnitState> pendingExtraActions = new ArrayList<>();
        Set<DamageReactionKey> processedDamageReactions = new HashSet<>();
        Set<Long> pendingExtraActionIds = new HashSet<>();

        for (BattleEventDTO event : events) {
            if (battle.isFinished()) {
                return;
            }

            if (event.type() == BattleEventType.DAMAGE) {
                BattleUnitState damaged = battle.findUnit(event.targetInstanceId());
                if (damaged != null && damaged.isAlive()) {
                    // COUNTER damage không kích hoạt COUNTER tiếp,
                    // tránh chuỗi phản công vô hạn giữa hai đơn vị.
                    boolean canTriggerDamagedPassive =
                            !"COUNTER".equalsIgnoreCase(event.effectCategory())
                                    && !"DEFENSE".equalsIgnoreCase(event.effectCategory());

                    DamageReactionKey reactionKey = new DamageReactionKey(
                            event.targetInstanceId(),
                            event.sourceInstanceId()
                    );

                    // Một đòn nhiều hit chỉ kích hoạt ON_DAMAGED một lần
                    // cho mỗi cặp mục tiêu/người tấn công trong cùng action.
                    if (canTriggerDamagedPassive
                            && processedDamageReactions.add(reactionKey)) {
                        skillExecutor.executePassives(
                                battle,
                                damaged,
                                BattlePassiveTrigger.ON_DAMAGED,
                                event.sourceInstanceId()
                        );
                        if (pendingExtraActionIds.add(damaged.getInstanceId())) {
                            pendingExtraActions.add(damaged);
                        }
                    }
                }
            }

            if (event.type() == BattleEventType.DEATH) {
                BattleUnitState killer = battle.findUnit(event.sourceInstanceId());
                if (killer != null && killer.isAlive()) {
                    skillExecutor.executePassives(
                            battle, killer, BattlePassiveTrigger.ON_KILL
                    );
                    if (pendingExtraActionIds.add(killer.getInstanceId())) {
                        pendingExtraActions.add(killer);
                    }
                }
            }
        }

        // Chỉ chạy hành động bổ sung sau khi duyệt xong snapshot sự kiện,
        // tránh sửa EventCollector khi đang duyệt subList.
        for (BattleUnitState unit : pendingExtraActions) {
            if (battle.isFinished()) {
                return;
            }
            executePendingExtraAction(battle, unit);
        }
    }

    private record DamageReactionKey(
            Long damagedInstanceId,
            Long attackerInstanceId
    ) {
    }
}
