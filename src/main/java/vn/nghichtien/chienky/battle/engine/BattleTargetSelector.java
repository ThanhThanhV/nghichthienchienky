package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTargetMode;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Chọn mục tiêu cho các kỹ năng.
 */
@Component
public class BattleTargetSelector {

    public List<BattleUnitState> selectTargets(
            BattleState battle,
            BattleUnitState source,
            BattleTargetMode mode
    ) {
        Objects.requireNonNull(battle, "Battle must not be null");
        Objects.requireNonNull(source, "Source must not be null");
        Objects.requireNonNull(mode, "Target mode must not be null");

        if (!source.isAlive()) {
            return List.of();
        }

        if (mode == BattleTargetMode.SELF) {
            return List.of(source);
        }

        List<BattleUnitState> enemies = battle.getUnits()
                .stream()
                .filter(BattleUnitState::isAlive)
                .filter(unit -> unit.getTeam() == source.getTeam().opponent())
                .collect(Collectors.toList());

        if (enemies.isEmpty()) {
            return List.of();
        }

        return switch (mode) {
            case SELF -> List.of(source);
            case RANDOM -> selectRandom(enemies);
            case FRONT -> selectFront(enemies);
            case LOWEST_HP -> selectLowestHp(enemies);
            case HIGHEST_ATK -> selectHighestAttack(enemies);
            case ALL_ENEMIES -> enemies;
        };
    }

    private List<BattleUnitState> selectRandom(
            List<BattleUnitState> candidates
    ) {
        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return List.of(candidates.get(index));
    }

    private List<BattleUnitState> selectFront(
            List<BattleUnitState> candidates
    ) {
        int frontRow = candidates.stream()
                .mapToInt(BattleUnitState::getRow)
                .min()
                .orElseThrow();

        return candidates.stream()
                .filter(unit -> unit.getRow() == frontRow)
                .sorted(Comparator.comparingInt(BattleUnitState::getColumn))
                .toList();
    }

    private List<BattleUnitState> selectLowestHp(
            List<BattleUnitState> candidates
    ) {
        return candidates.stream()
                .min(Comparator.comparing(BattleUnitState::getCurrentHp))
                .map(List::of)
                .orElse(List.of());
    }

    private List<BattleUnitState> selectHighestAttack(
            List<BattleUnitState> candidates
    ) {
        return candidates.stream()
                .max(Comparator.comparing(BattleUnitState::getAttack))
                .map(List::of)
                .orElse(List.of());
    }
}
