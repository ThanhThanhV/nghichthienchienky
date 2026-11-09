
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
        BattleDefenseEffect existing = effects.stream()
                .filter(item -> item.getEffectCode().equals(effectCode))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            if (existing.getType() != type) {
                throw new IllegalArgumentException(
                        "Conflicting defense effect type: " + effectCode
                );
            }
            existing.refresh(value, duration);
            return;
        }

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
    public boolean tryEvade(BigDecimal accuracy) {
        BigDecimal effectiveAccuracy = accuracy == null
                ? BigDecimal.ZERO
                : accuracy.max(BigDecimal.ZERO).min(BigDecimal.ONE);

        BigDecimal evasionChance = effects.stream()
                .filter(effect ->
                        effect.getType() == BattleEffectType.EVASION)
                .map(BattleDefenseEffect::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(BigDecimal.ONE);

        // ACCURACY và EVASION đều dùng ratio 0..1.
        // 0.10 ACCURACY triệt tiêu 10% tỷ lệ né tránh.
        BigDecimal accuracyChance = effectiveAccuracy;
        BigDecimal finalChance = evasionChance
                .subtract(accuracyChance)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE);

        if (finalChance.signum() <= 0) {
            return false;
        }

        double random = ThreadLocalRandom.current().nextDouble();

        return BigDecimal.valueOf(random).compareTo(finalChance) < 0;
    }

    /**
     * Tương thích với các vị trí gọi cũ chưa truyền ACCURACY.
     */
    public boolean tryEvade() {
        return tryEvade(BigDecimal.ZERO);
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
        decreaseDurations(List.copyOf(effects));
    }

    /**
     * Chỉ giảm thời hạn hiệu ứng phòng thủ đã tồn tại từ đầu round.
     */
    public void decreaseDurations(List<BattleDefenseEffect> roundStartEffects) {
        Iterator<BattleDefenseEffect> iterator = effects.iterator();

        while (iterator.hasNext()) {
            BattleDefenseEffect effect = iterator.next();
            if (roundStartEffects.contains(effect)
                    && effect.decreaseDuration()) {
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
