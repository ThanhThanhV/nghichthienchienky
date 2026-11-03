
package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleImmunityEffect;
import vn.nghichtien.chienky.battle.domain.BattleImmunityType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Quản lý các hiệu ứng miễn nhiễm của một nhân vật.
 */
@Component
public class BattleImmunityManager {

    private final List<BattleImmunityEffect> effects =
            new ArrayList<>();

    /**
     * Thêm hoặc gia hạn miễn nhiễm cùng loại.
     */
    public void apply(
            BattleImmunityType type,
            String effectCode,
            int duration
    ) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(effectCode);

        if (duration <= 0) {
            throw new IllegalArgumentException(
                    "Immunity duration must be positive"
            );
        }

        BattleImmunityEffect existing = effects.stream()
                .filter(effect -> effect.getType() == type)
                .findFirst()
                .orElse(null);

        if (existing != null) {
            // Cùng một hiệu ứng miễn nhiễm không tự gia hạn mỗi lần ACTIVE
            // lặp lại; thời hạn tối đa vẫn được tính từ lần áp dụng đầu tiên.
            if (existing.getEffectCode().equals(effectCode)) {
                return;
            }
            existing.extendToMax(duration);
            return;
        }

        effects.add(new BattleImmunityEffect(
                type,
                effectCode,
                duration
        ));
    }

    /**
     * Kiểm tra có miễn nhiễm với loại hiệu ứng hay không.
     */
    public boolean isImmune(BattleImmunityType type) {
        return effects.stream()
                .anyMatch(effect -> effect.getType() == type);
    }

    /**
     * Giảm thời hạn các hiệu ứng miễn nhiễm.
     */
    public void decreaseDurations() {
        Iterator<BattleImmunityEffect> iterator =
                effects.iterator();

        while (iterator.hasNext()) {
            if (iterator.next().decreaseDuration()) {
                iterator.remove();
            }
        }
    }

    public List<BattleImmunityEffect> getEffects() {
        return List.copyOf(effects);
    }

    public void clear() {
        effects.clear();
    }
}
