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

    private BattleTeam winner;

    /**
     * Khởi tạo trận đấu mới.
     *
     * @param units Danh sách nhân vật tham gia trận đấu.
     */
    public BattleState(List<BattleUnitState> units) {
        if (units == null || units.isEmpty()) {
            throw new IllegalArgumentException("Battle units must not be empty");
        }

        this.battleId = UUID.randomUUID().toString();
        this.units = new ArrayList<>(units);
        this.eventCollector = new BattleEventCollector();
        this.currentRound = 0;
        this.finished = false;

        validateTeams();
    }

    /**
     * Kiểm tra hai đội có nhân vật tham gia hay không.
     */
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
     * Tăng số round hiện tại.
     *
     * @return Round mới.
     */
    public int nextRound() {
        if (finished) {
            throw new IllegalStateException("Battle already finished");
        }

        return ++currentRound;
    }

    /**
     * Lấy danh sách nhân vật theo thứ tự tốc độ giảm dần.
     *
     * @return Danh sách nhân vật còn sống.
     */
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

    /**
     * Tìm nhân vật theo ID.
     *
     * @param instanceId ID nhân vật.
     * @return Trạng thái nhân vật hoặc null nếu không tồn tại.
     */
    public BattleUnitState findUnit(Long instanceId) {
        return units.stream()
                .filter(unit -> Objects.equals(
                        unit.getInstanceId(), instanceId
                ))
                .findFirst()
                .orElse(null);
    }

    /**
     * Kiểm tra trận đấu đã kết thúc hay chưa.
     *
     * @return true nếu một đội không còn nhân vật sống.
     */
    public boolean checkFinished() {
        boolean playerAlive = units.stream().anyMatch(
                unit -> unit.getTeam() == BattleTeam.PLAYER
                        && unit.isAlive()
        );

        boolean enemyAlive = units.stream().anyMatch(
                unit -> unit.getTeam() == BattleTeam.ENEMY
                        && unit.isAlive()
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