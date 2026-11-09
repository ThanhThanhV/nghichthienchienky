package vn.nghichtien.chienky.battle.domain;

import vn.nghichtien.chienky.battle.engine.BattleEventCollector;

import java.math.BigDecimal;
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

    /**
     * Kiểm tra điều kiện kết thúc mà không chốt trạng thái trận hoặc phát BATTLE_END.
     * Dùng để cho các phản ứng ON_KILL/REVIVE xử lý xong trước khi xác nhận kết quả.
     */

    public boolean wouldFinish() {
        boolean playerAlive = units.stream().anyMatch(
                unit -> unit.getTeam() == BattleTeam.PLAYER && unit.isAlive()
        );
        boolean enemyAlive = units.stream().anyMatch(
                unit -> unit.getTeam() == BattleTeam.ENEMY && unit.isAlive()
        );
        return !playerAlive || !enemyAlive;
    }

    public boolean checkFinished() {
        if (finished) {
            return true;
        }

        if (!wouldFinish()) {
            return false;
        }

        boolean playerAlive = units.stream().anyMatch(
                unit -> unit.getTeam() == BattleTeam.PLAYER && unit.isAlive()
        );
        boolean enemyAlive = units.stream().anyMatch(
                unit -> unit.getTeam() == BattleTeam.ENEMY && unit.isAlive()
        );

        finished = true;
        if (playerAlive) {
            winner = BattleTeam.PLAYER;
        } else if (enemyAlive) {
            winner = BattleTeam.ENEMY;
        }

        String result = winner == null ? "DRAW" : winner.name();
        String message = winner == null
                ? "Battle ended in a draw"
                : winner.name() + " team wins";

        eventCollector.record(
                BattleEventType.BATTLE_END,
                null,
                null,
                result,
                null,
                null,
                BigDecimal.ZERO,
                false,
                null,
                null,
                null,
                result,
                "RESULT",
                null,
                message
        );

        return true;
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
