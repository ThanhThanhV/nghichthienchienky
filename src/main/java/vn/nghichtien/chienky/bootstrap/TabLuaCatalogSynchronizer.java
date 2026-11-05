package vn.nghichtien.chienky.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đồng bộ phần dữ liệu kỹ năng trong JSON catalog từ các bảng TAB Lua gốc.
 *
 * Các hero thông thường giữ runtime metadata được cấu hình riêng trong JSON.
 * Riêng Gojo, slot order/type và skill groups được tạo trực tiếp từ TAB_Hero /
 * TAB_Skill; action/VFX mapping được tạo từ TAB_EffectPerfor và Spine assets.
 * ID, tên, mô tả, icon, loại TAB, script và toàn bộ levelRows luôn lấy từ Lua.
 */
@Component
public class TabLuaCatalogSynchronizer {

    private static final Pattern SCRIPT_REFERENCE = Pattern.compile("\\d{5,}");

    private final ObjectMapper objectMapper;

    public TabLuaCatalogSynchronizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void synchronize(JsonNode catalog, JsonNode spineCatalog) {
        if (catalog == null || !catalog.path("heroes").isObject()) {
            throw new IllegalStateException("TAB catalog không có object heroes");
        }
        if (spineCatalog == null || !spineCatalog.path("heroes").isObject()) {
            throw new IllegalStateException("Spine catalog không có object heroes");
        }

        Map<Integer, LuaRow> heroRows = readRows(
                "data/TAB_Hero_1.lua", "TAB_Hero"
        );
        Map<Integer, LuaRow> skillRows = new LinkedHashMap<>();
        mergeRows(skillRows, readRows("data/TAB_Skill_1.lua", "TAB_Skill"));
        mergeRows(skillRows, readRows("data/TAB_Skill_2.lua", "TAB_Skill"));
        mergeRows(skillRows, readRows("data/TAB_Skill_3.lua", "TAB_Skill"));

        Map<Integer, List<LuaRow>> skillsByGroup = new HashMap<>();
        for (LuaRow row : skillRows.values()) {
            if (row.id() == -1 || row.values().isEmpty()) {
                continue;
            }
            int groupId = numberAt(row, 0);
            skillsByGroup.computeIfAbsent(groupId, ignored -> new ArrayList<>())
                    .add(row);
        }
        skillsByGroup.values().forEach(rows ->
                rows.sort(Comparator
                        .comparingInt((LuaRow row) -> numberAt(row, 4))
                        .thenComparingInt(LuaRow::id))
        );

        Map<String, List<LuaRow>> sseByScriptReference = new HashMap<>();
        for (LuaRow row : readRows(
                "data/TAB_Sseffect_1.lua", "TAB_Sseffect"
        ).values()) {
            if (row.id() == -1 || row.values().size() < 2) {
                continue;
            }
            sseByScriptReference.computeIfAbsent(
                    stringAt(row, 0), ignored -> new ArrayList<>()
            ).add(row);
        }

        Map<Integer, LuaRow> performanceRows = readRows(
                "data/TAB_EffectPerfor_1.lua", "TAB_EffectPerfor"
        );
        Map<Integer, LuaRow> effectRows = new LinkedHashMap<>();
        mergeRows(effectRows, readRows("data/TAB_Effect_1.lua", "TAB_Effect"));
        mergeRows(effectRows, readRows("data/TAB_Effect_2.lua", "TAB_Effect"));
        mergeRows(effectRows, readRows("data/TAB_Effect_3.lua", "TAB_Effect"));
        Map<Integer, LuaRow> bufferRows = new LinkedHashMap<>();
        mergeRows(bufferRows, readRows("data/TAB_Buffer_1.lua", "TAB_Buffer"));
        mergeRows(bufferRows, readRows("data/TAB_Buffer_2.lua", "TAB_Buffer"));

        ObjectNode heroes = (ObjectNode) catalog.path("heroes");
        ObjectNode spineHeroes = (ObjectNode) spineCatalog.path("heroes");
        Iterator<Map.Entry<String, JsonNode>> heroIterator = heroes.fields();
        while (heroIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = heroIterator.next();
            if (!(entry.getValue() instanceof ObjectNode heroConfig)) {
                throw new IllegalStateException(
                        "TAB hero config không hợp lệ: " + entry.getKey()
                );
            }
            JsonNode mappingNode = spineHeroes.get(entry.getKey());
            if (!(mappingNode instanceof ObjectNode heroMapping)) {
                throw new IllegalStateException(
                        "Thiếu Spine mapping cho TAB hero " + entry.getKey()
                );
            }
            synchronizeHero(
                    entry.getKey(), heroConfig, heroMapping, heroRows, skillsByGroup,
                    sseByScriptReference, performanceRows, effectRows, bufferRows
            );
        }
    }

    private void synchronizeHero(
            String heroCode,
            ObjectNode heroConfig,
            ObjectNode heroMapping,
            Map<Integer, LuaRow> heroRows,
            Map<Integer, List<LuaRow>> skillsByGroup,
            Map<String, List<LuaRow>> sseByScriptReference,
            Map<Integer, LuaRow> performanceRows,
            Map<Integer, LuaRow> effectRows,
            Map<Integer, LuaRow> bufferRows
    ) {
        JsonNode tabHero = heroConfig.path("tabHero");
        // hero-tab-skill-config.json lưu tabHeroId/sourceHeroCode ở cấp hero,
        // còn các metadata mô tả nằm trong object tabHero.
        int tabHeroId = heroConfig.path("tabHeroId")
                .asInt(tabHero.path("tabHeroId").asInt(-1));
        String sourceHeroCode = heroConfig.path("sourceHeroCode")
                .asText(tabHero.path("sourceHeroCode").asText(""));
        String variantSourceHeroCode = heroConfig.path("variantSourceHeroCode")
                .asText(tabHero.path("variantSourceHeroCode").asText(""));
        LuaRow heroRow = heroRows.get(tabHeroId);
        if (tabHeroId <= 0 || heroRow == null) {
            throw new IllegalStateException(
                    "Không tìm thấy TAB_Hero[" + tabHeroId + "] cho " + heroCode
            );
        }

        Set<Integer> heroSkillGroups = new HashSet<>();
        int normalAttackGroupId = numberAt(heroRow, 10); // NormalAttID
        heroSkillGroups.add(normalAttackGroupId);
        Object heroSkillIds = valueAt(heroRow, 11); // u_SkillID
        List<Integer> luaSkillGroupOrder = new ArrayList<>();
        luaSkillGroupOrder.add(normalAttackGroupId);
        if (heroSkillIds instanceof List<?> ids) {
            for (Object id : ids) {
                if (id instanceof Number number) {
                    heroSkillGroups.add(number.intValue());
                    luaSkillGroupOrder.add(number.intValue());
                }
            }
        }

        ArrayNode skills;
        if ("gojo".equalsIgnoreCase(heroCode)) {
            // Gojo không lấy skill group/order/levelOneId từ JSON cũ.
            // Tạo mới 5 slot trực tiếp từ NormalAttID + u_SkillID trong TAB_Hero.
            if (luaSkillGroupOrder.size() != 5) {
                throw new IllegalStateException(
                        "TAB_Hero Gojo phải có 1 NormalAttID và 4 u_SkillID: "
                                + luaSkillGroupOrder
                );
            }
            skills = objectMapper.createArrayNode();
            for (int slot = 0; slot < luaSkillGroupOrder.size(); slot++) {
                Integer luaGroupId = luaSkillGroupOrder.get(slot);
                List<LuaRow> sourceRows = skillsByGroup.get(luaGroupId);
                if (sourceRows == null || sourceRows.isEmpty()) {
                    throw new IllegalStateException(
                            "Không tìm thấy TAB_Skill group Gojo " + luaGroupId
                    );
                }
                LuaRow levelOne = sourceRows.stream()
                        .filter(row -> numberAt(row, 4) == 1)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "TAB_Skill group Gojo " + luaGroupId
                                        + " không có level 1"
                        ));
                ObjectNode skillConfig = objectMapper.createObjectNode();
                skillConfig.put("groupId", luaGroupId);
                skillConfig.put("levelOneId", levelOne.id());
                skillConfig.put("order", slot + 1);
                skills.add(skillConfig);
            }
            heroConfig.set("skills", skills);
        } else {
            JsonNode configuredSkills = heroConfig.path("skills");
            if (!configuredSkills.isArray() || configuredSkills.size() != 5) {
                throw new IllegalStateException(
                        "TAB catalog phải có đúng 5 slot kỹ năng: " + heroCode
                );
            }
            skills = (ArrayNode) configuredSkills;
        }

        if (tabHero instanceof ObjectNode tabHeroObject) {
            tabHeroObject.put("raw", heroRow.raw());
        }

        for (int slot = 0; slot < skills.size(); slot++) {
            if (!(skills.get(slot) instanceof ObjectNode skillConfig)) {
                throw new IllegalStateException(
                        "Skill config không hợp lệ: " + heroCode + " slot " + (slot + 1)
                );
            }

            int groupId = skillConfig.path("groupId").asInt(-1);
            int levelOneId = skillConfig.path("levelOneId").asInt(-1);
            if (groupId <= 0 || levelOneId <= 0
                    || !heroSkillGroups.contains(groupId)) {
                throw new IllegalStateException(
                        "TAB_Hero không tham chiếu group kỹ năng " + groupId
                                + " của " + heroCode + " slot " + (slot + 1)
                );
            }

            List<LuaRow> levelRows = skillsByGroup.get(groupId);
            if (levelRows == null || levelRows.isEmpty()) {
                throw new IllegalStateException(
                        "Không tìm thấy TAB_Skill group " + groupId
                                + " cho " + heroCode + " slot " + (slot + 1)
                );
            }

            LuaRow levelOne = levelRows.stream()
                    .filter(row -> numberAt(row, 4) == 1)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "TAB_Skill group " + groupId + " không có level 1"
                    ));
            if (levelOne.id() != levelOneId) {
                throw new IllegalStateException(
                        "Sai levelOneId tại " + heroCode + " slot " + (slot + 1)
                                + ": catalog=" + levelOneId
                                + ", TAB_Skill=" + levelOne.id()
                );
            }

            if ("gojo".equalsIgnoreCase(heroCode)) {
                // TAB_Skill type/skillType 1/1 là đánh thường; 3/3 là PASSIVE.
                // Domain cũng là PASSIVE theo Lua dù phát superskill ở BATTLE_START.
                String runtimeSkillType;
                if (groupId == normalAttackGroupId
                        && numberAt(levelOne, 5) == 1
                        && numberAt(levelOne, 6) == 1) {
                    runtimeSkillType = "BASIC";
                } else if (numberAt(levelOne, 5) == 3
                        && numberAt(levelOne, 6) == 3) {
                    runtimeSkillType = "PASSIVE";
                } else {
                    throw new IllegalStateException(
                            "Không thể suy ra runtimeSkillType Gojo từ TAB_Skill["
                                    + levelOne.id() + "]: type="
                                    + numberAt(levelOne, 5) + ", skillType="
                                    + numberAt(levelOne, 6)
                    );
                }
                skillConfig.put("order", slot + 1);
                skillConfig.put("runtimeSkillType", runtimeSkillType);
            }

            synchronizeSkill(
                    heroCode, slot + 1, skillConfig, levelRows,
                    sseByScriptReference, performanceRows, effectRows, bufferRows
            );
        }

        // Binding được sinh từ TAB source + runtime slot metadata; không cần
        // duy trì một bản sao thủ công trong hero-skill-mapping.json.
        ObjectNode tabHeroSource = objectMapper.createObjectNode();
        tabHeroSource.put("tabHeroId", tabHeroId);
        tabHeroSource.put("sourceHeroCode", sourceHeroCode);
        if (variantSourceHeroCode.isBlank()) {
            tabHeroSource.putNull("variantSourceHeroCode");
        } else {
            tabHeroSource.put("variantSourceHeroCode", variantSourceHeroCode);
        }
        heroMapping.set("_TAB_HERO_SOURCE", tabHeroSource);

        ObjectNode generatedBindings = objectMapper.createObjectNode();
        for (int slot = 0; slot < skills.size(); slot++) {
            JsonNode skill = skills.get(slot);
            String runtimeSkillType = skill.path("runtimeSkillType").asText("");
            String skillCode = heroCode.toUpperCase(java.util.Locale.ROOT)
                    + "_" + runtimeSkillType.toUpperCase(java.util.Locale.ROOT)
                    + "_" + (slot + 1);
            ObjectNode binding = objectMapper.createObjectNode();
            binding.put("tabGroupId", skill.path("groupId").asText(""));
            binding.put("tabSkillId", skill.path("levelOneId").asText(""));
            binding.put("sourceHeroId", tabHeroId);
            binding.put("sourceHeroCode", sourceHeroCode);
            binding.put("runtimeSkillType", runtimeSkillType);
            binding.put("name", skill.path("name").asText(""));
            generatedBindings.set(skillCode, binding);
        }
        heroMapping.set("_TAB_SKILL_BINDINGS", generatedBindings);
    }

    private void synchronizeSkill(
            String heroCode,
            int slot,
            ObjectNode skillConfig,
            List<LuaRow> sourceRows,
            Map<String, List<LuaRow>> sseByScriptReference,
            Map<Integer, LuaRow> performanceRows,
            Map<Integer, LuaRow> effectRows,
            Map<Integer, LuaRow> bufferRows
    ) {
        ArrayNode levelRows = objectMapper.createArrayNode();
        LinkedHashSet<String> allScriptReferences = new LinkedHashSet<>();

        // TAB Lua là nguồn cho level rows, nhưng battleEffects là metadata
        // runtime do game cấu hình. Giữ lại cấu hình theo đúng level trước
        // khi tái tạo row từ Lua để các hiệu ứng theo cấp không bị mất startup.
        Map<Integer, JsonNode> runtimeBattleEffectsByLevel = new HashMap<>();
        JsonNode configuredLevelRows = skillConfig.path("levelRows");
        if (configuredLevelRows.isArray()) {
            for (JsonNode configuredLevel : configuredLevelRows) {
                int configuredLevelNumber = configuredLevel.path("level").asInt(-1);
                JsonNode configuredBattleEffects = configuredLevel.path("battleEffects");
                if (configuredLevelNumber > 0 && configuredBattleEffects.isArray()) {
                    runtimeBattleEffectsByLevel.put(
                            configuredLevelNumber,
                            configuredBattleEffects.deepCopy()
                    );
                }
            }
        }

        LuaRow levelOne = null;
        int expectedLevel = 1;

        for (LuaRow source : sourceRows) {
            int level = numberAt(source, 4);
            if (level != expectedLevel) {
                throw new IllegalStateException(
                        "TAB_Skill group " + numberAt(source, 0)
                                + " thiếu level " + expectedLevel
                );
            }
            expectedLevel++;

            if (level == 1) {
                levelOne = source;
            }

            List<String> references = extractScriptReferences(stringAt(source, 12));
            allScriptReferences.addAll(references);

            ObjectNode synchronizedLevel = toLevelJson(source, references);
            JsonNode runtimeBattleEffects = runtimeBattleEffectsByLevel.get(level);
            if (runtimeBattleEffects != null) {
                synchronizedLevel.set(
                        "battleEffects",
                        runtimeBattleEffects.deepCopy()
                );
            }
            levelRows.add(synchronizedLevel);
        }

        if (levelOne == null) {
            throw new IllegalStateException(
                    "Không tìm thấy level 1 của TAB_Skill group " + numberAt(sourceRows.get(0), 0)
            );
        }

        skillConfig.put("name", stringAt(levelOne, 18));
        skillConfig.put("description", stringAt(levelOne, 14));
        skillConfig.put("icon", numberAt(levelOne, 3));
        skillConfig.put("tabType", numberAt(levelOne, 5));
        skillConfig.put("tabSkillType", numberAt(levelOne, 6));
        skillConfig.put("script", stringAt(levelOne, 12));

        ArrayNode scriptReferences = objectMapper.createArrayNode();
        allScriptReferences.forEach(scriptReferences::add);
        skillConfig.set("scriptRefs", scriptReferences);
        skillConfig.set("levelRows", levelRows);
        ObjectNode scriptResolution = resolveScriptReferences(
                allScriptReferences, sseByScriptReference,
                performanceRows, effectRows, bufferRows
        );
        if (scriptResolution.path("unresolvedScriptRefs").size() > 0
                || scriptResolution.path("unresolvedPerformanceIds").size() > 0
                || scriptResolution.path("unresolvedBufferIds").size() > 0) {
            throw new IllegalStateException(
                    "TAB script reference không resolve đầy đủ: hero=" + heroCode
                            + ", slot=" + slot
                            + ", groupId=" + numberAt(levelOne, 0)
                            + ", unresolved=" + scriptResolution
            );
        }
        skillConfig.set("scriptResolution", scriptResolution);

        // Performance row của từng group được tra trực tiếp theo TAB_EffectPerfor:
        // <TAB skill group><10>. Trường action ở index 7 quyết định att/skill/
        // ultimateskill/superskill; không suy luận chỉ từ performance ID 10000xxx.
        LuaRow directPerformance = null;
        if ("gojo".equalsIgnoreCase(heroCode)) {
            int groupId = numberAt(levelOne, 0);
            long performanceRowId = (long) groupId * 100L + 10L;
            if (performanceRowId <= Integer.MAX_VALUE) {
                directPerformance = performanceRows.get((int) performanceRowId);
            }
        }
        if (directPerformance == null) {
            skillConfig.remove("tabPerformance");
        } else {
            ObjectNode performance = objectMapper.createObjectNode();
            performance.put("id", Integer.toString(directPerformance.id()));
            performance.set("fields", objectMapper.valueToTree(directPerformance.values()));
            performance.put("raw", directPerformance.raw());
            performance.put("source", directPerformance.source());
            skillConfig.set("tabPerformance", performance);
        }
    }

    private ObjectNode toLevelJson(LuaRow row, List<String> scriptReferences) {
        ObjectNode level = objectMapper.createObjectNode();
        level.put("id", Integer.toString(row.id()));
        level.put("groupId", numberAt(row, 0));
        level.put("icon", numberAt(row, 3));
        level.put("level", numberAt(row, 4));
        level.put("type", numberAt(row, 5));
        level.put("skillType", numberAt(row, 6));
        level.put("hurtType", numberAt(row, 7));
        level.put("priority", numberAt(row, 8));
        level.put("startRound", numberAt(row, 9));
        level.put("cooldown", numberAt(row, 10));
        level.put("ignoreControl", numberAt(row, 11));
        level.put("script", stringAt(row, 12));
        ArrayNode refs = objectMapper.createArrayNode();
        scriptReferences.forEach(refs::add);
        level.set("scriptRefs", refs);
        level.put("attributes", toLuaLiteral(valueAt(row, 13)));
        level.put("description", stringAt(row, 14));
        level.put("effectShow", toLuaLiteral(valueAt(row, 15)));
        level.put("name", stringAt(row, 18));
        level.put("raw", row.raw());
        level.put("source", row.source());
        return level;
    }

    private ObjectNode resolveScriptReferences(
            Set<String> references,
            Map<String, List<LuaRow>> sseByScriptReference,
            Map<Integer, LuaRow> performanceRows,
            Map<Integer, LuaRow> effectRows,
            Map<Integer, LuaRow> bufferRows
    ) {
        ObjectNode resolution = objectMapper.createObjectNode();
        ArrayNode sse = objectMapper.createArrayNode();
        ArrayNode performance = objectMapper.createArrayNode();
        ArrayNode effects = objectMapper.createArrayNode();
        ArrayNode buffers = objectMapper.createArrayNode();
        ArrayNode unresolvedScriptReferences = objectMapper.createArrayNode();
        ArrayNode unresolvedPerformanceIds = objectMapper.createArrayNode();
        ArrayNode unresolvedBufferIds = objectMapper.createArrayNode();

        Set<Integer> addedSseIds = new HashSet<>();
        Set<Integer> addedPerformanceIds = new HashSet<>();
        Set<Integer> addedEffectIds = new HashSet<>();
        Set<Integer> addedBufferIds = new HashSet<>();

        for (String reference : references) {
            boolean resolved = false;

            List<LuaRow> matchingSseRows = sseByScriptReference.get(reference);
            if (matchingSseRows != null) {
                for (LuaRow sseRow : matchingSseRows) {
                    resolved = true;
                    if (addedSseIds.add(sseRow.id())) {
                        ObjectNode sseNode = objectMapper.createObjectNode();
                        sseNode.put("id", Integer.toString(sseRow.id()));
                        sseNode.set("fields", objectMapper.valueToTree(sseRow.values()));
                        sseNode.put("raw", sseRow.raw());
                        sse.add(sseNode);
                    }
                    addPerformance(
                            stringAt(sseRow, 1), performanceRows, performance,
                            addedPerformanceIds, unresolvedPerformanceIds
                    );
                }
            }

            try {
                int referenceId = Integer.parseInt(reference);
                LuaRow effectRow = effectRows.get(referenceId);
                if (effectRow != null) {
                    resolved = true;
                    if (addedEffectIds.add(effectRow.id())) {
                        ObjectNode effectNode = objectMapper.createObjectNode();
                        effectNode.put("id", Integer.toString(effectRow.id()));
                        effectNode.set("fields", objectMapper.valueToTree(effectRow.values()));
                        effectNode.put("raw", effectRow.raw());
                        effects.add(effectNode);
                    }

                    int performanceId = optionalNumberAt(effectRow, 10);
                    if (performanceId > 0) {
                        addPerformance(
                                Integer.toString(performanceId), performanceRows,
                                performance, addedPerformanceIds,
                                unresolvedPerformanceIds
                        );
                    }

                    int bufferId = optionalNumberAt(effectRow, 11);
                    if (bufferId > 0) {
                        addBuffer(
                                bufferId, bufferRows, buffers,
                                addedBufferIds, unresolvedBufferIds
                        );
                    }
                }

                LuaRow bufferRow = bufferRows.get(referenceId);
                if (bufferRow != null) {
                    resolved = true;
                    addBuffer(
                            referenceId, bufferRows, buffers,
                            addedBufferIds, unresolvedBufferIds
                    );
                }
            } catch (NumberFormatException exception) {
                // A non-numeric script token is reported below as unresolved.
            }

            if (!resolved) {
                unresolvedScriptReferences.add(reference);
            }
        }

        resolution.set("sse", sse);
        resolution.set("performance", performance);
        resolution.set("effects", effects);
        resolution.set("buffers", buffers);
        resolution.set("unresolvedScriptRefs", unresolvedScriptReferences);
        resolution.set("unresolvedPerformanceIds", unresolvedPerformanceIds);
        resolution.set("unresolvedBufferIds", unresolvedBufferIds);
        return resolution;
    }

    private void addPerformance(
            String idValue,
            Map<Integer, LuaRow> performanceRows,
            ArrayNode performance,
            Set<Integer> addedPerformanceIds,
            ArrayNode unresolvedPerformanceIds
    ) {
        try {
            int id = Integer.parseInt(idValue);
            LuaRow row = performanceRows.get(id);
            if (row == null) {
                unresolvedPerformanceIds.add(idValue);
            } else if (addedPerformanceIds.add(id)) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", idValue);
                node.put("raw", row.raw());
                performance.add(node);
            }
        } catch (NumberFormatException exception) {
            unresolvedPerformanceIds.add(idValue);
        }
    }

    private void addBuffer(
            int id,
            Map<Integer, LuaRow> bufferRows,
            ArrayNode buffers,
            Set<Integer> addedBufferIds,
            ArrayNode unresolvedBufferIds
    ) {
        LuaRow row = bufferRows.get(id);
        if (row == null) {
            unresolvedBufferIds.add(Integer.toString(id));
        } else if (addedBufferIds.add(id)) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", Integer.toString(id));
            node.set("fields", objectMapper.valueToTree(row.values()));
            node.put("raw", row.raw());
            buffers.add(node);

            // TAB_Buffer[9] chứa liên kết buffer/effect dạng "#<id>" hoặc
            // "#<effectType>&<bufferId>". Theo tiếp các buffer phụ thuộc để
            // catalog không chỉ giữ buffer đầu tiên được script tham chiếu.
            String linkedReferences = stringAt(row, 8);
            Matcher linkedMatcher = Pattern.compile("\\d{5,}").matcher(
                    linkedReferences == null ? "" : linkedReferences
            );
            while (linkedMatcher.find()) {
                try {
                    int linkedId = Integer.parseInt(linkedMatcher.group());
                    if (bufferRows.containsKey(linkedId)) {
                        addBuffer(
                                linkedId, bufferRows, buffers,
                                addedBufferIds, unresolvedBufferIds
                        );
                    }
                } catch (NumberFormatException ignored) {
                    // Bỏ qua token không thể chuyển thành ID.
                }
            }

            // Lấy cả buffer con trỏ ngược về buffer hiện tại. TAB Gojo dùng
            // chuỗi field -> passive marker -> damage marker; nếu chỉ đi xuôi
            // từ passive marker sẽ bỏ sót damage marker là buffer cùng cấp.
            int bufferFamily = id / 10;
            for (LuaRow candidate : bufferRows.values()) {
                if (candidate.id() / 10 != bufferFamily) continue;
                String candidateLinks = stringAt(candidate, 8);
                if (candidateLinks != null
                        && candidateLinks.matches("(?s).*\\\\b" + id + "\\\\b.*")) {
                    addBuffer(
                            candidate.id(), bufferRows, buffers,
                            addedBufferIds, unresolvedBufferIds
                    );
                }
            }
        }
    }

    private List<String> extractScriptReferences(String script) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        Matcher matcher = SCRIPT_REFERENCE.matcher(script == null ? "" : script);
        while (matcher.find()) {
            references.add(matcher.group());
        }
        return new ArrayList<>(references);
    }

    private Map<Integer, LuaRow> readRows(String resourcePath, String tableName) {
        String source;
        try (var input = new ClassPathResource(resourcePath).getInputStream()) {
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể đọc TAB Lua: " + resourcePath, exception
            );
        }

        Pattern assignment = Pattern.compile(
                "(?m)^[ \\t]*" + Pattern.quote(tableName)
                        + "\\s*\\[\\s*(-?\\d+)\\s*\\][ \\t]*=[ \\t]*"
        );
        Matcher matcher = assignment.matcher(source);
        Map<Integer, LuaRow> rows = new LinkedHashMap<>();
        int searchFrom = 0;

        while (matcher.find(searchFrom)) {
            int id = Integer.parseInt(matcher.group(1));
            LuaLiteralParser parser = new LuaLiteralParser(
                    source, matcher.end(), resourcePath
            );
            Object value = parser.parseValue();
            if (!(value instanceof List<?> values)) {
                throw new IllegalStateException(
                        "TAB row phải là Lua table array: " + resourcePath + "[" + id + "]"
                );
            }

            LuaRow row = new LuaRow(
                    id, new ArrayList<>(values),
                    source.substring(matcher.start(), parser.position()).trim(),
                    resourcePath.substring(resourcePath.lastIndexOf('/') + 1)
            );
            if (rows.putIfAbsent(id, row) != null) {
                throw new IllegalStateException(
                        "Trùng TAB ID " + id + " trong " + resourcePath
                );
            }
            searchFrom = parser.position();
        }

        if (rows.isEmpty()) {
            throw new IllegalStateException(
                    "Không tìm thấy dữ liệu " + tableName + " trong " + resourcePath
            );
        }
        return rows;
    }

    private void mergeRows(Map<Integer, LuaRow> target, Map<Integer, LuaRow> source) {
        for (Map.Entry<Integer, LuaRow> entry : source.entrySet()) {
            if (target.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalStateException(
                        "Trùng TAB ID giữa các file: " + entry.getKey()
                );
            }
        }
    }

    private int numberAt(LuaRow row, int index) {
        Object value = valueAt(row, index);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "TAB row " + row.id() + " thiếu giá trị số tại cột " + index,
                    exception
            );
        }
    }

    private int optionalNumberAt(LuaRow row, int index) {
        Object value = valueAt(row, index);
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "TAB row " + row.id() + " có giá trị không phải số tại cột " + index,
                    exception
            );
        }
    }

    private String stringAt(LuaRow row, int index) {
        Object value = valueAt(row, index);
        return value == null ? "" : String.valueOf(value);
    }

    private Object valueAt(LuaRow row, int index) {
        if (index < 0 || index >= row.values().size()) {
            throw new IllegalStateException(
                    "TAB row " + row.id() + " không có cột " + index
            );
        }
        return row.values().get(index);
    }

    private String toLuaLiteral(Object value) {
        if (value == null) {
            return "nil";
        }
        if (value instanceof String string) {
            return "\"" + string
                    .replace("\\\\", "\\\\\\\\")
                    .replace("\"", "\\\\\"")
                    .replace("\n", "\\\\n")
                    .replace("\r", "\\\\r") + "\"";
        }
        if (value instanceof List<?> values) {
            List<String> items = new ArrayList<>();
            for (Object item : values) {
                items.add(toLuaLiteral(item));
            }
            return "{" + String.join(",", items) + "}";
        }
        return String.valueOf(value);
    }

    private record LuaRow(
            int id,
            List<Object> values,
            String raw,
            String source
    ) {
    }

    /**
     * Parser cho subset Lua table literal được dùng trong các file TAB:
     * array table lồng nhau, string, number, boolean và nil.
     * Không thực thi Lua code và chủ động báo lỗi nếu gặp cú pháp ngoài subset.
     */
    private static final class LuaLiteralParser {
        private static final Pattern NUMBER = Pattern.compile(
                "[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?"
        );

        private final String source;
        private final String resourcePath;
        private int position;

        private LuaLiteralParser(String source, int position, String resourcePath) {
            this.source = source;
            this.position = position;
            this.resourcePath = resourcePath;
        }

        private int position() {
            return position;
        }

        private Object parseValue() {
            skipWhitespace();
            if (position >= source.length()) {
                throw error("Thiếu giá trị Lua");
            }

            char current = source.charAt(position);
            if (current == '{') {
                return parseTable();
            }
            if (current == '"' || current == '\'') {
                return parseString(current);
            }
            return parseAtom();
        }

        private List<Object> parseTable() {
            expect('{');
            List<Object> values = new ArrayList<>();
            skipWhitespace();

            while (position < source.length() && source.charAt(position) != '}') {
                values.add(parseValue());
                skipWhitespace();

                if (position < source.length()
                        && (source.charAt(position) == ','
                        || source.charAt(position) == ';')) {
                    position++;
                    skipWhitespace();
                } else if (position >= source.length()
                        || source.charAt(position) != '}') {
                    throw error("Thiếu dấu phân cách trong Lua table");
                }
            }

            expect('}');
            return values;
        }

        private String parseString(char quote) {
            expect(quote);
            StringBuilder value = new StringBuilder();

            while (position < source.length()) {
                char current = source.charAt(position++);
                if (current == quote) {
                    return value.toString();
                }
                if (current != '\\') {
                    value.append(current);
                    continue;
                }
                if (position >= source.length()) {
                    throw error("Escape string chưa hoàn tất");
                }

                char escaped = source.charAt(position++);
                switch (escaped) {
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case '\\' -> value.append('\\');
                    case '"' -> value.append('"');
                    case '\'' -> value.append('\'');
                    default -> value.append(escaped);
                }
            }
            throw error("String Lua chưa đóng");
        }

        private Object parseAtom() {
            Matcher matcher = NUMBER.matcher(source);
            matcher.region(position, source.length());
            if (matcher.lookingAt()) {
                String number = matcher.group();
                position += number.length();
                if (number.contains(".") || number.contains("e") || number.contains("E")) {
                    return Double.parseDouble(number);
                }
                return Long.parseLong(number);
            }

            int start = position;
            while (position < source.length()) {
                char current = source.charAt(position);
                if (current == ',' || current == ';' || current == '}'
                        || Character.isWhitespace(current)) {
                    break;
                }
                position++;
            }
            if (start == position) {
                throw error("Không đọc được Lua literal");
            }

            String atom = source.substring(start, position);
            return switch (atom) {
                case "nil", "null" -> null;
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                default -> throw error("Lua literal không hỗ trợ: " + atom);
            };
        }

        private void skipWhitespace() {
            while (position < source.length()
                    && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private void expect(char expected) {
            skipWhitespace();
            if (position >= source.length() || source.charAt(position) != expected) {
                throw error("Mong đợi ký tự '" + expected + "'");
            }
            position++;
        }

        private IllegalStateException error(String message) {
            return new IllegalStateException(
                    message + " tại " + resourcePath + ":" + position
            );
        }
    }
}
