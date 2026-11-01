package vn.nghichtien.chienky.battle.domain;

import vn.nghichtien.chienky.battle.engine.BattleEventCollector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Quản lý trạng thái và dữ liệu của một trận chiến.
 */
public class BattleState {

    private final String battleId;
    private final List<BattleUnitState> units;
    private final BattleEventCollector eventCollector;
    private int currentRound;
    private boolean finished;
    private boolean passiveInitializationStarted;
    private BattleTeam winner;

    public BattleState(List<BattleUnitState> units) {
        if (units == null || units.isEmpty()) {
            throw new IllegalArgumentException("Battle units must not be empty");
        }

        this.battleId = UUID.randomUUID().toString();
        this.units = new ArrayList<>(units);
        this.eventCollector = new BattleEventCollector();
        this.currentRound = 0;
        this.finished = false;
        this.passiveInitializationStarted = false;

        validateTeams();
    }

    private void validateTeams() {
        boolean hasPlayer = units.stream()
                .anyMatch(unit -> unit.getTeam() == BattleTeam.PLAYER);
        boolean hasEnemy = units.stream()
                .anyMatch(unit -> unit.getTeam() == BattleTeam.ENEMY);

        if (!hasPlayer || !hasEnemy) {
            throw new IllegalArgumentException(
                    "Battle requires both PLAYER and ENEMY teams"
            );
        }

        long distinctIds = units.stream()
                .map(BattleUnitState::getInstanceId)
                .distinct()
                .count();

        if (distinctIds != units.size()) {
            throw new IllegalArgumentException(
                    "Duplicate hero instance ID in battle"
            );
        }
    }

    /**
     * Đảm bảo nội tại đầu trận chỉ được khởi tạo một lần.
     */
    public boolean beginPassiveInitialization() {
        if (passiveInitializationStarted) {
            return false;
        }
        passiveInitializationStarted = true;
        return true;
    }

    public int nextRound() {
        if (finished) {
            throw new IllegalStateException("Battle already finished");
        }
        return ++currentRound;
    }

    public List<BattleUnitState> getActionOrder() {
        return units.stream()
                .filter(BattleUnitState::isAlive)
                .sorted(
                        Comparator.comparing(
                                BattleUnitState::getSpeed,
                                Comparator.reverseOrder()
                        ).thenComparing(BattleUnitState::getInstanceId)
                )
                .toList();
    }

    public BattleUnitState findUnit(Long instanceId) {
        return units.stream()
                .filter(unit -> Objects.equals(
                        unit.getInstanceId(), instanceId
                ))
                .findFirst()
                .orElse(null);
    }

    public boolean checkFinished() {
        boolean playerAlive = units.stream().anyMatch(
                unit -> unit.getTeam() == BattleTeam.PLAYER && unit.isAlive()
        );
        boolean enemyAlive = units.stream().anyMatch(
                unit -> unit.getTeam() == BattleTeam.ENEMY && unit.isAlive()
        );

        if (!playerAlive || !enemyAlive) {
            finished = true;
            if (playerAlive) {
                winner = BattleTeam.PLAYER;
            } else if (enemyAlive) {
                winner = BattleTeam.ENEMY;
            }
            return true;
        }
        return false;
    }

    public String getBattleId() {
        return battleId;
    }

    public List<BattleUnitState> getUnits() {
        return List.copyOf(units);
    }

    public BattleEventCollector getEventCollector() {
        return eventCollector;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public boolean isFinished() {
        return finished;
    }

    public BattleTeam getWinner() {
        return winner;
    }
}
