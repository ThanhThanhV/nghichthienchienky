package vn.nghichtien.chienky.battle.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Đại diện cho một hiệu ứng trạng thái đang tồn tại trên nhân vật.
 */
public class BattleStatusEffect {

    private final String effectCode;
    private final String sourceTeam;
    private final Long sourceInstanceId;
    private final BattleStatType statType;
    private final BigDecimal valuePercent;
    private final boolean buff;
    private final int duration;
    private final boolean permanent;
    private int remainingDuration;

    public BattleStatusEffect(
            String effectCode,
            String sourceTeam,
            Long sourceInstanceId,
            BattleStatType statType,
            BigDecimal valuePercent,
            boolean buff,
            int duration
    ) {
        this(effectCode, sourceTeam, sourceInstanceId, statType,
                valuePercent, buff, duration, false);
    }

    /**
     * duration = 0 chỉ hợp lệ khi permanent = true.
     */
    public BattleStatusEffect(
            String effectCode,
            String sourceTeam,
            Long sourceInstanceId,
            BattleStatType statType,
            BigDecimal valuePercent,
            boolean buff,
            int duration,
            boolean permanent
    ) {
        this.effectCode = Objects.requireNonNull(effectCode);
        this.sourceTeam = sourceTeam;
        this.sourceInstanceId = sourceInstanceId;
        this.statType = Objects.requireNonNull(statType);
        this.valuePercent = Objects.requireNonNull(valuePercent);
        this.buff = buff;
        this.permanent = permanent;

        if (valuePercent.signum() < 0) {
            throw new IllegalArgumentException("Modifier value must not be negative");
        }
        if (permanent ? duration != 0 : duration <= 0) {
            throw new IllegalArgumentException(
                    permanent ? "Permanent modifier duration must be zero"
                            : "Duration must be positive"
            );
        }

        this.duration = duration;
        this.remainingDuration = duration;
    }

    public boolean decreaseDuration() {
        if (permanent) {
            return false;
        }
        if (remainingDuration > 0) {
            remainingDuration--;
        }
        return remainingDuration <= 0;
    }

    public String getEffectCode() { return effectCode; }
    public String getSourceTeam() { return sourceTeam; }
    public Long getSourceInstanceId() { return sourceInstanceId; }
    public BattleStatType getStatType() { return statType; }
    public BigDecimal getValuePercent() { return valuePercent; }
    public boolean isBuff() { return buff; }
    public int getDuration() { return duration; }
    public int getRemainingDuration() { return remainingDuration; }
    public boolean isPermanent() { return permanent; }
}
