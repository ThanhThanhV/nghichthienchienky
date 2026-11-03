package vn.nghichtien.chienky.battle.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.battle.domain.*;
import vn.nghichtien.chienky.hero.domain.HeroSkillDefinition;
import vn.nghichtien.chienky.hero.domain.HeroSkillEffect;
import vn.nghichtien.chienky.hero.repository.HeroSkillDefinitionRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Đọc cấu hình kỹ năng từ database, chuyển thành cấu hình runtime và thực thi.
 * Database lưu dữ liệu kỹ năng; Java thực thi luật chiến đấu.
 */
@Component
public class DatabaseSkillExecutor {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final HeroSkillDefinitionRepository skillRepository;
    private final BattleSkillEngine skillEngine;
    private final ObjectMapper objectMapper;

    public DatabaseSkillExecutor(
            HeroSkillDefinitionRepository skillRepository,
            BattleSkillEngine skillEngine,
            ObjectMapper objectMapper
    ) {
        this.skillRepository = skillRepository;
        this.skillEngine = skillEngine;
        this.objectMapper = objectMapper;
    }

    /**
     * Thực thi kỹ năng được chọn theo chu kỳ hành động của nhân vật.
     * Nếu thiếu ACTIVE hoặc ULTIMATE thì tự động dùng BASIC.
     *
     * @return loại hành động thực tế sau khi áp dụng fallback.
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
        BattleActionType actualAction = requestedAction;

        if (skill == null && requestedAction != BattleActionType.BASIC_ATTACK) {
            skill = findSkill(skills, "BASIC");
            actualAction = BattleActionType.BASIC_ATTACK;
        }

        if (skill == null) {
            throw new IllegalStateException(
                    "Không tìm thấy kỹ năng " + requestedType
                            + " hoặc BASIC cho nhân vật " + source.getHeroCode()
            );
        }

        List<BattleEffectConfig> effects = skill.getEffects().stream()
                .filter(effect -> Boolean.TRUE.equals(effect.getEnabled()))
                .sorted(Comparator.comparing(
                        HeroSkillEffect::getEffectOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .filter(this::passesChance)
                .map(this::toEffectConfig)
                .toList();

        skillEngine.executeSkill(battle, source, skill, actualAction, effects);

        return actualAction;
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

        List<HeroSkillDefinition> passiveSkills = loadSkills(source).stream()
                .filter(skill -> "PASSIVE".equalsIgnoreCase(skill.getSkillType()))
                .toList();

        for (HeroSkillDefinition skill : passiveSkills) {
            if (battle.isFinished() || !source.isAlive()) {
                break;
            }

            List<BattleEffectConfig> effects = skill.getEffects().stream()
                    .filter(effect -> Boolean.TRUE.equals(effect.getEnabled()))
                    .sorted(Comparator.comparing(
                            HeroSkillEffect::getEffectOrder,
                            Comparator.nullsLast(Integer::compareTo)
                    ))
                    .filter(effect -> getPassiveTrigger(effect) == trigger)
                    .filter(this::passesChance)
                    .map(this::toEffectConfig)
                    .toList();

            if (effects.isEmpty()) {
                continue;
            }

            skillEngine.executePassive(
                    battle,
                    source,
                    skill,
                    trigger,
                    effects,
                    trigger == BattlePassiveTrigger.ON_DAMAGED
                            ? counterTargetInstanceId
                            : null
            );
        }
    }

    private List<HeroSkillDefinition> loadSkills(BattleUnitState source) {
        return skillRepository
                .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                        source.getHeroCode()
                );
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
                    "Effect chance must be between 0 and 1: " + effect.getId()
            );
        }

        return chance.compareTo(ONE) == 0
                || ThreadLocalRandom.current().nextDouble() < chance.doubleValue();
    }

    private BattleEffectConfig toEffectConfig(HeroSkillEffect effect) {
        BattleEffectType effectType = parseEnum(
                BattleEffectType.class, effect.getEffectType(), "effectType"
        );
        BattleTargetMode targetMode = parseEnum(
                BattleTargetMode.class, effect.getTargetType(), "targetType"
        );
        JsonNode config = readConfig(effect.getConfigJson());

        return new BattleEffectConfig(
                "SKILL_EFFECT_" + effect.getId(),
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
                booleanFromJson(config, "permanent")
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

    private boolean booleanFromJson(JsonNode config, String field) {
        JsonNode value = config.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(
                    "configJson." + field + " phải là boolean"
            );
        }
        return value.booleanValue();
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

}
