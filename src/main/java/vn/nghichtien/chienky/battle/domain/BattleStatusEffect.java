
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

    private int remainingDuration;

    /**
     * Khởi tạo modifier chỉ số.
     *
     * valuePercent sử dụng đơn vị phần trăm.
     * Ví dụ: 20 tương đương tăng 20%.
     */
    public BattleStatusEffect(
            String effectCode,
            String sourceTeam,
            Long sourceInstanceId,
            BattleStatType statType,
            BigDecimal valuePercent,
            boolean buff,
            int duration
    ) {
        this.effectCode = Objects.requireNonNull(effectCode);
        this.sourceTeam = sourceTeam;
        this.sourceInstanceId = sourceInstanceId;
        this.statType = Objects.requireNonNull(statType);
        this.valuePercent = Objects.requireNonNull(valuePercent);
        this.buff = buff;

        if (valuePercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Modifier value must not be negative"
            );
        }

        if (duration <= 0) {
            throw new IllegalArgumentException(
                    "Duration must be positive"
            );
        }

        this.duration = duration;
        this.remainingDuration = duration;
    }

    /**
     * Giảm thời hạn hiệu ứng một round.
     *
     * @return true nếu hiệu ứng đã hết hạn.
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

    public String getSourceTeam() {
        return sourceTeam;
    }

    public Long getSourceInstanceId() {
        return sourceInstanceId;
    }

    public BattleStatType getStatType() {
        return statType;
    }

    public BigDecimal getValuePercent() {
        return valuePercent;
    }

    public boolean isBuff() {
        return buff;
    }

    public int getDuration() {
        return duration;
    }

    public int getRemainingDuration() {
        return remainingDuration;
    }
}
