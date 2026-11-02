package vn.nghichtien.chienky.battle.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Hiệu ứng sát thương hoặc hồi máu được kích hoạt định kỳ theo round.
 */
public class BattlePeriodicEffect {

    private final String effectCode;
    private final BattleEffectType effectType;
    private final String sourceTeam;
    private final Long sourceInstanceId;
    private final BigDecimal amountPerTick;
    private int remainingDuration;

    public BattlePeriodicEffect(
            String effectCode,
            BattleEffectType effectType,
            String sourceTeam,
            Long sourceInstanceId,
            BigDecimal amountPerTick,
            int duration
    ) {
        this.effectCode = Objects.requireNonNull(effectCode);
        this.effectType = Objects.requireNonNull(effectType);
        this.sourceTeam = Objects.requireNonNull(sourceTeam);
        this.sourceInstanceId = Objects.requireNonNull(sourceInstanceId);
        this.amountPerTick = Objects.requireNonNull(amountPerTick);

        if (effectType != BattleEffectType.DOT
                && effectType != BattleEffectType.HOT) {
            throw new IllegalArgumentException(
                    "Periodic effect type must be DOT or HOT"
            );
        }
        if (amountPerTick.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Periodic amount must be positive"
            );
        }
        if (duration <= 0) {
            throw new IllegalArgumentException(
                    "Periodic duration must be positive"
            );
        }

        this.remainingDuration = duration;
    }

    /**
     * Giảm thời hạn sau khi hiệu ứng đã tick trong round hiện tại.
     *
     * @return true nếu hiệu ứng đã hết thời hạn.
     */
    public boolean decreaseDuration() {
        if (remainingDuration > 0) {
            remainingDuration--;
        }
        return remainingDuration <= 0;
    }

    public String getEffectCode() {
        return effectCode;
    }

    public BattleEffectType getEffectType() {
        return effectType;
    }

    public String getSourceTeam() {
        return sourceTeam;
    }

    public Long getSourceInstanceId() {
        return sourceInstanceId;
    }

    public BigDecimal getAmountPerTick() {
        return amountPerTick;
    }

    public int getRemainingDuration() {
        return remainingDuration;
    }
}
