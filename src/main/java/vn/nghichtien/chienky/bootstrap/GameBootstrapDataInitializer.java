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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        // Chỉ bổ sung asset khi chưa được cấu hình, không ghi đè chỉnh sửa quản trị.
        String avatarCode = definition.getAvatarCode();
        String spineSkeleton = definition.getSpineSkeleton();
        definition.updateAssets(
                avatarCode == null || avatarCode.isBlank()
                        ? STARTER_HERO_CODE : avatarCode,
                spineSkeleton == null || spineSkeleton.isBlank()
                        ? STARTER_HERO_CODE : spineSkeleton
        );
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
        List<HeroSkillDefinition> existingSkills = heroSkillDefinitionRepository
                .findAll()
                .stream()
                .filter(skill -> STARTER_HERO_CODE.equals(
                        skill.getHeroDefinition().getHeroCode()
                ))
                .toList();

        Map<Integer, HeroSkillDefinition> skillsByOrder = new HashMap<>();
        existingSkills.forEach(skill ->
                skillsByOrder.put(skill.getSkillOrder(), skill)
        );

        List<HeroSkillDefinition> skillsToSave = new ArrayList<>();
        List<StarterSkill> starterSkills = List.of(
                new StarterSkill("TOHKA_BASIC", "Kiếm chém", "BASIC", 1),
                new StarterSkill("TOHKA_ACTIVE", "Kiếm khí", "ACTIVE", 2),
                new StarterSkill("TOHKA_ULTIMATE", "Thiên thần giáng thế", "ULTIMATE", 3),
                new StarterSkill("TOHKA_PASSIVE_1", "Ý chí chiến đấu", "PASSIVE", 4),
                new StarterSkill("TOHKA_PASSIVE_2", "Linh lực hộ thể", "PASSIVE", 5)
        );

        for (StarterSkill starterSkill : starterSkills) {
            HeroSkillDefinition skill = skillsByOrder.get(starterSkill.order());

            if (skill == null) {
                skill = createSkill(definition, starterSkill);
                skillsToSave.add(skill);
            } else {
                skill.changeEnabled(true);
            }

            if (starterSkill.order() == 1 && skill.getEffects().isEmpty()) {
                addDamageEffect(skill, "FRONT", "1.0");
            } else if (starterSkill.order() == 2 && skill.getEffects().isEmpty()) {
                addDamageEffect(skill, "ALL_ENEMIES", "1.5");
            } else if (starterSkill.order() == 3 && skill.getEffects().isEmpty()) {
                addDamageEffect(skill, "ALL_ENEMIES", "2.5");
            }
        }

        if (!skillsToSave.isEmpty()) {
            heroSkillDefinitionRepository.saveAll(skillsToSave);
        }
    }

    private HeroSkillDefinition createSkill(
            HeroDefinition definition,
            StarterSkill starterSkill
    ) {
        HeroSkillDefinition skill = new HeroSkillDefinition(
                definition,
                starterSkill.code(),
                starterSkill.name(),
                starterSkill.type(),
                starterSkill.order()
        );
        skill.updateInformation(starterSkill.name(), null, null);
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

    private record StarterSkill(
            String code,
            String name,
            String type,
            int order
    ) {
    }
}
