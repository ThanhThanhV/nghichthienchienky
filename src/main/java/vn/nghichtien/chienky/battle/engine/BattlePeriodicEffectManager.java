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

    public void add(BattlePeriodicEffect effect) {
        effects.add(Objects.requireNonNull(effect));
    }

    public List<BattlePeriodicEffect> getEffects() {
        return List.copyOf(effects);
    }

    public void remove(BattlePeriodicEffect effect) {
        effects.remove(effect);
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
