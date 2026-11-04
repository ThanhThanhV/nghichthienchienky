package vn.nghichtien.chienky.battle.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Cấu hình một hiệu ứng được thực thi trong trận chiến. */
public record BattleEffectConfig(
        String effectCode, BattleEffectType effectType, BattleTargetMode targetMode,
        BigDecimal multiplier, BigDecimal flatValue, Integer duration, Integer priority,
        BattleStatType statType, BattleStatusType statusType,
        BattleImmunityType immunityType, BattleDispelType dispelType,
        boolean permanent, int initialStacks, int maxStacks, boolean purgeable,
        BattleDamageType damageType, BigDecimal roleBonusPercent,
        boolean roleBonusTankSupport, String condition, Integer triggerRound,
        Integer untilRound, String eventSkillCode
) {
    public BattleEffectConfig(String effectCode, BattleEffectType effectType,
            BattleTargetMode targetMode, BigDecimal multiplier, BigDecimal flatValue,
            Integer duration, Integer priority, BattleStatType statType,
            BattleStatusType statusType, BattleImmunityType immunityType,
            BattleDispelType dispelType) {
        this(effectCode, effectType, targetMode, multiplier, flatValue, duration,
                priority, statType, statusType, immunityType, dispelType,
                false, 1, 1, true, BattleDamageType.PHYSICAL,
                BigDecimal.ZERO, false, null, null, null, null);
    }

    public BattleEffectConfig(String effectCode, BattleEffectType effectType,
            BattleTargetMode targetMode, BigDecimal multiplier, BigDecimal flatValue,
            Integer duration, Integer priority, BattleStatType statType,
            BattleStatusType statusType, BattleImmunityType immunityType,
            BattleDispelType dispelType, boolean permanent) {
        this(effectCode, effectType, targetMode, multiplier, flatValue, duration,
                priority, statType, statusType, immunityType, dispelType,
                permanent, 1, 1, true, BattleDamageType.PHYSICAL,
                BigDecimal.ZERO, false, null, null, null, null);
    }

    /** Tương thích constructor 15 tham số đã dùng trước khi mở rộng mechanics. */
    public BattleEffectConfig(String effectCode, BattleEffectType effectType,
            BattleTargetMode targetMode, BigDecimal multiplier, BigDecimal flatValue,
            Integer duration, Integer priority, BattleStatType statType,
            BattleStatusType statusType, BattleImmunityType immunityType,
            BattleDispelType dispelType, boolean permanent, int initialStacks,
            int maxStacks, boolean purgeable) {
        this(effectCode, effectType, targetMode, multiplier, flatValue, duration,
                priority, statType, statusType, immunityType, dispelType,
                permanent, initialStacks, maxStacks, purgeable,
                BattleDamageType.PHYSICAL, BigDecimal.ZERO, false,
                null, null, null, null);
    }

    public BattleEffectConfig {
        Objects.requireNonNull(effectCode);
        Objects.requireNonNull(effectType);
        Objects.requireNonNull(targetMode);
        multiplier = multiplier == null ? BigDecimal.ZERO : multiplier;
        flatValue = flatValue == null ? BigDecimal.ZERO : flatValue;
        duration = duration == null ? 0 : duration;
        priority = priority == null ? 0 : priority;
        damageType = damageType == null ? BattleDamageType.PHYSICAL : damageType;
        roleBonusPercent = roleBonusPercent == null ? BigDecimal.ZERO : roleBonusPercent;
        if (multiplier.signum() < 0 || flatValue.signum() < 0 || roleBonusPercent.signum() < 0)
            throw new IllegalArgumentException("Effect values must not be negative");
        if (duration < 0) throw new IllegalArgumentException("Duration must not be negative");
        if (initialStacks < 1 || maxStacks < 1 || initialStacks > maxStacks)
            throw new IllegalArgumentException("Invalid effect stack configuration");
        if (triggerRound != null && triggerRound < 1)
            throw new IllegalArgumentException("triggerRound must be positive");
        if (untilRound != null && untilRound < 1)
            throw new IllegalArgumentException("untilRound must be positive");
        if (triggerRound != null && untilRound != null && triggerRound > untilRound)
            throw new IllegalArgumentException("triggerRound cannot exceed untilRound");
        if (permanent && effectType != BattleEffectType.BUFF && effectType != BattleEffectType.DEBUFF)
            throw new IllegalArgumentException("Permanent mode is supported only for BUFF/DEBUFF");
        if ((effectType == BattleEffectType.BUFF || effectType == BattleEffectType.DEBUFF)
                && statType == null) throw new IllegalArgumentException("BUFF/DEBUFF requires statType");
        if (statType == BattleStatType.MAX_HP && !permanent)
            throw new IllegalArgumentException("MAX_HP modifier must be permanent");
        if (effectType == BattleEffectType.CC && statusType == null)
            throw new IllegalArgumentException("CC requires statusType");
        if (effectType == BattleEffectType.IMMUNITY && immunityType == null)
            throw new IllegalArgumentException("IMMUNITY requires immunityType");
        if (effectType == BattleEffectType.DISPEL && dispelType == null)
            throw new IllegalArgumentException("DISPEL requires dispelType");
        if ((effectType == BattleEffectType.DOT || effectType == BattleEffectType.HOT) && duration <= 0)
            throw new IllegalArgumentException("DOT/HOT requires a positive duration");
        if ((effectType == BattleEffectType.DOT || effectType == BattleEffectType.HOT)
                && multiplier.signum() == 0 && flatValue.signum() == 0)
            throw new IllegalArgumentException("DOT/HOT requires a positive periodic amount");
        if (effectType == BattleEffectType.ACTION_LOCK && duration <= 0)
            throw new IllegalArgumentException("ACTION_LOCK requires a positive duration");
    }
}
