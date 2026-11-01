package vn.nghichtien.chienky.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.battle.domain.BattleEffectType;
import vn.nghichtien.chienky.battle.domain.BattleTargetMode;
import vn.nghichtien.chienky.hero.domain.HeroBaseStats;
import vn.nghichtien.chienky.hero.domain.HeroDefinition;
import vn.nghichtien.chienky.hero.domain.HeroGrowthConfig;
import vn.nghichtien.chienky.hero.domain.HeroSkillDefinition;
import vn.nghichtien.chienky.hero.domain.HeroSkillEffect;
import vn.nghichtien.chienky.hero.repository.HeroBaseStatsRepository;
import vn.nghichtien.chienky.hero.repository.HeroDefinitionRepository;
import vn.nghichtien.chienky.hero.repository.HeroGrowthConfigRepository;
import vn.nghichtien.chienky.hero.repository.HeroSkillDefinitionRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Khởi tạo dữ liệu nhân vật khởi đầu cho database mới.
 *
 * Dữ liệu được tạo theo kiểu idempotent: không ghi đè cấu hình đã tồn tại.
 */
@Component
public class GameBootstrapDataInitializer implements ApplicationRunner {

    private static final String STARTER_HERO_CODE = "tohka_dark";

    private final HeroDefinitionRepository heroDefinitionRepository;
    private final HeroBaseStatsRepository heroBaseStatsRepository;
    private final HeroGrowthConfigRepository heroGrowthConfigRepository;
    private final HeroSkillDefinitionRepository heroSkillDefinitionRepository;

    public GameBootstrapDataInitializer(
            HeroDefinitionRepository heroDefinitionRepository,
            HeroBaseStatsRepository heroBaseStatsRepository,
            HeroGrowthConfigRepository heroGrowthConfigRepository,
            HeroSkillDefinitionRepository heroSkillDefinitionRepository
    ) {
        this.heroDefinitionRepository = heroDefinitionRepository;
        this.heroBaseStatsRepository = heroBaseStatsRepository;
        this.heroGrowthConfigRepository = heroGrowthConfigRepository;
        this.heroSkillDefinitionRepository = heroSkillDefinitionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        HeroDefinition definition = heroDefinitionRepository
                .findById(STARTER_HERO_CODE)
                .orElseGet(this::createStarterDefinition);

        // Mã nhân vật đã được khai báo trong SpineConfig.js.
        definition.updateAssets(STARTER_HERO_CODE, STARTER_HERO_CODE);
        definition.changeEnabled(true);
        heroDefinitionRepository.save(definition);

        seedBaseStats(definition);
        seedGrowthConfig(definition);
        seedStarterSkills(definition);
    }

    private HeroDefinition createStarterDefinition() {
        HeroDefinition definition = new HeroDefinition(
                STARTER_HERO_CODE,
                "Tohka Hắc Ám",
                "SSR",
                "WARRIOR",
                STARTER_HERO_CODE
        );
        definition.updateDescription("Nhân vật khởi đầu.");
        definition.changeSortOrder(1);
        return heroDefinitionRepository.save(definition);
    }

    private void seedBaseStats(HeroDefinition definition) {
        if (heroBaseStatsRepository.existsById(STARTER_HERO_CODE)) {
            return;
        }

        heroBaseStatsRepository.save(new HeroBaseStats(
                definition,
                new BigDecimal("1000"),
                new BigDecimal("100"),
                new BigDecimal("50"),
                new BigDecimal("100")
        ));
    }

    private void seedGrowthConfig(HeroDefinition definition) {
        if (heroGrowthConfigRepository.existsById(STARTER_HERO_CODE)) {
            return;
        }

        HeroGrowthConfig growth = new HeroGrowthConfig(definition);
        growth.updateGrowth(
                new BigDecimal("100"),
                new BigDecimal("10"),
                new BigDecimal("5"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        heroGrowthConfigRepository.save(growth);
    }

    private void seedStarterSkills(HeroDefinition definition) {
        List<HeroSkillDefinition> existingSkills =
                heroSkillDefinitionRepository
                        .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                                STARTER_HERO_CODE
                        );

        if (!existingSkills.isEmpty()) {
            return;
        }

        List<HeroSkillDefinition> skills = List.of(
                createSkill(definition, "TOHKA_BASIC", "Kiếm chém", "BASIC", 1),
                createSkill(definition, "TOHKA_ACTIVE", "Kiếm khí", "ACTIVE", 2),
                createSkill(definition, "TOHKA_ULTIMATE", "Thiên thần giáng thế", "ULTIMATE", 3),
                createSkill(definition, "TOHKA_PASSIVE_1", "Ý chí chiến đấu", "PASSIVE", 4),
                createSkill(definition, "TOHKA_PASSIVE_2", "Linh lực hộ thể", "PASSIVE", 5)
        );

        addDamageEffect(skills.get(0), "FRONT", "1.0");
        addDamageEffect(skills.get(1), "ALL_ENEMIES", "1.5");
        addDamageEffect(skills.get(2), "ALL_ENEMIES", "2.5");

        heroSkillDefinitionRepository.saveAll(skills);
    }

    private HeroSkillDefinition createSkill(
            HeroDefinition definition,
            String skillCode,
            String skillName,
            String skillType,
            int skillOrder
    ) {
        HeroSkillDefinition skill = new HeroSkillDefinition(
                definition,
                skillCode,
                skillName,
                skillType,
                skillOrder
        );
        skill.updateInformation(skillName, null, null);
        return skill;
    }

    private void addDamageEffect(
            HeroSkillDefinition skill,
            String targetType,
            String multiplier
    ) {
        HeroSkillEffect effect = new HeroSkillEffect(
                1,
                BattleEffectType.DAMAGE.name(),
                BattleTargetMode.valueOf(targetType).name()
        );
        effect.updateParameters(
                new BigDecimal(multiplier),
                BigDecimal.ONE,
                null
        );
        skill.addEffect(effect);
    }
}
