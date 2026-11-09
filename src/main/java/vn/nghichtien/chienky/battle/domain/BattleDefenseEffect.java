
package vn.nghichtien.chienky.battle.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Hiệu ứng né tránh hoặc chặn sát thương.
 */
public class BattleDefenseEffect {

    private final BattleEffectType type;

    private final String effectCode;

    private BigDecimal value;

    private int remainingDuration;

    public BattleDefenseEffect(
            BattleEffectType type,
            String effectCode,
            BigDecimal value,
            int duration
    ) {
        if (type != BattleEffectType.EVASION
                && type != BattleEffectType.BLOCK
                && type != BattleEffectType.REFLECT) {
            throw new IllegalArgumentException(
                    "Only EVASION, BLOCK and REFLECT are supported"
            );
        }

        this.type = Objects.requireNonNull(type);
        this.effectCode = Objects.requireNonNull(effectCode);
        this.value = Objects.requireNonNull(value);

        if (value.signum() < 0
                || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Defense effect value must be between 0 and 1"
            );
        }

        if (duration <= 0) {
            throw new IllegalArgumentException(
                    "Duration must be positive"
            );
        }

        this.remainingDuration = duration;
    }

    /**
     * Giảm thời hạn hiệu ứng.
     *
     * @return true nếu hiệu ứng đã hết hạn.
     */
    public boolean decreaseDuration() {
        remainingDuration--;
        return remainingDuration <= 0;
    }

    /**
     * Làm mới thời hạn và giữ mức phòng thủ mạnh nhất khi tái áp dụng.
     */
    public void refresh(BigDecimal newValue, int duration) {
        Objects.requireNonNull(newValue);
        if (newValue.signum() < 0 || newValue.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "Defense effect value must be between 0 and 1"
            );
        }
        if (duration <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        value = value.max(newValue);
        remainingDuration = Math.max(remainingDuration, duration);
    }

    public BattleEffectType getType() {
        return type;
    }

    public String getEffectCode() {
        return effectCode;
    }

    public BigDecimal getValue() {
        return value;
    }

    public int getRemainingDuration() {
        return remainingDuration;
    }
}
