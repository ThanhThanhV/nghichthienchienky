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
import java.util.Objects;
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

        // Một số nội tại ROUND_START có thể gây sát thương trực tiếp
        // (ví dụ đòn đánh thường phụ của Gojo ở round 11). Chụp snapshot
        // và dispatch qua cùng pipeline ON_DAMAGED/ON_KILL như action thường.
        int roundStartEventStartIndex = collector.getEvents().size();
        executePassivesForAllAlive(battle, BattlePassiveTrigger.ROUND_START);
        List<BattleEventDTO> roundStartEvents = List.copyOf(
                collector.getEvents().subList(
                        roundStartEventStartIndex, collector.getEvents().size()
                )
        );
        dispatchActionEventPassives(battle, roundStartEvents);

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

        // Chụp thứ tự hành động của round theo SPEED từ BattleState.
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

            if (unit.isActionLocked(battle.getCurrentRound())) {
                skillExecutor.executePassives(
                        battle, unit, BattlePassiveTrigger.AFTER_ACTION
                );
                collector.record(
                        BattleEventType.ACTION_END,
                        unit.getTeam().name(), unit.getInstanceId(),
                        null, null, null, null, null, null, null, null, null, null, null,
                        "Action locked by passive"
                );
                executePendingExtraAction(battle, unit);
                continue;
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

            // Chụp event của action gốc trước khi dispatch phản ứng ON_DAMAGED.
            List<BattleEventDTO> actionEvents = List.copyOf(collector.getEvents()
                    .subList(eventStartIndex, collector.getEvents().size()));

            skillExecutor.executePassives(
                    battle, unit, BattlePassiveTrigger.AFTER_ACTION
            );

            // Frontend phải hoàn tất animation đòn gốc trước khi phát phản kích.
            collector.record(
                    BattleEventType.ACTION_END,
                    unit.getTeam().name(),
                    unit.getInstanceId(),
                    null, null, null, null,
                    null, null, null, null, null, null, null,
                    "Character action finished"
            );

            dispatchActionEventPassives(battle, actionEvents);
            dispatchEnemyActionPassives(battle, unit, actionEvents);

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
                if (trigger == BattlePassiveTrigger.BATTLE_START) {
                    skillExecutor.executeNonPassiveBattleStartEffects(battle, unit);
                }
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

        if (unit.isActionLocked(battle.getCurrentRound())) {
            skillExecutor.executePassives(battle, unit, BattlePassiveTrigger.AFTER_ACTION);
            collector.record(
                    BattleEventType.ACTION_END,
                    unit.getTeam().name(), unit.getInstanceId(),
                    null, null, null, null, null, null, null, null, null, null, null,
                    "Extra action locked by passive"
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

        List<BattleEventDTO> actionEvents = List.of();
        if (!battle.isFinished() && unit.isAlive()) {
            int eventStartIndex = collector.getEvents().size();
            skillExecutor.execute(battle, unit, actionType);
            actionEvents = List.copyOf(collector.getEvents()
                    .subList(eventStartIndex, collector.getEvents().size()));
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

        if (!actionEvents.isEmpty()) {
            dispatchActionEventPassives(battle, actionEvents);
        }
        dispatchEnemyActionPassives(battle, unit, actionEvents);
    }

    /**
     * Chỉ phát trigger ON_ENEMY_ACTION khi actor vừa thực hiện một action có
     * hit-resolution event. Buff, domain setup hoặc skip không kích hoạt phản kích;
     * hit bị né, miễn nhiễm, chặn hoặc hấp thụ bởi khiên vẫn được tính.
     *
     * Snapshot actionEvents chỉ chứa event của action gốc; counter/reflect
     * reaction được phát sau đó không được xem là một action tấn công mới.
     */
    private void dispatchEnemyActionPassives(
            BattleState battle,
            BattleUnitState actor,
            List<BattleEventDTO> actionEvents
    ) {
        if (battle.isFinished() || actor == null || actionEvents == null) return;

        boolean resolvedDamagingHit = actionEvents.stream()
                .anyMatch(event -> (
                        event.type() == BattleEventType.DAMAGE
                                || event.type() == BattleEventType.EVASION
                                || event.type() == BattleEventType.IMMUNITY
                                || event.type() == BattleEventType.SHIELD
                                || event.type() == BattleEventType.BLOCK
                )
                        && Objects.equals(event.sourceInstanceId(), actor.getInstanceId())
                        && !"COUNTER".equalsIgnoreCase(event.effectCategory()));
        if (!resolvedDamagingHit) {
            return;
        }

        for (BattleUnitState observer : battle.getUnits()) {
            if (battle.isFinished()) return;
            if (!observer.isAlive() || observer.getTeam() == actor.getTeam()) continue;
            int eventStart = battle.getEventCollector().getEvents().size();
            skillExecutor.executePassives(
                    battle, observer, BattlePassiveTrigger.ON_ENEMY_ACTION,
                    actor.getInstanceId()
            );
            // Hách có thể kết liễu kẻ vừa hành động; chuyển tiếp DEATH để Lục Nhãn
            // nhận stack ON_KILL mà không phát lại ON_ENEMY_ACTION.
            List<BattleEventDTO> reactionEvents = List.copyOf(
                    battle.getEventCollector().getEvents().subList(
                            eventStart, battle.getEventCollector().getEvents().size()
                    )
            );
            for (BattleEventDTO event : reactionEvents) {
                if (event.type() != BattleEventType.DEATH) continue;
                BattleUnitState killer = battle.findUnit(event.sourceInstanceId());
                if (killer != null && killer.isAlive()) {
                    skillExecutor.executePassives(
                            battle, killer, BattlePassiveTrigger.ON_KILL
                    );
                }
            }
        }
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
                        battle.getEventCollector().record(
                                BattleEventType.ACTION_START,
                                damaged.getTeam().name(),
                                damaged.getInstanceId(),
                                null, null, null, null,
                                null, null, null, null, null, null, null,
                                "Damage reaction started"
                        );

                        skillExecutor.executePassives(
                                battle,
                                damaged,
                                BattlePassiveTrigger.ON_DAMAGED,
                                event.sourceInstanceId()
                        );

                        battle.getEventCollector().record(
                                BattleEventType.ACTION_END,
                                damaged.getTeam().name(),
                                damaged.getInstanceId(),
                                null, null, null, null,
                                null, null, null, null, null, null, null,
                                "Damage reaction finished"
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

            if (event.type() == BattleEventType.REVIVE) {
                BattleUnitState revived = battle.findUnit(event.targetInstanceId());
                if (revived != null && revived.isAlive()) {
                    // Chỉ kích hoạt sau khi revive thành công; nội tại phe đối phương
                    // nhận đúng instance vừa hồi sinh làm mục tiêu.
                    for (BattleUnitState observer : battle.getUnits()) {
                        if (battle.isFinished()) return;
                        if (observer.isAlive() && observer.getTeam() != revived.getTeam()) {
                            skillExecutor.executePassives(
                                    battle, observer, BattlePassiveTrigger.ON_REVIVE,
                                    revived.getInstanceId()
                            );
                        }
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
