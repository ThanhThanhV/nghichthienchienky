package vn.nghichtien.chienky.battle.engine;

import vn.nghichtien.chienky.battle.domain.BattlePeriodicEffect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Quản lý DOT/HOT đang gắn trên một nhân vật.
 */
public class BattlePeriodicEffectManager {

    private final List<BattlePeriodicEffect> effects = new ArrayList<>();

    /**
     * Thêm hiệu ứng mới; cùng effectCode và effectType sẽ được làm mới,
     * không cộng dồn vô hạn cùng một hiệu ứng từ cùng cấu hình kỹ năng.
     */
    public void add(BattlePeriodicEffect effect) {
        Objects.requireNonNull(effect);
        BattlePeriodicEffect existing = effects.stream()
                .filter(item -> item.getEffectType() == effect.getEffectType()
                        && item.getEffectCode().equals(effect.getEffectCode())
                        && item.getSourceTeam().equals(effect.getSourceTeam())
                        && item.getSourceInstanceId().equals(effect.getSourceInstanceId()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            // Tái áp dụng từ cùng nguồn: cập nhật lượng tick theo cấu hình mới
            // và làm mới duration, không tạo thêm bản sao DOT/HOT.
            existing.refresh(effect.getAmountPerTick(), effect.getRemainingDuration());
            return;
        }

        // Cùng mã từ nguồn khác nhau vẫn là các hiệu ứng độc lập để giữ
        // attribution cho DAMAGE/HEAL và không ghi đè hiệu ứng của đồng đội.
        effects.add(effect);
    }

    public List<BattlePeriodicEffect> getEffects() {
        return List.copyOf(effects);
    }

    public void remove(BattlePeriodicEffect effect) {
        effects.remove(effect);
    }

    /**
     * Xóa DOT hoặc HOT khi bị giải trừ.
     *
     * @return số hiệu ứng đã bị xóa.
     */
    public int dispel(vn.nghichtien.chienky.battle.domain.BattleEffectType type) {
        int before = effects.size();
        effects.removeIf(effect -> effect.getEffectType() == type);
        return before - effects.size();
    }

    public void clear() {
        effects.clear();
    }

    /**
     * Xóa các hiệu ứng hết hạn sau khi đã xử lý tick.
     */
    public void decreaseDurations() {
        Iterator<BattlePeriodicEffect> iterator = effects.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().decreaseDuration()) {
                iterator.remove();
            }
        }
    }
}
