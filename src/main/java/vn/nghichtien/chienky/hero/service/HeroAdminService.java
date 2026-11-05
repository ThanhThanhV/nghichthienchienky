package vn.nghichtien.chienky.hero.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.nghichtien.chienky.battle.domain.BattleEffectType;
import vn.nghichtien.chienky.battle.domain.BattleTargetMode;
import vn.nghichtien.chienky.hero.domain.*;
import vn.nghichtien.chienky.hero.dto.HeroAdminUpdateRequest;
import vn.nghichtien.chienky.hero.repository.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ danh sách và chỉnh sửa dữ liệu tướng dành riêng cho Admin.
 */
@Service
public class HeroAdminService {

    private static final Set<String> RARITIES =
            Set.of("COMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
    private static final Set<String> ROLES =
            Set.of("WARRIOR", "ASSASSIN", "MAGE", "TANK", "SUPPORT");

    private final HeroDefinitionRepository heroRepository;
    private final HeroBaseStatsRepository baseStatsRepository;
    private final HeroGrowthConfigRepository growthRepository;
    private final HeroSkillDefinitionRepository skillRepository;
    private final ObjectMapper objectMapper;

    public HeroAdminService(
            HeroDefinitionRepository heroRepository,
            HeroBaseStatsRepository baseStatsRepository,
            HeroGrowthConfigRepository growthRepository,
            HeroSkillDefinitionRepository skillRepository,
            ObjectMapper objectMapper
    ) {
        this.heroRepository = heroRepository;
        this.baseStatsRepository = baseStatsRepository;
        this.growthRepository = growthRepository;
        this.skillRepository = skillRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listHeroes() {
        return heroRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        HeroDefinition::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo)
                ).thenComparing(HeroDefinition::getHeroName))
                .map(hero -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("heroCode", hero.getHeroCode());
                    item.put("heroName", hero.getHeroName());
                    item.put("rarity", hero.getRarity());
                    item.put("role", hero.getRole());
                    item.put("faction", hero.getFaction());
                    item.put("avatarCode", hero.getAvatarCode());
                    item.put("enabled", hero.getEnabled());
                    item.put("sortOrder", hero.getSortOrder());
                    item.put("spineSkeleton", hero.getSpineSkeleton());
                    return item;
                }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getHero(String heroCode) {
        HeroDefinition hero = getDefinition(heroCode);
        HeroBaseStats base = baseStatsRepository.findByHeroCode(heroCode)
                .orElseThrow(() -> notFound("Không tìm thấy chỉ số cơ bản của tướng"));
        HeroGrowthConfig growth = growthRepository.findByHeroCode(heroCode)
                .orElseThrow(() -> notFound("Không tìm thấy cấu hình tăng trưởng của tướng"));
        List<HeroSkillDefinition> skills =
                skillRepository.findAllByHeroDefinitionHeroCodeOrderBySkillOrderAsc(heroCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("heroCode", hero.getHeroCode());
        result.put("heroName", hero.getHeroName());
        result.put("rarity", hero.getRarity());
        result.put("role", hero.getRole());
        result.put("faction", hero.getFaction());
        result.put("avatarCode", hero.getAvatarCode());
        result.put("description", hero.getDescription());
        result.put("enabled", hero.getEnabled());
        result.put("sortOrder", hero.getSortOrder());
        result.put("basicAttackMovement", readConfig(hero.getSpineSkillAnimations())
                .path("_BASIC_ATTACK").path("movement").asText("DASH"));
        result.put("spineSkillAnimations", readConfig(hero.getSpineSkillAnimations()));
        result.put("spineSkeleton", hero.getSpineSkeleton());
        result.put("spineAtlas", hero.getSpineAtlas());
        result.put("spineTextures", parseJsonArray(hero.getSpineTextures()));
        result.put("baseStats", statsMap(base));
        result.put("growthStats", statsMap(growth));
        result.put("skills", skills.stream().map(this::skillMap).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> updateHero(String heroCode, HeroAdminUpdateRequest request) {
        HeroDefinition hero = getDefinition(heroCode);
        validateRequest(request);

        hero.updateManagementInformation(
                request.heroName().trim(),
                request.rarity().trim().toUpperCase(Locale.ROOT),
                request.role().trim().toUpperCase(Locale.ROOT),
                request.faction().trim(),
                request.avatarCode().trim(),
                request.description() == null ? "" : request.description().trim(),
                request.enabled() == null ? hero.getEnabled() : request.enabled(),
                request.sortOrder() == null ? hero.getSortOrder() : request.sortOrder()
        );

        HeroBaseStats base = baseStatsRepository.findByHeroCode(heroCode)
                .orElseThrow(() -> notFound("Không tìm thấy chỉ số cơ bản của tướng"));
        applyStats(base, request.baseStats(), true);

        HeroGrowthConfig growth = growthRepository.findByHeroCode(heroCode)
                .orElseThrow(() -> notFound("Không tìm thấy cấu hình tăng trưởng của tướng"));
        applyStats(growth, request.growthStats(), false);

        List<HeroSkillDefinition> currentSkills =
                skillRepository.findAllByHeroDefinitionHeroCodeOrderBySkillOrderAsc(heroCode);
        if (currentSkills.size() != 5 || request.skills().size() != currentSkills.size()) {
            throw badRequest("Mỗi tướng phải giữ đúng 5 kỹ năng theo cấu trúc hiện tại");
        }

        Map<String, HeroSkillDefinition> skillByCode = currentSkills.stream()
                .collect(Collectors.toMap(HeroSkillDefinition::getSkillCode, skill -> skill));
        Map<String, Object> animationMapping = new LinkedHashMap<>();
        String basicAttackMovement = request.basicAttackMovement() == null
                ? "DASH"
                : request.basicAttackMovement().trim().toUpperCase(Locale.ROOT);
        animationMapping.put("_BASIC_ATTACK", Map.of("movement", basicAttackMovement));

        Set<String> retainedSkillCodes = new HashSet<>();
        for (HeroAdminUpdateRequest.SkillRequest skillRequest : request.skills()) {
            if (skillRequest.skillCode() == null
                    || !retainedSkillCodes.add(skillRequest.skillCode())) {
                throw badRequest("skillCode bị thiếu hoặc bị lặp trong payload");
            }

            HeroSkillDefinition skill = skillByCode.get(skillRequest.skillCode());
            if (skill == null
                    || !Objects.equals(skill.getSkillOrder(), skillRequest.skillOrder())) {
                throw badRequest("Kỹ năng gửi lên không thuộc tướng hoặc sai thứ tự");
            }
            if (skillRequest.skillName() == null || skillRequest.skillName().isBlank()) {
                throw badRequest("Tên kỹ năng không được để trống");
            }

            skill.updateInformation(
                    skillRequest.skillName().trim(),
                    skillRequest.description() == null ? "" : skillRequest.description().trim(),
                    blankToNull(skillRequest.iconCode())
            );
            skill.changeEnabled(skillRequest.enabled() == null
                    || skillRequest.enabled());

            List<HeroSkillEffect> existingEffects = new ArrayList<>(skill.getEffects());
            Map<Integer, HeroSkillEffect> effectByOrder = existingEffects.stream()
                    .filter(effect -> effect.getEffectOrder() != null)
                    .collect(Collectors.toMap(HeroSkillEffect::getEffectOrder, effect -> effect));
            Set<Integer> retainedEffectOrders = new HashSet<>();

            if (skillRequest.effects() == null || skillRequest.effects().isEmpty()) {
                throw badRequest("Kỹ năng " + skill.getSkillName() + " phải có ít nhất một hiệu ứng");
            }

            for (HeroAdminUpdateRequest.EffectRequest effectRequest : skillRequest.effects()) {
                validateEffect(effectRequest);
                if (!retainedEffectOrders.add(effectRequest.effectOrder())) {
                    throw badRequest("effectOrder bị lặp trong kỹ năng " + skill.getSkillName());
                }

                HeroSkillEffect effect = effectByOrder.get(effectRequest.effectOrder());
                if (effect == null) {
                    effect = new HeroSkillEffect(
                            effectRequest.effectOrder(),
                            effectRequest.effectType().trim().toUpperCase(Locale.ROOT),
                            effectRequest.targetType().trim().toUpperCase(Locale.ROOT)
                    );
                    skill.addEffect(effect);
                } else {
                    effect.updateDefinition(
                            effectRequest.effectOrder(),
                            effectRequest.effectType().trim().toUpperCase(Locale.ROOT),
                            effectRequest.targetType().trim().toUpperCase(Locale.ROOT)
                    );
                }

                effect.updateParameters(
                        effectRequest.multiplier(),
                        effectRequest.chance(),
                        effectRequest.duration()
                );
                effect.updateConfig(normalizeConfig(effectRequest.configJson()));
                effect.changeEnabled(effectRequest.enabled() == null
                        || effectRequest.enabled());
            }

            for (HeroSkillEffect oldEffect : existingEffects) {
                if (oldEffect.getEffectOrder() == null
                        || !retainedEffectOrders.contains(oldEffect.getEffectOrder())) {
                    skill.removeEffect(oldEffect);
                }
            }

            HeroSkillEffect animationEffect = skill.getEffects().stream()
                    .filter(effect -> Boolean.TRUE.equals(effect.getEnabled()))
                    .min(Comparator.comparing(
                            HeroSkillEffect::getEffectOrder,
                            Comparator.nullsLast(Integer::compareTo)
                    )).orElse(null);
            if (animationEffect != null) {
                JsonNode config = readConfig(animationEffect.getConfigJson());
                Map<String, Object> animation = new LinkedHashMap<>();
                copyIfPresent(config, animation, "animation");
                copyIfPresent(config, animation, "animationDurationMs");
                copyIfPresent(config, animation, "impactTimeMs");
                if (!animation.isEmpty()) {
                    animationMapping.put(skill.getSkillCode(), animation);
                }
            }
        }

        hero.updateSpineSkillAnimations(writeJson(animationMapping));
        heroRepository.flush();
        baseStatsRepository.flush();
        growthRepository.flush();
        skillRepository.flush();
        return getHero(heroCode);
    }

    private void validateRequest(HeroAdminUpdateRequest request) {
        if (request == null || request.heroName() == null || request.heroName().isBlank()
                || request.rarity() == null || request.role() == null
                || request.faction() == null || request.avatarCode() == null
                || request.baseStats() == null || request.growthStats() == null
                || request.skills() == null) {
            throw badRequest("Thiếu thông tin bắt buộc khi cập nhật tướng");
        }
        if (!RARITIES.contains(request.rarity().trim().toUpperCase(Locale.ROOT))) {
            throw badRequest("Độ hiếm không hợp lệ");
        }
        if (!ROLES.contains(request.role().trim().toUpperCase(Locale.ROOT))) {
            throw badRequest("Vai trò không hợp lệ");
        }
        if (request.basicAttackMovement() != null
                && !Set.of("DASH", "STATIONARY").contains(
                        request.basicAttackMovement().trim().toUpperCase(Locale.ROOT))) {
            throw badRequest("Kiểu di chuyển đánh thường chỉ được là DASH hoặc STATIONARY");
        }
        validateStats(request.baseStats(), true);
        validateStats(request.growthStats(), false);
    }

    private void validateStats(HeroAdminUpdateRequest.StatsRequest stats, boolean base) {
        List<BigDecimal> values = Arrays.asList(
                stats.hp(), stats.atk(), stats.def(), stats.speed(), stats.crit(),
                stats.critDamage(), stats.accuracy(), stats.resistance(),
                stats.penFlat(), stats.penPercent(), stats.lifesteal()
        );
        for (int i = 0; i < values.size(); i++) {
            BigDecimal value = values.get(i);
            if (value == null || value.signum() < 0
                    || (base && i < 4 && value.signum() == 0)) {
                throw badRequest("Giá trị chỉ số không hợp lệ");
            }
        }
    }

    private void validateEffect(HeroAdminUpdateRequest.EffectRequest effect) {
        if (effect.effectOrder() == null || effect.effectOrder() < 1
                || effect.effectType() == null || effect.targetType() == null
                || effect.multiplier() == null || effect.multiplier().signum() < 0
                || effect.duration() == null || effect.duration() < 0) {
            throw badRequest("Thiếu hoặc sai thông số hiệu ứng");
        }
        try {
            BattleEffectType.valueOf(effect.effectType().trim().toUpperCase(Locale.ROOT));
            BattleTargetMode.valueOf(effect.targetType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("Loại hiệu ứng hoặc cách chọn mục tiêu không hợp lệ");
        }
        if (effect.chance() != null && (effect.chance().signum() < 0
                || effect.chance().compareTo(BigDecimal.ONE) > 0)) {
            throw badRequest("Chance phải nằm trong khoảng 0 đến 1");
        }
        String normalizedConfig = normalizeConfig(effect.configJson());
        JsonNode config = readConfig(normalizedConfig);
        String type = effect.effectType().trim().toUpperCase(Locale.ROOT);
        if ("SUMMON".equals(type)) {
            throw badRequest("SUMMON chưa được hỗ trợ trong bộ máy chiến đấu hiện tại");
        }
        JsonNode permanentNode = config.get("permanent");
        if (permanentNode != null && !permanentNode.isNull() && !permanentNode.isBoolean()) {
            throw badRequest("permanent phải là boolean");
        }
        boolean permanent = permanentNode != null && permanentNode.asBoolean();
        if (permanent && !"BUFF".equals(type) && !"DEBUFF".equals(type)) {
            throw badRequest("Hiệu ứng vĩnh viễn chỉ áp dụng cho BUFF/DEBUFF");
        }
        if (("BUFF".equals(type) || "DEBUFF".equals(type))
                && !isOneOf(config, "statType", Set.of(
                        "ATK", "DEF", "SPEED", "CRIT", "CRIT_DAMAGE",
                        "ACCURACY", "RESISTANCE", "PEN_FLAT", "PEN_PERCENT", "LIFESTEAL"))) {
            throw badRequest(type + " cần cấu hình statType hợp lệ");
        }
        if ("CC".equals(type) && !isOneOf(config, "statusType",
                Set.of("STUN", "FREEZE", "SILENCE", "TAUNT"))) {
            throw badRequest("CC cần cấu hình statusType hợp lệ");
        }
        if ("IMMUNITY".equals(type) && !isOneOf(config, "immunityType",
                Set.of("BUFF", "DEBUFF", "CC", "DAMAGE"))) {
            throw badRequest("IMMUNITY cần cấu hình immunityType hợp lệ");
        }
        if ("DISPEL".equals(type) && !isOneOf(config, "dispelType",
                Set.of("BUFF", "DEBUFF", "CC"))) {
            throw badRequest("DISPEL cần cấu hình dispelType hợp lệ");
        }
        JsonNode flatValue = config.get("flatValue");
        if (flatValue != null && !flatValue.isNull()
                && (!flatValue.isNumber() || flatValue.decimalValue().signum() < 0)) {
            throw badRequest("flatValue phải là số không âm");
        }
        if (("DOT".equals(type) || "HOT".equals(type))
                && effect.duration() <= 0) {
            throw badRequest(type + " cần duration lớn hơn 0");
        }
        if ("DOT".equals(type) || "HOT".equals(type)) {
            BigDecimal flat = flatValue == null || !flatValue.isNumber()
                    ? BigDecimal.ZERO : flatValue.decimalValue();
            if (effect.multiplier().signum() == 0 && flat.signum() == 0) {
                throw badRequest(type + " cần multiplier hoặc flatValue lớn hơn 0");
            }
        }
        JsonNode animationDuration = config.get("animationDurationMs");
        JsonNode impactTime = config.get("impactTimeMs");
        if (animationDuration != null && !animationDuration.isNull()
                && (!animationDuration.isNumber() || animationDuration.asInt() < 1)) {
            throw badRequest("animationDurationMs phải là số lớn hơn 0");
        }
        if (impactTime != null && !impactTime.isNull()
                && (!impactTime.isNumber() || impactTime.asInt() < 0)) {
            throw badRequest("impactTimeMs phải là số không âm");
        }
        if (impactTime != null && impactTime.isNumber()
                && animationDuration != null && animationDuration.isNumber()
                && impactTime.asInt() > animationDuration.asInt()) {
            throw badRequest("impactTimeMs không được vượt animationDurationMs");
        }
        JsonNode animation = config.get("animation");
        if (animation != null && !animation.isNull() && !animation.isTextual()) {
            throw badRequest("animation phải là chuỗi");
        }
        JsonNode trigger = config.get("trigger");
        if (trigger != null && !trigger.isNull()
                && (!trigger.isTextual() || !Set.of(
                        "BATTLE_START", "ROUND_START", "BEFORE_ACTION",
                        "AFTER_ACTION", "ON_ATTACK", "ON_DAMAGED", "ON_KILL"
                ).contains(trigger.asText().trim().toUpperCase(Locale.ROOT)))) {
            throw badRequest("Trigger nội tại không được hỗ trợ");
        }
    }

    private boolean isOneOf(JsonNode config, String field, Set<String> allowed) {
        JsonNode value = config.get(field);
        return value != null && value.isTextual()
                && allowed.contains(value.asText().trim().toUpperCase(Locale.ROOT));
    }

    private String normalizeConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return "{}";
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            if (node == null || !node.isObject()) {
                throw badRequest("Cấu hình nâng cao phải là JSON object");
            }
            return objectMapper.writeValueAsString(node);
        } catch (IOException exception) {
            throw badRequest("JSON cấu hình hiệu ứng không hợp lệ: " + exception.getMessage());
        }
    }

    private JsonNode readConfig(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (IOException exception) {
            throw badRequest("JSON cấu hình hiệu ứng không hợp lệ");
        }
    }

    private void copyIfPresent(JsonNode source, Map<String, Object> target, String field) {
        JsonNode value = source.get(field);
        if (value != null && !value.isNull()) {
            target.put(field, value.isNumber() ? value.numberValue() : value.asText());
        }
    }

    private void applyStats(HeroBaseStats entity, HeroAdminUpdateRequest.StatsRequest stats, boolean base) {
        entity.updateStats(stats.hp(), stats.atk(), stats.def(), stats.speed(),
                stats.crit(), stats.critDamage(), stats.accuracy(), stats.resistance(),
                stats.penFlat(), stats.penPercent(), stats.lifesteal());
    }

    private void applyStats(HeroGrowthConfig entity, HeroAdminUpdateRequest.StatsRequest stats, boolean ignored) {
        entity.updateGrowth(stats.hp(), stats.atk(), stats.def(), stats.speed(),
                stats.crit(), stats.critDamage(), stats.accuracy(), stats.resistance(),
                stats.penFlat(), stats.penPercent(), stats.lifesteal());
    }

    private Map<String, Object> statsMap(HeroBaseStats stats) {
        return statsMap(stats.getHp(), stats.getAtk(), stats.getDef(), stats.getSpeed(),
                stats.getCrit(), stats.getCritDamage(), stats.getAccuracy(), stats.getResistance(),
                stats.getPenFlat(), stats.getPenPercent(), stats.getLifesteal());
    }

    private Map<String, Object> statsMap(HeroGrowthConfig stats) {
        return statsMap(stats.getHp(), stats.getAtk(), stats.getDef(), stats.getSpeed(),
                stats.getCrit(), stats.getCritDamage(), stats.getAccuracy(), stats.getResistance(),
                stats.getPenFlat(), stats.getPenPercent(), stats.getLifesteal());
    }

    private Map<String, Object> statsMap(BigDecimal hp, BigDecimal atk, BigDecimal def,
            BigDecimal speed, BigDecimal crit, BigDecimal critDamage, BigDecimal accuracy,
            BigDecimal resistance, BigDecimal penFlat, BigDecimal penPercent, BigDecimal lifesteal) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hp", hp); result.put("atk", atk); result.put("def", def); result.put("speed", speed);
        result.put("crit", crit); result.put("critDamage", critDamage); result.put("accuracy", accuracy);
        result.put("resistance", resistance); result.put("penFlat", penFlat);
        result.put("penPercent", penPercent); result.put("lifesteal", lifesteal);
        return result;
    }

    private Map<String, Object> skillMap(HeroSkillDefinition skill) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillCode", skill.getSkillCode());
        result.put("skillName", skill.getSkillName());
        result.put("skillType", skill.getSkillType());
        result.put("skillOrder", skill.getSkillOrder());
        result.put("description", skill.getDescription());
        result.put("iconCode", skill.getIconCode());
        result.put("enabled", skill.getEnabled());
        result.put("effects", skill.getEffects().stream()
                .sorted(Comparator.comparing(HeroSkillEffect::getEffectOrder))
                .map(this::effectMap).toList());
        return result;
    }

    private Map<String, Object> effectMap(HeroSkillEffect effect) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("effectOrder", effect.getEffectOrder());
        result.put("effectType", effect.getEffectType());
        result.put("targetType", effect.getTargetType());
        result.put("multiplier", effect.getMultiplier());
        result.put("chance", effect.getChance());
        result.put("duration", effect.getDuration());
        result.put("configJson", effect.getConfigJson());
        result.put("enabled", effect.getEnabled());
        return result;
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (IOException exception) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo JSON mapping animation", exception);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private HeroDefinition getDefinition(String heroCode) {
        return heroRepository.findById(heroCode)
                .orElseThrow(() -> notFound("Không tìm thấy tướng: " + heroCode));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
