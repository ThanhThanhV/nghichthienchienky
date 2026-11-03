package vn.nghichtien.chienky.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
        JsonNode spineCatalog = loadSpineMappingCatalog();
        JsonNode animationConfigCatalog = loadAnimationConfigCatalog();
        validateSkillAnimationCatalog(seeds, spineCatalog, animationConfigCatalog);
        List<String> heroCodes = seeds.stream().map(HeroSeed::code).toList();

        // Tái tạo định nghĩa kỹ năng Gojo từ đầu mỗi lần khởi động.
        // Chỉ xóa liên kết kỹ năng và skill data của Gojo, không xóa dữ liệu
        // tài khoản, bản sao tướng, chỉ số hoặc đội hình người chơi.
        resetGojoSkillSeed();

        for (int i = 0; i < seeds.size(); i++) {
            seedHero(seeds.get(i), i + 1, spineCatalog, animationConfigCatalog);
        }

        // Gắn lại 5 kỹ năng cấp 1 cho mọi bản sao tướng người chơi đang sở hữu.
        rebuildOwnedHeroSkills(heroCodes);
    }

    private void resetGojoSkillSeed() {
        List<String> gojoCodes = List.of("gojo");
        heroInstanceSkillRepository
                .deleteAllBySkillDefinitionHeroDefinitionHeroCodeIn(gojoCodes);
        heroInstanceSkillRepository.flush();

        // Load effects để cascade remove xóa cả hero_skill_effects.
        List<HeroSkillDefinition> oldSkills =
                heroSkillDefinitionRepository
                        .findAllByHeroDefinitionHeroCodeOrderBySkillOrderAsc("gojo");
        if (!oldSkills.isEmpty()) {
            heroSkillDefinitionRepository.deleteAll(oldSkills);
            heroSkillDefinitionRepository.flush();
        }
    }

    private void rebuildOwnedHeroSkills(List<String> heroCodes) {
        Map<String, List<HeroSkillDefinition>> skillsByHero = new HashMap<>();
        for (String heroCode : heroCodes) {
            List<HeroSkillDefinition> skills =
                    heroSkillDefinitionRepository
                            .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                                    heroCode
                            );
            if (skills.size() != 5) {
                throw new IllegalStateException(
                        "Seed phải tạo đúng 5 kỹ năng cho tướng " + heroCode
                );
            }
            skillsByHero.put(heroCode, skills);
        }

        List<HeroInstanceSkill> links = new ArrayList<>();
        List<HeroInstance> ownedHeroes =
                heroInstanceRepository.findAllByHeroDefinitionHeroCodeIn(heroCodes);

        for (HeroInstance instance : ownedHeroes) {
            List<HeroSkillDefinition> skills = skillsByHero.get(instance.getHeroCode());
            if (skills == null) {
                continue;
            }
            for (HeroSkillDefinition skill : skills) {
                boolean exists = heroInstanceSkillRepository
                        .findByHeroInstanceIdAndSkillDefinitionId(instance.getId(), skill.getId())
                        .isPresent();
                if (!exists) {
                    links.add(new HeroInstanceSkill(instance, skill));
                }
            }
        }

        if (!links.isEmpty()) {
            heroInstanceSkillRepository.saveAll(links);
            heroInstanceSkillRepository.flush();
        }
    }

    private void seedHero(
            HeroSeed seed,
            int sortOrder,
            JsonNode spineCatalog,
            JsonNode animationConfigCatalog
    ) {
        HeroDefinition definition = heroDefinitionRepository.findById(seed.code())
                .orElseGet(() -> createDefinition(seed, sortOrder));

        String avatarCode = definition.getAvatarCode();
        String spineSkeleton = definition.getSpineSkeleton();
        definition.updateAssets(
                avatarCode == null || avatarCode.isBlank() ? seed.code() : avatarCode,
                spineSkeleton == null || spineSkeleton.isBlank() ? seed.code() : spineSkeleton
        );
        applySpineMapping(
                definition,
                spineCatalog.path("heroes").path(seed.code()),
                animationConfigCatalog
        );
        if (definition.getSortOrder() == null || definition.getSortOrder() == 0) {
            definition.changeSortOrder(sortOrder);
        }
        heroDefinitionRepository.save(definition);

        seedBaseStats(definition);
        seedGrowthConfig(definition);
        seedSkills(definition, seed);
    }

    /**
     * Đọc catalog Spine được lưu cùng static resources.
     * Catalog là nguồn seed duy nhất cho mapping animation và VFX của 10 tướng.
     */
    private JsonNode loadSpineMappingCatalog() {
        try (var input = new ClassPathResource(
                "static/spine/hero-skill-mapping.json"
        ).getInputStream()) {
            JsonNode catalog = objectMapper.readTree(input);
            if (catalog == null || !catalog.path("heroes").isObject()) {
                throw new IllegalStateException(
                        "Catalog Spine không có cấu trúc heroes hợp lệ"
                );
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể đọc catalog Spine hero-skill-mapping.json",
                    exception
            );
        }
    }


    /**
     * Đọc animation_config.lua. Tệp là Lua table literal; chuyển cú pháp
     * key [number]/["string"] và phép gán thành JSON để Jackson parse.
     * Giữ nguyên toàn bộ tọa độ, eventName và effectId từ nguồn.
     */
    private JsonNode loadAnimationConfigCatalog() {
        try (var input = new ClassPathResource("animation_config.lua").getInputStream()) {
            String lua = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String json = lua
                    .replaceFirst("^\\s*local\\s+animation_config\\s*=\\s*", "")
                    .replaceFirst("\\s*return\\s+animation_config\\s*;?\\s*$", "")
                    .replaceAll("\\[\"([^\"]+)\"\\]\\s*=", "\"$1\":")
                    .replaceAll("\\[(\\d+)\\]\\s*=", "\"$1\":")
                    .replaceAll("=", ":")
                    .replaceAll(",\\s*([}\\]])", "$1");
            JsonNode catalog = objectMapper.readTree(json);
            if (catalog == null || !catalog.isObject() || catalog.isEmpty()) {
                throw new IllegalStateException("animation_config.lua không có dữ liệu hợp lệ");
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể đọc hoặc parse animation_config.lua", exception);
        }
    }

    /**
     * Kiểm tra catalog animation phải khớp chính xác skillCode mà seedSkills tạo ra.
     * Kỹ năng chủ động phải có mapping animation; PASSIVE không cần animation cast.
     * Nếu mapping lệch, dừng khởi động thay vì lưu cấu hình thiếu.
     */
    private void validateSkillAnimationCatalog(
            List<HeroSeed> seeds,
            JsonNode catalog,
            JsonNode animationConfigCatalog
    ) {
        JsonNode heroes = catalog.path("heroes");

        for (HeroSeed seed : seeds) {
            JsonNode heroMapping = heroes.path(seed.code());
            String assetBase = heroMapping.path("asset").path("base").asText("");
            String configId = assetBase.replaceFirst("^sanim_", "");
            if (configId.isBlank() || !animationConfigCatalog.path(configId).isObject()) {
                throw new IllegalStateException(
                        "Không tìm thấy animation_config.lua cho " + seed.code()
                                + " (asset " + assetBase + ")"
                );
            }

            JsonNode animations = heroMapping.path("skillAnimations");
            if (!animations.isObject()) {
                throw new IllegalStateException(
                        "Thiếu skillAnimations trong catalog cho tướng " + seed.code()
                );
            }

            // Catalog có thể khai báo animation cho cả PASSIVE để phục vụ
            // animation/hiển thị trạng thái. Chỉ bắt buộc mapping cho các skill
            // có hành động trong lượt: BASIC, ACTIVE và ULTIMATE.
            for (int index = 0; index < seed.skills().size(); index++) {
                int order = index + 1;
                SkillSeed skill = seed.skills().get(index);
                String skillType = skill.type();
                if ("PASSIVE".equalsIgnoreCase(skillType)) {
                    continue;
                }
                String skillCode = seed.code().toUpperCase(java.util.Locale.ROOT)
                        + "_" + skillType + "_" + order;
                JsonNode animation = animations.get(skillCode);

                if (animation == null || !animation.isTextual()
                        || animation.asText().isBlank()) {
                    throw new IllegalStateException(
                            "Thiếu mapping animation cho skillCode "
                                    + skillCode + " của tướng " + seed.code()
                    );
                }
            }
        }
    }

    /**
     * Seed asset và toàn bộ mapping Spine vào HeroDefinition.
     * Frontend chỉ cần đọc spineSkeleton/spineAtlas/spineTextures và
     * spineSkillAnimations từ DTO, không tự ghép mapping mặc định nữa.
     */
    private void applySpineMapping(
            HeroDefinition definition,
            JsonNode heroMapping,
            JsonNode animationConfigCatalog
    ) {
        if (heroMapping == null || !heroMapping.isObject()) {
            throw new IllegalStateException(
                    "Thiếu mapping Spine cho tướng " + definition.getHeroCode()
            );
        }

        JsonNode asset = heroMapping.path("asset");
        String folder = asset.path("folder").asText("");
        String base = asset.path("base").asText("");
        JsonNode textureFiles = asset.path("textures");
        if (folder.isBlank() || base.isBlank()
                || !textureFiles.isArray() || textureFiles.isEmpty()) {
            throw new IllegalStateException(
                    "Asset Spine không hợp lệ cho tướng " + definition.getHeroCode()
            );
        }

        String assetBaseUrl = "/assets/spine/heroes/" + folder + "/" + base;
        ArrayNode textureUrls = objectMapper.createArrayNode();
        textureFiles.forEach(texture -> textureUrls.add(
                "/assets/spine/heroes/" + folder + "/" + texture.asText()
        ));
        definition.updateSpineAssets(
                assetBaseUrl + ".json",
                assetBaseUrl + ".atlas",
                writeJson(textureUrls)
        );

        String configId = base.replaceFirst("^sanim_", "");
        JsonNode heroAnimationConfig = animationConfigCatalog.path(configId);
        if (!heroAnimationConfig.isObject()) {
            throw new IllegalStateException(
                    "Thiếu animation_config.lua cho " + definition.getHeroCode()
                            + " (" + configId + ")"
            );
        }

        ObjectNode skillAnimations = objectMapper.createObjectNode();
        JsonNode animationMappings = heroMapping.path("skillAnimations");
        if (!animationMappings.isObject()) {
            throw new IllegalStateException(
                    "Thiếu skillAnimations cho tướng " + definition.getHeroCode()
            );
        }

        Iterator<Map.Entry<String, JsonNode>> animationFields =
                animationMappings.fields();
        while (animationFields.hasNext()) {
            Map.Entry<String, JsonNode> field = animationFields.next();
            String actionName = field.getValue().asText();
            ObjectNode skillAnimation = objectMapper.createObjectNode()
                    .put("animation", actionName);
            JsonNode actionConfig = heroAnimationConfig.path(actionName);
            if (actionConfig.isObject()) {
                skillAnimation.set("animationConfig", actionConfig.deepCopy());
            }
            skillAnimations.set(field.getKey(), skillAnimation);
        }

        skillAnimations.put("_ANIMATION_CONFIG_ID", configId);
        skillAnimations.set("_ANIMATION_CONFIG", heroAnimationConfig.deepCopy());

        for (String key : List.of(
                "_BASIC_ATTACK",
                "_SKILL_EFFECTS",
                "_SKILL_EFFECT_ALIASES",
                "_SPINE_EFFECT_BASE_PATH",
                "_SPINE_EFFECT_TEXTURE_PAGES",
                "_SPINE_EFFECT_DEFAULTS"
        )) {
            JsonNode value = heroMapping.get(key);
            if (value != null) {
                skillAnimations.set(key, value.deepCopy());
            }
        }

        definition.updateSpineSkillAnimations(writeJson(skillAnimations));
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Không thể serialize Spine mapping cho database",
                    exception
            );
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
            String skillType = skillSeed.type();
            HeroSkillDefinition skill = skillsByOrder.get(order);

            if (skill == null) {
                skill = new HeroSkillDefinition(
                        definition,
                        seed.code().toUpperCase() + "_" + skillType + "_" + order,
                        skillSeed.name(),
                        skillType,
                        order
                );
                skill.updateInformation(skillSeed.name(),
                        "Kỹ năng khởi tạo của " + seed.name() + ".", null);
                skillsToSave.add(skill);
                skillsByOrder.put(order, skill);
            } else {
                skill.changeEnabled(true);
            }

            if (!"PASSIVE".equalsIgnoreCase(skillType)) {
                if (order == 1 && skill.getEffects().isEmpty()) {
                    addDamageEffect(skill, "FRONT", "1.0");
                } else if (order == 2 && skill.getEffects().isEmpty()) {
                    addDamageEffect(skill, "ALL_ENEMIES", "1.5");
                } else if (order == 3 && skill.getEffects().isEmpty()) {
                    addDamageEffect(skill, "ALL_ENEMIES", "2.5");
                }
            }
        }


        if (!skillsToSave.isEmpty()) heroSkillDefinitionRepository.saveAllAndFlush(skillsToSave);
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
