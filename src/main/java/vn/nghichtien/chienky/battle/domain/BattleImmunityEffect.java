
package vn.nghichtien.chienky.battle.domain;

import java.util.Objects;

/**
 * Lưu một hiệu ứng miễn nhiễm trên nhân vật.
 */
public class BattleImmunityEffect {

    private final BattleImmunityType type;
    private final String effectCode;
    private int remainingDuration;

    public BattleImmunityEffect(
            BattleImmunityType type,
            String effectCode,
            int duration
    ) {
        this.type = Objects.requireNonNull(type);
        this.effectCode = Objects.requireNonNull(effectCode);

        if (duration <= 0) {
            throw new IllegalArgumentException(
                    "Immunity duration must be positive"
            );
        }

        this.remainingDuration = duration;
    }

    /**
     * Giảm thời hạn miễn nhiễm một round.
     *
     * @return true nếu miễn nhiễm đã hết hạn.
     */
    public boolean decreaseDuration() {
        remainingDuration--;
        return remainingDuration <= 0;
    }

    public BattleImmunityType getType() {
        return type;
    }

    public String getEffectCode() {
        return effectCode;
    }

    public int getRemainingDuration() {
        return remainingDuration;
    }

    public void extendToMax(int duration) {
        remainingDuration = Math.max(remainingDuration, duration);
    }
}
