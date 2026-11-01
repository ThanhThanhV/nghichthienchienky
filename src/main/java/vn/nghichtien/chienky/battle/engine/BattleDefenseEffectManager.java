
package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleDefenseEffect;
import vn.nghichtien.chienky.battle.domain.BattleEffectType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Quản lý hiệu ứng né tránh và chặn sát thương.
 */
@Component
public class BattleDefenseEffectManager {

    private final List<BattleDefenseEffect> effects =
            new ArrayList<>();

    /**
     * Thêm hiệu ứng phòng thủ.
     */
    public void apply(
            BattleEffectType type,
            String effectCode,
            BigDecimal value,
            int duration
    ) {
        effects.add(new BattleDefenseEffect(
                type,
                effectCode,
                value,
                duration
        ));
    }

    /**
     * Kiểm tra mục tiêu có né được đòn đánh hay không.
     *
     * Nếu có nhiều hiệu ứng né, xác suất được cộng lại và giới hạn 100%.
     */
    public boolean tryEvade() {
        BigDecimal chance = effects.stream()
                .filter(effect ->
                        effect.getType() == BattleEffectType.EVASION)
                .map(BattleDefenseEffect::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(BigDecimal.ONE);

        if (chance.signum() <= 0) {
            return false;
        }

        double random = ThreadLocalRandom.current().nextDouble();

        return BigDecimal.valueOf(random).compareTo(chance) < 0;
    }

    /**
     * Tính sát thương sau khi áp dụng BLOCK.
     *
     * Nhiều hiệu ứng BLOCK được cộng tỷ lệ giảm và giới hạn 100%.
     */
    public BigDecimal applyBlock(BigDecimal damage) {
        if (damage == null || damage.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal reduction = effects.stream()
                .filter(effect ->
                        effect.getType() == BattleEffectType.BLOCK)
                .map(BattleDefenseEffect::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(BigDecimal.ONE);

        return damage.multiply(BigDecimal.ONE.subtract(reduction));
    }

    /**
     * Giảm thời hạn các hiệu ứng phòng thủ.
     */
    public void decreaseDurations() {
        Iterator<BattleDefenseEffect> iterator = effects.iterator();

        while (iterator.hasNext()) {
            if (iterator.next().decreaseDuration()) {
                iterator.remove();
            }
        }
    }

    public List<BattleDefenseEffect> getEffects() {
        return List.copyOf(effects);
    }

    public void clear() {
        effects.clear();
    }

    /**
     * Tính sát thương phản lại dựa trên tổng tỷ lệ REFLECT.
     *
     * @param receivedDamage sát thương thực tế mục tiêu nhận.
     * @return sát thương phản lại.
     */
    public BigDecimal calculateReflectDamage(BigDecimal receivedDamage) {
        if (receivedDamage == null || receivedDamage.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal reflectPercent = effects.stream()
                .filter(effect -> effect.getType() == BattleEffectType.REFLECT)
                .map(BattleDefenseEffect::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(BigDecimal.ONE);

        return receivedDamage.multiply(reflectPercent)
                .setScale(6, RoundingMode.HALF_UP);
    }
}
