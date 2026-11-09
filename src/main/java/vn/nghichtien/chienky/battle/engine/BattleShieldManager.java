
package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleShield;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Quản lý các lớp khiên của một nhân vật.
 */
@Component
public class BattleShieldManager {

    private final List<BattleShield> shields = new ArrayList<>();

    /**
     * Thêm một lớp khiên mới.
     */
    public void addShield(
            String effectCode,
            String sourceTeam,
            Long sourceInstanceId,
            BigDecimal amount,
            int duration
    ) {
        shields.add(new BattleShield(
                effectCode,
                sourceTeam,
                sourceInstanceId,
                amount,
                duration
        ));
    }

    /**
     * Phân bổ sát thương lần lượt qua các lớp khiên.
     *
     * @return lượng sát thương còn lại sau khi hấp thụ.
     */
    public BigDecimal absorbDamage(BigDecimal damage) {
        if (damage == null || damage.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal remainingDamage = damage;

        Iterator<BattleShield> iterator = shields.iterator();

        while (iterator.hasNext() && remainingDamage.signum() > 0) {
            BattleShield shield = iterator.next();

            BigDecimal absorbed = shield.absorb(remainingDamage);
            remainingDamage = remainingDamage.subtract(absorbed);

            if (shield.isDepleted()) {
                iterator.remove();
            }
        }

        return remainingDamage;
    }

    /**
     * Giảm thời hạn tất cả khiên.
     */
    public void decreaseDurations() {
        decreaseDurations(List.copyOf(shields));
    }

    /**
     * Chỉ giảm thời hạn khiên đã tồn tại từ đầu round.
     */
    public void decreaseDurations(List<BattleShield> roundStartShields) {
        Iterator<BattleShield> iterator = shields.iterator();

        while (iterator.hasNext()) {
            BattleShield shield = iterator.next();
            if (roundStartShields.contains(shield)
                    && shield.decreaseDuration()) {
                iterator.remove();
            }
        }
    }

    public BigDecimal getTotalShield() {
        return shields.stream()
                .map(BattleShield::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<BattleShield> getShields() {
        return List.copyOf(shields);
    }

    public void clear() {
        shields.clear();
    }
}
