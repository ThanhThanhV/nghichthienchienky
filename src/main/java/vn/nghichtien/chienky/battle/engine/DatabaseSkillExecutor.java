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
    private final BattleEffectProcessor effectProcessor;
    private final ObjectMapper objectMapper;

    public DatabaseSkillExecutor(
            HeroSkillDefinitionRepository skillRepository,
            BattleEffectProcessor effectProcessor,
            ObjectMapper objectMapper
    ) {
        this.skillRepository = skillRepository;
        this.effectProcessor = effectProcessor;
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

        List<HeroSkillDefinition> skills =
                skillRepository
                        .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                                source.getHeroCode()
                        );

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

        recordSkillActivation(battle, source, skill, actualAction);
        effectProcessor.executeEffects(battle, source, effects);

        return actualAction;
    }

    /**
     * Tìm kỹ năng theo loại, không phân biệt chữ hoa/chữ thường.
     */
    private HeroSkillDefinition findSkill(
            List<HeroSkillDefinition> skills,
            String skillType
    ) {
        return skills.stream()
                .filter(skill -> skillType.equalsIgnoreCase(skill.getSkillType()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Chuyển loại hành động runtime thành loại kỹ năng trong database.
     */
    private String toSkillType(BattleActionType actionType) {
        return switch (actionType) {
            case BASIC_ATTACK -> "BASIC";
            case ACTIVE_SKILL -> "ACTIVE";
            case ULTIMATE_SKILL -> "ULTIMATE";
        };
    }

    /**
     * Kiểm tra xác suất kích hoạt hiệu ứng.
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

    /**
     * Chuyển một hiệu ứng database thành cấu hình mà battle engine hiểu.
     */
    private BattleEffectConfig toEffectConfig(HeroSkillEffect effect) {
        BattleEffectType effectType = parseEnum(
                BattleEffectType.class,
                effect.getEffectType(),
                "effectType"
        );

        BattleTargetMode targetMode = parseEnum(
                BattleTargetMode.class,
                effect.getTargetType(),
                "targetType"
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
                enumFromJson(config, "dispelType", BattleDispelType.class)
        );
    }

    /**
     * Đọc configJson; cấu hình trống được xem là object rỗng.
     */
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

    /**
     * Đọc giá trị số trong configJson.
     */
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

    /**
     * Đọc enum tùy chọn trong configJson.
     */
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

    /**
     * Parse enum an toàn và báo rõ trường cấu hình sai.
     */
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
                    enumType,
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Giá trị " + fieldName + " không được hỗ trợ: " + value,
                    exception
            );
        }
    }

    /**
     * Ghi một sự kiện kích hoạt kỹ năng. Đây là nơi duy nhất ghi event
     * BASIC/ACTIVE/ULTIMATE để tránh tạo sự kiện trùng lặp.
     */
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
                null,
                null,
                skill.getSkillCode(),
                null,
                false,
                source.getCurrentHp(),
                source.getMaxHp(),
                source.isAlive(),
                null,
                null,
                null,
                "Skill activated: " + skill.getSkillName()
        );
    }
}
