package vn.nghichtien.chienky.battle.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleStateTest {

    @Test
    void checkFinishedShouldEmitExactlyOneBattleEndForDraw() {
        BattleUnitState player = unit(1L, BattleTeam.PLAYER);
        BattleUnitState enemy = unit(-1L, BattleTeam.ENEMY);
        BattleState battle = new BattleState(List.of(player, enemy));

        player.takeDamage(player.getCurrentHp());
        enemy.takeDamage(enemy.getCurrentHp());

        assertTrue(battle.checkFinished());
        assertTrue(battle.checkFinished());
        assertNull(battle.getWinner());
        assertEquals(1L, battle.getEventCollector().getEvents().stream()
                .filter(event -> event.type() == BattleEventType.BATTLE_END)
                .count());
    }

    private BattleUnitState unit(Long id, BattleTeam team) {
        return new BattleUnitState(
                id,
                team,
                "HERO_" + id,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ONE
        );
    }
}
