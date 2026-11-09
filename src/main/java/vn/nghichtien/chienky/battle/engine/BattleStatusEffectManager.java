
package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleCrowdControl;
import vn.nghichtien.chienky.battle.domain.BattleStatType;
import vn.nghichtien.chienky.battle.domain.BattleStatusEffect;
import vn.nghichtien.chienky.battle.domain.BattleStatusType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Quản lý BUFF, DEBUFF và CC của một nhân vật trong trận chiến.
 */
@Component
public class BattleStatusEffectManager {

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal MAX_BUFF =
            new BigDecimal("100");

    private static final BigDecimal MAX_DEBUFF =
            new BigDecimal("-80");

    private final List<BattleStatusEffect> modifiers =
            new ArrayList<>();

    private BattleCrowdControl crowdControl;

    /**
     * Thêm modifier chỉ số.
     */
    public void addModifier(BattleStatusEffect effect) {
        Objects.requireNonNull(effect, "Effect must not be null");

        // Cùng một effectCode không được cộng dồn khi ACTIVE được gọi lại
        // trong chu kỳ ACTIVE -> ULTIMATE -> BASIC.
        BattleStatusEffect existing = modifiers.stream()
                .filter(item -> item.getEffectCode().equals(effect.getEffectCode()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.addStacks(effect.getStackCount());
            return;
        }

        modifiers.add(effect);
    }

    /**
     * Tính phần trăm thay đổi của một chỉ số.
     * BUFF tối đa +100%, DEBUFF tối đa -80%.
     */
    public BigDecimal getModifierPercent(BattleStatType statType) {
        BigDecimal buffTotal = BigDecimal.ZERO;
        BigDecimal debuffTotal = BigDecimal.ZERO;

        for (BattleStatusEffect effect : modifiers) {
            if (effect.getStatType() != statType) {
                continue;
            }

            if (effect.isBuff()) {
                buffTotal = buffTotal.add(effect.getValuePercent());
            } else {
                debuffTotal = debuffTotal.subtract(
                        effect.getValuePercent()
                );
            }
        }

        buffTotal = buffTotal.min(MAX_BUFF);
        debuffTotal = debuffTotal.max(MAX_DEBUFF);

        return buffTotal.add(debuffTotal);
    }

    /**
     * Tính chỉ số hiệu lực từ chỉ số gốc và modifier.
     */
    public BigDecimal calculateEffectiveStat(
            BattleStatType statType,
            BigDecimal baseValue
    ) {
        Objects.requireNonNull(statType);
        Objects.requireNonNull(baseValue);

        if (baseValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal modifier = getModifierPercent(statType);

        BigDecimal multiplier = BigDecimal.ONE.add(
                modifier.divide(ONE_HUNDRED, 8,
                        java.math.RoundingMode.HALF_UP)
        );

        return baseValue.multiply(multiplier);
    }

    /**
     * Áp dụng CC mới.
     * CC cùng loại giữ thời hạn dài hơn.
     * CC khác loại thay thế CC hiện tại.
     */
    public boolean applyCrowdControl(
            BattleStatusType type,
            String effectCode,
            String sourceTeam,
            Long sourceInstanceId,
            int duration,
            boolean immune
    ) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(effectCode);

        if (duration <= 0 || immune) {
            return false;
        }

        if (crowdControl != null
                && crowdControl.getType() == type) {
            crowdControl.extendToMax(duration);
            return true;
        }

        crowdControl = new BattleCrowdControl(
                type,
                effectCode,
                sourceTeam,
                sourceInstanceId,
                duration
        );

        return true;
    }

    public void removeCrowdControl() {
        crowdControl = null;
    }

    public boolean hasCrowdControl() {
        return crowdControl != null;
    }

    public boolean shouldSkipAction() {
        return crowdControl != null
                && (crowdControl.getType() == BattleStatusType.STUN
                || crowdControl.getType() == BattleStatusType.FREEZE);
    }

    public boolean isSilenced() {
        return crowdControl != null
                && crowdControl.getType() == BattleStatusType.SILENCE;
    }

    public BattleCrowdControl getCrowdControl() {
        return crowdControl;
    }

    public List<BattleStatusEffect> getModifiers() {
        return List.copyOf(modifiers);
    }

    public void dispelModifiers(boolean buff) {
        modifiers.removeIf(effect -> effect.isBuff() == buff && effect.isPurgeable());
    }

    /**
     * Giảm thời hạn trạng thái một round.
     */
    public void decreaseDurations() {
        decreaseDurations(List.copyOf(modifiers), crowdControl);
    }

    /**
     * Chỉ giảm thời hạn những hiệu ứng đã tồn tại tại thời điểm bắt đầu round.
     * Hiệu ứng được áp dụng giữa round sẽ có hiệu lực trọn round kế tiếp.
     */
    public void decreaseDurations(
            List<BattleStatusEffect> roundStartModifiers,
            BattleCrowdControl roundStartCrowdControl
    ) {
        Iterator<BattleStatusEffect> iterator = modifiers.iterator();

        while (iterator.hasNext()) {
            BattleStatusEffect effect = iterator.next();
            if (roundStartModifiers.contains(effect)
                    && effect.decreaseDuration()) {
                iterator.remove();
            }
        }

        if (crowdControl != null
                && crowdControl == roundStartCrowdControl
                && crowdControl.decreaseDuration()) {
            crowdControl = null;
        }
    }

    public void clear() {
        modifiers.clear();
        crowdControl = null;
    }

    /**
     * Xóa CC hiện tại.
     *
     * @return true nếu có CC được xóa.
     */
    public boolean dispelCrowdControl() {
        if (crowdControl == null) {
            return false;
        }

        crowdControl = null;
        return true;
    }
}
