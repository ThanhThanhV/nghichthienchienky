package vn.nghichtien.chienky.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
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
@Order(2)
public class GameBootstrapDataInitializer implements ApplicationRunner {
    private final ObjectMapper objectMapper;
    private final TabLuaCatalogSynchronizer tabLuaCatalogSynchronizer;
    private final HeroDefinitionRepository heroDefinitionRepository;
    private final HeroBaseStatsRepository heroBaseStatsRepository;
    private final HeroGrowthConfigRepository heroGrowthConfigRepository;
    private final HeroSkillDefinitionRepository heroSkillDefinitionRepository;
    private final HeroInstanceRepository heroInstanceRepository;
    private final HeroInstanceSkillRepository heroInstanceSkillRepository;

    public GameBootstrapDataInitializer(
            ObjectMapper objectMapper,
            TabLuaCatalogSynchronizer tabLuaCatalogSynchronizer,
            HeroDefinitionRepository heroDefinitionRepository,
            HeroBaseStatsRepository heroBaseStatsRepository,
            HeroGrowthConfigRepository heroGrowthConfigRepository,
            HeroSkillDefinitionRepository heroSkillDefinitionRepository,
            HeroInstanceRepository heroInstanceRepository,
            HeroInstanceSkillRepository heroInstanceSkillRepository
    ) {
        this.objectMapper = objectMapper;
        this.tabLuaCatalogSynchronizer = tabLuaCatalogSynchronizer;
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
        JsonNode tabSkillCatalog = loadTabSkillConfigCatalog();
        // Dữ liệu ID/tên/mô tả/script/level được hydrate trực tiếp từ TAB Lua
        // trước khi validate và ghi xuống database; JSON chỉ giữ runtime metadata.
        tabLuaCatalogSynchronizer.synchronize(tabSkillCatalog, spineCatalog);
        validateSkillAnimationCatalog(
                seeds, spineCatalog, animationConfigCatalog, tabSkillCatalog
        );
        List<String> heroCodes = seeds.stream().map(HeroSeed::code).toList();

        // Đồng bộ định nghĩa kỹ năng theo skillCode hiện có, không xóa bản ghi.
        // Nhờ giữ nguyên skill ID, cấp kỹ năng của từng HeroInstance vẫn được bảo toàn.
        for (int i = 0; i < seeds.size(); i++) {
            HeroSeed seed = seeds.get(i);
            seedHero(
                    seed, i + 1, spineCatalog, animationConfigCatalog,
                    tabSkillCatalog.path("heroes").path(seed.code())
            );
        }

        // Gắn lại 5 kỹ năng cấp 1 cho mọi bản sao tướng người chơi đang sở hữu.
        rebuildOwnedHeroSkills(heroCodes);
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
            JsonNode animationConfigCatalog,
            JsonNode tabSkillConfig
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

        seedBaseStats(definition, tabSkillConfig);
        seedGrowthConfig(definition);
        seedSkills(definition, seed, tabSkillConfig);
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
    private JsonNode loadTabSkillConfigCatalog() {
        try (var input = new ClassPathResource(
                "static/spine/hero-tab-skill-config.json"
        ).getInputStream()) {
            JsonNode catalog = objectMapper.readTree(input);
            if (catalog == null || !catalog.path("heroes").isObject()) {
                throw new IllegalStateException(
                        "TAB skill catalog không có cấu trúc heroes hợp lệ"
                );
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể đọc catalog TAB hero-tab-skill-config.json",
                    exception
            );
        }
    }

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
     * Ưu tiên đúng animation_config ID lấy từ tên skeleton.
     * Chỉ fallback về asset gốc khi Lua không khai báo cấu hình riêng cho biến thể.
     */
    private String resolveAnimationConfigId(
            String assetBase,
            JsonNode animationConfigCatalog
    ) {
        String exactId = assetBase.replaceFirst("^sanim_", "");
        if (exactId.isBlank()) {
            return "";
        }
        if (animationConfigCatalog.path(exactId).isObject()) {
            return exactId;
        }

        String fallbackId = exactId.replaceFirst("_1$", "");
        if (!fallbackId.equals(exactId)
                && animationConfigCatalog.path(fallbackId).isObject()) {
            return fallbackId;
        }
        return exactId;
    }

    /**
     * Đối chiếu skill với TAB_Sseffect -> TAB_EffectPerfor.
     * Các performance ID của bộ hero hiện tại xác định trực tiếp action family:
     * 10000110 = đánh thường, 10000210 = skill, 10000310 = ultimate.
     * Những passive không có Sseffect không bị ép phát animation.
     */
    private void validateTabSkillAnimationBinding(
            String heroCode,
            String skillCode,
            JsonNode tabSkill,
            JsonNode skillAnimations
    ) {
        JsonNode sseRows = tabSkill.path("scriptResolution").path("sse");
        if (!sseRows.isArray() || sseRows.isEmpty()) {
            return;
        }

        java.util.LinkedHashSet<String> expectedAnimations =
                new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> performanceIds =
                new java.util.LinkedHashSet<>();

        for (JsonNode sseRow : sseRows) {
            String performanceId = sseRow.path("fields").path(1).asText("");
            if (performanceId.isBlank()) {
                continue;
            }
            performanceIds.add(performanceId);
            String animation = switch (performanceId) {
                case "10000110" -> "att";
                case "10000210" -> "skill";
                case "10000310" -> "ultimateskill";
                default -> throw new IllegalStateException(
                        "Chưa có quy tắc animation cho TAB_EffectPerfor "
                                + performanceId + " tại " + heroCode
                                + " / " + skillCode
                );
            };
            expectedAnimations.add(animation);
        }

        if (expectedAnimations.isEmpty()) {
            return;
        }
        if (expectedAnimations.size() != 1) {
            throw new IllegalStateException(
                    "Một skill có nhiều action animation từ TAB: "
                            + heroCode + " / " + skillCode
                            + " / performance=" + performanceIds
            );
        }

        String expected = expectedAnimations.iterator().next();
        JsonNode configured = skillAnimations.path(skillCode);
        String actual = configured.isObject()
                ? configured.path("animation").asText("")
                : configured.asText("");

        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "Sai animation mapping theo TAB Lua tại "
                            + heroCode + " / " + skillCode
                            + ": TAB=" + expected
                            + ", catalog=" + actual
                            + ", performance=" + performanceIds
            );
        }
    }

    /**
     * Kiểm tra catalog animation phải khớp chính xác skillCode mà seedSkills tạo ra.
     * BASIC/ACTIVE/ULTIMATE phải có mapping; PASSIVE chỉ cần mapping khi
     * TAB_Sseffect xác định một action performance. Mọi mapping đều được
     * kiểm tra với skeleton thật trước khi ghi database.
     */
    private void validateSkillAnimationCatalog(
            List<HeroSeed> seeds,
            JsonNode catalog,
            JsonNode animationConfigCatalog,
            JsonNode tabSkillCatalog
    ) {
        JsonNode heroes = catalog.path("heroes");

        for (HeroSeed seed : seeds) {
            JsonNode heroMapping = heroes.path(seed.code());
            JsonNode tabHero = tabSkillCatalog.path("heroes").path(seed.code());

            validateTabHeroSource(seed.code(), heroMapping, tabHero);

            JsonNode tabBindings = heroMapping.path("_TAB_SKILL_BINDINGS");
            if (!tabBindings.isObject()) {
                throw new IllegalStateException(
                        "Thiếu _TAB_SKILL_BINDINGS cho tướng " + seed.code()
                );
            }

            if (!tabHero.path("skills").isArray()
                    || tabHero.path("skills").size() != 5
                    || tabHero.path("skills").size() != seed.skills().size()) {
                throw new IllegalStateException(
                        "TAB phải cung cấp đúng 5 slot kỹ năng cho " + seed.code()
                );
            }
            for (int slot = 0; slot < seed.skills().size(); slot++) {
                String tabRuntimeType = tabHero.path("skills").get(slot)
                        .path("runtimeSkillType").asText("");
                if (!tabRuntimeType.equalsIgnoreCase(seed.skills().get(slot).type())) {
                    throw new IllegalStateException(
                            "Sai runtimeSkillType tại " + seed.code()
                                    + " slot " + (slot + 1)
                                    + ": seed=" + seed.skills().get(slot).type()
                                    + ", TAB=" + tabRuntimeType
                    );
                }

                JsonNode tabSkill = tabHero.path("skills").get(slot);
                String skillCode = seed.code().toUpperCase(java.util.Locale.ROOT)
                        + "_" + tabRuntimeType.toUpperCase(java.util.Locale.ROOT)
                        + "_" + (slot + 1);
                JsonNode binding = tabBindings.path(skillCode);
                validateTabSkillBinding(
                        seed.code(), skillCode, tabHero, tabSkill, binding
                );
                validateTabSkillAnimationBinding(
                        seed.code(), skillCode, tabSkill,
                        heroMapping.path("skillAnimations")
                );
            }
            validateSkillEffectBindings(seed.code(), heroMapping, tabBindings);
            validateSkillEffectAssets(seed.code(), heroMapping);

            String assetBase = heroMapping.path("asset").path("base").asText("");
            String configId = resolveAnimationConfigId(assetBase, animationConfigCatalog);
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

            JsonNode skeletonAnimations = loadSkeletonAnimations(heroMapping, seed.code());
            Iterator<Map.Entry<String, JsonNode>> configuredAnimations = animations.fields();
            while (configuredAnimations.hasNext()) {
                Map.Entry<String, JsonNode> configured = configuredAnimations.next();
                String animationName = configured.getValue().asText("");
                if (animationName.isBlank() || !skeletonAnimations.has(animationName)) {
                    throw new IllegalStateException(
                            "Animation mapping không tồn tại trong Spine skeleton: "
                                    + seed.code() + " / " + configured.getKey()
                                    + " -> " + animationName
                    );
                }
            }

            // BASIC/ACTIVE/ULTIMATE bắt buộc có mapping theo skillCode.
            // PASSIVE chỉ có animation khi TAB_Sseffect chỉ ra action performance;
            // passive chỉ số/hiệu ứng nền không bị ép phát animation.
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
     * Đối chiếu metadata hero TAB với metadata được đóng gói trong Spine catalog.
     * Các biến thể dùng chung hàng TAB gốc phải khai báo sourceHeroCode/variant
     * nhất quán; runtime heroCode vẫn là namespace riêng của từng biến thể.
     */
    private void validateTabHeroSource(
            String runtimeHeroCode,
            JsonNode heroMapping,
            JsonNode tabHero
    ) {
        JsonNode source = heroMapping.path("_TAB_HERO_SOURCE");
        if (!source.isObject()) {
            throw new IllegalStateException(
                    "Thiếu _TAB_HERO_SOURCE cho tướng " + runtimeHeroCode
            );
        }

        String tabHeroId = tabHero.path("tabHeroId").asText("");
        String tabSourceHeroCode = tabHero.path("sourceHeroCode").asText("");
        String tabVariantSourceHeroCode =
                tabHero.path("variantSourceHeroCode").asText("");

        if (tabHeroId.isBlank()
                || !tabHeroId.equals(source.path("tabHeroId").asText(""))
                || tabSourceHeroCode.isBlank()
                || !tabSourceHeroCode.equals(source.path("sourceHeroCode").asText(""))
                || !tabVariantSourceHeroCode.equals(
                        source.path("variantSourceHeroCode").asText("")
                )) {
            throw new IllegalStateException(
                    "TAB hero source không đồng bộ với Spine mapping: "
                            + runtimeHeroCode
            );
        }
    }

    /**
     * Đảm bảo mỗi runtime skill slot trỏ đúng group/level-one skill của TAB.
     */
    private void validateTabSkillBinding(
            String runtimeHeroCode,
            String skillCode,
            JsonNode tabHero,
            JsonNode tabSkill,
            JsonNode binding
    ) {
        String tabHeroId = tabHero.path("tabHeroId").asText("");
        String sourceHeroCode = tabHero.path("sourceHeroCode").asText("");

        if (!binding.isObject()
                || !tabSkill.path("groupId").asText("")
                        .equals(binding.path("tabGroupId").asText(""))
                || !tabSkill.path("levelOneId").asText("")
                        .equals(binding.path("tabSkillId").asText(""))
                || !tabHeroId.equals(binding.path("sourceHeroId").asText(""))
                || !sourceHeroCode.equals(binding.path("sourceHeroCode").asText(""))
                || !tabSkill.path("runtimeSkillType").asText("")
                        .equalsIgnoreCase(binding.path("runtimeSkillType").asText(""))
                || !tabSkill.path("name").asText("")
                        .equals(binding.path("name").asText(""))) {
            throw new IllegalStateException(
                    "TAB skill binding không đồng bộ: hero=" + runtimeHeroCode
                            + ", skillCode=" + skillCode
                            + ", tabSkillId=" + tabSkill.path("levelOneId").asText("")
            );
        }
    }

    /**
     * Mọi VFX được gắn vào kỹ năng phải có TAB binding tương ứng.
     */
    private void validateSkillEffectBindings(
            String heroCode,
            JsonNode heroMapping,
            JsonNode tabBindings
    ) {
        JsonNode skillEffects = heroMapping.path("_SKILL_EFFECTS");
        if (skillEffects.isMissingNode() || skillEffects.isNull()) {
            return;
        }
        if (!skillEffects.isObject()) {
            throw new IllegalStateException(
                    "_SKILL_EFFECTS không hợp lệ cho tướng " + heroCode
            );
        }

        Iterator<String> skillCodes = skillEffects.fieldNames();
        while (skillCodes.hasNext()) {
            String skillCode = skillCodes.next();
            if (!tabBindings.path(skillCode).isObject()) {
                throw new IllegalStateException(
                        "VFX đang trỏ tới skillCode không có TAB binding: "
                                + heroCode + " / " + skillCode
                );
            }
        }
    }

    /**
     * Kiểm tra file skeleton, atlas và toàn bộ texture page của từng VFX.
     * Không cho phép lưu mapping có đường dẫn tài nguyên không tồn tại.
     */
    private void validateSkillEffectAssets(String heroCode, JsonNode heroMapping) {
        JsonNode skillEffects = heroMapping.path("_SKILL_EFFECTS");
        if (!skillEffects.isObject()) {
            return;
        }

        JsonNode texturePages = heroMapping.path("_SPINE_EFFECT_TEXTURE_PAGES");
        Map<String, JsonNode> animationsByEffectFile = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> skills = skillEffects.fields();
        while (skills.hasNext()) {
            Map.Entry<String, JsonNode> skill = skills.next();
            if (!skill.getValue().isObject()) {
                throw new IllegalStateException(
                        "Cấu hình VFX không hợp lệ: " + heroCode + " / " + skill.getKey()
                );
            }

            Iterator<Map.Entry<String, JsonNode>> phases = skill.getValue().fields();
            while (phases.hasNext()) {
                Map.Entry<String, JsonNode> phase = phases.next();
                JsonNode configuredEffects = phase.getValue();

                if (configuredEffects.isArray()) {
                    if (configuredEffects.isEmpty()) {
                        throw new IllegalStateException(
                                "Danh sách VFX rỗng: " + heroCode + " / "
                                        + skill.getKey() + " / " + phase.getKey()
                        );
                    }
                    for (JsonNode effect : configuredEffects) {
                        validateSkillEffectAsset(
                                heroCode,
                                skill.getKey(),
                                phase.getKey(),
                                effect,
                                texturePages,
                                animationsByEffectFile
                        );
                    }
                } else {
                    validateSkillEffectAsset(
                            heroCode,
                            skill.getKey(),
                            phase.getKey(),
                            configuredEffects,
                            texturePages,
                            animationsByEffectFile
                    );
                }
            }
        }
    }

    /**
     * Validate một Spine VFX instance. Một phase có thể chứa nhiều instance
     * để phát đồng thời các lớp FRONT/BACK hoặc nhiều nhịp impact.
     */
    private void validateSkillEffectAsset(
            String heroCode,
            String skillCode,
            String phase,
            JsonNode effect,
            JsonNode texturePages,
            Map<String, JsonNode> animationsByEffectFile
    ) {
        String file = effect.path("file").asText("");
        String animation = effect.path("animation").asText("");
        if (!effect.isObject() || file.isBlank() || animation.isBlank()) {
            throw new IllegalStateException(
                    "VFX thiếu file/animation: " + heroCode + " / "
                            + skillCode + " / " + phase
            );
        }

        String basePath = "static/assets/spine/seffects/";
        String skeletonPath = basePath + file + ".json";
        requireClasspathAsset(skeletonPath, heroCode, skillCode);
        requireClasspathAsset(basePath + file + ".atlas", heroCode, skillCode);

        JsonNode skeletonAnimations = animationsByEffectFile.computeIfAbsent(
                file,
                ignored -> loadEffectSkeletonAnimations(
                        skeletonPath, heroCode, skillCode
                )
        );
        if (!skeletonAnimations.has(animation)) {
            throw new IllegalStateException(
                    "VFX animation không tồn tại trong Spine skeleton: hero="
                            + heroCode + ", skill=" + skillCode
                            + ", phase=" + phase
                            + ", file=" + file + ", animation=" + animation
            );
        }

        JsonNode configuredTextures = texturePages.path(file);
        if (!configuredTextures.isArray() || configuredTextures.isEmpty()) {
            requireClasspathAsset(
                    basePath + file + ".png", heroCode, skillCode
            );
        } else {
            for (JsonNode texture : configuredTextures) {
                requireClasspathAsset(
                        basePath + texture.asText(), heroCode, skillCode
                );
            }
        }
    }

    /**
     * Đọc danh sách animation trực tiếp từ skeleton JSON của VFX.
     * Cache được giữ trong lần validate hiện tại để không parse lặp một file
     * khi nhiều skill/phase cùng sử dụng chung hiệu ứng.
     */
    private JsonNode loadEffectSkeletonAnimations(
            String resourcePath,
            String heroCode,
            String skillCode
    ) {
        try (var input = new ClassPathResource(resourcePath).getInputStream()) {
            JsonNode skeleton = objectMapper.readTree(input);
            JsonNode animations = skeleton == null ? null : skeleton.path("animations");
            if (animations == null || !animations.isObject()) {
                throw new IllegalStateException(
                        "VFX skeleton không có danh sách animations: hero="
                                + heroCode + ", skill=" + skillCode
                                + ", path=" + resourcePath
                );
            }
            return animations;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể đọc Spine VFX skeleton: " + resourcePath,
                    exception
            );
        }
    }

    private void requireClasspathAsset(
            String resourcePath,
            String heroCode,
            String skillCode
    ) {
        if (!new ClassPathResource(resourcePath).exists()) {
            throw new IllegalStateException(
                    "Thiếu Spine VFX asset: hero=" + heroCode
                            + ", skill=" + skillCode + ", path=" + resourcePath
            );
        }
    }

    private JsonNode loadSkeletonAnimations(JsonNode heroMapping, String heroCode) {
        String folder = heroMapping.path("asset").path("folder").asText("");
        String base = heroMapping.path("asset").path("base").asText("");
        if (folder.isBlank() || base.isBlank()) {
            throw new IllegalStateException(
                    "Thiếu folder/base Spine khi validate animation của " + heroCode
            );
        }

        String resourcePath = "static/assets/spine/heroes/"
                + folder + "/" + base + ".json";
        try (var input = new ClassPathResource(resourcePath).getInputStream()) {
            JsonNode skeleton = objectMapper.readTree(input);
            JsonNode animations = skeleton.path("animations");
            if (!animations.isObject()) {
                throw new IllegalStateException(
                        "Skeleton không có danh sách animations: " + resourcePath
                );
            }
            return animations;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể đọc Spine skeleton để validate animation: " + resourcePath,
                    exception
            );
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

        String configId = resolveAnimationConfigId(base, animationConfigCatalog);
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
                "_TAB_HERO_SOURCE",
                "_TAB_SKILL_BINDINGS",
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

    private void seedBaseStats(
            HeroDefinition definition,
            JsonNode tabSkillConfig
    ) {
        JsonNode stats = tabSkillConfig.path("tabHero").path("baseStatsScaled");
        if (!stats.isObject()) {
            throw new IllegalStateException(
                    "Thiếu baseStatsScaled từ TAB cho " + definition.getHeroCode()
            );
        }

        BigDecimal hp = new BigDecimal(stats.path("hp").asText());
        BigDecimal atk = new BigDecimal(stats.path("atk").asText());
        BigDecimal def = new BigDecimal(stats.path("def").asText());
        BigDecimal speed = new BigDecimal(stats.path("speed").asText());

        HeroBaseStats baseStats = heroBaseStatsRepository
                .findByHeroCode(definition.getHeroCode())
                .orElseGet(() -> new HeroBaseStats(
                        definition, hp, atk, def, speed
                ));
        baseStats.updateStats(
                hp, atk, def, speed,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        heroBaseStatsRepository.save(baseStats);
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

    /**
     * Migration định danh có chủ đích cho các skillCode legacy đã biết.
     *
     * Chỉ đổi mã trên cùng HeroSkillDefinition, không tạo/xóa entity:
     * ID kỹ năng và FK hero_instance_skills.skill_id vì vậy được giữ nguyên,
     * bao gồm skill_level của từng bản sao tướng.
     */
    private void migrateSkillCodeIfDeclared(
            String heroCode,
            int skillOrder,
            HeroSkillDefinition occupiedSlot,
            String targetSkillCode,
            List<HeroSkillDefinition> existingSkills
    ) {
        if ("gojo".equals(heroCode)
                && skillOrder == 1
                && "GOJO_PASSIVE_1".equals(occupiedSlot.getSkillCode())
                && "GOJO_BASIC_1".equals(targetSkillCode)) {
            boolean targetAlreadyExists = existingSkills.stream()
                    .anyMatch(existing -> existing != occupiedSlot
                            && targetSkillCode.equals(existing.getSkillCode()));
            if (targetAlreadyExists) {
                throw new IllegalStateException(
                        "Không thể migration GOJO_PASSIVE_1 -> GOJO_BASIC_1: "
                                + "GOJO_BASIC_1 đã tồn tại trong DB"
                );
            }

            occupiedSlot.changeSkillCode(targetSkillCode);
            return;
        }

        throw new IllegalStateException(
                "Không có migration định danh kỹ năng được khai báo tại "
                        + heroCode + " slot " + skillOrder
                        + ": DB=" + occupiedSlot.getSkillCode()
                        + ", TAB=" + targetSkillCode
        );
    }

    private void seedSkills(
            HeroDefinition definition,
            HeroSeed seed,
            JsonNode tabSkillConfig
    ) {
        JsonNode tabSkills = tabSkillConfig.path("skills");
        if (!tabSkills.isArray() || tabSkills.size() != 5) {
            throw new IllegalStateException(
                    "TAB skill config phải có đúng 5 kỹ năng cho " + seed.code()
            );
        }

        // Lấy cả kỹ năng đang tắt để có thể bật lại mà không tạo ID mới.
        List<HeroSkillDefinition> existingSkills =
                heroSkillDefinitionRepository
                        .findAllByHeroDefinitionHeroCodeOrderBySkillOrderAsc(seed.code());

        List<HeroSkillDefinition> skillsToSave = new ArrayList<>();

        for (int i = 0; i < tabSkills.size(); i++) {
            JsonNode tabSkill = tabSkills.get(i);
            int order = i + 1;
            String skillType = tabSkill.path("runtimeSkillType").asText("");
            String skillName = tabSkill.path("name").asText("");
            String description = tabSkill.path("description").asText("");
            String tabGroupId = tabSkill.path("groupId").asText("");
            String skillCode = seed.code().toUpperCase(java.util.Locale.ROOT)
                    + "_" + skillType + "_" + order;

            if (skillType.isBlank() || skillName.isBlank()
                    || description.isBlank() || tabGroupId.isBlank()) {
                throw new IllegalStateException(
                        "TAB skill config thiếu type/name/description/groupId: "
                                + seed.code() + " slot " + order
                );
            }

            HeroSkillDefinition skill = existingSkills.stream()
                    .filter(existing -> skillCode.equals(existing.getSkillCode()))
                    .findFirst()
                    .orElse(null);

            if (skill == null) {
                HeroSkillDefinition occupiedSlot = existingSkills.stream()
                        .filter(existing -> order == existing.getSkillOrder())
                        .findFirst()
                        .orElse(null);
                if (occupiedSlot != null) {
                    migrateSkillCodeIfDeclared(
                            seed.code(), order, occupiedSlot, skillCode, existingSkills
                    );
                    skill = occupiedSlot;
                } else {
                    skill = new HeroSkillDefinition(
                            definition, skillCode, skillName, skillType, order
                    );
                }
            }

            skill.updateInformation(
                    skillName, description, tabSkill.path("icon").asText("")
            );
            skill.changeSkillType(skillType);
            skill.updateLevelRowsJson(writeJson(tabSkill.path("levelRows")));
            skill.changeEnabled(true);

            // Thay thế hiệu ứng theo catalog TAB mới nhưng giữ nguyên skill ID.
            // HeroInstanceSkill tham chiếu skill ID này nên cấp kỹ năng không bị reset.
            List<HeroSkillEffect> synchronizedEffects = new ArrayList<>();
            JsonNode effects = tabSkill.path("battleEffects");
            if (effects.isArray()) {
                for (int effectIndex = 0; effectIndex < effects.size(); effectIndex++) {
                    JsonNode effectConfig = effects.get(effectIndex);
                    String effectType = effectConfig.path("effectType").asText("");
                    String targetType = effectConfig.path("targetType").asText("");
                    if (effectType.isBlank() || targetType.isBlank()) {
                        throw new IllegalStateException(
                                "TAB battle effect thiếu effectType/targetType: "
                                        + skillCode
                        );
                    }

                    HeroSkillEffect effect = new HeroSkillEffect(
                            effectIndex + 1, effectType, targetType
                    );
                    BigDecimal multiplier = effectConfig.hasNonNull("multiplier")
                            ? effectConfig.path("multiplier").decimalValue()
                            : BigDecimal.ZERO;
                    BigDecimal chance = effectConfig.hasNonNull("chance")
                            ? effectConfig.path("chance").decimalValue()
                            : BigDecimal.ONE;
                    Integer duration = effectConfig.hasNonNull("duration")
                            ? effectConfig.path("duration").asInt()
                            : null;
                    effect.updateParameters(multiplier, chance, duration);
                    JsonNode configJson = effectConfig.path("configJson");
                    effect.updateConfig(
                            configJson.isObject() ? writeJson(configJson) : "{}"
                    );
                    synchronizedEffects.add(effect);
                }
            }
            skill.replaceEffects(synchronizedEffects);

            skillsToSave.add(skill);
        }

        // Không xóa kỹ năng cũ để tránh làm mất liên kết/cấp kỹ năng người chơi.
        // Các slot không còn trong catalog được vô hiệu hóa thay vì DELETE.
        for (HeroSkillDefinition existing : existingSkills) {
            if (existing.getSkillOrder() != null
                    && existing.getSkillOrder() > tabSkills.size()) {
                existing.changeEnabled(false);
                skillsToSave.add(existing);
            }
        }

        heroSkillDefinitionRepository.saveAllAndFlush(skillsToSave);
    }

    
    /**
     * Cấu hình kỹ năng Gojo bằng cùng hệ thống HeroSkillEffect/DatabaseSkillExecutor.
     * Vô Hạ Hạn tồn tại tối đa 20 round; nội tại phản công chỉ kích hoạt ON_DAMAGED.
     */
    private void seedGojoSkillEffects(HeroSkillDefinition skill, int order) {
        if (!skill.getEffects().isEmpty()) {
            return;
        }

        switch (order) {
            case 1 -> addDamageEffect(skill, "FRONT", "1.0");
            case 2 -> {
                addGojoEffect(
                        skill, 1, "BUFF", "SELF", "0.30", 20,
                        "{\"statType\":\"DEF\"}"
                );
                addGojoEffect(
                        skill, 2, "BLOCK", "SELF", "0.30", 20, "{}"
                );
                addGojoEffect(
                        skill, 3, "IMMUNITY", "SELF", "0", 20,
                        "{\"immunityType\":\"CC\"}"
                );
            }
            case 3 -> addDamageEffect(skill, "ALL_ENEMIES", "2.5");
            case 4 -> addGojoEffect(
                    skill, 1, "BUFF", "SELF", "0.20", 20,
                    "{\"statType\":\"CRIT\"}"
            );
            case 5 -> addGojoEffect(
                    skill, 1, "COUNTER", "SELF", "0.50", 0,
                    "{\"trigger\":\"ON_DAMAGED\"}"
            );
            default -> {
                // Các passive khác chưa có hiệu ứng riêng được giữ nguyên.
            }
        }
    }

    private void addGojoEffect(
            HeroSkillDefinition skill,
            int order,
            String effectType,
            String targetType,
            String multiplier,
            int duration,
            String configJson
    ) {
        HeroSkillEffect effect = new HeroSkillEffect(
                order, effectType, targetType
        );
        effect.updateParameters(
                new BigDecimal(multiplier),
                BigDecimal.ONE,
                duration
        );
        effect.updateConfig(configJson);
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
                        skillsWithoutUltimate(
                                "Đánh Thường", "Vô Hạ Hạn",
                                "Thuật thức Phản Chuyển-Hách",
                                "Chú thuật Vô Hạ Hạn", "Lục Nhãn"
                        )),
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

    private List<SkillSeed> skillsWithoutUltimate(
            String basic, String active, String passiveOne,
            String passiveTwo, String passiveThree
    ) {
        return List.of(
                new SkillSeed(basic, "BASIC"),
                new SkillSeed(active, "ACTIVE"),
                new SkillSeed(passiveOne, "PASSIVE"),
                new SkillSeed(passiveTwo, "PASSIVE"),
                new SkillSeed(passiveThree, "PASSIVE")
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
