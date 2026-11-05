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
import vn.nghichtien.chienky.hero.domain.HeroSkillUpgradeConfig;
import vn.nghichtien.chienky.hero.repository.HeroBaseStatsRepository;
import vn.nghichtien.chienky.hero.repository.HeroDefinitionRepository;
import vn.nghichtien.chienky.hero.repository.HeroGrowthConfigRepository;
import vn.nghichtien.chienky.hero.repository.HeroInstanceRepository;
import vn.nghichtien.chienky.hero.repository.HeroInstanceSkillRepository;
import vn.nghichtien.chienky.hero.repository.HeroSkillDefinitionRepository;
import vn.nghichtien.chienky.hero.repository.HeroSkillUpgradeConfigRepository;

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
    private final HeroSkillUpgradeConfigRepository heroSkillUpgradeConfigRepository;
    private final HeroInstanceRepository heroInstanceRepository;
    private final HeroInstanceSkillRepository heroInstanceSkillRepository;

    public GameBootstrapDataInitializer(
            ObjectMapper objectMapper,
            TabLuaCatalogSynchronizer tabLuaCatalogSynchronizer,
            HeroDefinitionRepository heroDefinitionRepository,
            HeroBaseStatsRepository heroBaseStatsRepository,
            HeroGrowthConfigRepository heroGrowthConfigRepository,
            HeroSkillDefinitionRepository heroSkillDefinitionRepository,
            HeroSkillUpgradeConfigRepository heroSkillUpgradeConfigRepository,
            HeroInstanceRepository heroInstanceRepository,
            HeroInstanceSkillRepository heroInstanceSkillRepository
    ) {
        this.objectMapper = objectMapper;
        this.tabLuaCatalogSynchronizer = tabLuaCatalogSynchronizer;
        this.heroDefinitionRepository = heroDefinitionRepository;
        this.heroBaseStatsRepository = heroBaseStatsRepository;
        this.heroGrowthConfigRepository = heroGrowthConfigRepository;
        this.heroSkillDefinitionRepository = heroSkillDefinitionRepository;
        this.heroSkillUpgradeConfigRepository = heroSkillUpgradeConfigRepository;
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
        // Gojo không đọc skillAnimations/_SKILL_EFFECTS cũ trong JSON.
        // Tái tạo toàn bộ action/VFX trực tiếp từ TAB_EffectPerfor + Spine asset events.
        generateGojoSpineMappingFromLua(tabSkillCatalog, spineCatalog);
        validateSkillAnimationCatalog(
                seeds, spineCatalog, animationConfigCatalog, tabSkillCatalog
        );
        List<String> heroCodes = seeds.stream().map(HeroSeed::code).toList();
        if (!heroCodes.equals(HeroSeedCodes.current())) {
            throw new IllegalStateException(
                    "HeroSeedCodes phải khớp chính xác thứ tự heroSeeds(): "
                            + heroCodes
            );
        }

        // Lưu cấp skill Gojo theo mã Lua mới trước khi thay catalog.
        Map<Long, Map<String, Integer>> gojoSkillLevels = captureGojoSkillLevels();

        // Làm mới riêng catalog Gojo vì các skillCode cũ đã sai slot.
        // Nằm trong transaction của runner: nếu seed/validate lỗi thì rollback.
        resetGojoSkillCatalogInDatabase();

        // Chuẩn hóa mã cấu hình nâng cấp Gojo theo slot skill đã xác nhận từ Lua.
        remapGojoSkillUpgradeConfigs();

        // Đồng bộ catalog skill/effect/action/VFX của toàn bộ hero theo TAB Lua + Spine.
        // ID tự tăng chỉ còn là
        // khóa nội bộ của JPA, không dùng để liên kết cấp kỹ năng người chơi.
        for (int i = 0; i < seeds.size(); i++) {
            HeroSeed seed = seeds.get(i);
            seedHero(
                    seed, i + 1, spineCatalog, animationConfigCatalog,
                    tabSkillCatalog.path("heroes").path(seed.code())
            );
        }

        // Cấu hình nâng cấp được giữ theo code, nhưng dọn các skillCode đã bị
        // loại khỏi TAB catalog để tránh config mồ côi sau lần seed mới.
        for (String heroCode : heroCodes) {
            List<String> currentSkillCodes = heroSkillDefinitionRepository
                    .findAllByHeroDefinitionHeroCodeOrderBySkillOrderAsc(heroCode)
                    .stream()
                    .map(HeroSkillDefinition::getSkillCode)
                    .toList();
            if (currentSkillCodes.size() != 5) {
                throw new IllegalStateException(
                        "Catalog skill phải có đúng 5 mã cho hero " + heroCode
                );
            }
            heroSkillUpgradeConfigRepository
                    .deleteAllByHeroCodeAndSkillCodeNotIn(
                            heroCode, currentSkillCodes
                    );
        }

        // Bổ sung các liên kết skill còn thiếu sau khi Gojo được đổi skillCode;
        // các hero khác giữ nguyên cấp kỹ năng đã sở hữu.
        rebuildOwnedHeroSkills(heroCodes);
        restoreGojoSkillLevels(gojoSkillLevels);
    }

    /**
     * Chụp cấp kỹ năng Gojo trước khi thay catalog. SkillCode cũ được chuyển
     * sang skillCode mới theo mapping đã xác nhận từ TAB Lua.
     */
    private Map<Long, Map<String, Integer>> captureGojoSkillLevels() {
        Map<Long, Map<String, Integer>> levelsByInstance = new HashMap<>();
        List<HeroInstance> gojoInstances =
                heroInstanceRepository.findAllByHeroDefinitionHeroCodeIn(
                        List.of("gojo")
                );

        for (HeroInstance instance : gojoInstances) {
            Map<String, Integer> levelsBySkill = new HashMap<>();
            List<HeroInstanceSkill> skills =
                    heroInstanceSkillRepository
                            .findAllByHeroInstanceIdOrderBySkillDefinitionSkillOrderAsc(
                                    instance.getId()
                            );
            for (HeroInstanceSkill skill : skills) {
                levelsBySkill.put(
                        normalizeGojoSkillCode(skill.getSkillCode()),
                        skill.getSkillLevel()
                );
            }
            levelsByInstance.put(instance.getId(), levelsBySkill);
        }
        return levelsByInstance;
    }

    /**
     * Khôi phục cấp skill sau khi catalog Gojo mới đã được seed và liên kết lại.
     */
    private void restoreGojoSkillLevels(
            Map<Long, Map<String, Integer>> levelsByInstance
    ) {
        if (levelsByInstance == null || levelsByInstance.isEmpty()) {
            return;
        }

        List<HeroInstanceSkill> updates = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Integer>> entry : levelsByInstance.entrySet()) {
            List<HeroInstanceSkill> skills =
                    heroInstanceSkillRepository
                            .findAllByHeroInstanceIdOrderBySkillDefinitionSkillOrderAsc(
                                    entry.getKey()
                            );
            for (HeroInstanceSkill skill : skills) {
                Integer oldLevel = entry.getValue().get(skill.getSkillCode());
                if (oldLevel != null && oldLevel > 1) {
                    skill.changeLevel(oldLevel);
                    updates.add(skill);
                }
            }
        }
        if (!updates.isEmpty()) {
            heroInstanceSkillRepository.saveAll(updates);
            heroInstanceSkillRepository.flush();
        }
    }

    private String normalizeGojoSkillCode(String oldSkillCode) {
        return switch (oldSkillCode) {
            case "GOJO_PASSIVE_3" -> "GOJO_PASSIVE_2";
            case "GOJO_PASSIVE_4" -> "GOJO_PASSIVE_3";
            case "GOJO_PASSIVE_5" -> "GOJO_PASSIVE_4";
            case "GOJO_ACTIVE_2" -> "GOJO_PASSIVE_5";
            default -> oldSkillCode;
        };
    }

    /**
     * Xóa skill links và definitions Gojo cũ để seed lại đúng skillCode theo Lua.
     * Các hero khác được cập nhật tại chỗ, giữ nguyên cấp kỹ năng người chơi.
     */
    private void resetGojoSkillCatalogInDatabase() {
        List<String> gojoOnly = List.of("gojo");

        heroInstanceSkillRepository
                .deleteAllBySkillDefinitionHeroDefinitionHeroCodeIn(gojoOnly);
        heroInstanceSkillRepository.flush();

        heroSkillDefinitionRepository
                .deleteAllByHeroDefinitionHeroCodeIn(gojoOnly);
        heroSkillDefinitionRepository.flush();
    }

    /**
     * Chuyển cấu hình nâng cấp Gojo từ các skillCode cũ sang mã đã chuẩn hóa
     * theo TAB Lua. Dùng mã tạm ở lượt đầu để tránh va chạm unique key khi
     * hoán đổi nhiều skillCode trong cùng một hero.
     */
    private void remapGojoSkillUpgradeConfigs() {
        List<HeroSkillUpgradeConfig> configs =
                heroSkillUpgradeConfigRepository.findAllByHeroCode("gojo");
        if (configs.isEmpty()) {
            return;
        }

        Map<String, HeroSkillUpgradeConfig> targets = new HashMap<>();
        Map<HeroSkillUpgradeConfig, String> newCodes = new HashMap<>();

        for (HeroSkillUpgradeConfig config : configs) {
            String newCode = switch (config.getSkillCode()) {
                case "GOJO_PASSIVE_3" -> "GOJO_PASSIVE_2";
                case "GOJO_PASSIVE_4" -> "GOJO_PASSIVE_3";
                case "GOJO_PASSIVE_5" -> "GOJO_PASSIVE_4";
                case "GOJO_ACTIVE_2" -> "GOJO_PASSIVE_5";
                default -> config.getSkillCode();
            };

            String uniqueKey = newCode + "#" + config.getTargetSkillLevel();
            if (targets.putIfAbsent(uniqueKey, config) != null) {
                throw new IllegalStateException(
                        "Trùng cấu hình nâng cấp Gojo sau khi chuẩn hóa Lua: "
                                + uniqueKey
                );
            }
            newCodes.put(config, newCode);
        }

        boolean hasChanges = configs.stream().anyMatch(config ->
                !config.getSkillCode().equals(newCodes.get(config))
        );
        if (!hasChanges) {
            return;
        }

        for (HeroSkillUpgradeConfig config : configs) {
            if (!config.getSkillCode().equals(newCodes.get(config))) {
                config.changeSkillCode(
                        "__GOJO_LUA_REMAP__" + config.getSkillCode()
                );
            }
        }
        heroSkillUpgradeConfigRepository.saveAllAndFlush(configs);

        for (HeroSkillUpgradeConfig config : configs) {
            config.changeSkillCode(newCodes.get(config));
        }
        heroSkillUpgradeConfigRepository.saveAllAndFlush(configs);
    }

    /**
     * Sinh mapping Gojo từ TAB_EffectPerfor.
     *
     * TAB_EffectPerfor.<groupId>10:
     * - fields[7]: action family 1/11/21/31 -> att/skill/ultimateskill/superskill
     * - fields[10]: VFX cast
     * - fields[17]: VFX impact
     *
     * Event marker và tên animation_0/animation_1 được đọc từ Spine JSON thực tế,
     * không hard-code theo mapping cũ.
     */
    private void generateGojoSpineMappingFromLua(
            JsonNode tabSkillCatalog,
            JsonNode spineCatalog
    ) {
        JsonNode gojoTab = tabSkillCatalog.path("heroes").path("gojo");
        JsonNode skills = gojoTab.path("skills");
        JsonNode gojoMappingNode = spineCatalog.path("heroes").path("gojo");
        if (!(gojoMappingNode instanceof ObjectNode gojoMapping)
                || !skills.isArray() || skills.isEmpty()) {
            throw new IllegalStateException(
                    "Không thể tạo mapping Gojo từ TAB Lua: thiếu hero/skills"
            );
        }

        ObjectNode skillAnimations = objectMapper.createObjectNode();
        ObjectNode skillEffects = objectMapper.createObjectNode();
        ObjectNode performanceAudit = objectMapper.createObjectNode();

        for (JsonNode skill : skills) {
            int order = skill.path("order").asInt(-1);
            int groupId = skill.path("groupId").asInt(-1);
            String runtimeType = skill.path("runtimeSkillType").asText("");
            if (order <= 0 || groupId <= 0 || runtimeType.isBlank()) {
                throw new IllegalStateException(
                        "TAB Gojo có slot kỹ năng không hợp lệ: " + skill
                );
            }

            String skillCode = "GOJO_" + runtimeType.toUpperCase(java.util.Locale.ROOT)
                    + "_" + order;
            JsonNode performance = skill.path("tabPerformance");
            if (!performance.isObject()) {
                // Vô Hạ Hạn là passive chỉ số, không có TAB_EffectPerfor/VFX action.
                continue;
            }
            performanceAudit.set(skillCode, performance.deepCopy());

            int actionFamily = performance.path("fields").path(7).asInt(-1);
            String animation = switch (actionFamily) {
                case 1 -> "att";
                case 11 -> "skill";
                case 21 -> "ultimateskill";
                case 31 -> "superskill";
                default -> throw new IllegalStateException(
                        "TAB_EffectPerfor action chưa được hỗ trợ cho Gojo: "
                                + performance.path("id").asText("")
                                + ", action=" + actionFamily
                );
            };
            String castTrigger = switch (groupId) {
                case 525171 -> "ON_ENEMY_ACTION";
                case 525173 -> "ON_REVIVE";
                case 525174 -> "BATTLE_START";
                default -> null;
            };
            ObjectNode actionMapping = objectMapper.createObjectNode()
                    .put("animation", animation);
            if (castTrigger != null) {
                actionMapping.put("trigger", castTrigger);
            }
            skillAnimations.set(skillCode, actionMapping);

            String impactTrigger = switch (groupId) {
                case 525171 -> "COUNTER";
                case 525174 -> "BATTLE_START";
                default -> null;
            };
            String castAnchor = groupId == 525173 ? "target" : "source";
            String impactAnchor = switch (groupId) {
                // Hach impact bam dung root position cua instance vua hanh dong.
                // Khong neo vao mep DOM slot vi se day VFX khoi diem dat Spine.
                case 525171 -> "target";
                case 525174 -> "enemy_formation";
                default -> "target";
            };

            ObjectNode phases = objectMapper.createObjectNode();
            ArrayNode castEffects = buildGojoVfxPhase(
                    skillCode, "cast",
                    performance.path("fields").path(10),
                    "animation_0", "qishou_", castAnchor, castTrigger
            );
            ArrayNode impactEffects = buildGojoVfxPhase(
                    skillCode, "impact",
                    performance.path("fields").path(17),
                    "animation_1", "hit_", impactAnchor, impactTrigger
            );
            if (!castEffects.isEmpty()) {
                phases.set("cast", castEffects.size() == 1
                        ? castEffects.get(0) : castEffects);
            }
            if (!impactEffects.isEmpty()) {
                phases.set("impact", impactEffects.size() == 1
                        ? impactEffects.get(0) : impactEffects);
            }
            if (!phases.isEmpty()) {
                skillEffects.set(skillCode, phases);
            }
        }

        if (!skillAnimations.has("GOJO_BASIC_1")
                || !skillAnimations.has("GOJO_PASSIVE_5")
                || !skillAnimations.has("GOJO_PASSIVE_2")
                || !skillAnimations.has("GOJO_PASSIVE_4")
                || skillAnimations.has("GOJO_PASSIVE_3")) {
            throw new IllegalStateException(
                    "Mapping animation Gojo sinh từ Lua không đúng tập skill action: "
                            + skillAnimations
            );
        }

        validateGojoGeneratedVfxShape(skills, skillEffects);
        gojoMapping.set("skillAnimations", skillAnimations);
        gojoMapping.set("_SKILL_EFFECTS", skillEffects);
        gojoMapping.set("_TAB_EFFECT_PERFORMANCE", performanceAudit);
    }

    /**
     * Validate mapping được sinh từ chính các tuple VFX trong TAB_EffectPerfor.
     *
     * Lua quyết định:
     * - Có phase cast/impact hay không.
     * - VFX file nào phải được nạp.
     * Spine JSON quyết định danh sách event thực tế trong animation.
     *
     * Không hard-code số lượng skill, phase hoặc hit event của từng Gojo slot.
     */
    private void validateGojoGeneratedVfxShape(
            JsonNode skills,
            JsonNode skillEffects
    ) {
        if (!skills.isArray()) {
            throw new IllegalStateException("TAB Gojo skills không phải array");
        }

        java.util.Set<String> expectedSkillCodes = new java.util.LinkedHashSet<>();
        for (JsonNode skill : skills) {
            int order = skill.path("order").asInt(-1);
            String runtimeType = skill.path("runtimeSkillType").asText("");
            if (order <= 0 || runtimeType.isBlank()) {
                throw new IllegalStateException(
                        "TAB Gojo có slot không hợp lệ khi validate VFX: " + skill
                );
            }

            String skillCode = "GOJO_" + runtimeType.toUpperCase(java.util.Locale.ROOT)
                    + "_" + order;
            expectedSkillCodes.add(skillCode);

            JsonNode performance = skill.path("tabPerformance");
            if (!performance.isObject()) {
                if (skillEffects.has(skillCode)) {
                    throw new IllegalStateException(
                            "Gojo có VFX mapping nhưng TAB_EffectPerfor không định nghĩa: "
                                    + skillCode
                    );
                }
                continue;
            }

            validateGojoVfxPhaseAgainstLua(
                    skillCode, "cast", "animation_0", "qishou_",
                    performance.path("fields").path(10), skillEffects
            );
            validateGojoVfxPhaseAgainstLua(
                    skillCode, "impact", "animation_1", "hit_",
                    performance.path("fields").path(17), skillEffects
            );
        }

        java.util.Iterator<String> generatedSkills = skillEffects.fieldNames();
        while (generatedSkills.hasNext()) {
            String skillCode = generatedSkills.next();
            if (!expectedSkillCodes.contains(skillCode)) {
                throw new IllegalStateException(
                        "Gojo có VFX mapping không được TAB_Hero/TAB_Skill tham chiếu: "
                                + skillCode
                );
            }
        }
    }

    private void validateGojoVfxPhaseAgainstLua(
            String skillCode,
            String phase,
            String expectedAnimation,
            String expectedEventPrefix,
            JsonNode luaVfxRows,
            JsonNode skillEffects
    ) {
        List<JsonNode> expectedRows = normalizeGojoVfxRows(
                luaVfxRows, skillCode, phase
        );
        JsonNode generatedPhase = skillEffects.path(skillCode).path(phase);

        if (expectedRows.isEmpty()) {
            if (!generatedPhase.isMissingNode()) {
                throw new IllegalStateException(
                        "Gojo có VFX " + phase + " nhưng Lua không khai báo: "
                                + skillCode + ", generated=" + generatedPhase
                );
            }
            return;
        }

        List<JsonNode> generatedEffects = new ArrayList<>();
        if (generatedPhase.isArray()) {
            generatedPhase.forEach(generatedEffects::add);
        } else if (generatedPhase.isObject()) {
            generatedEffects.add(generatedPhase);
        }

        if (generatedEffects.size() != expectedRows.size()) {
            throw new IllegalStateException(
                    "Sai số lượng VFX row so với TAB_EffectPerfor: "
                            + skillCode + "/" + phase
                            + ", luaRows=" + expectedRows.size()
                            + ", generated=" + generatedEffects.size()
            );
        }

        for (int i = 0; i < expectedRows.size(); i++) {
            JsonNode luaRow = expectedRows.get(i);
            JsonNode generated = generatedEffects.get(i);
            String expectedFile = "seffect_" + luaRow.get(0).asText();

            if (!expectedFile.equals(generated.path("file").asText(""))
                    || !expectedAnimation.equals(generated.path("animation").asText(""))) {
                throw new IllegalStateException(
                        "Sai Spine VFX mapping so với Lua: " + skillCode + "/" + phase
                                + ", expected=" + expectedFile + "/" + expectedAnimation
                                + ", actual=" + generated
                );
            }

            JsonNode events = generated.path("impactEvents");
            if (!events.isArray() || events.isEmpty()) {
                throw new IllegalStateException(
                        "Spine VFX không có event phù hợp: "
                                + skillCode + "/" + phase + "/" + expectedFile
                );
            }
            for (JsonNode event : events) {
                if (!event.isTextual()
                        || !event.asText().startsWith(expectedEventPrefix)) {
                    throw new IllegalStateException(
                            "Spine event không đúng phase " + phase + ": "
                                    + skillCode + ", event=" + event
                    );
                }
            }
            if (!generated.path("impactEvent").asText("")
                    .equals(events.get(0).asText())) {
                throw new IllegalStateException(
                        "impactEvent không khớp event đầu tiên: "
                                + skillCode + "/" + phase
                );
            }
        }
    }

    /**
     * TAB_EffectPerfor có hai kiểu biểu diễn:
     * - Danh sách nhiều VFX row: {{effectId, ...}, {effectId, ...}}.
     * - Một VFX row duy nhất: {effectId, ...}.
     *
     * Không được coi từng scalar trong tuple là một row riêng.
     */
    private List<JsonNode> normalizeGojoVfxRows(
            JsonNode vfxRows,
            String skillCode,
            String phase
    ) {
        List<JsonNode> rows = new ArrayList<>();
        if (vfxRows == null || vfxRows.isMissingNode() || vfxRows.isNull()) {
            return rows;
        }
        if (!vfxRows.isArray()) {
            throw new IllegalStateException(
                    "TAB_EffectPerfor VFX column không phải Lua table: "
                            + skillCode + "/" + phase + ", value=" + vfxRows
            );
        }
        if (vfxRows.isEmpty()) {
            return rows;
        }

        if (vfxRows.get(0).isArray()) {
            for (JsonNode row : vfxRows) {
                if (!row.isArray()) {
                    throw new IllegalStateException(
                            "TAB_EffectPerfor có VFX row trộn kiểu: "
                                    + skillCode + "/" + phase + ", value=" + vfxRows
                    );
                }
                rows.add(row);
            }
        } else {
            rows.add(vfxRows);
        }

        for (JsonNode row : rows) {
            // Effect ID trong Lua có thể vượt Integer.MAX_VALUE (ví dụ 52517111011).
            // Phải validate bằng long; không ép về int vì sẽ loại nhầm ID hợp lệ.
            if (row.isEmpty() || !row.get(0).canConvertToLong()
                    || row.get(0).asLong() <= 0L) {
                throw new IllegalStateException(
                        "TAB_EffectPerfor có effect ID không hợp lệ: "
                                + skillCode + "/" + phase + ", row=" + row
                );
            }
        }
        return rows;
    }

    /**
     * Đọc VFX ID từ đúng cột cast/impact của TAB_EffectPerfor và lấy event
     * marker từ animation thực tế của từng seffect JSON.
     */
    private ArrayNode buildGojoVfxPhase(
            String skillCode,
            String phase,
            JsonNode vfxRows,
            String animationName,
            String eventPrefix,
            String anchor,
            String trigger
    ) {
        ArrayNode result = objectMapper.createArrayNode();
        List<JsonNode> normalizedVfxRows = normalizeGojoVfxRows(
                vfxRows, skillCode, phase
        );

        for (JsonNode vfxRow : normalizedVfxRows) {
            String effectFile = "seffect_" + vfxRow.get(0).asText();
            String resourcePath = "static/assets/spine/seffects/"
                    + effectFile + ".json";
            JsonNode animations = loadEffectSkeletonAnimations(
                    resourcePath, "gojo", skillCode
            );
            JsonNode animation = animations.path(animationName);
            if (!animation.isObject()) {
                throw new IllegalStateException(
                        "TAB Gojo yêu cầu VFX animation không tồn tại: "
                                + effectFile + "/" + animationName
                );
            }

            String expectedPrefix = eventPrefix;
            ArrayNode impactEvents = objectMapper.createArrayNode();
            JsonNode sourceEvents = animation.path("events");
            if (sourceEvents.isArray()) {
                for (JsonNode event : sourceEvents) {
                    String eventName = event.path("name").asText("");
                    if (eventName.startsWith(expectedPrefix)) {
                        impactEvents.add(eventName);
                    }
                }
            }
            if (impactEvents.isEmpty()) {
                throw new IllegalStateException(
                        "Không tìm thấy Spine event " + expectedPrefix
                                + " trong " + effectFile + "/" + animationName
                );
            }

            ObjectNode effect = objectMapper.createObjectNode();
            effect.put("code", "gojo_" + skillCode.toLowerCase(java.util.Locale.ROOT)
                    + "_" + phase + "_" + effectFile);
            effect.put("file", effectFile);
            effect.put("animation", animationName);
            effect.put("anchor", anchor);
            effect.put("impactEvent", impactEvents.get(0).asText());
            effect.set("impactEvents", impactEvents);
            if (trigger != null && !trigger.isBlank()) {
                effect.put("trigger", trigger);
            }
            result.add(effect);
        }
        return result;
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
                        .findByHeroInstanceIdAndSkillCode(instance.getId(), skill.getSkillCode())
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
        avatarCode = avatarCode == null || avatarCode.isBlank() ? seed.code() : avatarCode;
        spineSkeleton = spineSkeleton == null || spineSkeleton.isBlank() ? seed.code() : spineSkeleton;

        // Catalog được đồng bộ bằng heroCode, không giữ metadata cũ chỉ vì row đã tồn tại.
        definition.updateManagementInformation(
                seed.name(),
                seed.rarity(),
                seed.role(),
                definition.getFaction(),
                avatarCode,
                definition.getDescription(),
                true,
                sortOrder
        );
        definition.updateAssets(avatarCode, spineSkeleton);
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
     * Catalog cung cấp asset manifest và mapping của các hero dùng cấu hình tĩnh.
     * Riêng Gojo, action/VFX mapping được tạo mới trực tiếp từ TAB Lua bên dưới.
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
     * Đối chiếu action animation với TAB Lua.
     * Hero thông thường dùng TAB_Sseffect performance ID; Gojo dùng trực tiếp
     * action family tại TAB_EffectPerfor.fields[7] để phân biệt đủ 4 action.
     * Passive không có action performance không bị ép phát animation.
     */
    private void validateTabSkillAnimationBinding(
            String heroCode,
            String skillCode,
            JsonNode tabSkill,
            JsonNode skillAnimations
    ) {
        // Gojo action family được khai báo trực tiếp ở TAB_EffectPerfor.fields[7].
        // 10000310 dùng chung cho Lục Nhãn và Domain, nên không thể dùng riêng
        // performance ID để phân biệt ultimateskill với superskill.
        if ("gojo".equalsIgnoreCase(heroCode)
                && tabSkill.path("tabPerformance").isObject()) {
            int actionFamily = tabSkill.path("tabPerformance")
                    .path("fields").path(7).asInt(-1);
            String expected = switch (actionFamily) {
                case 1 -> "att";
                case 11 -> "skill";
                case 21 -> "ultimateskill";
                case 31 -> "superskill";
                default -> throw new IllegalStateException(
                        "TAB_EffectPerfor action Gojo không hợp lệ: "
                                + actionFamily + " / " + skillCode
                );
            };
            JsonNode configured = skillAnimations.path(skillCode);
            String actual = configured.isObject()
                    ? configured.path("animation").asText("")
                    : configured.asText("");
            if (!expected.equals(actual)) {
                throw new IllegalStateException(
                        "Sai Gojo animation mapping theo TAB_EffectPerfor: "
                                + skillCode + ", TAB=" + expected + ", mapping=" + actual
                );
            }
            return;
        }

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
                JsonNode animationConfig = configured.getValue();
                String animationName = animationConfig.isObject()
                        ? animationConfig.path("animation").asText("")
                        : animationConfig.asText("");
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
                // Mapping có thể là chuỗi animation cũ ("att") hoặc object
                // sinh từ Lua ("animation":"att", kèm trigger/metadata).
                String animationName = animation == null
                        ? ""
                        : animation.isObject()
                                ? animation.path("animation").asText("")
                                : animation.asText("");

                if (animationName.isBlank()) {
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

        // Iori dùng nhiều event marker theo từng nhịp đánh trong một VFX.
        // Validate trực tiếp event timeline của animation JSON để tránh trường
        // hợp tên animation đúng nhưng impactEvent/impactEvents sai, khiến
        // frontend phải fallback timing hoặc không đồng bộ DAMAGE với VFX.
        if ("iori".equalsIgnoreCase(heroCode)) {
            JsonNode animationEvents = skeletonAnimations.path(animation).path("events");
            java.util.Set<String> availableEventNames = new java.util.HashSet<>();
            if (animationEvents.isArray()) {
                for (JsonNode animationEvent : animationEvents) {
                    String eventName = animationEvent.path("name").asText("");
                    if (!eventName.isBlank()) {
                        availableEventNames.add(eventName);
                    }
                }
            }

            String impactEvent = effect.path("impactEvent").asText("");
            if (!impactEvent.isBlank() && !availableEventNames.contains(impactEvent)) {
                throw new IllegalStateException(
                        "VFX impactEvent không tồn tại trong Spine animation: hero="
                                + heroCode + ", skill=" + skillCode
                                + ", phase=" + phase + ", file=" + file
                                + ", animation=" + animation + ", event=" + impactEvent
                );
            }

            JsonNode impactEvents = effect.path("impactEvents");
            if (impactEvents.isArray()) {
                for (JsonNode configuredEvent : impactEvents) {
                    String eventName = configuredEvent.asText("");
                    if (!eventName.isBlank() && !availableEventNames.contains(eventName)) {
                        throw new IllegalStateException(
                                "VFX impactEvents không tồn tại trong Spine animation: hero="
                                        + heroCode + ", skill=" + skillCode
                                        + ", phase=" + phase + ", file=" + file
                                        + ", animation=" + animation + ", event=" + eventName
                        );
                    }
                }
            }
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
            JsonNode configuredAction = field.getValue();
            String actionName = configuredAction.isObject()
                    ? configuredAction.path("animation").asText("")
                    : configuredAction.asText("");
            if (actionName.isBlank()) {
                throw new IllegalStateException(
                        "Animation action rỗng trong mapping của "
                                + definition.getHeroCode() + " / " + field.getKey()
                );
            }
            ObjectNode skillAnimation = objectMapper.createObjectNode()
                    .put("animation", actionName);
            if (configuredAction.isObject()
                    && configuredAction.hasNonNull("trigger")) {
                skillAnimation.put(
                        "trigger",
                        configuredAction.path("trigger").asText()
                );
            }
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
                "_TAB_EFFECT_PERFORMANCE",
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
        StatSeed stats = statsForRole(definition.getRole());
        HeroGrowthConfig growth = heroGrowthConfigRepository
                .findById(definition.getHeroCode())
                .orElseGet(() -> new HeroGrowthConfig(definition));
        // Ghi đè theo seed mỗi lần khởi động; không phụ thuộc dữ liệu cũ trong DB.
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
     * Không migration tại chỗ các skillCode Gojo cũ.
     * Catalog Gojo luôn được xóa và seed lại từ TAB Lua trước bước này.
     */
    private void rejectLegacySkillCodeMismatch(
            String heroCode,
            int skillOrder,
            HeroSkillDefinition occupiedSlot,
            String targetSkillCode
    ) {
        throw new IllegalStateException(
                "SkillCode DB không khớp catalog Lua sau reset tại "
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
                    rejectLegacySkillCodeMismatch(
                            seed.code(), order, occupiedSlot, skillCode
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

            // Thay thế hiệu ứng theo catalog TAB mới. Các liên kết HeroInstanceSkill
            // được truy vấn và đồng bộ bằng skillCode, không bằng skill ID.
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
            if ("gojo".equalsIgnoreCase(definition.getHeroCode())) {
                switch (skillCode) {
                    case "GOJO_BASIC_1" -> seedGojoBasicAttackEffects(skill);
                    case "GOJO_PASSIVE_2" -> seedGojoHachEffects(skill);
                    case "GOJO_PASSIVE_3" -> seedGojoInfinityEffects(skill);
                    case "GOJO_PASSIVE_4" -> seedGojoSixEyesEffects(skill);
                    case "GOJO_PASSIVE_5" -> seedGojoDomainEffects(skill);
                    default -> { }
                }
            }
            if ("dark_tohka".equalsIgnoreCase(definition.getHeroCode())) {
                switch (skillCode) {
                    case "DARK_TOHKA_BASIC_1" -> addDamageEffect(skill, "FRONT", "1.0");
                    case "DARK_TOHKA_ACTIVE_2" -> seedTohkaSandalphonEffects(skill);
                    case "DARK_TOHKA_ULTIMATE_3" -> seedTohkaHalvanhelevEffects(skill);
                    case "DARK_TOHKA_PASSIVE_4" -> seedTohkaAdonaiEffects(skill);
                    case "DARK_TOHKA_PASSIVE_5" -> seedTohkaMalkuthEffects(skill);
                    default -> { }
                }
            }

            skillsToSave.add(skill);
        }

        // Các slot không còn trong catalog được vô hiệu hóa thay vì DELETE;
        // dữ liệu kỹ năng người chơi hiện liên kết bằng code ổn định.
        for (HeroSkillDefinition existing : existingSkills) {
            if (existing.getSkillOrder() != null
                    && existing.getSkillOrder() > tabSkills.size()) {
                existing.changeEnabled(false);
                skillsToSave.add(existing);
            }
        }

        heroSkillDefinitionRepository.saveAllAndFlush(skillsToSave);
    }

    
    /** Gojo Hách: khóa hành động 10 round, phản kích phép mỗi lần địch hành động,
     * sau round 10 phát thêm một đòn đánh thường hệ số 50%. */
    private void seedGojoHachEffects(HeroSkillDefinition skill) {
        String[] magicCounter = {"0.97","1.17","1.40","1.45","1.51","1.57","1.62","1.68"};
        writeLevelEffects(skill, level -> List.of(
                createGojoEffectJson("ACTION_LOCK","SELF","0",10,
                        "{\"trigger\":\"BATTLE_START\"}"),
                createGojoEffectJson("COUNTER","SELF",magicCounter[level - 1],0,
                        "{\"trigger\":\"ON_ENEMY_ACTION\",\"damageType\":\"MAGIC\","
                                + "\"untilRound\":10,\"eventSkillCode\":\"GOJO_PASSIVE_2\"}"),
                createGojoEffectJson("DAMAGE","FRONT","0.5",0,
                        "{\"trigger\":\"ROUND_START\",\"triggerRound\":11,"
                                + "\"eventSkillCode\":\"GOJO_BASIC_1\"}")
        ));
        HeroSkillEffect lock = new HeroSkillEffect(1,"ACTION_LOCK","SELF");
        lock.updateParameters(BigDecimal.ZERO,BigDecimal.ONE,10);
        lock.updateConfig("{\"trigger\":\"BATTLE_START\"}");
        HeroSkillEffect counter = new HeroSkillEffect(2,"COUNTER","SELF");
        counter.updateParameters(new BigDecimal("0.97"),BigDecimal.ONE,0);
        counter.updateConfig("{\"trigger\":\"ON_ENEMY_ACTION\",\"damageType\":\"MAGIC\","
                + "\"untilRound\":10,\"eventSkillCode\":\"GOJO_PASSIVE_2\"}");
        HeroSkillEffect followUp = new HeroSkillEffect(3,"DAMAGE","FRONT");
        followUp.updateParameters(new BigDecimal("0.5"),BigDecimal.ONE,0);
        followUp.updateConfig("{\"trigger\":\"ROUND_START\",\"triggerRound\":11,"
                + "\"eventSkillCode\":\"GOJO_BASIC_1\"}");
        skill.replaceEffects(List.of(lock,counter,followUp));
    }

    /** Vô Hạ Hạn: ATK/HP/CRIT tăng theo đúng cấp TAB. */
    private void seedGojoInfinityEffects(HeroSkillDefinition skill) {
        String[] atk = {"0.20","0.30","0.40","0.40","0.40","0.40","0.40","0.40"};
        String[] hp = {"0.10","0.15","0.20","0.24","0.28","0.32","0.36","0.40"};
        String[] crit = {"0.05","0.10","0.15","0.15","0.15","0.15","0.15","0.15"};
        writeLevelEffects(skill, level -> List.of(
                createGojoEffectJson("BUFF","SELF",atk[level - 1],0,
                        "{\"trigger\":\"BATTLE_START\",\"statType\":\"ATK\",\"permanent\":true}"),
                createGojoEffectJson("BUFF","SELF",hp[level - 1],0,
                        "{\"trigger\":\"BATTLE_START\",\"statType\":\"MAX_HP\",\"permanent\":true}"),
                createGojoEffectJson("BUFF","SELF",crit[level - 1],0,
                        "{\"trigger\":\"BATTLE_START\",\"statType\":\"CRIT\",\"permanent\":true}")
        ));
        skill.replaceEffects(List.of(
                modifierEffect(1,"ATK","0.20",0,true,"BATTLE_START"),
                modifierEffect(2,"MAX_HP","0.10",0,true,"BATTLE_START"),
                modifierEffect(3,"CRIT","0.05",0,true,"BATTLE_START")
        ));
    }

    /** Sandalphon: damage AOE theo level, buff CRIT cho Tank/Hỗ Trợ trong 2 round. */
    private void seedTohkaSandalphonEffects(HeroSkillDefinition skill) {
        String[] damage = {"0.68","0.82","1.00","1.06","1.12","1.18","1.24","1.30"};
        String[] crit = {"0.05","0.10","0.15","0.15","0.15","0.15","0.15","0.15"};
        writeLevelEffects(skill, level -> List.of(
                createGojoEffectJson("BUFF","ALL_ALLIES","0.01",2,
                        "{\"statType\":\"KINGDOM_BLESSING\",\"purgeable\":false,"
                                + "\"eventSkillCode\":\"DARK_TOHKA_ACTIVE_2\"}"),
                createGojoEffectJson("BUFF","ALL_ALLIES",crit[level - 1],2,
                        "{\"statType\":\"KINGDOM_BLESSING_CRIT\",\"purgeable\":false,"
                                + "\"eventSkillCode\":\"DARK_TOHKA_ACTIVE_2\"}"),
                createGojoEffectJson("DAMAGE","ALL_ENEMIES",damage[level - 1],0,
                        "{\"damageType\":\"PHYSICAL\","
                                + "\"eventSkillCode\":\"DARK_TOHKA_ACTIVE_2\"}")
        ));
        skill.replaceEffects(List.of(
                new HeroSkillEffect(1,"BUFF","ALL_ALLIES"),
                new HeroSkillEffect(2,"BUFF","ALL_ALLIES"),
                new HeroSkillEffect(3,"DAMAGE","ALL_ENEMIES")
        ));
    }

    /** Halvanhelev: chọn hàng địch đông nhất, tăng damage riêng với Tank/Hỗ Trợ. */
    private void seedTohkaHalvanhelevEffects(HeroSkillDefinition skill) {
        String[] damage = {"1.61","1.92","2.25","2.33","2.41","2.49","2.57","2.63"};
        String[] bonus = {"10","20","30","30","30","30","30","30"};
        writeLevelEffects(skill, level -> List.of(
                createGojoEffectJson("DAMAGE","MOST_POPULATED_ROW",damage[level - 1],0,
                        "{\"damageType\":\"PHYSICAL\",\"roleBonusTankSupport\":true,"
                                + "\"roleBonusPercent\":" + bonus[level - 1] + ","
                                + "\"eventSkillCode\":\"DARK_TOHKA_ULTIMATE_3\"}")
        ));
        skill.replaceEffects(List.of(new HeroSkillEffect(1,"DAMAGE","MOST_POPULATED_ROW")));
    }

    /** Adonai Melekh: chỉ số cơ bản và kháng khống theo level TAB. */
    private void seedTohkaAdonaiEffects(HeroSkillDefinition skill) {
        String[] atk = {"0.20","0.30","0.40","0.40","0.40","0.40","0.40","0.40"};
        String[] hp = {"0.10","0.15","0.20","0.24","0.28","0.32","0.36","0.40"};
        String[] crit = {"0.05","0.10","0.15","0.15","0.15","0.15","0.15","0.15"};
        String[] resistance = {"0.10","0.20","0.30","0.30","0.30","0.30","0.30","0.30"};
        String[] ccReduction = {"0.30","0.40","0.50","0.50","0.50","0.50","0.50","0.50"};
        writeLevelEffects(skill, level -> {
            List<ObjectNode> effects = new ArrayList<>(List.of(
                    createGojoEffectJson("BUFF","SELF",atk[level - 1],0,
                            "{\"trigger\":\"BATTLE_START\",\"statType\":\"ATK\",\"permanent\":true}"),
                    createGojoEffectJson("BUFF","SELF",hp[level - 1],0,
                            "{\"trigger\":\"BATTLE_START\",\"statType\":\"MAX_HP\",\"permanent\":true}"),
                    createGojoEffectJson("BUFF","SELF",crit[level - 1],0,
                            "{\"trigger\":\"BATTLE_START\",\"statType\":\"CRIT\",\"permanent\":true}"),
                    createGojoEffectJson("BUFF","SELF",resistance[level - 1],0,
                            "{\"trigger\":\"BATTLE_START\",\"statType\":\"RESISTANCE\",\"permanent\":true}"),
                    createGojoEffectJson("BUFF","SELF",ccReduction[level - 1],0,
                            "{\"trigger\":\"BATTLE_START\",\"statType\":\"CONTROL_DURATION_REDUCTION\",\"permanent\":true}")
            ));
            if (level >= 3) effects.add(createGojoEffectJson("BUFF","SELF","0",0,
                    "{\"trigger\":\"BATTLE_START\",\"statType\":\"SPEED_FLAT\","
                            + "\"flatValue\":15,\"permanent\":true}"));
            return effects;
        });
        skill.replaceEffects(List.of(
                modifierEffect(1,"ATK","0.20",0,true,"BATTLE_START"),
                modifierEffect(2,"MAX_HP","0.10",0,true,"BATTLE_START"),
                modifierEffect(3,"CRIT","0.05",0,true,"BATTLE_START"),
                modifierEffect(4,"RESISTANCE","0.10",0,true,"BATTLE_START"),
                modifierEffect(5,"CONTROL_DURATION_REDUCTION","0.30",0,true,"BATTLE_START")
        ));
    }

    /** Kabala-Malkuth: nếu Tohka nhanh nhất phe mình thì buff cho đòn này;
     * nếu không, nhận bộ Giải phóng Thần Lực vĩnh viễn, có thể thanh tẩy. */
    private void seedTohkaMalkuthEffects(HeroSkillDefinition skill) {
        String[] attack = {"0.15","0.20","0.25","0.25","0.25","0.25","0.25","0.25"};
        String[] crit = {"0.05","0.10","0.15","0.15","0.15","0.15","0.15","0.15"};
        String[] physicalResist = {"0.04","0.06","0.08","0.08","0.08","0.08","0.08","0.08"};
        String[] damageResist = {"0.03","0.05","0.07","0.07","0.07","0.07","0.07","0.07"};
        String[] resistance = {"0.05","0.07","0.10","0.10","0.10","0.10","0.10","0.10"};
        String[] lifesteal = {"0.15","0.20","0.25","0.25","0.25","0.25","0.25","0.25"};
        writeLevelEffects(skill, level -> {
            List<ObjectNode> effects = new ArrayList<>();
            if (level >= 4) {
                String[] basePhysicalDamage = {"0","0","0","0.01","0.02","0.03","0.04","0.05"};
                effects.add(createGojoEffectJson("BUFF","SELF",basePhysicalDamage[level - 1],0,
                        "{\"trigger\":\"BATTLE_START\",\"statType\":\"PHYSICAL_DAMAGE\","
                                + "\"permanent\":true}"));
            }
            effects.add(createGojoEffectJson("BUFF","SELF",attack[level - 1],1,
                    "{\"trigger\":\"BEFORE_ACTION\",\"condition\":\"SOURCE_IS_FASTEST_ALLY\","
                            + "\"statType\":\"DAMAGE_DEALT\"}"));
            effects.add(createGojoEffectJson("BUFF","SELF",crit[level - 1],1,
                    "{\"trigger\":\"BEFORE_ACTION\",\"condition\":\"SOURCE_IS_FASTEST_ALLY\","
                            + "\"statType\":\"CRIT\"}"));
            String[] stats = {"PHYSICAL_DAMAGE_REDUCTION","MAGIC_DAMAGE_REDUCTION","DAMAGE_REDUCTION",
                    "RESISTANCE","HEAL_RECEIVED","ACCURACY","CRIT_DAMAGE","PHYSICAL_DAMAGE","LIFESTEAL"};
            String[] values = {physicalResist[level - 1],physicalResist[level - 1],damageResist[level - 1],
                    resistance[level - 1],resistance[level - 1],resistance[level - 1],resistance[level - 1],
                    resistance[level - 1],lifesteal[level - 1]};
            for (int i = 0; i < stats.length; i++) {
                effects.add(createGojoEffectJson("BUFF","SELF",values[i],0,
                        "{\"trigger\":\"BATTLE_START\",\"condition\":\"SOURCE_IS_NOT_FASTEST_ALLY\","
                                + "\"statType\":\"" + stats[i] + "\",\"permanent\":true}"));
            }
            return effects;
        });
        skill.replaceEffects(List.of(
                modifierEffect(1,"DAMAGE_DEALT","0.15",1,false,"BEFORE_ACTION"),
                modifierEffect(2,"CRIT","0.05",1,false,"BEFORE_ACTION")
        ));
    }

    private HeroSkillEffect modifierEffect(
            int order, String stat, String value, int duration,
            boolean permanent, String trigger
    ) {
        HeroSkillEffect effect = new HeroSkillEffect(order, "BUFF", "SELF");
        effect.updateParameters(new BigDecimal(value), BigDecimal.ONE, duration);
        effect.updateConfig("{\"trigger\":\"" + trigger + "\",\"statType\":\"" + stat
                + "\",\"permanent\":" + permanent + "}");
        return effect;
    }

    @FunctionalInterface
    private interface LevelEffectFactory {
        List<ObjectNode> create(int level);
    }

    private void writeLevelEffects(HeroSkillDefinition skill, LevelEffectFactory factory) {
        try {
            JsonNode rowsNode = objectMapper.readTree(skill.getLevelRowsJson());
            if (rowsNode instanceof ArrayNode rows) {
                for (JsonNode rowNode : rows) {
                    if (rowNode instanceof ObjectNode row) {
                        int level = Math.max(1, Math.min(8, row.path("level").asInt(1)));
                        ArrayNode effects = objectMapper.createArrayNode();
                        factory.create(level).forEach(effects::add);
                        row.set("battleEffects", effects);
                    }
                }
                skill.updateLevelRowsJson(writeJson(rows));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể tạo battleEffects theo cấp cho " + skill.getSkillCode(),
                    exception
            );
        }
    }

    /** Khoảng Trống Vô Tận: tăng sát thương địch phải nhận theo từng cấp TAB. */
    /**
     * Khoảng Trống Vô Tận:
     * - Effect 43: áp trạng thái lĩnh vực lên toàn bộ địch.
     * - Effect 57: tăng DAMAGE_TAKEN theo cấp kỹ năng.
     * - Buffer 0/1/2: field, passive marker và damage amplification.
     *
     * Runtime biểu diễn trạng thái lĩnh vực bằng modifier DAMAGE_TAKEN gắn
     * lên toàn bộ địch; các ID/quan hệ Lua được giữ trong config để truy vết
     * và đồng bộ VFX/UI, không tự suy diễn thêm một hiệu ứng khống chế.
     */
    private void seedGojoDomainEffects(HeroSkillDefinition skill) {
        String[] damageTaken = {
                "0.15", "0.20", "0.25", "0.26",
                "0.27", "0.28", "0.29", "0.30"
        };

        try {
            JsonNode rowsNode = objectMapper.readTree(skill.getLevelRowsJson());
            if (rowsNode instanceof ArrayNode rows) {
                for (JsonNode rowNode : rows) {
                    if (!(rowNode instanceof ObjectNode row)) continue;
                    int level = Math.max(1, Math.min(8, row.path("level").asInt(1)));
                    int levelSuffix = level * 10;
                    int fieldEffectId = 52517400 + levelSuffix;
                    int damageEffectId = fieldEffectId + 1;
                    int fieldBufferId = fieldEffectId;
                    int passiveBufferId = fieldEffectId + 1;
                    int damageBufferId = fieldEffectId + 2;

                    ObjectNode config = objectMapper.createObjectNode();
                    config.put("trigger", "BATTLE_START");
                    config.put("statType", "DAMAGE_TAKEN");
                    config.put("permanent", true);
                    config.put("ignoreImmunity", true);
                    config.put("eventSkillCode", "GOJO_PASSIVE_5");
                    config.put("domain", true);
                    config.put("domainEffectType", 43);
                    config.put("damageAmplificationEffectType", 57);
                    config.put("luaSkillGroupId", 525174);
                    config.put("luaLevel", level);
                    config.put("luaFieldEffectId", Integer.toString(fieldEffectId));
                    config.put("luaDamageEffectId", Integer.toString(damageEffectId));
                    config.put("luaFieldBufferId", Integer.toString(fieldBufferId));
                    config.put("luaPassiveBufferId", Integer.toString(passiveBufferId));
                    config.put("luaDamageBufferId", Integer.toString(damageBufferId));
                    ArrayNode effectIds = objectMapper.createArrayNode();
                    effectIds.add(Integer.toString(fieldEffectId));
                    effectIds.add(Integer.toString(damageEffectId));
                    config.set("luaEffectIds", effectIds);
                    ArrayNode bufferIds = objectMapper.createArrayNode();
                    bufferIds.add(Integer.toString(fieldBufferId));
                    bufferIds.add(Integer.toString(passiveBufferId));
                    bufferIds.add(Integer.toString(damageBufferId));
                    config.set("luaBufferIds", bufferIds);
                    ArrayNode bufferLinks = objectMapper.createArrayNode();
                    bufferLinks.add("#43&" + fieldBufferId);
                    bufferLinks.add("#" + passiveBufferId);
                    bufferLinks.add("#" + damageBufferId);
                    config.set("luaBufferLinks", bufferLinks);

                    ArrayNode effects = objectMapper.createArrayNode();
                    effects.add(createGojoEffectJson(
                            "DEBUFF", "ALL_ENEMIES", damageTaken[level - 1], 0,
                            writeJson(config)
                    ));
                    row.set("battleEffects", effects);
                }
                skill.updateLevelRowsJson(writeJson(rows));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể tạo battleEffects theo level cho GOJO_PASSIVE_5",
                    exception
            );
        }

        ObjectNode fallbackConfig = objectMapper.createObjectNode();
        fallbackConfig.put("trigger", "BATTLE_START");
        fallbackConfig.put("statType", "DAMAGE_TAKEN");
        fallbackConfig.put("permanent", true);
        fallbackConfig.put("ignoreImmunity", true);
        fallbackConfig.put("eventSkillCode", "GOJO_PASSIVE_5");
        fallbackConfig.put("domain", true);
        fallbackConfig.put("domainEffectType", 43);
        fallbackConfig.put("damageAmplificationEffectType", 57);
        fallbackConfig.put("luaSkillGroupId", 525174);
        fallbackConfig.put("luaLevel", 1);
        fallbackConfig.put("luaFieldEffectId", "52517410");
        fallbackConfig.put("luaDamageEffectId", "52517411");
        fallbackConfig.put("luaFieldBufferId", "52517410");
        fallbackConfig.put("luaPassiveBufferId", "52517411");
        fallbackConfig.put("luaDamageBufferId", "52517412");
        ArrayNode fallbackEffectIds = objectMapper.createArrayNode();
        fallbackEffectIds.add("52517410");
        fallbackEffectIds.add("52517411");
        fallbackConfig.set("luaEffectIds", fallbackEffectIds);
        ArrayNode fallbackBufferIds = objectMapper.createArrayNode();
        fallbackBufferIds.add("52517410");
        fallbackBufferIds.add("52517411");
        fallbackBufferIds.add("52517412");
        fallbackConfig.set("luaBufferIds", fallbackBufferIds);
        ArrayNode fallbackLinks = objectMapper.createArrayNode();
        fallbackLinks.add("#43&52517410");
        fallbackLinks.add("#52517411");
        fallbackLinks.add("#52517412");
        fallbackConfig.set("luaBufferLinks", fallbackLinks);

        HeroSkillEffect domain = new HeroSkillEffect(1, "DEBUFF", "ALL_ENEMIES");
        domain.updateParameters(new BigDecimal("0.15"), BigDecimal.ONE, 0);
        domain.updateConfig(writeJson(fallbackConfig));
        skill.replaceEffects(List.of(domain));
    }

    /**
     * Lục Nhãn: 5 stack giảm 3% sát thương trong 4 round;
     * mỗi lần hạ địch cộng 6% ATK (tối đa 3 stack, vĩnh viễn và không thể tẩy);
     * khi địch hồi sinh, truy kích đúng mục tiêu vừa hồi sinh.
     */
    private void seedGojoSixEyesEffects(HeroSkillDefinition skill) {
        // Giá trị lấy theo mô tả từng level trong TAB_Skill, không dùng cố định level 1.
        String[] reductionPerStack = {
                "0.03", "0.04", "0.05", "0.052",
                "0.054", "0.056", "0.058", "0.06"
        };
        String[] attackPerKill = {
                "0.06", "0.08", "0.10", "0.104",
                "0.108", "0.112", "0.116", "0.12"
        };
        String[] reviveMagicDamage = {
                "0.10", "0.15", "0.20", "0.20",
                "0.20", "0.20", "0.20", "0.20"
        };

        try {
            JsonNode rowsNode = objectMapper.readTree(skill.getLevelRowsJson());
            if (rowsNode instanceof ArrayNode rows) {
                for (JsonNode rowNode : rows) {
                    if (!(rowNode instanceof ObjectNode row)) continue;
                    int level = Math.max(1, Math.min(8, row.path("level").asInt(1)));
                    ArrayNode battleEffects = objectMapper.createArrayNode();

                    battleEffects.add(createGojoEffectJson(
                            "BUFF", "SELF", reductionPerStack[level - 1], 4,
                            "{\"trigger\":\"BATTLE_START\",\"statType\":\"DAMAGE_REDUCTION\","
                                    + "\"stacks\":5,\"maxStacks\":5,\"purgeable\":false}"
                    ));
                    battleEffects.add(createGojoEffectJson(
                            "BUFF", "SELF", attackPerKill[level - 1], 0,
                            "{\"trigger\":\"ON_KILL\",\"statType\":\"ATK\","
                                    + "\"permanent\":true,\"stacks\":1,\"maxStacks\":3,"
                                    + "\"purgeable\":false}"
                    ));
                    battleEffects.add(createGojoEffectJson(
                            "COUNTER", "SELF", reviveMagicDamage[level - 1], 0,
                            "{\"trigger\":\"ON_REVIVE\",\"damageType\":\"MAGIC\","
                                    + "\"eventSkillCode\":\"GOJO_PASSIVE_4\"}"
                    ));
                    row.set("battleEffects", battleEffects);
                }
                skill.updateLevelRowsJson(writeJson(rows));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể tạo battleEffects theo level cho GOJO_PASSIVE_4",
                    exception
            );
        }

        // Fallback tương thích với dữ liệu kỹ năng chưa có levelRows.
        List<HeroSkillEffect> fallback = new ArrayList<>();
        HeroSkillEffect reduction = new HeroSkillEffect(1, "BUFF", "SELF");
        reduction.updateParameters(new BigDecimal("0.03"), BigDecimal.ONE, 4);
        reduction.updateConfig("{\"trigger\":\"BATTLE_START\",\"statType\":\"DAMAGE_REDUCTION\","
                + "\"stacks\":5,\"maxStacks\":5,\"purgeable\":false}");
        fallback.add(reduction);
        HeroSkillEffect attack = new HeroSkillEffect(2, "BUFF", "SELF");
        attack.updateParameters(new BigDecimal("0.06"), BigDecimal.ONE, 0);
        attack.updateConfig("{\"trigger\":\"ON_KILL\",\"statType\":\"ATK\","
                + "\"permanent\":true,\"maxStacks\":3,\"purgeable\":false}");
        fallback.add(attack);
        HeroSkillEffect chase = new HeroSkillEffect(3, "COUNTER", "SELF");
        chase.updateParameters(new BigDecimal("0.10"), BigDecimal.ONE, 0);
        chase.updateConfig("{\"trigger\":\"ON_REVIVE\",\"damageType\":\"MAGIC\","
                + "\"eventSkillCode\":\"GOJO_PASSIVE_4\"}");
        fallback.add(chase);
        skill.replaceEffects(fallback);
    }

    private ObjectNode createGojoEffectJson(
            String effectType, String targetType, String multiplier,
            int duration, String configJson
    ) {
        ObjectNode effect = objectMapper.createObjectNode();
        effect.put("effectType", effectType);
        effect.put("targetType", targetType);
        effect.put("multiplier", multiplier);
        effect.put("chance", "1");
        effect.put("duration", duration);
        try {
            effect.set("configJson", objectMapper.readTree(configJson));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Gojo effect config JSON không hợp lệ", exception);
        }
        return effect;
    }

    /**
     * Đòn đánh thường Gojo phải giữ runtime skillCode để FE resolve đúng
     * cast/impact Spine; không dùng SKILL_EFFECT_<id> làm mã kỹ năng.
     */
    private void seedGojoBasicAttackEffects(HeroSkillDefinition skill) {
        writeLevelEffects(skill, level -> List.of(
                createGojoEffectJson("DAMAGE", "FRONT", "1.0", 0,
                        "{\"damageType\":\"PHYSICAL\","
                                + "\"eventSkillCode\":\"GOJO_BASIC_1\"}")
        ));

        HeroSkillEffect basic = new HeroSkillEffect(1, "DAMAGE", "FRONT");
        basic.updateParameters(BigDecimal.ONE, BigDecimal.ONE, null);
        basic.updateConfig("{\"damageType\":\"PHYSICAL\","
                + "\"eventSkillCode\":\"GOJO_BASIC_1\"}");
        skill.replaceEffects(List.of(basic));
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

        // TAB synchronization may already have created effect_order=1.
        // Replace by order instead of appending a duplicate
        // (hero_code, skill_code, effect_order) unique key.
        skill.replaceEffects(List.of(effect));
    }

    private List<HeroSeed> heroSeeds() {
        return List.of(
                new HeroSeed("dark_tohka", "Tohka Hắc Ám", "LEGENDARY", "WARRIOR",
                        skills("Kiếm chém", "Kiếm khí", "Thiên thần giáng thế", "Ý chí chiến đấu", "Linh lực hộ thể")),
                new HeroSeed("asuna_dark", "Asuna Hắc Ám", "LEGENDARY", "ASSASSIN",
                        skills("Kiếm tốc ảnh", "Liên kích", "Tuyệt kỹ kiếm vũ", "Tốc độ phản xạ", "Kiếm ý")),
                new HeroSeed("gojo", "Gojo Satoru", "MYTHIC", "MAGE",
                        List.of(
                                new SkillSeed("Đánh Thường", "BASIC"),
                                new SkillSeed("Thuật thức Phản Chuyển-Hách", "PASSIVE"),
                                new SkillSeed("Chú thuật Vô Hạ Hạn", "PASSIVE"),
                                new SkillSeed("Lục Nhãn", "PASSIVE"),
                                new SkillSeed("Triển khai {Khoảng Trống Vô Tận}", "PASSIVE")
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
