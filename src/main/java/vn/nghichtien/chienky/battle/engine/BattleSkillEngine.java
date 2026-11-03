package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleActionType;
import vn.nghichtien.chienky.battle.domain.BattleEffectConfig;
import vn.nghichtien.chienky.battle.domain.BattleEventType;
import vn.nghichtien.chienky.battle.domain.BattlePassiveTrigger;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;
import vn.nghichtien.chienky.hero.domain.HeroSkillDefinition;

import java.util.List;
import java.util.Objects;

/**
 * Runtime engine thực thi kỹ năng độc lập với nguồn lưu trữ cấu hình.
 *
 * DatabaseSkillExecutor chịu trách nhiệm đọc dữ liệu và chuyển đổi JSON;
 * BattleSkillEngine chịu trách nhiệm ghi nhận kích hoạt và chạy chuỗi effect.
 */
@Component
public class BattleSkillEngine {

    private final BattleEffectProcessor effectProcessor;

    public BattleSkillEngine(BattleEffectProcessor effectProcessor) {
        this.effectProcessor = effectProcessor;
    }

    /**
     * Thực thi một kỹ năng chủ động theo thứ tự effect đã được adapter chuẩn hóa.
     */
    public void executeSkill(
            BattleState battle,
            BattleUnitState source,
            HeroSkillDefinition skill,
            BattleActionType actionType,
            List<BattleEffectConfig> effects
    ) {
        Objects.requireNonNull(battle, "Battle must not be null");
        Objects.requireNonNull(source, "Source must not be null");
        Objects.requireNonNull(skill, "Skill must not be null");
        Objects.requireNonNull(actionType, "Action type must not be null");
        Objects.requireNonNull(effects, "Effects must not be null");

        if (battle.isFinished() || !source.isAlive()) {
            return;
        }

        BattleEventType eventType = switch (actionType) {
            case BASIC_ATTACK -> BattleEventType.BASIC_ATTACK;
            case ACTIVE_SKILL -> BattleEventType.ACTIVE_SKILL;
            case ULTIMATE_SKILL -> BattleEventType.ULTIMATE_SKILL;
        };

        battle.getEventCollector().record(
                eventType,
                source.getTeam().name(),
                source.getInstanceId(),
                null,
                null,
                skill.getSkillCode(),
                null,
                false,
                source.getCurrentHp(),
                source.getMaxHp(),
                source.isAlive(),
                null,
                null,
                null,
                "Skill activated: " + skill.getSkillName()
        );

        effectProcessor.executeEffects(battle, source, effects);
    }

    /**
     * Thực thi nội tại sau khi adapter đã lọc đúng trigger và chance.
     * counterTargetInstanceId chỉ được truyền cho ON_DAMAGED.
     */
    public void executePassive(
            BattleState battle,
            BattleUnitState source,
            HeroSkillDefinition skill,
            BattlePassiveTrigger trigger,
            List<BattleEffectConfig> effects,
            Long counterTargetInstanceId
    ) {
        Objects.requireNonNull(battle, "Battle must not be null");
        Objects.requireNonNull(source, "Source must not be null");
        Objects.requireNonNull(skill, "Skill must not be null");
        Objects.requireNonNull(trigger, "Trigger must not be null");
        Objects.requireNonNull(effects, "Effects must not be null");

        if (battle.isFinished() || !source.isAlive() || effects.isEmpty()) {
            return;
        }

        battle.getEventCollector().record(
                BattleEventType.PASSIVE,
                source.getTeam().name(),
                source.getInstanceId(),
                null,
                null,
                skill.getSkillCode(),
                null,
                false,
                source.getCurrentHp(),
                source.getMaxHp(),
                source.isAlive(),
                null,
                trigger.name(),
                null,
                "Passive activated: " + skill.getSkillName()
        );

        effectProcessor.executeEffects(
                battle,
                source,
                effects,
                trigger == BattlePassiveTrigger.ON_DAMAGED
                        ? counterTargetInstanceId
                        : null
        );
    }
}
