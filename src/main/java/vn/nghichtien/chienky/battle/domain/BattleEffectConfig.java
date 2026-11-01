
package vn.nghichtien.chienky.battle.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Cấu hình một hiệu ứng được thực thi trong trận chiến.
 */
public record BattleEffectConfig(
        String effectCode,
        BattleEffectType effectType,
        BattleTargetMode targetMode,
        BigDecimal multiplier,
        BigDecimal flatValue,
        Integer duration,
        Integer priority,
        BattleStatType statType,
        BattleStatusType statusType,
        BattleImmunityType immunityType,
        BattleDispelType dispelType
) {

    public BattleEffectConfig {
        Objects.requireNonNull(effectCode);
        Objects.requireNonNull(effectType);
        Objects.requireNonNull(targetMode);

        multiplier = multiplier == null
                ? BigDecimal.ZERO : multiplier;

        flatValue = flatValue == null
                ? BigDecimal.ZERO : flatValue;

        duration = duration == null ? 0 : duration;
        priority = priority == null ? 0 : priority;

        if (multiplier.signum() < 0 || flatValue.signum() < 0) {
            throw new IllegalArgumentException(
                    "Effect values must not be negative"
            );
        }

        if (duration < 0) {
            throw new IllegalArgumentException(
                    "Duration must not be negative"
            );
        }

        if ((effectType == BattleEffectType.BUFF
                || effectType == BattleEffectType.DEBUFF)
                && statType == null) {
            throw new IllegalArgumentException(
                    "BUFF/DEBUFF requires statType"
            );
        }

        if (effectType == BattleEffectType.CC && statusType == null) {
            throw new IllegalArgumentException(
                    "CC requires statusType"
            );
        }

        if (effectType == BattleEffectType.IMMUNITY
                && immunityType == null) {
            throw new IllegalArgumentException(
                    "IMMUNITY requires immunityType"
            );
        }

        if (effectType == BattleEffectType.DISPEL
                && dispelType == null) {
            throw new IllegalArgumentException(
                    "DISPEL requires dispelType"
            );
        }
    }
}
