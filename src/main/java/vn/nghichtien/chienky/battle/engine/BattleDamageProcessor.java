
package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleEventType;
import vn.nghichtien.chienky.battle.domain.BattleImmunityType;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;
import java.util.concurrent.ThreadLocalRandom;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.math.RoundingMode;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tính toán và áp dụng sát thương trong trận chiến.
 */
@Component
public class BattleDamageProcessor {

    private static final BigDecimal DEFENSE_CONSTANT =
            new BigDecimal("1000");

    private static final int DAMAGE_SCALE = 6;


    /**
     * Thực thi sát thương lên một mục tiêu.
     *
     * Thứ tự xử lý:
     * 1. Tính phòng thủ sau xuyên giáp.
     * 2. Tính sát thương cơ bản.
     * 3. Kiểm tra chí mạng.
     * 4. Kiểm tra né tránh.
     * 5. Kiểm tra miễn nhiễm sát thương.
     * 6. Áp dụng BLOCK.
     * 7. Áp dụng SHIELD.
     * 8. Trừ HP và ghi nhận DAMAGE.
     * 9. Áp dụng LIFESTEAL.
     * 10. Ghi nhận DEATH và kiểm tra kết thúc trận.
     *
     * @param battle trận đấu.
     * @param source nhân vật tấn công.
     * @param target mục tiêu.
     * @param skillMultiplier hệ số sát thương kỹ năng.
     * @param skillCode mã kỹ năng.
     */
    public void executeDamage(
            BattleState battle,
            BattleUnitState source,
            BattleUnitState target,
            BigDecimal skillMultiplier,
            String skillCode
    ) {
        Objects.requireNonNull(battle, "Battle must not be null");
        Objects.requireNonNull(source, "Source must not be null");
        Objects.requireNonNull(target, "Target must not be null");
        Objects.requireNonNull(skillMultiplier, "Skill multiplier must not be null");

        if (skillMultiplier.signum() < 0) {
            throw new IllegalArgumentException("Skill multiplier must not be negative");
        }

        if (!source.isAlive()
                || !target.isAlive()
                || battle.isFinished()) {
            return;
        }

        if (source.getTeam() == target.getTeam()) {
            throw new IllegalArgumentException("Source and target must be opponents");
        }

        // 1. Tính sát thương sau xuyên giáp.
        BigDecimal baseDamage = calculateBaseDamage(
                source,
                target,
                skillMultiplier
        );

        if (baseDamage.signum() <= 0) {
            return;
        }

        // 2. Kiểm tra chí mạng.
        boolean critical = isCritical(source);

        BigDecimal calculatedDamage = critical
                ? baseDamage.multiply(source.getCritDamage())
                .setScale(6, RoundingMode.HALF_UP)
                : baseDamage;

        if (calculatedDamage.signum() <= 0) {
            return;
        }

        // 3. Kiểm tra né tránh.
        if (target.getDefenseEffectManager().tryEvade()) {
            battle.getEventCollector().record(
                    BattleEventType.EVASION,
                    source.getTeam().name(),
                    source.getInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    skillCode,
                    BigDecimal.ZERO,
                    critical,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    target.isAlive(),
                    "EVASION",
                    "DEFENSE",
                    null,
                    "Attack evaded"
            );
            return;
        }

        // 4. Kiểm tra miễn nhiễm sát thương.
        if (target.getImmunityManager().isImmune(BattleImmunityType.DAMAGE)) {
            battle.getEventCollector().record(
                    BattleEventType.IMMUNITY,
                    source.getTeam().name(),
                    source.getInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    skillCode,
                    BigDecimal.ZERO,
                    critical,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    target.isAlive(),
                    "DAMAGE_IMMUNITY",
                    "IMMUNITY",
                    null,
                    "Damage immune"
            );
            return;
        }

        // 5. Áp dụng BLOCK.
        BigDecimal remainingDamage =
                target.getDefenseEffectManager().applyBlock(calculatedDamage);

        BigDecimal blockedDamage = calculatedDamage.subtract(remainingDamage);

        if (blockedDamage.signum() > 0) {
            battle.getEventCollector().record(
                    BattleEventType.BLOCK,
                    source.getTeam().name(),
                    source.getInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    skillCode,
                    blockedDamage,
                    critical,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    target.isAlive(),
                    "BLOCK",
                    "DEFENSE",
                    null,
                    "Damage blocked"
            );
        }

        // 6. Áp dụng SHIELD.
        remainingDamage = target.getShieldManager()
                .absorbDamage(remainingDamage);

        if (remainingDamage.signum() <= 0) {
            return;
        }

        // 7. Trừ HP.
        BigDecimal actualDamage = target.takeDamage(remainingDamage);

        // Phản sát thương trực tiếp, không kích hoạt REFLECT dây chuyền.
        if (actualDamage.signum() > 0 && source.isAlive()) {
            BigDecimal reflectedDamage = target.getDefenseEffectManager()
                    .calculateReflectDamage(actualDamage);

            if (reflectedDamage.signum() > 0) {
                BigDecimal actualReflectedDamage =
                        source.takeDamage(reflectedDamage);

                battle.getEventCollector().record(
                        BattleEventType.DAMAGE,
                        target.getTeam().name(),
                        target.getInstanceId(),
                        source.getTeam().name(),
                        source.getInstanceId(),
                        skillCode,
                        actualReflectedDamage,
                        false,
                        source.getCurrentHp(),
                        source.getMaxHp(),
                        source.isAlive(),
                        "REFLECT",
                        "DEFENSE",
                        null,
                        "Reflected damage"
                );

                if (!source.isAlive()) {
                    battle.getEventCollector().record(
                            BattleEventType.DEATH,
                            target.getTeam().name(),
                            target.getInstanceId(),
                            source.getTeam().name(),
                            source.getInstanceId(),
                            skillCode,
                            actualReflectedDamage,
                            false,
                            source.getCurrentHp(),
                            source.getMaxHp(),
                            false,
                            null,
                            null,
                            null,
                            "Defeated by reflected damage"
                    );
                }

            }
        }

        battle.getEventCollector().record(
                BattleEventType.DAMAGE,
                source.getTeam().name(),
                source.getInstanceId(),
                target.getTeam().name(),
                target.getInstanceId(),
                skillCode,
                actualDamage,
                critical,
                target.getCurrentHp(),
                target.getMaxHp(),
                target.isAlive(),
                null,
                null,
                null,
                critical ? "Critical damage" : "Damage dealt"
        );

        // 8. Hút máu dựa trên HP thực tế bị mất.
        if (actualDamage.signum() > 0
                && source.isAlive()
                && source.getLifesteal().signum() > 0) {

            BigDecimal healAmount = calculateLifesteal(
                    actualDamage,
                    source.getLifesteal()
            );

            if (healAmount.signum() > 0) {
                BigDecimal actualHeal = source.heal(healAmount);

                if (actualHeal.signum() > 0) {
                    battle.getEventCollector().record(
                            BattleEventType.HEAL,
                            source.getTeam().name(),
                            source.getInstanceId(),
                            source.getTeam().name(),
                            source.getInstanceId(),
                            skillCode,
                            actualHeal,
                            false,
                            source.getCurrentHp(),
                            source.getMaxHp(),
                            source.isAlive(),
                            "LIFESTEAL",
                            "HEAL",
                            null,
                            "Lifesteal"
                    );
                }
            }
        }

        // 9. Ghi nhận tử vong.
        if (!target.isAlive()) {
            battle.getEventCollector().record(
                    BattleEventType.DEATH,
                    source.getTeam().name(),
                    source.getInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    skillCode,
                    actualDamage,
                    critical,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    false,
                    null,
                    null,
                    null,
                    "Unit defeated"
            );
        }

        // 10. Kiểm tra kết thúc trận.
        battle.checkFinished();
    }

    /**
     * Kiểm tra đòn đánh có chí mạng hay không.
     *
     * @param source nhân vật tấn công.
     * @return true nếu chí mạng.
     */
    private boolean isCritical(BattleUnitState source) {
        BigDecimal chance = source.getCrit()
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100));

        double random = ThreadLocalRandom.current().nextDouble(100);

        return BigDecimal.valueOf(random).compareTo(chance) < 0;
    }

    /**
     * Tính sát thương sau khi áp dụng xuyên giáp.
     *
     * @param source nhân vật tấn công.
     * @param target mục tiêu.
     * @param skillMultiplier hệ số kỹ năng.
     * @return sát thương trước khi xét chí mạng.
     */
    private BigDecimal calculateBaseDamage(
            BattleUnitState source,
            BattleUnitState target,
            BigDecimal skillMultiplier
    ) {
        BigDecimal penPercent = source.getPenPercent()
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE);

        BigDecimal effectiveDefense = target.getDefense()
                .multiply(BigDecimal.ONE.subtract(penPercent))
                .subtract(source.getPenFlat())
                .max(BigDecimal.ZERO);

        BigDecimal defenseFactor = BigDecimal.valueOf(1000)
                .divide(
                        BigDecimal.valueOf(1000).add(effectiveDefense),
                        6,
                        RoundingMode.HALF_UP
                );

        return source.getAttack()
                .multiply(skillMultiplier)
                .multiply(defenseFactor)
                .setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Tính lượng HP hồi phục từ hút máu.
     *
     * @param actualDamage sát thương HP thực tế.
     * @param lifesteal tỷ lệ hút máu.
     * @return lượng HP hồi phục.
     */
    private BigDecimal calculateLifesteal(
            BigDecimal actualDamage,
            BigDecimal lifesteal
    ) {
        return actualDamage.multiply(
                lifesteal.divide(
                        BigDecimal.valueOf(100),
                        6,
                        RoundingMode.HALF_UP
                )
        ).setScale(6, RoundingMode.HALF_UP);
    }
}
