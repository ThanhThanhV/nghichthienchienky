package vn.nghichtien.chienky.battle.dto;

import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTeam;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;
import vn.nghichtien.chienky.hero.domain.HeroDefinition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

public record BattleScreenDTO(
        String battleId,
        int round,
        boolean finished,
        String winner,
        List<UnitDTO> playerUnits,
        List<UnitDTO> enemyUnits,
        Integer campaignStageNumber,
        String campaignStageName,
        boolean campaignFirstClear,
        Map<String, BigDecimal> campaignRewards
) {
    public record UnitDTO(
            Long instanceId,
            String heroCode,
            String heroName,
            String role,
            String avatarCode,
            String spineSkeleton,
            String spineAtlas,
            String spineTextures,
            Integer level,
            int row,
            int column,
            BigDecimal currentHp,
            BigDecimal maxHp,
            BigDecimal hpPercent,
            boolean alive,
            BigDecimal speed
    ) {}

    public static BattleScreenDTO from(BattleState battle) {
        return from(battle, Map.of(), Map.of());
    }

    public static BattleScreenDTO from(
            BattleState battle,
            Map<String, HeroDefinition> definitions,
            Map<Long, Integer> levels
    ) {
        return from(battle, definitions, levels, null, null, false, Map.of());
    }

    public static BattleScreenDTO from(
            BattleState battle,
            Map<String, HeroDefinition> definitions,
            Map<Long, Integer> levels,
            Integer campaignStageNumber,
            String campaignStageName,
            boolean campaignFirstClear,
            Map<String, BigDecimal> campaignRewards
    ) {
        List<BattleUnitState> units = battle.getUnits();
        List<UnitDTO> players = units.stream()
                .filter(unit -> unit.getTeam() == BattleTeam.PLAYER)
                .map(unit -> toUnitDTO(unit, definitions, levels))
                .toList();
        List<UnitDTO> enemies = units.stream()
                .filter(unit -> unit.getTeam() == BattleTeam.ENEMY)
                .map(unit -> toUnitDTO(unit, definitions, levels))
                .toList();

        return new BattleScreenDTO(
                battle.getBattleId(),
                battle.getCurrentRound(),
                battle.isFinished(),
                battle.getWinner() == null ? null : battle.getWinner().name(),
                players,
                enemies,
                campaignStageNumber,
                campaignStageName,
                campaignFirstClear,
                campaignRewards == null ? Map.of() : Map.copyOf(campaignRewards)
        );
    }

    private static UnitDTO toUnitDTO(
            BattleUnitState unit,
            Map<String, HeroDefinition> definitions,
            Map<Long, Integer> levels
    ) {
        HeroDefinition definition = definitions.get(unit.getHeroCode());
        BigDecimal maxHp = unit.getMaxHp();
        BigDecimal currentHp = unit.getCurrentHp();
        BigDecimal hpPercent = maxHp.signum() <= 0
                ? BigDecimal.ZERO
                : currentHp.multiply(BigDecimal.valueOf(100))
                .divide(maxHp, 2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100));

        return new UnitDTO(
                unit.getInstanceId(),
                unit.getHeroCode(),
                definition == null ? unit.getHeroCode() : definition.getHeroName(),
                definition == null ? null : definition.getRole(),
                definition == null ? null : definition.getAvatarCode(),
                definition == null ? null : definition.getSpineSkeleton(),
                definition == null ? null : definition.getSpineAtlas(),
                definition == null ? null : definition.getSpineTextures(),
                levels.get(unit.getInstanceId()),
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
