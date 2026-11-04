
package vn.nghichtien.chienky.battle.domain;

import vn.nghichtien.chienky.battle.engine.BattleStatusEffectManager;

import java.math.BigDecimal;
import java.util.Objects;
import vn.nghichtien.chienky.battle.engine.BattleImmunityManager;
import vn.nghichtien.chienky.battle.engine.BattleShieldManager;
import vn.nghichtien.chienky.battle.engine.BattleDefenseEffectManager;
import vn.nghichtien.chienky.battle.engine.BattlePeriodicEffectManager;

/**
 * Lưu trạng thái chiến đấu của một nhân vật trong trận đấu.
 */
public class BattleUnitState {

    private final Long instanceId;
    private final BattleTeam team;
    private final String heroCode;
    private BigDecimal maxHp;
    private BigDecimal currentHp;

    private final BigDecimal attack;
    private final BigDecimal defense;
    private final BigDecimal speed;

    private BigDecimal crit = BigDecimal.ZERO;
    private BigDecimal critDamage = BigDecimal.ZERO;
    private BigDecimal accuracy = BigDecimal.ZERO;
    private BigDecimal resistance = BigDecimal.ZERO;
    private BigDecimal penFlat = BigDecimal.ZERO;
    private BigDecimal penPercent = BigDecimal.ZERO;
    private BigDecimal lifesteal = BigDecimal.ZERO;

    private final int row;
    private final int column;

    private final BattleStatusEffectManager statusEffectManager =
            new BattleStatusEffectManager();
    private final BattleImmunityManager immunityManager =
            new BattleImmunityManager();
    private final BattleShieldManager shieldManager =
            new BattleShieldManager();
    private final BattleDefenseEffectManager defenseEffectManager =
            new BattleDefenseEffectManager();
    private final BattlePeriodicEffectManager periodicEffectManager =
            new BattlePeriodicEffectManager();

    private int actionCount;
    private boolean extraActionPending;
    private boolean extraActionUsedThisRound;
    private boolean alive;
    private String role = "";
    private int actionLockedUntilRound;

    /**
     * Khởi tạo nhân vật có vị trí trong đội hình 3x3.
     */
    public BattleUnitState(
            Long instanceId,
            BattleTeam team,
            String heroCode,
            BigDecimal maxHp,
            BigDecimal attack,
            BigDecimal defense,
            BigDecimal speed,
            int row,
            int column
    ) {
        this.instanceId = Objects.requireNonNull(instanceId);
        this.team = Objects.requireNonNull(team);
        this.heroCode = Objects.requireNonNull(heroCode);
        this.maxHp = Objects.requireNonNull(maxHp);
        this.attack = Objects.requireNonNull(attack);
        this.defense = Objects.requireNonNull(defense);
        this.speed = Objects.requireNonNull(speed);

        if (maxHp.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Max HP must be positive");
        }

        if (attack.compareTo(BigDecimal.ZERO) < 0
                || defense.compareTo(BigDecimal.ZERO) < 0
                || speed.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Combat stats must not be negative"
            );
        }

        if (row < 0 || row > 2 || column < 0 || column > 2) {
            throw new IllegalArgumentException(
                    "Formation position must be within 3x3"
            );
        }

        this.row = row;
        this.column = column;
        this.currentHp = maxHp;
        this.actionCount = 0;
        this.alive = true;
    }

    /**
     * Constructor tương thích với code khởi tạo cũ.
     * Vị trí mặc định là hàng 0, cột 0.
     */
    public BattleUnitState(
            Long instanceId,
            BattleTeam team,
            String heroCode,
            BigDecimal maxHp,
            BigDecimal attack,
            BigDecimal defense,
            BigDecimal speed
    ) {
        this(
                instanceId,
                team,
                heroCode,
                maxHp,
                attack,
                defense,
                speed,
                0,
                0
        );
    }

    /**
     * Trừ HP theo lượng sát thương thực tế.
     */
    public BigDecimal takeDamage(BigDecimal damage) {
        Objects.requireNonNull(damage);

        if (damage.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Damage must not be negative"
            );
        }

        if (!alive || damage.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal actualDamage = damage.min(currentHp);
        currentHp = currentHp.subtract(actualDamage);

        if (currentHp.compareTo(BigDecimal.ZERO) <= 0) {
            currentHp = BigDecimal.ZERO;
            alive = false;

            // Khi nhân vật tử trận, toàn bộ hiệu ứng đang gắn trên đơn vị
            // kết thúc. REVIVE đưa nhân vật trở lại với trạng thái sạch,
            // tránh mang theo khiên/miễn nhiễm/CC hoặc buff từ trước khi chết.
            statusEffectManager.clear();
            immunityManager.clear();
            shieldManager.clear();
            defenseEffectManager.clear();
            periodicEffectManager.clear();
        }

        return actualDamage;
    }

    /**
     * Hồi sinh nhân vật đã tử vong với lượng HP được chỉ định.
     *
     * @param amount lượng HP khi hồi sinh, phải lớn hơn 0
     * @return lượng HP thực tế sau khi hồi sinh; trả về 0 nếu nhân vật còn sống
     */
    public BigDecimal revive(BigDecimal amount) {
        Objects.requireNonNull(amount, "Revive amount must not be null");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Revive amount must be positive");
        }

        if (alive) {
            return BigDecimal.ZERO;
        }

        currentHp = amount.min(maxHp);
        alive = true;
        return currentHp;
    }

    /**
     * Hồi HP, không vượt quá HP tối đa.
     */
    public BigDecimal heal(BigDecimal amount) {
        Objects.requireNonNull(amount);

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Heal amount must not be negative"
            );
        }

        if (!alive || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal actualHeal = amount.min(
                maxHp.subtract(currentHp)
        );

        currentHp = currentHp.add(actualHeal);

        return actualHeal;
    }

    public void incrementActionCount() {
        actionCount++;
    }

    /**
     * Đặt lại giới hạn EXTRA_ACTION khi bắt đầu round mới.
     */
    public void resetExtraActionForRound() {
        extraActionPending = false;
        extraActionUsedThisRound = false;
    }

    /**
     * Mỗi đơn vị chỉ nhận tối đa một EXTRA_ACTION trong một round.
     */
    public boolean grantExtraAction() {
        if (!alive || extraActionUsedThisRound || extraActionPending) {
            return false;
        }
        extraActionPending = true;
        extraActionUsedThisRound = true;
        return true;
    }

    public boolean consumeExtraAction() {
        if (!extraActionPending || !alive) {
            extraActionPending = false;
            return false;
        }
        extraActionPending = false;
        return true;
    }

    public Long getInstanceId() {
        return instanceId;
    }

    public BattleTeam getTeam() {
        return team;
    }

    public String getHeroCode() {
        return heroCode;
    }

    public BigDecimal getMaxHp() {
        return maxHp;
    }

    public void setRole(String role) {
        this.role = role == null ? "" : role;
    }

    public String getRole() {
        return role;
    }

    public boolean isTankOrSupport() {
        String normalized = role.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("tank") || normalized.contains("support")
                || normalized.contains("hỗ trợ") || normalized.contains("ho tro");
    }

    public void setActionLockedUntilRound(int round) {
        actionLockedUntilRound = Math.max(actionLockedUntilRound, round);
    }

    public boolean isActionLocked(int round) {
        return round > 0 && round <= actionLockedUntilRound;
    }

    /** Tăng HP tối đa vĩnh viễn và giữ nguyên tỷ lệ HP hiện tại. */
    public void increaseMaxHpPercent(BigDecimal percent) {
        if (percent == null || percent.signum() <= 0) return;
        BigDecimal oldMax = maxHp;
        BigDecimal increase = oldMax.multiply(percent)
                .divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);
        if (increase.signum() <= 0) return;
        BigDecimal currentRatio = currentHp.divide(oldMax, 8, java.math.RoundingMode.HALF_UP);
        maxHp = oldMax.add(increase);
        currentHp = maxHp.multiply(currentRatio).setScale(6, java.math.RoundingMode.HALF_UP)
                .min(maxHp);
    }

    public BigDecimal getCurrentHp() {
        return currentHp;
    }

    public BigDecimal getBaseAttack() {
        return attack;
    }

    public BigDecimal getBaseDefense() {
        return defense;
    }

    public BigDecimal getBaseSpeed() {
        return speed;
    }

    /**
     * Trả về ATK sau khi áp dụng BUFF và DEBUFF.
     */
    public BigDecimal getAttack() {
        return statusEffectManager.calculateEffectiveStat(
                BattleStatType.ATK,
                attack
        );
    }

    /**
     * Trả về DEF sau khi áp dụng BUFF và DEBUFF.
     */
    public BigDecimal getDefense() {
        return statusEffectManager.calculateEffectiveStat(
                BattleStatType.DEF,
                defense
        );
    }

    /**
     * Trả về SPEED sau khi áp dụng BUFF và DEBUFF.
     */
    public BigDecimal getSpeed() {
        return statusEffectManager.calculateEffectiveStat(
                BattleStatType.SPEED,
                speed
        ).add(statusEffectManager.getModifierPercent(BattleStatType.SPEED_FLAT));
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public int getActionCount() {
        return actionCount;
    }

    public boolean isAlive() {
        return alive;
    }

    public BattleStatusEffectManager getStatusEffectManager() {
        return statusEffectManager;
    }

    public BattleImmunityManager getImmunityManager() {
        return immunityManager;
    }

    public BattleShieldManager getShieldManager() {
        return shieldManager;
    }

    public BattleDefenseEffectManager getDefenseEffectManager() {
        return defenseEffectManager;
    }

    public BattlePeriodicEffectManager getPeriodicEffectManager() {
        return periodicEffectManager;
    }

    /**
     * Tính chỉ số chiến đấu sau khi áp dụng buff và debuff.
     *
     * @param baseStat chỉ số gốc.
     * @param statType loại chỉ số.
     * @return chỉ số hiệu lực sau khi áp dụng hiệu ứng.
     */
    private BigDecimal calculateEffectiveStat(
            BigDecimal baseStat,
            BattleStatType statType
    ) {
        return statusEffectManager.calculateEffectiveStat(
                statType,
                baseStat
        );
    }

    /**
     * Lấy tỷ lệ chí mạng sau khi áp dụng hiệu ứng.
     *
     * @return tỷ lệ chí mạng.
     */
    public BigDecimal getCrit() {
        return calculateEffectiveStat(crit, BattleStatType.CRIT);
    }

    /**
     * Lấy sát thương chí mạng sau khi áp dụng hiệu ứng.
     *
     * @return sát thương chí mạng.
     */
    public BigDecimal getCritDamage() {
        return calculateEffectiveStat(
                critDamage,
                BattleStatType.CRIT_DAMAGE
        );
    }

    /**
     * Lấy độ chính xác sau khi áp dụng hiệu ứng.
     *
     * @return độ chính xác.
     */
    public BigDecimal getAccuracy() {
        return calculateEffectiveStat(
                accuracy,
                BattleStatType.ACCURACY
        );
    }

    /**
     * Lấy kháng hiệu ứng sau khi áp dụng hiệu ứng.
     *
     * @return kháng hiệu ứng.
     */
    public BigDecimal getResistance() {
        return calculateEffectiveStat(
                resistance,
                BattleStatType.RESISTANCE
        );
    }

    /**
     * Lấy xuyên giáp cố định sau khi áp dụng hiệu ứng.
     *
     * @return xuyên giáp cố định.
     */
    public BigDecimal getPenFlat() {
        return calculateEffectiveStat(
                penFlat,
                BattleStatType.PEN_FLAT
        );
    }

    /**
     * Lấy xuyên giáp phần trăm sau khi áp dụng hiệu ứng.
     *
     * @return xuyên giáp phần trăm.
     */
    public BigDecimal getPenPercent() {
        return calculateEffectiveStat(
                penPercent,
                BattleStatType.PEN_PERCENT
        );
    }

    /**
     * Lấy tỷ lệ hút máu sau khi áp dụng hiệu ứng.
     *
     * @return tỷ lệ hút máu.
     */
    public BigDecimal getLifesteal() {
        return calculateEffectiveStat(
                lifesteal,
                BattleStatType.LIFESTEAL
        );
    }

    /** Phần trăm sát thương bị giảm từ các hiệu ứng đang còn hiệu lực. */
    public BigDecimal getDamageReductionPercent() {
        return getDamageReductionPercent(BattleDamageType.PHYSICAL);
    }

    public BigDecimal getDamageReductionPercent(BattleDamageType damageType) {
        BigDecimal reduction = statusEffectManager.getModifierPercent(BattleStatType.DAMAGE_REDUCTION);
        reduction = reduction.add(statusEffectManager.getModifierPercent(
                damageType == BattleDamageType.MAGIC
                        ? BattleStatType.MAGIC_DAMAGE_REDUCTION
                        : BattleStatType.PHYSICAL_DAMAGE_REDUCTION
        ));
        return reduction.max(BigDecimal.ZERO).min(new BigDecimal("100"));
    }

    public BigDecimal getDamageDealtPercent(BattleDamageType damageType) {
        BigDecimal bonus = statusEffectManager.getModifierPercent(BattleStatType.DAMAGE_DEALT);
        if (damageType == BattleDamageType.PHYSICAL) {
            bonus = bonus.add(statusEffectManager.getModifierPercent(BattleStatType.PHYSICAL_DAMAGE));
        }
        return bonus;
    }

    public BigDecimal getHealReceivedPercent() {
        return statusEffectManager.getModifierPercent(BattleStatType.HEAL_RECEIVED);
    }

    public BigDecimal getControlDurationReductionPercent() {
        return statusEffectManager.getModifierPercent(BattleStatType.CONTROL_DURATION_REDUCTION)
                .max(BigDecimal.ZERO).min(new BigDecimal("100"));
    }

    /** Modifier sát thương nhận: DEBUFF -15% tương ứng chịu thêm 15% sát thương. */
    public BigDecimal getDamageTakenModifierPercent() {
        return statusEffectManager.getModifierPercent(BattleStatType.DAMAGE_TAKEN);
    }

    /**
     * Cập nhật các chỉ số chiến đấu bổ sung.
     *
     * @param crit tỷ lệ chí mạng.
     * @param critDamage sát thương chí mạng.
     * @param accuracy độ chính xác.
     * @param resistance kháng hiệu ứng.
     * @param penFlat xuyên giáp cố định.
     * @param penPercent xuyên giáp phần trăm.
     * @param lifesteal tỷ lệ hút máu.
     */
    public void setCombatStats(
            BigDecimal crit,
            BigDecimal critDamage,
            BigDecimal accuracy,
            BigDecimal resistance,
            BigDecimal penFlat,
            BigDecimal penPercent,
            BigDecimal lifesteal
    ) {
        this.crit = Objects.requireNonNull(crit);
        this.critDamage = Objects.requireNonNull(critDamage);
        this.accuracy = Objects.requireNonNull(accuracy);
        this.resistance = Objects.requireNonNull(resistance);
        this.penFlat = Objects.requireNonNull(penFlat);
        this.penPercent = Objects.requireNonNull(penPercent);
        this.lifesteal = Objects.requireNonNull(lifesteal);
    }
}
