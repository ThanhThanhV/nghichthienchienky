
package vn.nghichtien.chienky.battle.dto;

import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTeam;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;

import java.math.BigDecimal;
import java.util.List;

public record BattleScreenDTO(
        String battleId,
        int round,
        boolean finished,
        String winner,
        List<UnitDTO> playerUnits,
        List<UnitDTO> enemyUnits
) {

    public record UnitDTO(
            Long instanceId,
            String heroCode,
            int row,
            int column,
            BigDecimal currentHp,
            BigDecimal maxHp,
            BigDecimal hpPercent,
            boolean alive,
            BigDecimal speed
    ) {}

    public static BattleScreenDTO from(BattleState battle) {
        List<BattleUnitState> units = battle.getUnits();

        List<UnitDTO> players = units.stream()
                .filter(unit -> unit.getTeam() == BattleTeam.PLAYER)
                .map(BattleScreenDTO::toUnitDTO)
                .toList();

        List<UnitDTO> enemies = units.stream()
                .filter(unit -> unit.getTeam() == BattleTeam.ENEMY)
                .map(BattleScreenDTO::toUnitDTO)
                .toList();

        return new BattleScreenDTO(
                battle.getBattleId().toString(),
                battle.getCurrentRound(),
                battle.isFinished(),
                battle.getWinner() == null ? null : battle.getWinner().name(),
                players,
                enemies
        );
    }

    private static UnitDTO toUnitDTO(BattleUnitState unit) {
        BigDecimal maxHp = unit.getMaxHp();
        BigDecimal currentHp = unit.getCurrentHp();

        BigDecimal hpPercent = maxHp.signum() <= 0
                ? BigDecimal.ZERO
                : currentHp.multiply(BigDecimal.valueOf(100))
                .divide(maxHp, 2, java.math.RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100));

        return new UnitDTO(
                unit.getInstanceId(),
                unit.getHeroCode(),
                unit.getRow(),
                unit.getColumn(),
                currentHp,
                maxHp,
                hpPercent,
                unit.isAlive(),
                unit.getSpeed()
        );
    }
}
