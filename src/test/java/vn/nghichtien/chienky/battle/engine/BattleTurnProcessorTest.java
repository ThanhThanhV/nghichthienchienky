package vn.nghichtien.chienky.battle.engine;

import org.junit.jupiter.api.Test;
import vn.nghichtien.chienky.battle.domain.BattleActionType;
import vn.nghichtien.chienky.battle.domain.BattleEventType;
import vn.nghichtien.chienky.battle.domain.BattlePassiveTrigger;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleStatusType;
import vn.nghichtien.chienky.battle.domain.BattleTeam;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BattleTurnProcessorTest {

    @Test
    void processRoundShouldStopAfterAnActionDefeatsTheLastEnemy() {
        DatabaseSkillExecutor skillExecutor = mock(DatabaseSkillExecutor.class);
        BattleEffectProcessor effectProcessor = mock(BattleEffectProcessor.class);
        BattleTurnProcessor processor = new BattleTurnProcessor(
                skillExecutor,
                effectProcessor
        );

        BattleUnitState firstPlayer = unit(
                1L, BattleTeam.PLAYER, "PLAYER_1", "100", "30"
        );
        BattleUnitState secondPlayer = unit(
                2L, BattleTeam.PLAYER, "PLAYER_2", "100", "20"
        );
        BattleUnitState enemy = unit(
                -1L, BattleTeam.ENEMY, "ENEMY_1", "10", "10"
        );

        BattleState battle = new BattleState(
                List.of(firstPlayer, secondPlayer, enemy)
        );

        doAnswer(invocation -> {
            enemy.takeDamage(enemy.getCurrentHp());
            return invocation.getArgument(2);
        }).when(skillExecutor).execute(
                eq(battle),
                any(BattleUnitState.class),
                any(BattleActionType.class)
        );

        processor.processRound(battle);

        verify(skillExecutor, times(1)).execute(
                eq(battle),
                any(BattleUnitState.class),
                any(BattleActionType.class)
        );

        assertTrue(battle.isFinished());
        assertEquals(BattleTeam.PLAYER, battle.getWinner());
        assertEquals(1L, battle.getEventCollector().getEvents().stream()
                .filter(event -> event.type() == BattleEventType.BATTLE_END)
                .count());
        assertEquals(1L, battle.getEventCollector().getEvents().stream()
                .filter(event -> event.type() == BattleEventType.ACTION_START)
                .count());
    }

    @Test
    void processRoundShouldKeepTheBattleOpenUntilTheCurrentActionFinishes() {
        DatabaseSkillExecutor skillExecutor = mock(DatabaseSkillExecutor.class);
        BattleEffectProcessor effectProcessor = mock(BattleEffectProcessor.class);
        BattleTurnProcessor processor = new BattleTurnProcessor(
                skillExecutor,
                effectProcessor
        );

        BattleUnitState player = unit(
                1L, BattleTeam.PLAYER, "PLAYER_1", "100", "30"
        );
        BattleUnitState enemy = unit(
                -1L, BattleTeam.ENEMY, "ENEMY_1", "10", "10"
        );
        BattleState battle = new BattleState(List.of(player, enemy));

        doAnswer(invocation -> {
            // Giả lập toàn bộ chuỗi effect của một skill: mục tiêu bị hạ,
            // sau đó một effect trong cùng skill hồi sinh mục tiêu.
            enemy.takeDamage(enemy.getCurrentHp());
            enemy.revive(BigDecimal.ONE);
            return invocation.getArgument(2);
        }).when(skillExecutor).execute(
                eq(battle),
                eq(player),
                any(BattleActionType.class)
        );

        processor.processRound(battle);

        assertTrue(enemy.isAlive());
        assertTrue(!battle.isFinished());
        assertEquals(0L, battle.getEventCollector().getEvents().stream()
                .filter(event -> event.type() == BattleEventType.BATTLE_END)
                .count());
    }

    @Test
    void processRoundShouldKeepCrowdControlMessageWhenAfterActionPassiveDispelsIt() {
        DatabaseSkillExecutor skillExecutor = mock(DatabaseSkillExecutor.class);
        BattleEffectProcessor effectProcessor = mock(BattleEffectProcessor.class);
        BattleTurnProcessor processor = new BattleTurnProcessor(
                skillExecutor,
                effectProcessor
        );

        BattleUnitState player = unit(
                1L, BattleTeam.PLAYER, "PLAYER_1", "100", "30"
        );
        BattleUnitState enemy = unit(
                -1L, BattleTeam.ENEMY, "ENEMY_1", "100", "10"
        );
        BattleState battle = new BattleState(List.of(player, enemy));

        doAnswer(invocation -> {
            BattleUnitState source = invocation.getArgument(1);
            BattlePassiveTrigger trigger = invocation.getArgument(2);
            if (source == player && trigger == BattlePassiveTrigger.BEFORE_ACTION) {
                player.getStatusEffectManager().applyCrowdControl(
                        BattleStatusType.STUN,
                        "TEST_STUN",
                        "ENEMY",
                        enemy.getInstanceId(),
                        1,
                        false
                );
            }
            if (source == player && trigger == BattlePassiveTrigger.AFTER_ACTION) {
                player.getStatusEffectManager().dispelCrowdControl();
            }
            return null;
        }).when(skillExecutor).executePassives(
                eq(battle),
                any(BattleUnitState.class),
                any(BattlePassiveTrigger.class)
        );

        processor.processRound(battle);

        verify(skillExecutor, never()).execute(
                eq(battle),
                eq(player),
                any(BattleActionType.class)
        );
        assertTrue(battle.getEventCollector().getEvents().stream()
                .anyMatch(event -> event.type() == BattleEventType.ACTION_END
                        && event.sourceInstanceId().equals(player.getInstanceId())
                        && event.message().contains("STUN")));
    }

    private BattleUnitState unit(
            Long id,
            BattleTeam team,
            String heroCode,
            String hp,
            String speed
    ) {
        return new BattleUnitState(
                id,
                team,
                heroCode,
                new BigDecimal(hp),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                new BigDecimal(speed)
        );
    }
}
