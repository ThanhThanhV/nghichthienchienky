package vn.nghichtien.chienky.battle.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.nghichtien.chienky.battle.domain.BattleActionType;
import vn.nghichtien.chienky.battle.domain.BattleEventCollector;
import vn.nghichtien.chienky.battle.domain.BattleState;
import vn.nghichtien.chienky.battle.domain.BattleTeam;
import vn.nghichtien.chienky.battle.domain.BattleUnitState;
import vn.nghichtien.chienky.hero.domain.HeroSkillDefinition;
import vn.nghichtien.chienky.hero.domain.HeroSkillEffect;
import vn.nghichtien.chienky.hero.repository.HeroSkillDefinitionRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseSkillExecutorTest {

    @Mock
    private HeroSkillDefinitionRepository skillRepository;

    @Mock
    private BattleEffectProcessor effectProcessor;

    @Mock
    private BattleState battle;

    @Mock
    private BattleUnitState source;

    @Mock
    private BattleEventCollector eventCollector;

    @Mock
    private HeroSkillDefinition activeSkill;

    @Mock
    private HeroSkillDefinition basicSkill;

    @Mock
    private HeroSkillEffect effect;

    private DatabaseSkillExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DatabaseSkillExecutor(
                skillRepository,
                effectProcessor,
                new com.fasterxml.jackson.databind.ObjectMapper()
        );

        when(battle.isFinished()).thenReturn(false);
        when(battle.getEventCollector()).thenReturn(eventCollector);
        when(source.isAlive()).thenReturn(true);
        when(source.getHeroCode()).thenReturn("HERO_001");
        when(source.getTeam()).thenReturn(BattleTeam.PLAYER);
        when(source.getInstanceId()).thenReturn(10L);
        when(source.getCurrentHp()).thenReturn(new BigDecimal("100"));
        when(source.getMaxHp()).thenReturn(new BigDecimal("100"));
    }

    @Test
    void executeShouldRunActiveSkillFromDatabase() {
        when(activeSkill.getSkillType()).thenReturn("ACTIVE");
        when(activeSkill.getSkillCode()).thenReturn("HERO_001_ACTIVE");
        when(activeSkill.getSkillName()).thenReturn("Linh Kiếm Quyết");
        when(activeSkill.getEffects()).thenReturn(List.of());

        when(skillRepository
                .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                        "HERO_001"
                ))
                .thenReturn(List.of(activeSkill));

        BattleActionType actual = executor.execute(
                battle,
                source,
                BattleActionType.ACTIVE_SKILL
        );

        assertEquals(BattleActionType.ACTIVE_SKILL, actual);
        verify(effectProcessor).executeEffects(
                eq(battle),
                eq(source),
                eq(List.of())
        );
    }

    @Test
    void executeShouldFallbackToBasicWhenActiveSkillIsMissing() {
        when(basicSkill.getSkillType()).thenReturn("BASIC");
        when(basicSkill.getSkillCode()).thenReturn("HERO_001_BASIC");
        when(basicSkill.getSkillName()).thenReturn("Cơ bản");
        when(basicSkill.getEffects()).thenReturn(List.of());

        when(skillRepository
                .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                        "HERO_001"
                ))
                .thenReturn(List.of(basicSkill));

        BattleActionType actual = executor.execute(
                battle,
                source,
                BattleActionType.ACTIVE_SKILL
        );

        assertEquals(BattleActionType.BASIC_ATTACK, actual);
        verify(effectProcessor).executeEffects(
                eq(battle),
                eq(source),
                eq(List.of())
        );
    }

    @Test
    void executeShouldSkipEffectWhenChanceIsZero() {
        when(activeSkill.getSkillType()).thenReturn("ACTIVE");
        when(activeSkill.getSkillCode()).thenReturn("HERO_001_ACTIVE");
        when(activeSkill.getSkillName()).thenReturn("Linh Kiếm Quyết");
        when(activeSkill.getEffects()).thenReturn(List.of(effect));

        when(effect.getEnabled()).thenReturn(true);
        when(effect.getChance()).thenReturn(BigDecimal.ZERO);
        when(effect.getEffectOrder()).thenReturn(1);

        when(skillRepository
                .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                        "HERO_001"
                ))
                .thenReturn(List.of(activeSkill));

        executor.execute(battle, source, BattleActionType.ACTIVE_SKILL);

        verify(effectProcessor).executeEffects(
                eq(battle),
                eq(source),
                eq(List.of())
        );
    }

    @Test
    void executeShouldRejectChanceGreaterThanOne() {
        when(activeSkill.getSkillType()).thenReturn("ACTIVE");
        when(activeSkill.getEffects()).thenReturn(List.of(effect));

        when(effect.getEnabled()).thenReturn(true);
        when(effect.getChance()).thenReturn(new BigDecimal("1.01"));
        when(effect.getId()).thenReturn(99L);
        when(effect.getEffectOrder()).thenReturn(1);

        when(skillRepository
                .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                        "HERO_001"
                ))
                .thenReturn(List.of(activeSkill));

        assertThrows(
                IllegalArgumentException.class,
                () -> executor.execute(
                        battle,
                        source,
                        BattleActionType.ACTIVE_SKILL
                )
        );
    }
}
