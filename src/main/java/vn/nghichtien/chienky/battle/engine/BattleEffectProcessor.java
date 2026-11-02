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

            boolean applied = target.getStatusEffectManager()
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
                    yield before != target.getStatusEffectManager()
                            .getModifiers().size();
                }
                case DEBUFF -> {
                    int before = target.getStatusEffectManager()
                            .getModifiers().size();
                    target.getStatusEffectManager().dispelModifiers(false);
                    yield before != target.getStatusEffectManager()
                            .getModifiers().size();
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
                    "EVASION/BLOCK multiplier must not exceed 1"
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
                    effect.effectType() == BattleEffectType.EVASION
                            ? BattleEventType.EVASION
                            : BattleEventType.BLOCK,
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
