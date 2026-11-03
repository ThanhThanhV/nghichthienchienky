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

@Component
public class GameBootstrapDataInitializer implements ApplicationRunner {
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
        List<HeroSeed> seeds = heroSeeds();
        for (int i = 0; i < seeds.size(); i++) {
            seedHero(seeds.get(i), i + 1);
        }
    }

    private void seedHero(HeroSeed seed, int sortOrder) {
        HeroDefinition definition = heroDefinitionRepository.findById(seed.code())
                .orElseGet(() -> createDefinition(seed, sortOrder));

        String avatarCode = definition.getAvatarCode();
        String spineSkeleton = definition.getSpineSkeleton();
        definition.updateAssets(
                avatarCode == null || avatarCode.isBlank() ? seed.code() : avatarCode,
                spineSkeleton == null || spineSkeleton.isBlank() ? seed.code() : spineSkeleton
        );
        if (definition.getSortOrder() == null || definition.getSortOrder() == 0) {
            definition.changeSortOrder(sortOrder);
        }
        heroDefinitionRepository.save(definition);

        seedBaseStats(definition);
        seedGrowthConfig(definition);
        seedSkills(definition, seed);
    }

    private HeroDefinition createDefinition(HeroSeed seed, int sortOrder) {
        HeroDefinition definition = new HeroDefinition(
                seed.code(), seed.name(), seed.rarity(), seed.role(), seed.code()
        );
        definition.updateDescription("Nhân vật được khởi tạo từ Spine asset: " + seed.code() + ".");
        definition.changeSortOrder(sortOrder);
        return heroDefinitionRepository.save(definition);
    }

    private void seedBaseStats(HeroDefinition definition) {
        if (heroBaseStatsRepository.existsById(definition.getHeroCode())) return;
        StatSeed stats = statsForRole(definition.getRole());
        heroBaseStatsRepository.save(new HeroBaseStats(
                definition, stats.hp(), stats.atk(), stats.def(), stats.speed()
        ));
    }

    private void seedGrowthConfig(HeroDefinition definition) {
        if (heroGrowthConfigRepository.existsById(definition.getHeroCode())) return;
        StatSeed stats = statsForRole(definition.getRole());
        HeroGrowthConfig growth = new HeroGrowthConfig(definition);
        growth.updateGrowth(
                stats.hpGrowth(), stats.atkGrowth(), stats.defGrowth(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO
        );
        heroGrowthConfigRepository.save(growth);
    }

    private StatSeed statsForRole(String role) {
        return switch (role) {
            case "ASSASSIN" -> new StatSeed("950", "145", "45", "120", "90", "14", "5");
            case "MAGE" -> new StatSeed("1000", "135", "50", "110", "95", "13", "5");
            case "TANK" -> new StatSeed("1600", "90", "100", "90", "150", "8", "10");
            case "SUPPORT" -> new StatSeed("1150", "100", "70", "105", "110", "9", "7");
            default -> new StatSeed("1200", "120", "60", "105", "110", "11", "6");
        };
    }

    private void seedSkills(HeroDefinition definition, HeroSeed seed) {
        List<HeroSkillDefinition> existingSkills = heroSkillDefinitionRepository.findAll()
                .stream()
                .filter(skill -> definition.getHeroCode().equals(
                        skill.getHeroDefinition().getHeroCode()
                ))
                .toList();

        Map<Integer, HeroSkillDefinition> skillsByOrder = new HashMap<>();
        existingSkills.forEach(skill -> skillsByOrder.put(skill.getSkillOrder(), skill));

        List<HeroSkillDefinition> skillsToSave = new ArrayList<>();
        for (int i = 0; i < seed.skills().size(); i++) {
            int order = i + 1;
            SkillSeed skillSeed = seed.skills().get(i);
            HeroSkillDefinition skill = skillsByOrder.get(order);

            if (skill == null) {
                skill = new HeroSkillDefinition(
                        definition,
                        seed.code().toUpperCase() + "_" + skillSeed.type() + "_" + order,
                        skillSeed.name(),
                        skillSeed.type(),
                        order
                );
                skill.updateInformation(skillSeed.name(),
                        "Kỹ năng khởi tạo của " + seed.name() + ".", null);
                skillsToSave.add(skill);
                skillsByOrder.put(order, skill);
            } else {
                skill.changeEnabled(true);
            }

            if (order == 1 && skill.getEffects().isEmpty()) {
                addDamageEffect(skill, "FRONT", "1.0");
            } else if (order == 2 && skill.getEffects().isEmpty()) {
                addDamageEffect(skill, "ALL_ENEMIES", "1.5");
            } else if (order == 3 && skill.getEffects().isEmpty()) {
                addDamageEffect(skill, "ALL_ENEMIES", "2.5");
            }
        }

        if ("gojo".equalsIgnoreCase(definition.getHeroCode())) {
            seedGojoInfinityEffects(skillsByOrder);
        }

        if (!skillsToSave.isEmpty()) heroSkillDefinitionRepository.saveAll(skillsToSave);
    }

    /**
     * Bổ sung cấu hình mặc định cho Gojo mà không ghi đè các hiệu ứng
     * đã được quản trị viên cấu hình. Mỗi hiệu ứng có bootstrapKey riêng
     * để việc khởi tạo có thể chạy lặp lại an toàn.
     */
    private void seedGojoInfinityEffects(
            Map<Integer, HeroSkillDefinition> skillsByOrder
    ) {
        HeroSkillDefinition infinity = skillsByOrder.get(2);
        if (infinity != null) {
            // Chỉ đổi tên mặc định cũ; giữ nguyên tên do quản trị viên tự đặt.
            if ("Lam".equals(infinity.getSkillName())) {
                infinity.updateInformation(
                        "Vô Hạ Hạn",
                        infinity.getDescription(),
                        infinity.getIconCode()
                );
            }

            addGojoEffectIfMissing(
                    infinity,
                    "GOJO_INFINITY_DEF",
                    BattleEffectType.BUFF,
                    "SELF",
                    "0.5",
                    20,
                    "{\"statType\":\"DEF\"}"
            );
            addGojoEffectIfMissing(
                    infinity,
                    "GOJO_INFINITY_CC_IMMUNITY",
                    BattleEffectType.IMMUNITY,
                    "SELF",
                    "0",
                    20,
                    "{\"immunityType\":\"CC\"}"
            );
        }

        HeroSkillDefinition sixEyes = skillsByOrder.get(4);
        if (sixEyes != null) {
            addGojoEffectIfMissing(
                    sixEyes,
                    "GOJO_SIX_EYES_COUNTER",
                    BattleEffectType.COUNTER,
                    "FRONT",
                    "1.0",
                    null,
                    "{\"trigger\":\"ON_DAMAGED\"}"
            );
        }
    }

    private void addGojoEffectIfMissing(
            HeroSkillDefinition skill,
            String bootstrapKey,
            BattleEffectType effectType,
            String targetType,
            String multiplier,
            Integer duration,
            String config
    ) {
        String marker = "\"bootstrapKey\":\"" + bootstrapKey + "\"";
        boolean alreadySeeded = skill.getEffects().stream()
                .anyMatch(effect -> effect.getConfigJson() != null
                        && effect.getConfigJson().contains(marker));

        if (alreadySeeded) {
            return;
        }

        int nextOrder = skill.getEffects().stream()
                .map(HeroSkillEffect::getEffectOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        HeroSkillEffect effect = new HeroSkillEffect(
                nextOrder,
                effectType.name(),
                BattleTargetMode.valueOf(targetType).name()
        );
        effect.updateParameters(
                new BigDecimal(multiplier),
                BigDecimal.ONE,
                duration
        );
        effect.updateConfig(config.substring(0, config.length() - 1)
                + ",\"bootstrapKey\":\"" + bootstrapKey + "\"}");
        skill.addEffect(effect);
    }

    private void addDamageEffect(HeroSkillDefinition skill, String targetType, String multiplier) {
        HeroSkillEffect effect = new HeroSkillEffect(
                1, BattleEffectType.DAMAGE.name(), BattleTargetMode.valueOf(targetType).name()
        );
        effect.updateParameters(new BigDecimal(multiplier), BigDecimal.ONE, null);
        skill.addEffect(effect);
    }

    private List<HeroSeed> heroSeeds() {
        return List.of(
                new HeroSeed("tohka_dark", "Tohka Hắc Ám", "LEGENDARY", "WARRIOR",
                        skills("Kiếm chém", "Kiếm khí", "Thiên thần giáng thế", "Ý chí chiến đấu", "Linh lực hộ thể")),
                new HeroSeed("asuna_dark", "Asuna Hắc Ám", "LEGENDARY", "ASSASSIN",
                        skills("Kiếm tốc ảnh", "Liên kích", "Tuyệt kỹ kiếm vũ", "Tốc độ phản xạ", "Kiếm ý")),
                new HeroSeed("gojo", "Gojo Satoru", "MYTHIC", "MAGE",
                        skills("Vô hạn", "Vô Hạ Hạn", "Hư thức: Tử", "Lục nhãn", "Vô hạ hạn")),
                new HeroSeed("iori", "Iori", "LEGENDARY", "ASSASSIN",
                        skills("Hỏa quyền", "Bạo liệt", "Quỷ thiêu", "Cuồng chiến", "Huyết thống")),
                new HeroSeed("iori_dark", "Iori Hắc Ám", "MYTHIC", "ASSASSIN",
                        skills("Hắc hỏa", "Quỷ vương", "Hỏa diệm tận diệt", "Ám khí", "Ma lực")),
                new HeroSeed("kirito", "Kirito", "LEGENDARY", "WARRIOR",
                        skills("Song kiếm", "Nhật quang trảm", "Starburst Stream", "Phản xạ", "Kiếm sĩ")),
                new HeroSeed("leloud", "Lelouch", "LEGENDARY", "SUPPORT",
                        skills("Mệnh lệnh tuyệt đối", "Chiến thuật", "Zero", "Geass", "Trí tuệ")),
                new HeroSeed("leloud_dark", "Lelouch Hắc Ám", "MYTHIC", "MAGE",
                        skills("Mệnh lệnh hắc ám", "Hắc chiến thuật", "Geass hủy diệt", "Đế vương", "Hắc trí")),
                new HeroSeed("thor", "Thor", "LEGENDARY", "TANK",
                        skills("Lôi thần chùy", "Sấm sét", "Ragnarok", "Thần lực", "Lôi giáp")),
                new HeroSeed("thor_rak", "Thor Thức Tỉnh", "MYTHIC", "WARRIOR",
                        skills("Lôi thần thức tỉnh", "Bão sấm", "Lôi đình Ragnarok", "Bất diệt", "Lôi huyết"))
        );
    }

    private List<SkillSeed> skills(String basic, String active, String ultimate,
                                   String passiveOne, String passiveTwo) {
        return List.of(
                new SkillSeed(basic, "BASIC"),
                new SkillSeed(active, "ACTIVE"),
                new SkillSeed(ultimate, "ULTIMATE"),
                new SkillSeed(passiveOne, "PASSIVE"),
                new SkillSeed(passiveTwo, "PASSIVE")
        );
    }

    private record StatSeed(BigDecimal hp, BigDecimal atk, BigDecimal def, BigDecimal speed,
                            BigDecimal hpGrowth, BigDecimal atkGrowth, BigDecimal defGrowth) {
        private StatSeed(String hp, String atk, String def, String speed,
                         String hpGrowth, String atkGrowth, String defGrowth) {
            this(new BigDecimal(hp), new BigDecimal(atk), new BigDecimal(def),
                    new BigDecimal(speed), new BigDecimal(hpGrowth),
                    new BigDecimal(atkGrowth), new BigDecimal(defGrowth));
        }
    }

    private record SkillSeed(String name, String type) {}
    private record HeroSeed(String code, String name, String rarity, String role,
                            List<SkillSeed> skills) {}
}
