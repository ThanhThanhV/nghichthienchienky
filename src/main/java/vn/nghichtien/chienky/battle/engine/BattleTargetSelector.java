package vn.nghichtien.chienky.battle.engine;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTargetMode;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chọn mục tiêu cho kỹ năng. Phe mục tiêu do loại hiệu ứng quyết định;
 * selector áp dụng quy tắc trên danh sách ứng viên đã được lọc.
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

        List<BattleUnitState> enemies = battle.getUnits().stream()
                .filter(BattleUnitState::isAlive)
                .filter(unit -> unit.getTeam() == source.getTeam().opponent())
                .toList();
        return selectFromCandidates(enemies, mode, source);
    }

    /**
     * Dùng chung cho mục tiêu địch, đồng minh và đồng minh đã tử trận cần hồi sinh.
     */
    public List<BattleUnitState> selectFromCandidates(
            List<BattleUnitState> candidates,
            BattleTargetMode mode,
            BattleUnitState source
    ) {
        Objects.requireNonNull(candidates, "Candidates must not be null");
        Objects.requireNonNull(mode, "Target mode must not be null");
        Objects.requireNonNull(source, "Source must not be null");

        if (candidates.isEmpty()) {
            return List.of();
        }

        return switch (mode) {
            case SELF -> candidates.contains(source) ? List.of(source) : List.of();
            case RANDOM -> selectRandom(candidates, 1);
            case RANDOM_2 -> selectRandom(candidates, 2);
            case RANDOM_3 -> selectRandom(candidates, 3);
            case FRONT -> selectRow(candidates, true);
            case FRONT_AND_MIDDLE -> selectFirstRows(candidates, 2);
            case BACK -> selectRow(candidates, false);
            case LOWEST_HP_BACK -> selectLowestHpBackRow(candidates);
            case MOST_POPULATED_ROW -> selectMostPopulatedRow(candidates);
            case HIGHEST_ATK_3 -> selectTopByAttack(candidates, 3);
            case LOWEST_HP -> selectOne(candidates,
                    Comparator.comparing(BattleUnitState::getCurrentHp));
            case LOWEST_HP_PERCENT -> selectOne(candidates, this::compareHpPercent);
            case HIGHEST_HP_PERCENT -> selectOne(candidates,
                    (left, right) -> compareHpPercent(right, left));
            case HIGHEST_ATK -> selectOne(candidates,
                    Comparator.comparing(BattleUnitState::getAttack).reversed());
            case LOWEST_ATK -> selectOne(candidates,
                    Comparator.comparing(BattleUnitState::getAttack));
            case ALL_ENEMIES, ALL_ALLIES -> List.copyOf(candidates);
        };
    }

    private List<BattleUnitState> selectRandom(
            List<BattleUnitState> candidates,
            int requestedCount
    ) {
        List<BattleUnitState> shuffled = new ArrayList<>(candidates);
        java.util.Collections.shuffle(shuffled, ThreadLocalRandom.current());
        return List.copyOf(shuffled.subList(0, Math.min(requestedCount, shuffled.size())));
    }

    private List<BattleUnitState> selectFirstRows(
            List<BattleUnitState> candidates,
            int rowCount
    ) {
        List<Integer> rows = candidates.stream()
                .map(BattleUnitState::getRow)
                .distinct()
                .sorted()
                .limit(rowCount)
                .toList();
        return candidates.stream()
                .filter(unit -> rows.contains(unit.getRow()))
                .sorted(Comparator.comparingInt(BattleUnitState::getRow)
                        .thenComparingInt(BattleUnitState::getColumn))
                .toList();
    }

    private List<BattleUnitState> selectLowestHpBackRow(
            List<BattleUnitState> candidates
    ) {
        int backRow = candidates.stream()
                .mapToInt(BattleUnitState::getRow)
                .max()
                .orElseThrow();
        List<BattleUnitState> backRowCandidates = candidates.stream()
                .filter(unit -> unit.getRow() == backRow)
                .toList();
        return selectOne(
                backRowCandidates,
                Comparator.comparing(BattleUnitState::getCurrentHp)
        );
    }

    private List<BattleUnitState> selectMostPopulatedRow(
            List<BattleUnitState> candidates
    ) {
        int selectedRow = candidates.stream()
                .map(BattleUnitState::getRow)
                .distinct()
                .min(Comparator
                        .<Integer>comparingLong(row -> -candidates.stream()
                                .filter(unit -> unit.getRow() == row)
                                .count())
                        .thenComparingInt(Integer::intValue))
                .orElseThrow();
        return candidates.stream()
                .filter(unit -> unit.getRow() == selectedRow)
                .sorted(Comparator.comparingInt(BattleUnitState::getColumn))
                .toList();
    }

    private List<BattleUnitState> selectTopByAttack(
            List<BattleUnitState> candidates,
            int count
    ) {
        return candidates.stream()
                .sorted(Comparator.comparing(
                        BattleUnitState::getAttack
                ).reversed().thenComparingInt(BattleUnitState::getRow)
                        .thenComparingInt(BattleUnitState::getColumn))
                .limit(count)
                .toList();
    }

    private List<BattleUnitState> selectRow(
            List<BattleUnitState> candidates,
            boolean front
    ) {
        int selectedRow = candidates.stream()
                .mapToInt(BattleUnitState::getRow)
                .reduce(front ? Integer.MAX_VALUE : Integer.MIN_VALUE,
                        front ? Math::min : Math::max);
        return candidates.stream()
                .filter(unit -> unit.getRow() == selectedRow)
                .sorted(Comparator.comparingInt(BattleUnitState::getColumn))
                .toList();
    }

    private List<BattleUnitState> selectOne(
            List<BattleUnitState> candidates,
            Comparator<BattleUnitState> comparator
    ) {
        return candidates.stream()
                .min(comparator.thenComparingInt(BattleUnitState::getRow)
                        .thenComparingInt(BattleUnitState::getColumn))
                .map(List::of)
                .orElse(List.of());
    }

    private int compareHpPercent(BattleUnitState left, BattleUnitState right) {
        BigDecimal leftCross = left.getCurrentHp().multiply(right.getMaxHp());
        BigDecimal rightCross = right.getCurrentHp().multiply(left.getMaxHp());
        return leftCross.compareTo(rightCross);
    }
}
