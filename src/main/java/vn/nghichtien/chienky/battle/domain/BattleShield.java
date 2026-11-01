
package vn.nghichtien.chienky.battle.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Một lớp khiên hấp thụ sát thương.
 */
public class BattleShield {

    private final String effectCode;
    private final String sourceTeam;
    private final Long sourceInstanceId;

    private BigDecimal remainingAmount;
    private int remainingDuration;

    public BattleShield(
            String effectCode,
            String sourceTeam,
            Long sourceInstanceId,
            BigDecimal amount,
            int duration
    ) {
        this.effectCode = Objects.requireNonNull(effectCode);
        this.sourceTeam = Objects.requireNonNull(sourceTeam);
        this.sourceInstanceId = Objects.requireNonNull(sourceInstanceId);

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Shield amount must be positive"
            );
        }

        if (duration <= 0) {
            throw new IllegalArgumentException(
                    "Shield duration must be positive"
            );
        }

        this.remainingAmount = amount;
        this.remainingDuration = duration;
    }

    /**
     * Hấp thụ sát thương vào khiên.
     *
     * @return lượng sát thương đã được khiên hấp thụ.
     */
    public BigDecimal absorb(BigDecimal damage) {
        if (damage == null || damage.signum() <= 0
                || remainingAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal absorbed = remainingAmount.min(damage);
        remainingAmount = remainingAmount.subtract(absorbed);

        return absorbed;
    }

    /**
     * Giảm thời hạn khiên một round.
     *
     * @return true nếu khiên hết thời hạn hoặc hết lượng hấp thụ.
     */
    public boolean decreaseDuration() {
        remainingDuration--;

        return remainingDuration <= 0
                || remainingAmount.signum() <= 0;
    }

    public boolean isDepleted() {
        return remainingAmount.signum() <= 0;
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

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public int getRemainingDuration() {
        return remainingDuration;
    }
}
