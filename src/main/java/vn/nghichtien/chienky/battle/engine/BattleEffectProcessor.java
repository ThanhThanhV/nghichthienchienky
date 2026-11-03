package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Điều phối thực thi các hiệu ứng của kỹ năng.
 */
@Component
public class BattleEffectProcessor {

    private final BattleTargetSelector targetSelector;
    private final BattleDamageProcessor damageProcessor;

    public BattleEffectProcessor(
            BattleTargetSelector targetSelector,
            BattleDamageProcessor damageProcessor
    ) {
        this.targetSelector = targetSelector;
        this.damageProcessor = damageProcessor;
    }

    public void executeEffects(
            BattleState battle,
            BattleUnitState source,
            List<BattleEffectConfig> effects
    ) {
        Objects.requireNonNull(battle);
        Objects.requireNonNull(source);
        Objects.requireNonNull(effects);

        if (!source.isAlive() || battle.isFinished()) {
            return;
        }

        for (BattleEffectConfig effect : effects) {
            if (battle.isFinished() || !source.isAlive()) {
                break;
            }
            executeEffect(battle, source, effect);
        }
    }

    private void executeEffect(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect
    ) {
        Objects.requireNonNull(effect);

        switch (effect.effectType()) {
            case DAMAGE -> executeDamage(battle, source, effect);
            case HEAL -> executeHeal(battle, source, effect);
            case DOT, HOT -> executePeriodicEffect(battle, source, effect);
            case REVIVE -> executeRevive(battle, source, effect);
            case BUFF -> executeModifier(battle, source, effect, true);
            case DEBUFF -> executeModifier(battle, source, effect, false);
            case CC -> executeCrowdControl(battle, source, effect);
            case IMMUNITY -> executeImmunity(battle, source, effect);
            case DISPEL -> executeDispel(battle, source, effect);
            case SHIELD -> executeShield(battle, source, effect);
            case EVASION, BLOCK, REFLECT ->
                    executeDefenseEffect(battle, source, effect);
            default -> throw new UnsupportedOperationException(
                    "Effect type has not been implemented: " + effect.effectType()
            );
        }
    }

    private void executeDamage(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect
    ) {
        List<BattleUnitState> targets = targetSelector.selectTargets(battle, source, effect.targetMode());

        for (BattleUnitState target : targets) {
            if (battle.isFinished()) {
                break;
            }
            if (!target.isAlive()) {
                continue;
            }
            damageProcessor.executeDamage(
                    battle, source, target, effect.multiplier(), effect.effectCode()
            );
        }
    }

    /**
     * Hồi sinh đồng đội đã tử vong theo lượng HP tính từ ATK của nguồn và flatValue.
     */
    private void executeRevive(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect
    ) {
        BigDecimal reviveAmount = source.getAttack()
                .multiply(effect.multiplier())
                .add(effect.flatValue());

        if (reviveAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "REVIVE amount must be positive: " + effect.effectCode()
            );
        }

        List<BattleUnitState> targets = selectReviveTargets(
                battle, source, effect.targetMode()
        );

        for (BattleUnitState target : targets) {
            BigDecimal revivedHp = target.revive(reviveAmount);
            if (revivedHp.signum() <= 0) {
                continue;
            }

            battle.getEventCollector().record(
                    BattleEventType.REVIVE,
                    source.getTeam().name(),
                    source.getInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    effect.effectCode(),
                    revivedHp,
                    false,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    true,
                    effect.effectCode(),
                    "REVIVE",
                    null,
                    "Unit revived"
            );
        }
    }

    /**
     * Chọn các đồng đội đã tử vong để hồi sinh.
     */
    private List<BattleUnitState> selectReviveTargets(
            BattleState battle,
            BattleUnitState source,
            BattleTargetMode mode
    ) {
        List<BattleUnitState> deadAllies = battle.getUnits().stream()
                .filter(unit -> unit.getTeam() == source.getTeam())
                .filter(unit -> !unit.isAlive())
                .toList();

        if (deadAllies.isEmpty()) {
            return List.of();
        }

        return switch (mode) {
            case SELF -> List.of();
            case RANDOM -> List.of(deadAllies.get(
                    ThreadLocalRandom.current().nextInt(deadAllies.size())
            ));
            case FRONT -> {
                int frontRow = deadAllies.stream()
                        .mapToInt(BattleUnitState::getRow)
                        .min()
                        .orElseThrow();
                yield deadAllies.stream()
                        .filter(unit -> unit.getRow() == frontRow)
                        .sorted(java.util.Comparator.comparingInt(
                                BattleUnitState::getColumn
                        ))
                        .toList();
            }
            case LOWEST_HP -> deadAllies.stream()
                    .min(java.util.Comparator
                            .comparing(BattleUnitState::getCurrentHp)
                            .thenComparingInt(BattleUnitState::getRow)
                            .thenComparingInt(BattleUnitState::getColumn))
                    .map(List::of)
                    .orElse(List.of());
            case HIGHEST_ATK -> deadAllies.stream()
                    .max(java.util.Comparator.comparing(
                            BattleUnitState::getAttack
                    ))
                    .map(List::of)
                    .orElse(List.of());
            case ALL_ENEMIES -> deadAllies;
        };
    }

    /**
     * Gắn DOT/HOT lên mục tiêu; lượng mỗi tick được chụp tại thời điểm áp dụng.
     */
    private void executePeriodicEffect(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect
    ) {
        if (effect.duration() <= 0) {
            throw new IllegalArgumentException(
                    "DOT/HOT duration must be positive: " + effect.effectCode()
            );
        }

        BigDecimal amountPerTick = source.getAttack()
                .multiply(effect.multiplier())
                .add(effect.flatValue());

        if (amountPerTick.signum() <= 0) {
            throw new IllegalArgumentException(
                    "DOT/HOT amount must be positive: " + effect.effectCode()
            );
        }

        boolean damageOverTime = effect.effectType() == BattleEffectType.DOT;
        List<BattleUnitState> targets = damageOverTime
                ? targetSelector.selectTargets(battle, source, effect.targetMode())
                : selectAllies(battle, source, effect.targetMode());

        BattleImmunityType immunityType = damageOverTime
                ? BattleImmunityType.DEBUFF
                : BattleImmunityType.BUFF;

        for (BattleUnitState target : targets) {
            if (!target.isAlive()) {
                continue;
            }

            if (target.getImmunityManager().isImmune(immunityType)) {
                recordImmune(battle, source, target, effect);
                continue;
            }

            target.getPeriodicEffectManager().add(
                    new BattlePeriodicEffect(
                            effect.effectCode(),
                            effect.effectType(),
                            source.getTeam().name(),
                            source.getInstanceId(),
                            amountPerTick,
                            effect.duration()
                    )
            );

            battle.getEventCollector().record(
                    damageOverTime ? BattleEventType.DEBUFF : BattleEventType.BUFF,
                    source.getTeam().name(),
                    source.getInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    effect.effectCode(),
                    amountPerTick,
                    false,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    target.isAlive(),
                    effect.effectCode(),
                    effect.effectType().name(),
                    effect.duration(),
                    damageOverTime ? "Damage over time applied"
                            : "Heal over time applied"
            );
        }
    }

    /**
     * Tick toàn bộ DOT/HOT một lần ở đầu round.
     *
     * DOT bỏ qua DEF, né tránh, chí mạng, phản sát thương và hút máu;
     * vẫn chịu miễn nhiễm DAMAGE và được SHIELD hấp thụ.
     */
    public void tickPeriodicEffects(BattleState battle) {
        Objects.requireNonNull(battle);

        if (battle.isFinished()) {
            return;
        }

        for (BattleUnitState target : battle.getUnits()) {
            List<BattlePeriodicEffect> effects =
                    target.getPeriodicEffectManager().getEffects();

            for (BattlePeriodicEffect effect : effects) {
                if (target.isAlive()) {
                    if (effect.getEffectType() == BattleEffectType.DOT) {
                        tickDamageOverTime(battle, target, effect);
                    } else if (effect.getEffectType() == BattleEffectType.HOT) {
                        tickHealOverTime(battle, target, effect);
                    }
                }
            }

            // Tick xong mới giảm duration để duration=1 vẫn có đúng một tick.
            target.getPeriodicEffectManager().decreaseDurations();
        }
    }

    private void tickDamageOverTime(
            BattleState battle,
            BattleUnitState target,
            BattlePeriodicEffect effect
    ) {
        if (target.getImmunityManager().isImmune(BattleImmunityType.DAMAGE)) {
            battle.getEventCollector().record(
                    BattleEventType.IMMUNITY,
                    effect.getSourceTeam(),
                    effect.getSourceInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    effect.getEffectCode(),
                    BigDecimal.ZERO,
                    false,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    target.isAlive(),
                    effect.getEffectCode(),
                    "DOT",
                    effect.getRemainingDuration(),
                    "Damage over time was immune"
            );
            return;
        }

        BigDecimal remainingDamage = target.getShieldManager()
                .absorbDamage(effect.getAmountPerTick());
        BigDecimal absorbedDamage = effect.getAmountPerTick()
                .subtract(remainingDamage);

        if (absorbedDamage.signum() > 0) {
            battle.getEventCollector().record(
                    BattleEventType.BLOCK,
                    effect.getSourceTeam(),
                    effect.getSourceInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    effect.getEffectCode(),
                    absorbedDamage,
                    false,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    target.isAlive(),
                    effect.getEffectCode(),
                    "DOT",
                    effect.getRemainingDuration(),
                    "Shield absorbed damage over time"
            );
        }

        BigDecimal actualDamage = target.takeDamage(remainingDamage);
        if (actualDamage.signum() <= 0) {
            return;
        }

        battle.getEventCollector().record(
                BattleEventType.DAMAGE,
                effect.getSourceTeam(),
                effect.getSourceInstanceId(),
                target.getTeam().name(),
                target.getInstanceId(),
                effect.getEffectCode(),
                actualDamage,
                false,
                target.getCurrentHp(),
                target.getMaxHp(),
                target.isAlive(),
                effect.getEffectCode(),
                "DOT",
                effect.getRemainingDuration(),
                "Damage over time"
        );

        if (!target.isAlive()) {
            battle.getEventCollector().record(
                    BattleEventType.DEATH,
                    effect.getSourceTeam(),
                    effect.getSourceInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    effect.getEffectCode(),
                    actualDamage,
                    false,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    false,
                    effect.getEffectCode(),
                    "DOT",
                    effect.getRemainingDuration(),
                    "Defeated by damage over time"
            );
        }
    }

    private void tickHealOverTime(
            BattleState battle,
            BattleUnitState target,
            BattlePeriodicEffect effect
    ) {
        BigDecimal actualHeal = target.heal(effect.getAmountPerTick());

        // Vẫn phát HEAL với amount=0 để frontend cập nhật duration khi mục tiêu đã đầy HP.
        battle.getEventCollector().record(
                BattleEventType.HEAL,
                effect.getSourceTeam(),
                effect.getSourceInstanceId(),
                target.getTeam().name(),
                target.getInstanceId(),
                effect.getEffectCode(),
                actualHeal,
                false,
                target.getCurrentHp(),
                target.getMaxHp(),
                target.isAlive(),
                effect.getEffectCode(),
                "HOT",
                effect.getRemainingDuration(),
                "Heal over time"
        );
    }

    private void executeHeal(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect
    ) {
        List<BattleUnitState> targets = selectAllies(
                battle, source, effect.targetMode()
        );

        for (BattleUnitState target : targets) {
            if (!target.isAlive()) {
                continue;
            }

            BigDecimal amount = source.getAttack()
                    .multiply(effect.multiplier())
                    .add(effect.flatValue());
            BigDecimal actualHeal = target.heal(amount);

            if (actualHeal.signum() <= 0) {
                continue;
            }

            battle.getEventCollector().record(
                    BattleEventType.HEAL,
                    source.getTeam().name(),
                    source.getInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    effect.effectCode(),
                    actualHeal,
                    false,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    target.isAlive(),
                    effect.effectCode(),
                    "HEAL",
                    effect.duration(),
                    "Healing applied"
            );
        }
    }

    private void executeModifier(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect,
            boolean buff
    ) {
        if (effect.permanent()) {
            if (effect.duration() != 0) {
                throw new IllegalArgumentException(
                        "Permanent modifier must have duration 0"
                );
            }
        } else if (effect.duration() <= 0) {
            throw new IllegalArgumentException(
                    "Modifier duration must be positive"
            );
        }

        List<BattleUnitState> targets = buff
                ? selectAllies(battle, source, effect.targetMode())
                : targetSelector.selectTargets(battle, source, effect.targetMode());

        BigDecimal value = effect.multiplier()
                .multiply(new BigDecimal("100"))
                .add(effect.flatValue());

        if (value.signum() <= 0) {
            return;
        }

        for (BattleUnitState target : targets) {
            BattleImmunityType immunityType = buff
                    ? BattleImmunityType.BUFF
                    : BattleImmunityType.DEBUFF;

            if (target.getImmunityManager().isImmune(immunityType)) {
                recordImmune(battle, source, target, effect);
                continue;
            }

            target.getStatusEffectManager().addModifier(
                    new BattleStatusEffect(
                            effect.effectCode(),
                            source.getTeam().name(),
                            source.getInstanceId(),
                            effect.statType(),
                            value,
                            buff,
                            effect.duration(),
                            effect.permanent()
                    )
            );

            recordStatus(
                    battle, source, target, effect,
                    buff ? BattleEventType.BUFF : BattleEventType.DEBUFF,
                    buff ? "BUFF" : "DEBUFF",
                    effect.statType().name()
                            + (effect.permanent() ? " permanent modifier applied"
                            : " modifier applied")
            );
        }
    }

    private void executeCrowdControl(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect
    ) {
        if (effect.duration() <= 0) {
            throw new IllegalArgumentException("CC duration must be positive");
        }

        List<BattleUnitState> targets = targetSelector.selectTargets(battle, source, effect.targetMode());

        for (BattleUnitState target : targets) {
            boolean immune = target.getImmunityManager()
                    .isImmune(BattleImmunityType.CC);

            if (immune) {
                recordImmune(battle, source, target, effect);
                continue;
            }

            // RESISTANCE dùng thang 0..100: mỗi điểm kháng giảm
            // một điểm phần trăm xác suất trúng CC. Miễn nhiễm CC vẫn
            // được kiểm tra riêng ở trên và luôn chặn hiệu ứng.
            BigDecimal resistance = target.getResistance()
                    .max(BigDecimal.ZERO)
                    .min(new BigDecimal("100"));
            BigDecimal applicationChance = new BigDecimal("100")
                    .subtract(resistance);

            boolean resisted = applicationChance.signum() <= 0
                    || BigDecimal.valueOf(
                            ThreadLocalRandom.current().nextDouble(100)
                    ).compareTo(applicationChance) >= 0;

            boolean applied = !resisted
                    && target.getStatusEffectManager()
                    .applyCrowdControl(
                            effect.statusType(),
                            effect.effectCode(),
                            source.getTeam().name(),
                            source.getInstanceId(),
                            effect.duration(),
                            false
                    );

            recordStatus(
                    battle, source, target, effect,
                    BattleEventType.CC,
                    "CC",
                    applied ? "CC applied: " + effect.statusType()
                            : resisted ? "CC resisted: " + effect.statusType()
                            : "CC was not applied"
            );
        }
    }

    private void executeImmunity(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect
    ) {
        if (effect.duration() <= 0) {
            throw new IllegalArgumentException(
                    "Immunity duration must be positive"
            );
        }

        List<BattleUnitState> targets = selectAllies(
                battle, source, effect.targetMode()
        );

        for (BattleUnitState target : targets) {
            target.getImmunityManager().apply(
                    effect.immunityType(), effect.effectCode(), effect.duration()
            );

            if (effect.immunityType() == BattleImmunityType.CC) {
                target.getStatusEffectManager().dispelCrowdControl();
            }

            recordStatus(
                    battle, source, target, effect,
                    BattleEventType.IMMUNITY,
                    "IMMUNITY",
                    "IMMUNITY applied: " + effect.immunityType()
            );
        }
    }

    private void executeDispel(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect
    ) {
        List<BattleUnitState> targets = selectAllies(
                battle, source, effect.targetMode()
        );

        for (BattleUnitState target : targets) {
            boolean removed = switch (effect.dispelType()) {
                case BUFF -> {
                    int before = target.getStatusEffectManager()
                            .getModifiers().size();
                    target.getStatusEffectManager().dispelModifiers(true);
                    int removedPeriodic = target.getPeriodicEffectManager()
                            .dispel(BattleEffectType.HOT);
                    yield before != target.getStatusEffectManager()
                            .getModifiers().size() || removedPeriodic > 0;
                }
                case DEBUFF -> {
                    int before = target.getStatusEffectManager()
                            .getModifiers().size();
                    target.getStatusEffectManager().dispelModifiers(false);
                    int removedPeriodic = target.getPeriodicEffectManager()
                            .dispel(BattleEffectType.DOT);
                    yield before != target.getStatusEffectManager()
                            .getModifiers().size() || removedPeriodic > 0;
                }
                case CC -> target.getStatusEffectManager()
                        .dispelCrowdControl();
            };

            recordStatus(
                    battle, source, target, effect,
                    BattleEventType.DISPEL,
                    "DISPEL",
                    removed ? "DISPEL applied: " + effect.dispelType()
                            : "No matching effect to dispel"
            );
        }
    }

    private void recordImmune(
            BattleState battle,
            BattleUnitState source,
            BattleUnitState target,
            BattleEffectConfig effect
    ) {
        battle.getEventCollector().record(
                BattleEventType.IMMUNITY,
                source.getTeam().name(),
                source.getInstanceId(),
                target.getTeam().name(),
                target.getInstanceId(),
                effect.effectCode(),
                null,
                false,
                target.getCurrentHp(),
                target.getMaxHp(),
                target.isAlive(),
                effect.effectCode(),
                "IMMUNITY",
                effect.duration(),
                "IMMUNE"
        );
    }

    private void recordStatus(
            BattleState battle,
            BattleUnitState source,
            BattleUnitState target,
            BattleEffectConfig effect,
            BattleEventType eventType,
            String category,
            String message
    ) {
        battle.getEventCollector().record(
                eventType,
                source.getTeam().name(),
                source.getInstanceId(),
                target.getTeam().name(),
                target.getInstanceId(),
                effect.effectCode(),
                null,
                false,
                target.getCurrentHp(),
                target.getMaxHp(),
                target.isAlive(),
                effect.effectCode(),
                category,
                effect.permanent() ? 0 : effect.duration(),
                message
        );
    }

    private List<BattleUnitState> selectAllies(
            BattleState battle,
            BattleUnitState source,
            BattleTargetMode mode
    ) {
        List<BattleUnitState> allies = battle.getUnits().stream()
                .filter(BattleUnitState::isAlive)
                .filter(unit -> unit.getTeam() == source.getTeam())
                .toList();

        if (allies.isEmpty()) {
            return List.of();
        }

        return switch (mode) {
            case SELF -> List.of(source);
            case RANDOM -> List.of(allies.get(
                    ThreadLocalRandom.current().nextInt(allies.size())
            ));
            case FRONT -> {
                int frontRow = allies.stream()
                        .mapToInt(BattleUnitState::getRow)
                        .min()
                        .orElseThrow();
                yield allies.stream()
                        .filter(unit -> unit.getRow() == frontRow)
                        .sorted(java.util.Comparator.comparingInt(
                                BattleUnitState::getColumn
                        ))
                        .toList();
            }
            case LOWEST_HP -> allies.stream()
                    .min(java.util.Comparator.comparing(
                            BattleUnitState::getCurrentHp
                    ))
                    .map(List::of)
                    .orElse(List.of());
            case HIGHEST_ATK -> allies.stream()
                    .max(java.util.Comparator.comparing(
                            BattleUnitState::getAttack
                    ))
                    .map(List::of)
                    .orElse(List.of());
            case ALL_ENEMIES -> allies;
        };
    }

    private void executeShield(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect
    ) {
        if (effect.duration() <= 0) {
            throw new IllegalArgumentException("Shield duration must be positive");
        }

        List<BattleUnitState> targets = selectAllies(
                battle, source, effect.targetMode()
        );
        BigDecimal amount = source.getAttack()
                .multiply(effect.multiplier())
                .add(effect.flatValue());

        if (amount.signum() <= 0) {
            return;
        }

        for (BattleUnitState target : targets) {
            target.getShieldManager().addShield(
                    effect.effectCode(),
                    source.getTeam().name(),
                    source.getInstanceId(),
                    amount,
                    effect.duration()
            );

            battle.getEventCollector().record(
                    BattleEventType.SHIELD,
                    source.getTeam().name(),
                    source.getInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    effect.effectCode(),
                    amount,
                    false,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    target.isAlive(),
                    effect.effectCode(),
                    "SHIELD",
                    effect.duration(),
                    "Shield applied"
            );
        }
    }

    private void executeDefenseEffect(
            BattleState battle,
            BattleUnitState source,
            BattleEffectConfig effect
    ) {
        if (effect.duration() <= 0) {
            throw new IllegalArgumentException(
                    "Defense effect duration must be positive"
            );
        }

        if (effect.multiplier().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "EVASION/BLOCK/REFLECT multiplier must not exceed 1"
            );
        }

        List<BattleUnitState> targets = selectAllies(
                battle, source, effect.targetMode()
        );

        for (BattleUnitState target : targets) {
            target.getDefenseEffectManager().apply(
                    effect.effectType(),
                    effect.effectCode(),
                    effect.multiplier(),
                    effect.duration()
            );

            battle.getEventCollector().record(
                    switch (effect.effectType()) {
                        case EVASION -> BattleEventType.EVASION;
                        case BLOCK -> BattleEventType.BLOCK;
                        case REFLECT -> BattleEventType.REFLECT;
                        default -> throw new IllegalStateException(
                                "Unsupported defense effect: " + effect.effectType()
                        );
                    },
                    source.getTeam().name(),
                    source.getInstanceId(),
                    target.getTeam().name(),
                    target.getInstanceId(),
                    effect.effectCode(),
                    effect.multiplier(),
                    false,
                    target.getCurrentHp(),
                    target.getMaxHp(),
                    target.isAlive(),
                    effect.effectCode(),
                    effect.effectType().name(),
                    effect.duration(),
                    "Defense effect applied"
            );
        }
    }
}
