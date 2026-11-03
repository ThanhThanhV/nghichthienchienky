package vn.nghichtien.chienky.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.battle.domain.BattleEffectType;
import vn.nghichtien.chienky.battle.domain.BattleTargetMode;
import vn.nghichtien.chienky.hero.domain.HeroBaseStats;
import vn.nghichtien.chienky.hero.domain.HeroDefinition;
import vn.nghichtien.chienky.hero.domain.HeroGrowthConfig;
import vn.nghichtien.chienky.hero.domain.HeroInstance;
import vn.nghichtien.chienky.hero.domain.HeroInstanceSkill;
import vn.nghichtien.chienky.hero.domain.HeroSkillDefinition;
import vn.nghichtien.chienky.hero.domain.HeroSkillEffect;
import vn.nghichtien.chienky.hero.repository.HeroBaseStatsRepository;
import vn.nghichtien.chienky.hero.repository.HeroDefinitionRepository;
import vn.nghichtien.chienky.hero.repository.HeroGrowthConfigRepository;
import vn.nghichtien.chienky.hero.repository.HeroInstanceRepository;
import vn.nghichtien.chienky.hero.repository.HeroInstanceSkillRepository;
import vn.nghichtien.chienky.hero.repository.HeroSkillDefinitionRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GameBootstrapDataInitializer implements ApplicationRunner {
    private final ObjectMapper objectMapper;
    private final HeroDefinitionRepository heroDefinitionRepository;
    private final HeroBaseStatsRepository heroBaseStatsRepository;
    private final HeroGrowthConfigRepository heroGrowthConfigRepository;
    private final HeroSkillDefinitionRepository heroSkillDefinitionRepository;
    private final HeroInstanceRepository heroInstanceRepository;
    private final HeroInstanceSkillRepository heroInstanceSkillRepository;

    public GameBootstrapDataInitializer(
            ObjectMapper objectMapper,
            HeroDefinitionRepository heroDefinitionRepository,
            HeroBaseStatsRepository heroBaseStatsRepository,
            HeroGrowthConfigRepository heroGrowthConfigRepository,
            HeroSkillDefinitionRepository heroSkillDefinitionRepository,
            HeroInstanceRepository heroInstanceRepository,
            HeroInstanceSkillRepository heroInstanceSkillRepository
    ) {
        this.objectMapper = objectMapper;
        this.heroDefinitionRepository = heroDefinitionRepository;
        this.heroBaseStatsRepository = heroBaseStatsRepository;
        this.heroGrowthConfigRepository = heroGrowthConfigRepository;
        this.heroSkillDefinitionRepository = heroSkillDefinitionRepository;
        this.heroInstanceRepository = heroInstanceRepository;
        this.heroInstanceSkillRepository = heroInstanceSkillRepository;
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
        ensureBasicAttackDash(definition);
        if (definition.getSortOrder() == null || definition.getSortOrder() == 0) {
            definition.changeSortOrder(sortOrder);
        }
        heroDefinitionRepository.save(definition);

        seedBaseStats(definition);
        seedGrowthConfig(definition);
        seedSkills(definition, seed);
    }

    /**
     * Đặt kiểu đánh thường mặc định DASH cho các tướng hiện có.
     * Chỉ bổ sung khi chưa có cấu hình movement, không ghi đè lựa chọn
     * đã được quản trị viên lưu và không làm mất mapping animation khác.
     */
    private void ensureBasicAttackDash(HeroDefinition definition) {
        String currentConfig = definition.getSpineSkillAnimations();
        ObjectNode animations;

        try {
            if (currentConfig == null || currentConfig.isBlank()) {
                animations = objectMapper.createObjectNode();
            } else {
                JsonNode parsed = objectMapper.readTree(currentConfig);
                if (parsed == null || !parsed.isObject()) {
                    return;
                }
                animations = (ObjectNode) parsed.deepCopy();
            }

            JsonNode basicAttack = animations.get("_BASIC_ATTACK");
            if (basicAttack != null && basicAttack.isObject()
                    && basicAttack.hasNonNull("movement")) {
                return;
            }

            ObjectNode basicAttackConfig = basicAttack != null && basicAttack.isObject()
                    ? (ObjectNode) basicAttack
                    : objectMapper.createObjectNode();
            basicAttackConfig.put("movement", "DASH");
            animations.set("_BASIC_ATTACK", basicAttackConfig);
            definition.updateSpineSkillAnimations(
                    objectMapper.writeValueAsString(animations)
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // Không ghi đè mapping cũ nếu JSON hiện tại không hợp lệ.
            // Frontend vẫn mặc định DASH khi không đọc được movement.
        }
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
            } else if (order == 2 && skill.getEffects().isEmpty()
                    && !"gojo".equalsIgnoreCase(definition.getHeroCode())) {
                addDamageEffect(skill, "ALL_ENEMIES", "1.5");
            } else if (order == 3 && skill.getEffects().isEmpty()) {
                addDamageEffect(skill, "ALL_ENEMIES", "2.5");
            }
        }

        if ("gojo".equalsIgnoreCase(definition.getHeroCode())) {
            seedGojoInfinityEffects(skillsByOrder);
        }

        if (!skillsToSave.isEmpty()) {
            heroSkillDefinitionRepository.saveAll(skillsToSave);
        }
        heroSkillDefinitionRepository.flush();

        // Các Gojo đã sở hữu từ trước được tự động gắn lại bộ kỹ năng mới.
        // Bản ghi cấp kỹ năng đã tồn tại được giữ nguyên level.
        if ("gojo".equalsIgnoreCase(definition.getHeroCode())) {
            initializeGojoInstanceSkills(definition, skillsByOrder);
        }
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
            // ACTIVE Vô Hạ Hạn chỉ phòng thủ, tuyệt đối không còn DAMAGE mặc định.
            for (HeroSkillEffect effect : new ArrayList<>(infinity.getEffects())) {
                if (BattleEffectType.DAMAGE.name().equalsIgnoreCase(effect.getEffectType())) {
                    infinity.removeEffect(effect);
                }
            }
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

        // Nội tại Vô Hạ Hạn: giảm 20% sát thương nhận vào trong tối đa 20 round.
        // BLOCK là hiệu ứng phòng thủ dùng chung, được BattleDamageProcessor áp dụng.
        HeroSkillDefinition passiveInfinity = skillsByOrder.get(5);
        if (passiveInfinity != null) {
            addGojoEffectIfMissing(
                    passiveInfinity,
                    "GOJO_INFINITY_DAMAGE_REDUCTION",
                    BattleEffectType.BLOCK,
                    "SELF",
                    "0.2",
                    20,
                    "{\"trigger\":\"BATTLE_START\"}"
            );
        }
    }

    /**
     * Tạo liên kết cấp kỹ năng level 1 cho các bản sao Gojo đang sở hữu.
     * Không ghi đè level nếu liên kết đã tồn tại.
     */
    private void initializeGojoInstanceSkills(
            HeroDefinition definition,
            Map<Integer, HeroSkillDefinition> skillsByOrder
    ) {
        List<HeroInstanceSkill> missingSkills = new ArrayList<>();
        List<HeroSkillDefinition> definitions = skillsByOrder.values().stream()
                .filter(skill -> Boolean.TRUE.equals(skill.getEnabled()))
                .toList();

        for (HeroInstance instance :
                heroInstanceRepository.findAllByHeroDefinitionHeroCode(
                        definition.getHeroCode()
                )) {
            for (HeroSkillDefinition skill : definitions) {
                boolean exists = heroInstanceSkillRepository
                        .findByHeroInstanceIdAndSkillDefinitionId(
                                instance.getId(), skill.getId()
                        )
                        .isPresent();

                if (!exists) {
                    missingSkills.add(new HeroInstanceSkill(instance, skill));
                }
            }
        }

        if (!missingSkills.isEmpty()) {
            heroInstanceSkillRepository.saveAll(missingSkills);
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
