
package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleActionType;
import vn.nghichtien.chienky.battle.domain.BattleEventType;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTargetMode;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Thực thi kỹ năng chiến đấu và điều phối các hiệu ứng của kỹ năng.
 */
@Component
public class BattleSkillProcessor {

    private final BattleTargetSelector targetSelector;
    private final BattleDamageProcessor damageProcessor;

    public BattleSkillProcessor(
            BattleTargetSelector targetSelector,
            BattleDamageProcessor damageProcessor
    ) {
        this.targetSelector = targetSelector;
        this.damageProcessor = damageProcessor;
    }


    /**
     * Thực thi kỹ năng gây sát thương lên các mục tiêu được chọn.
     *
     * @param battle trận chiến hiện tại
     * @param source nhân vật sử dụng kỹ năng
     * @param actionType loại hành động
     * @param skillCode mã kỹ năng
     * @param skillMultiplier hệ số sát thương
     * @param targetMode chế độ lựa chọn mục tiêu
     */
    public void executeDamageSkill(
            BattleState battle,
            BattleUnitState source,
            BattleActionType actionType,
            String skillCode,
            BigDecimal skillMultiplier,
            BattleTargetMode targetMode
    ) {
        Objects.requireNonNull(battle, "Battle must not be null");
        Objects.requireNonNull(source, "Source must not be null");
        Objects.requireNonNull(actionType, "Action type must not be null");
        Objects.requireNonNull(skillCode, "Skill code must not be null");
        Objects.requireNonNull(skillMultiplier, "Skill multiplier must not be null");
        Objects.requireNonNull(targetMode, "Target mode must not be null");

        if (battle.isFinished() || !source.isAlive()) {
            return;
        }

        if (skillMultiplier.signum() < 0) {
            throw new IllegalArgumentException(
                    "Skill multiplier must not be negative"
            );
        }

        // Chỉ cho phép các hành động tấn công.
        if (actionType != BattleActionType.BASIC_ATTACK
                && actionType != BattleActionType.ACTIVE_SKILL
                && actionType != BattleActionType.ULTIMATE_SKILL) {
            throw new IllegalArgumentException(
                    "Unsupported damage action type: " + actionType
            );
        }

        // Ghi nhận loại kỹ năng được sử dụng.
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
                skillCode,
                null,
                false,
                source.getCurrentHp(),
                source.getMaxHp(),
                source.isAlive(),
                null,
                null,
                null,
                "Skill activated"
        );

        // Lựa chọn mục tiêu.
        List<BattleUnitState> targets = targetSelector.selectTargets(
                battle,
                source,
                targetMode
        );

        // Thực thi sát thương lần lượt trên từng mục tiêu.
        for (BattleUnitState target : targets) {
            if (battle.isFinished() || !source.isAlive()) {
                break;
            }

            if (!target.isAlive()) {
                continue;
            }

            damageProcessor.executeDamage(
                    battle,
                    source,
                    target,
                    skillMultiplier,
                    skillCode
            );
        }
    }


    /**
     * Chuyển loại hành động thành loại sự kiện chiến đấu.
     *
     * @param actionType Loại hành động.
     * @return Loại sự kiện tương ứng.
     */
    private BattleEventType toEventType(BattleActionType actionType) {
        return switch (actionType) {
            case ACTIVE_SKILL -> BattleEventType.ACTIVE_SKILL;
            case ULTIMATE_SKILL -> BattleEventType.ULTIMATE_SKILL;
            case BASIC_ATTACK -> BattleEventType.BASIC_ATTACK;
        };
    }
}
