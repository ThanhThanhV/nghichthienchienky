package vn.nghichtien.chienky.battle.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Đại diện cho một modifier chỉ số đang tồn tại trên nhân vật. */
public class BattleStatusEffect {
    private final String effectCode;
    private final String sourceTeam;
    private final Long sourceInstanceId;
    private final BattleStatType statType;
    private final BigDecimal valuePerStack;
    private BigDecimal valuePercent;
    private final boolean buff;
    private final int duration;
    private final boolean permanent;
    private final boolean purgeable;
    private final int maxStacks;
    private int stackCount;
    private int remainingDuration;

    public BattleStatusEffect(String effectCode, String sourceTeam, Long sourceInstanceId,
            BattleStatType statType, BigDecimal valuePercent, boolean buff, int duration) {
        this(effectCode, sourceTeam, sourceInstanceId, statType, valuePercent, buff,
                duration, false, 1, 1, true);
    }

    public BattleStatusEffect(String effectCode, String sourceTeam, Long sourceInstanceId,
            BattleStatType statType, BigDecimal valuePercent, boolean buff,
            int duration, boolean permanent) {
        this(effectCode, sourceTeam, sourceInstanceId, statType, valuePercent, buff,
                duration, permanent, 1, 1, true);
    }

    public BattleStatusEffect(String effectCode, String sourceTeam, Long sourceInstanceId,
            BattleStatType statType, BigDecimal valuePerStack, boolean buff,
            int duration, boolean permanent, int initialStacks,
            int maxStacks, boolean purgeable) {
        this.effectCode = Objects.requireNonNull(effectCode);
        this.sourceTeam = sourceTeam;
        this.sourceInstanceId = sourceInstanceId;
        this.statType = Objects.requireNonNull(statType);
        this.valuePerStack = Objects.requireNonNull(valuePerStack);
        this.buff = buff;
        this.permanent = permanent;
        this.purgeable = purgeable;
        if (valuePerStack.signum() < 0) throw new IllegalArgumentException("Modifier value must not be negative");
        if (permanent ? duration != 0 : duration <= 0) throw new IllegalArgumentException(
                permanent ? "Permanent modifier duration must be zero" : "Duration must be positive");
        if (maxStacks < 1 || initialStacks < 1 || initialStacks > maxStacks)
            throw new IllegalArgumentException("Invalid modifier stack configuration");
        this.duration = duration;
        this.remainingDuration = duration;
        this.maxStacks = maxStacks;
        this.stackCount = initialStacks;
        this.valuePercent = valuePerStack.multiply(BigDecimal.valueOf(initialStacks));
    }

    /** Cộng thêm stack nhưng không vượt giới hạn đã khai báo. */
    public boolean addStacks(int amount) {
        if (amount <= 0 || stackCount >= maxStacks) return false;
        int added = Math.min(amount, maxStacks - stackCount);
        stackCount += added;
        valuePercent = valuePerStack.multiply(BigDecimal.valueOf(stackCount));
        return added > 0;
    }

    public boolean decreaseDuration() {
        if (permanent) return false;
        if (remainingDuration > 0) remainingDuration--;
        return remainingDuration <= 0;
    }

    /**
     * Làm mới thời hạn khi cùng hiệu ứng được áp dụng lại.
     * Không cộng dồn thời gian vượt quá duration cấu hình của lần áp dụng mới.
     */
    public void refreshDurationToMax(int duration) {
        if (permanent) return;
        if (duration <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        remainingDuration = Math.max(remainingDuration, duration);
    }


    public String getEffectCode() { return effectCode; }
    public String getSourceTeam() { return sourceTeam; }
    public Long getSourceInstanceId() { return sourceInstanceId; }
    public BattleStatType getStatType() { return statType; }
    public BigDecimal getValuePercent() { return valuePercent; }
    public BigDecimal getValuePerStack() { return valuePerStack; }
    public boolean isBuff() { return buff; }
    public int getDuration() { return duration; }
    public int getRemainingDuration() { return remainingDuration; }
    public boolean isPermanent() { return permanent; }
    public boolean isPurgeable() { return purgeable; }
    public int getStackCount() { return stackCount; }
    public int getMaxStacks() { return maxStacks; }
}
