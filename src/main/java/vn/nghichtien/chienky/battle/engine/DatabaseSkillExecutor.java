package vn.nghichtien.chienky.battle.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.battle.domain.*;
import vn.nghichtien.chienky.hero.domain.HeroSkillDefinition;
import vn.nghichtien.chienky.hero.domain.HeroSkillEffect;
import vn.nghichtien.chienky.hero.domain.HeroInstanceSkill;
import vn.nghichtien.chienky.hero.repository.HeroSkillDefinitionRepository;
import vn.nghichtien.chienky.hero.repository.HeroInstanceSkillRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Đọc cấu hình kỹ năng từ database, chuyển thành cấu hình runtime và thực thi.
 * Database lưu dữ liệu kỹ năng; Java thực thi luật chiến đấu.
 */
@Component
public class DatabaseSkillExecutor {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final HeroSkillDefinitionRepository skillRepository;
    private final HeroInstanceSkillRepository instanceSkillRepository;
    private final BattleEffectProcessor effectProcessor;
    private final ObjectMapper objectMapper;

    public DatabaseSkillExecutor(
            HeroSkillDefinitionRepository skillRepository,
            HeroInstanceSkillRepository instanceSkillRepository,
            BattleEffectProcessor effectProcessor,
            ObjectMapper objectMapper
    ) {
        this.skillRepository = skillRepository;
        this.instanceSkillRepository = instanceSkillRepository;
        this.effectProcessor = effectProcessor;
        this.objectMapper = objectMapper;
    }

    /**
     * Thực thi kỹ năng tương ứng với lượt trong chu kỳ chiến đấu.
     * Nếu nhân vật không có kỹ năng thuộc nhóm hành động được yêu cầu
     * (ví dụ Gojo không có BASIC/ULTIMATE), lượt đó được bỏ qua.
     *
     * @return loại hành động được yêu cầu; không tự fallback sang BASIC.
     */
    @Transactional(readOnly = true)
    public BattleActionType execute(
            BattleState battle,
            BattleUnitState source,
            BattleActionType requestedAction
    ) {
        if (battle == null || source == null || requestedAction == null) {
            throw new IllegalArgumentException(
                    "Battle, source and requestedAction must not be null"
            );
        }

        if (battle.isFinished() || !source.isAlive()) {
            return requestedAction;
        }

        List<HeroSkillDefinition> skills = loadSkills(source);
        String requestedType = toSkillType(requestedAction);

        HeroSkillDefinition skill = findSkill(skills, requestedType);

        // Không tự biến lượt ACTIVE/ULTIMATE thành đòn BASIC.
        // Một số nhân vật (Gojo) không có kỹ năng gây sát thương ở các lượt này.
        if (skill == null) {
            return requestedAction;
        }

        int skillLevel = loadSkillLevels(source).getOrDefault(skill.getSkillCode(), 1);
        SkillEffectSet effectSet = loadEffectsForLevel(skill, skillLevel);
        List<BattleEffectConfig> effects = effectSet.effects().stream()
                .filter(effect -> Boolean.TRUE.equals(effect.getEnabled()))
                .filter(effect -> !hasExplicitTrigger(effect))
                .sorted(Comparator.comparing(
                        HeroSkillEffect::getEffectOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .filter(this::passesChance)
                .map(effect -> toEffectConfig(
                        effect, effectCode(skill, effectSet.level(), effect)
                ))
                .toList();

        recordSkillActivation(battle, source, skill, requestedAction);
        effectProcessor.executeEffects(battle, source, effects);

        return requestedAction;
    }

    /**
     * Thực thi trigger BATTLE_START được khai báo tường minh trên kỹ năng
     * ACTIVE/ULTIMATE/BASIC. Dùng cho field/domain được kích hoạt ngay đầu trận.
     */
    @Transactional(readOnly = true)
    public void executeNonPassiveBattleStartEffects(BattleState battle, BattleUnitState source) {
        if (battle == null || source == null || battle.isFinished() || !source.isAlive()) return;
        Map<String, Integer> skillLevels = loadSkillLevels(source);
        for (HeroSkillDefinition skill : loadSkills(source)) {
            if ("PASSIVE".equalsIgnoreCase(skill.getSkillType())) continue;
            SkillEffectSet effectSet = loadEffectsForLevel(
                    skill, skillLevels.getOrDefault(skill.getSkillCode(), 1)
            );
            List<BattleEffectConfig> effects = effectSet.effects().stream()
                    .filter(effect -> Boolean.TRUE.equals(effect.getEnabled()))
                    .filter(this::hasExplicitTrigger)
                    .filter(effect -> getPassiveTrigger(effect) == BattlePassiveTrigger.BATTLE_START)
                    .filter(effect -> passesTriggerWindow(effect, battle))
                    .filter(this::passesChance)
                    .map(effect -> toEffectConfig(
                            effect, effectCode(skill, effectSet.level(), effect)
                    ))
                    .toList();
            if (effects.isEmpty()) continue;
            recordPassiveActivation(
                    battle, source, skill, BattlePassiveTrigger.BATTLE_START, null
            );
            effectProcessor.executeEffects(battle, source, effects);
        }
    }

    private boolean hasExplicitTrigger(HeroSkillEffect effect) {
        JsonNode trigger = readConfig(effect.getConfigJson()).get("trigger");
        return trigger != null && !trigger.isNull() && !trigger.asText().isBlank();
    }

    /**
     * Tự động thực thi các nội tại có trigger trùng với sự kiện hiện tại.
     * Mặc định trigger là BATTLE_START nếu configJson không khai báo.
     */
    @Transactional(readOnly = true)
    public void executePassives(
            BattleState battle,
            BattleUnitState source,
            BattlePassiveTrigger trigger
    ) {
        executePassives(battle, source, trigger, null);
    }

    /**
     * Thực thi nội tại với ngữ cảnh kẻ vừa gây sát thương, dùng riêng cho ON_DAMAGED.
     */
    @Transactional(readOnly = true)
    public void executePassives(
            BattleState battle,
            BattleUnitState source,
            BattlePassiveTrigger trigger,
            Long counterTargetInstanceId
    ) {
        if (battle == null || source == null || trigger == null) {
            throw new IllegalArgumentException(
                    "Battle, source and trigger must not be null"
            );
        }
        if (battle.isFinished() || !source.isAlive()) {
            return;
        }

        Map<String, Integer> skillLevels = loadSkillLevels(source);
        List<HeroSkillDefinition> passiveSkills = loadSkills(source).stream()
                .filter(skill -> "PASSIVE".equalsIgnoreCase(skill.getSkillType()))
                .toList();

        for (HeroSkillDefinition skill : passiveSkills) {
            if (battle.isFinished() || !source.isAlive()) {
                break;
            }

            SkillEffectSet effectSet = loadEffectsForLevel(
                    skill, skillLevels.getOrDefault(skill.getSkillCode(), 1)
            );
            List<BattleEffectConfig> effects = effectSet.effects().stream()
                    .filter(effect -> Boolean.TRUE.equals(effect.getEnabled()))
                    .sorted(Comparator.comparing(
                            HeroSkillEffect::getEffectOrder,
                            Comparator.nullsLast(Integer::compareTo)
                    ))
                    .filter(effect -> getPassiveTrigger(effect) == trigger)
                    .filter(effect -> passesTriggerWindow(effect, battle))
                    .filter(effect -> passesCondition(effect, battle, source))
                    .filter(this::passesChance)
                    .map(effect -> toEffectConfig(
                            effect, effectCode(skill, effectSet.level(), effect)
                    ))
                    .toList();

            if (effects.isEmpty()) {
                continue;
            }

            recordPassiveActivation(
                    battle, source, skill, trigger, counterTargetInstanceId
            );

            // Hách có một đòn đánh thường phụ ở ROUND_START của round 11.
            // Phải phát đủ ACTION_START -> BASIC_ATTACK -> DAMAGE -> ACTION_END
            // để FE chạy animation att/cast/impact và đưa Gojo về idle đúng lúc.
            boolean gojoHachFollowUp = "gojo".equalsIgnoreCase(source.getHeroCode())
                    && "GOJO_PASSIVE_3".equalsIgnoreCase(skill.getSkillCode())
                    && trigger == BattlePassiveTrigger.ROUND_START
                    && effects.stream().anyMatch(effect ->
                            "GOJO_BASIC_1".equalsIgnoreCase(effect.eventSkillCode()));

            if (gojoHachFollowUp) {
                battle.getEventCollector().record(
                        BattleEventType.ACTION_START,
                        source.getTeam().name(), source.getInstanceId(),
                        null, null, null, null,
                        null, null, null, null, null, null, null,
                        "Gojo Hach follow-up action started"
                );
                battle.getEventCollector().record(
                        BattleEventType.BASIC_ATTACK,
                        source.getTeam().name(), source.getInstanceId(),
                        null, null, "GOJO_BASIC_1",
                        null, false,
                        source.getCurrentHp(), source.getMaxHp(), source.isAlive(),
                        null, null, null,
                        "Gojo Hach follow-up basic attack"
                );
            }

            try {
                effectProcessor.executeEffects(
                        battle,
                        source,
                        effects,
                        trigger == BattlePassiveTrigger.ON_DAMAGED
                                || trigger == BattlePassiveTrigger.ON_REVIVE
                                || trigger == BattlePassiveTrigger.ON_ENEMY_ACTION
                                ? counterTargetInstanceId
                                : null
                );
            } finally {
                if (gojoHachFollowUp) {
                    battle.getEventCollector().record(
                            BattleEventType.ACTION_END,
                            source.getTeam().name(), source.getInstanceId(),
                            null, null, null, null,
                            null, null, null, null, null, null, null,
                            "Gojo Hach follow-up action finished"
                    );
                }
            }
        }
    }

    private List<HeroSkillDefinition> loadSkills(BattleUnitState source) {
        return skillRepository
                .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                        source.getHeroCode()
                );
    }

    /**
     * Tải cấp độ của toàn bộ kỹ năng thuộc đúng bản sao tướng trong một lần truy vấn.
     */
    private Map<String, Integer> loadSkillLevels(BattleUnitState source) {
        Map<String, Integer> levels = new HashMap<>();
        for (HeroInstanceSkill instanceSkill :
                instanceSkillRepository
                        .findAllByHeroInstanceIdOrderBySkillDefinitionSkillOrderAsc(
                                source.getInstanceId()
                        )) {
            levels.put(
                    instanceSkill.getSkillCode(),
                    instanceSkill.getSkillLevel()
            );
        }
        return levels;
    }

    /**
     * Chọn bộ hiệu ứng theo cấp kỹ năng đã tải cho bản sao tướng hiện tại.
     * Nếu TAB level row chưa khai báo battleEffects, giữ tương thích với bộ
     * HeroSkillEffect chung hiện tại thay vì tự suy diễn từ script Lua.
     */
    private SkillEffectSet loadEffectsForLevel(
            HeroSkillDefinition skill,
            int skillLevel
    ) {
        JsonNode levelRows = readLevelRows(skill.getLevelRowsJson());
        if (!levelRows.isArray() || levelRows.isEmpty()) {
            return new SkillEffectSet(skillLevel, skill.getEffects());
        }

        JsonNode selectedLevel = null;
        int selectedLevelNumber = 0;
        for (JsonNode row : levelRows) {
            int rowLevel = row.path("level").asInt(-1);
            if (rowLevel <= skillLevel && rowLevel > selectedLevelNumber) {
                selectedLevel = row;
                selectedLevelNumber = rowLevel;
            }
        }
        if (selectedLevel == null) {
            selectedLevel = levelRows.get(0);
            selectedLevelNumber = selectedLevel.path("level").asInt(1);
        }

        JsonNode levelEffects = selectedLevel.path("battleEffects");
        if (!levelEffects.isArray()) {
            return new SkillEffectSet(selectedLevelNumber, skill.getEffects());
        }

        List<HeroSkillEffect> effects = new java.util.ArrayList<>();
        for (int index = 0; index < levelEffects.size(); index++) {
            JsonNode config = levelEffects.get(index);
            String effectType = config.path("effectType").asText("");
            String targetType = config.path("targetType").asText("");
            if (effectType.isBlank() || targetType.isBlank()) {
                throw new IllegalStateException(
                        "TAB level battleEffect thiếu effectType/targetType: "
                                + skill.getSkillCode() + " level " + selectedLevelNumber
                                + " effect " + (index + 1)
                );
            }

            HeroSkillEffect effect = new HeroSkillEffect(
                    index + 1, effectType, targetType
            );
            BigDecimal multiplier = config.hasNonNull("multiplier")
                    ? config.path("multiplier").decimalValue()
                    : BigDecimal.ZERO;
            BigDecimal chance = config.hasNonNull("chance")
                    ? config.path("chance").decimalValue()
                    : BigDecimal.ONE;
            Integer duration = config.hasNonNull("duration")
                    ? config.path("duration").asInt()
                    : null;
            effect.updateParameters(multiplier, chance, duration);
            effect.updateConfig(writeJson(config.path("configJson")));
            effects.add(effect);
        }

        return new SkillEffectSet(selectedLevelNumber, effects);
    }

    private JsonNode readLevelRows(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode rows = objectMapper.readTree(json);
            if (rows == null || !rows.isArray()) {
                throw new IllegalArgumentException(
                        "levelRowsJson phải là JSON array"
                );
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "levelRowsJson của kỹ năng không hợp lệ", exception
            );
        }
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Không thể serialize battleEffect configJson", exception
            );
        }
    }

    /**
     * Tạo mã effect ổn định theo heroCode/skillCode, không dựa vào ID tự tăng.
     * Cùng một effect ở các lần seed hoặc battle sẽ giữ nguyên định danh.
     */
    private String effectCode(
            HeroSkillDefinition skill,
            int skillLevel,
            HeroSkillEffect effect
    ) {
        return "SKILL_EFFECT_"
                + skill.getHeroDefinition().getHeroCode().toUpperCase(Locale.ROOT)
                + "_"
                + skill.getSkillCode().toUpperCase(Locale.ROOT)
                + "_L"
                + skillLevel
                + "_E"
                + effect.getEffectOrder();
    }

    private record SkillEffectSet(int level, List<HeroSkillEffect> effects) {
    }

    private boolean passesTriggerWindow(HeroSkillEffect effect, BattleState battle) {
        JsonNode config = readConfig(effect.getConfigJson());
        Integer triggerRound = optionalIntegerFromJson(config, "triggerRound");
        Integer untilRound = optionalIntegerFromJson(config, "untilRound");
        return (triggerRound == null || battle.getCurrentRound() == triggerRound)
                && (untilRound == null || battle.getCurrentRound() <= untilRound);
    }

    private boolean passesCondition(
            HeroSkillEffect effect, BattleState battle, BattleUnitState source
    ) {
        String condition = textFromJson(readConfig(effect.getConfigJson()), "condition");
        if (condition == null) return true;
        BigDecimal fastestSpeed = battle.getUnits().stream()
                .filter(BattleUnitState::isAlive)
                .filter(unit -> unit.getTeam() == source.getTeam())
                .map(BattleUnitState::getSpeed)
                .max(BigDecimal::compareTo)
                .orElse(source.getSpeed());
        boolean fastest = source.getSpeed().compareTo(fastestSpeed) >= 0;
        return switch (condition) {
            case "SOURCE_IS_FASTEST_ALLY" -> fastest;
            case "SOURCE_IS_NOT_FASTEST_ALLY" -> !fastest;
            default -> throw new IllegalArgumentException(
                    "Unsupported passive condition: " + condition);
        };
    }

    private BattlePassiveTrigger getPassiveTrigger(HeroSkillEffect effect) {
        JsonNode config = readConfig(effect.getConfigJson());
        JsonNode triggerNode = config.get("trigger");

        if (triggerNode == null || triggerNode.isNull()
                || triggerNode.asText().isBlank()) {
            return BattlePassiveTrigger.BATTLE_START;
        }

        return parseEnum(
                BattlePassiveTrigger.class,
                triggerNode.asText(),
                "configJson.trigger"
        );
    }

    private HeroSkillDefinition findSkill(
            List<HeroSkillDefinition> skills,
            String skillType
    ) {
        return skills.stream()
                .filter(skill -> skillType.equalsIgnoreCase(skill.getSkillType()))
                .findFirst()
                .orElse(null);
    }

    private String toSkillType(BattleActionType actionType) {
        return switch (actionType) {
            case BASIC_ATTACK -> "BASIC";
            case ACTIVE_SKILL -> "ACTIVE";
            case ULTIMATE_SKILL -> "ULTIMATE";
        };
    }

    /**
     * chance null được hiểu là 100%; giá trị hợp lệ nằm trong [0, 1].
     */
    private boolean passesChance(HeroSkillEffect effect) {
        BigDecimal chance = effect.getChance();

        if (chance == null) {
            return true;
        }
        if (chance.signum() < 0 || chance.compareTo(ONE) > 0) {
            throw new IllegalArgumentException(
                    "Effect chance must be between 0 and 1 at effect order: "
                            + effect.getEffectOrder()
            );
        }

        return chance.compareTo(ONE) == 0
                || ThreadLocalRandom.current().nextDouble() < chance.doubleValue();
    }

    private BattleEffectConfig toEffectConfig(
            HeroSkillEffect effect,
            String effectCode
    ) {
        BattleEffectType effectType = parseEnum(
                BattleEffectType.class, effect.getEffectType(), "effectType"
        );
        BattleTargetMode targetMode = parseEnum(
                BattleTargetMode.class, effect.getTargetType(), "targetType"
        );
        JsonNode config = readConfig(effect.getConfigJson());

        return new BattleEffectConfig(
                effectCode,
                effectType,
                targetMode,
                effect.getMultiplier(),
                decimalFromJson(config, "flatValue"),
                effect.getDuration(),
                effect.getEffectOrder(),
                enumFromJson(config, "statType", BattleStatType.class),
                enumFromJson(config, "statusType", BattleStatusType.class),
                enumFromJson(config, "immunityType", BattleImmunityType.class),
                enumFromJson(config, "dispelType", BattleDispelType.class),
                booleanFromJson(config, "permanent"),
                integerFromJson(config, "stacks", 1),
                integerFromJson(config, "maxStacks", 1),
                booleanFromJson(config, "purgeable", true),
                booleanFromJson(config, "ignoreImmunity"),
                enumFromJson(config, "damageType", BattleDamageType.class),
                decimalFromJson(config, "roleBonusPercent"),
                booleanFromJson(config, "roleBonusTankSupport"),
                textFromJson(config, "condition"),
                optionalIntegerFromJson(config, "triggerRound"),
                optionalIntegerFromJson(config, "untilRound"),
                textFromJson(config, "eventSkillCode")
        );
    }

    private JsonNode readConfig(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException(
                        "configJson phải là một JSON object"
                );
            }
            return node;
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "configJson của hiệu ứng kỹ năng không hợp lệ",
                    exception
            );
        }
    }

    private BigDecimal decimalFromJson(JsonNode config, String field) {
        JsonNode value = config.get(field);
        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException(
                    "configJson." + field + " phải là số"
            );
        }

        BigDecimal result = value.decimalValue();
        if (result.signum() < 0) {
            throw new IllegalArgumentException(
                    "configJson." + field + " không được âm"
            );
        }
        return result;
    }

    private String textFromJson(JsonNode config, String field) {
        JsonNode value = config.get(field);
        return value == null || value.isNull() || value.asText().isBlank()
                ? null : value.asText();
    }

    private Integer optionalIntegerFromJson(JsonNode config, String field) {
        JsonNode value = config.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || value.intValue() < 1)
            throw new IllegalArgumentException("configJson." + field + " phải là số nguyên dương");
        return value.intValue();
    }

    private boolean booleanFromJson(JsonNode config, String field) {
        return booleanFromJson(config, field, false);
    }

    private boolean booleanFromJson(JsonNode config, String field, boolean defaultValue) {
        JsonNode value = config.get(field);
        if (value == null || value.isNull()) return defaultValue;
        if (!value.isBoolean()) throw new IllegalArgumentException(
                "configJson." + field + " phải là boolean");
        return value.booleanValue();
    }

    private int integerFromJson(JsonNode config, String field, int defaultValue) {
        JsonNode value = config.get(field);
        if (value == null || value.isNull()) return defaultValue;
        if (!value.isIntegralNumber() || value.intValue() < 1)
            throw new IllegalArgumentException("configJson." + field + " phải là số nguyên dương");
        return value.intValue();
    }

    private <E extends Enum<E>> E enumFromJson(
            JsonNode config,
            String field,
            Class<E> enumType
    ) {
        JsonNode value = config.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return parseEnum(enumType, value.asText(), "configJson." + field);
    }

    private <E extends Enum<E>> E parseEnum(
            Class<E> enumType,
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " không được để trống"
            );
        }

        try {
            return Enum.valueOf(
                    enumType, value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Giá trị " + fieldName + " không được hỗ trợ: " + value,
                    exception
            );
        }
    }

    private void recordSkillActivation(
            BattleState battle,
            BattleUnitState source,
            HeroSkillDefinition skill,
            BattleActionType actionType
    ) {
        BattleEventType eventType = switch (actionType) {
            case BASIC_ATTACK -> BattleEventType.BASIC_ATTACK;
            case ACTIVE_SKILL -> BattleEventType.ACTIVE_SKILL;
            case ULTIMATE_SKILL -> BattleEventType.ULTIMATE_SKILL;
        };

        battle.getEventCollector().record(
                eventType,
                source.getTeam().name(),
                source.getInstanceId(),
                null, null,
                skill.getSkillCode(),
                null,
                false,
                source.getCurrentHp(),
                source.getMaxHp(),
                source.isAlive(),
                null, null, null,
                "Skill activated: " + skill.getSkillName()
        );
    }

    private void recordPassiveActivation(
            BattleState battle,
            BattleUnitState source,
            HeroSkillDefinition skill,
            BattlePassiveTrigger trigger,
            Long counterTargetInstanceId
    ) {
        // Các trigger phản ứng theo một đơn vị cụ thể phải giữ đúng instance
        // làm mục tiêu. Với Gojo, ON_ENEMY_ACTION truyền actor địch vừa hành động;
        // không được để FE mất targetInstanceId của event PASSIVE.
        BattleUnitState counterTarget = (trigger == BattlePassiveTrigger.ON_DAMAGED
                || trigger == BattlePassiveTrigger.ON_REVIVE
                || trigger == BattlePassiveTrigger.ON_ENEMY_ACTION)
                && counterTargetInstanceId != null
                ? battle.findUnit(counterTargetInstanceId)
                : null;

        battle.getEventCollector().record(
                BattleEventType.PASSIVE,
                source.getTeam().name(),
                source.getInstanceId(),
                counterTarget == null ? null : counterTarget.getTeam().name(),
                counterTarget == null ? null : counterTarget.getInstanceId(),
                skill.getSkillCode(),
                null,
                false,
                source.getCurrentHp(),
                source.getMaxHp(),
                source.isAlive(),
                null,
                trigger.name(),
                null,
                "Passive activated: " + skill.getSkillName()
        );
    }
}
