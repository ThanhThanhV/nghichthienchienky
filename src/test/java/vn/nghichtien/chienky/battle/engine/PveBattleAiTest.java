package vn.nghichtien.chienky.battle.engine;

import org.junit.jupiter.api.Test;
import vn.nghichtien.chienky.battle.domain.BattleActionType;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTeam;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PveBattleAiTest {

    private final PveBattleAi ai = new PveBattleAi();

    @Test
    void shouldUseUltimateWhenSeveralTargetsAreAliveAndTeamIsWornDown() {
        BattleUnitState targetOne = player(1L, "p1", 1000, 700);
        BattleUnitState targetTwo = player(2L, "p2", 1000, 1000);
        BattleUnitState targetThree = player(3L, "p3", 1000, 1000);
        BattleUnitState enemy = enemy(10L, "enemy", 1000, 200);

        BattleState battle = battle(
                List.of(targetOne, targetTwo, targetThree, enemy)
        );

        assertEquals(
                BattleActionType.ULTIMATE_SKILL,
                ai.chooseAction(battle, enemy)
        );
    }

    @Test
    void shouldUseActiveSkillWhenOnlyOneHealthyTargetRemains() {
        BattleUnitState target = player(1L, "p1", 1000, 1000);
        BattleUnitState enemy = enemy(10L, "enemy", 1000, 200);
        BattleState battle = battle(List.of(target, enemy));

        assertEquals(
                BattleActionType.ACTIVE_SKILL,
                ai.chooseAction(battle, enemy)
        );
    }

    @Test
    void shouldUseUltimateToFinishAWeakTargetOnSecondAction() {
        BattleUnitState target = player(1L, "p1", 1000, 400);
        BattleUnitState enemy = enemy(10L, "enemy", 1000, 200);
        BattleState battle = battle(List.of(target, enemy));
        enemy.incrementActionCount();

        assertEquals(
                BattleActionType.ULTIMATE_SKILL,
                ai.chooseAction(battle, enemy)
        );
    }

    @Test
    void shouldUseBasicAttackWhenThereIsNoUltimateFinisherOpportunity() {
        BattleUnitState target = player(1L, "p1", 1000, 1000);
        BattleUnitState enemy = enemy(10L, "enemy", 1000, 200);
        BattleState battle = battle(List.of(target, enemy));
        enemy.incrementActionCount();

        assertEquals(
                BattleActionType.BASIC_ATTACK,
                ai.chooseAction(battle, enemy)
        );
    }

    @Test
    void shouldSaveActiveSkillWhenBasicAttackCanFinishTarget() {
        BattleUnitState target = player(1L, "p1", 1000, 150);
        BattleUnitState enemy = enemy(10L, "enemy", 1000, 200);
        BattleState battle = battle(List.of(target, enemy));
        enemy.incrementActionCount();
        enemy.incrementActionCount();

        assertEquals(
                BattleActionType.BASIC_ATTACK,
                ai.chooseAction(battle, enemy)
        );
    }

    @Test
    void shouldKeepExistingActionCycleForPlayerUnits() {
        BattleUnitState player = player(1L, "p1", 1000, 1000);
        BattleUnitState enemy = enemy(10L, "enemy", 1000, 200);
        BattleState battle = battle(List.of(player, enemy));

        assertEquals(
                BattleActionType.ACTIVE_SKILL,
                ai.chooseAction(battle, player)
        );
        player.incrementActionCount();
        assertEquals(
                BattleActionType.ULTIMATE_SKILL,
                ai.chooseAction(battle, player)
        );
        player.incrementActionCount();
        assertEquals(
                BattleActionType.BASIC_ATTACK,
                ai.chooseAction(battle, player)
        );
    }

    @Test
    void shouldRejectNullBattleOrActor() {
        BattleUnitState enemy = enemy(10L, "enemy", 1000, 200);
        BattleState battle = battle(List.of(
                player(1L, "p1", 1000, 1000), enemy
        ));

        assertThrows(
                IllegalArgumentException.class,
                () -> ai.chooseAction(null, enemy)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ai.chooseAction(battle, null)
        );
    }

    private BattleState battle(List<BattleUnitState> units) {
        return new BattleState(new ArrayList<>(units));
    }

    private BattleUnitState player(
            Long id,
            String code,
            int maxHp,
            int currentHp
    ) {
        BattleUnitState unit = new BattleUnitState(
                id,
                BattleTeam.PLAYER,
                code,
                BigDecimal.valueOf(maxHp),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(100),
                1,
                1
        );
        if (currentHp < maxHp) {
            unit.takeDamage(BigDecimal.valueOf(maxHp - currentHp));
        }
        return unit;
    }

    private BattleUnitState enemy(
            Long id,
            String code,
            int maxHp,
            int attack
    ) {
        return new BattleUnitState(
                id,
                BattleTeam.ENEMY,
                code,
                BigDecimal.valueOf(maxHp),
                BigDecimal.valueOf(attack),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(100),
                1,
                1
        );
    }
}
