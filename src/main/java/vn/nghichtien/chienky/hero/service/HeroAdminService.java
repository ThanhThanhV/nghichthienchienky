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

        Map<Long, HeroSkillDefinition> skillById = currentSkills.stream()
                .collect(Collectors.toMap(HeroSkillDefinition::getId, skill -> skill));
        Map<String, Object> animationMapping = new LinkedHashMap<>();

        for (HeroAdminUpdateRequest.SkillRequest skillRequest : request.skills()) {
            HeroSkillDefinition skill = skillById.get(skillRequest.id());
            if (skill == null || !Objects.equals(skill.getSkillOrder(), skillRequest.skillOrder())) {
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
            Map<Long, HeroSkillEffect> effectById = existingEffects.stream()
                    .filter(effect -> effect.getId() != null)
                    .collect(Collectors.toMap(HeroSkillEffect::getId, effect -> effect));
            Set<Long> retainedIds = new HashSet<>();

            if (skillRequest.effects() == null || skillRequest.effects().isEmpty()) {
                throw badRequest("Kỹ năng " + skill.getSkillName() + " phải có ít nhất một hiệu ứng");
            }

            for (HeroAdminUpdateRequest.EffectRequest effectRequest : skillRequest.effects()) {
                validateEffect(effectRequest);
                HeroSkillEffect effect;
                if (effectRequest.id() == null) {
                    effect = new HeroSkillEffect(
                            effectRequest.effectOrder(),
                            effectRequest.effectType().trim().toUpperCase(Locale.ROOT),
                            effectRequest.targetType().trim().toUpperCase(Locale.ROOT)
                    );
                    skill.addEffect(effect);
                } else {
                    effect = effectById.get(effectRequest.id());
                    if (effect == null || !retainedIds.add(effectRequest.id())) {
                        throw badRequest("Hiệu ứng không thuộc kỹ năng " + skill.getSkillName());
                    }
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
                if (oldEffect.getId() != null && !retainedIds.contains(oldEffect.getId())) {
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
        validateStats(request.baseStats(), true);
        validateStats(request.growthStats(), false);
    }

    private void validateStats(HeroAdminUpdateRequest.StatsRequest stats, boolean base) {
        List<BigDecimal> values = List.of(
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
        normalizeConfig(effect.configJson());
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
        result.put("id", skill.getId());
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
        result.put("id", effect.getId());
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
