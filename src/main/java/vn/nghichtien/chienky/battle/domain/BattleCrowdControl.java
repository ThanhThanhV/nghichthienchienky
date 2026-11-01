
package vn.nghichtien.chienky.battle.domain;

import java.util.Objects;

/**
 * Trạng thái khống chế hiện tại của một nhân vật.
 */
public class BattleCrowdControl {

    private final BattleStatusType type;

    private final String effectCode;

    private final String sourceTeam;

    private final Long sourceInstanceId;

    private int remainingDuration;

    public BattleCrowdControl(
            BattleStatusType type,
            String effectCode,
            String sourceTeam,
            Long sourceInstanceId,
            int duration
    ) {
        this.type = Objects.requireNonNull(type);
        this.effectCode = Objects.requireNonNull(effectCode);
        this.sourceTeam = sourceTeam;
        this.sourceInstanceId = sourceInstanceId;

        if (duration <= 0) {
            throw new IllegalArgumentException(
                    "Duration must be positive"
            );
        }

        this.remainingDuration = duration;
    }

    /**
     * Cập nhật thời hạn theo quy tắc MAX.
     */
    public void extendToMax(int duration) {
        remainingDuration = Math.max(remainingDuration, duration);
    }

    /**
     * Giảm thời hạn khống chế một round.
     *
     * @return true nếu CC đã hết hạn.
     */
    public boolean decreaseDuration() {
        if (remainingDuration > 0) {
            remainingDuration--;
        }

        return remainingDuration <= 0;
    }

    public BattleStatusType getType() {
        return type;
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

    public int getRemainingDuration() {
        return remainingDuration;
    }
}
