package vn.nghichtien.chienky.hero.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO kỹ năng định danh bằng skillCode, không công khai ID tự tăng của catalog.
 */
public class HeroSkillResponse {

    private final String skillCode;
    private final String skillName;
    private final String skillType;
    private final Integer skillOrder;
    private final String description;
    private final String iconCode;
    private final Integer skillLevel;
    private final List<EffectResponse> effects;

    public HeroSkillResponse(
            String skillCode,
            String skillName,
            String skillType,
            Integer skillOrder,
            String description,
            String iconCode,
            Integer skillLevel,
            List<EffectResponse> effects
    ) {
        this.skillCode = skillCode;
        this.skillName = skillName;
        this.skillType = skillType;
        this.skillOrder = skillOrder;
        this.description = description;
        this.iconCode = iconCode;
        this.skillLevel = skillLevel;
        this.effects = List.copyOf(effects);
    }

    public String getSkillCode() { return skillCode; }
    public String getSkillName() { return skillName; }
    public String getSkillType() { return skillType; }
    public Integer getSkillOrder() { return skillOrder; }
    public String getDescription() { return description; }
    public String getIconCode() { return iconCode; }
    public Integer getSkillLevel() { return skillLevel; }
    public List<EffectResponse> getEffects() { return effects; }

    public static class EffectResponse {
        private final Integer effectOrder;
        private final String effectType;
        private final String targetType;
        private final BigDecimal multiplier;
        private final BigDecimal chance;
        private final Integer duration;
        private final String configJson;

        public EffectResponse(
                Integer effectOrder,
                String effectType,
                String targetType,
                BigDecimal multiplier,
                BigDecimal chance,
                Integer duration,
                String configJson
        ) {
            this.effectOrder = effectOrder;
            this.effectType = effectType;
            this.targetType = targetType;
            this.multiplier = multiplier;
            this.chance = chance;
            this.duration = duration;
            this.configJson = configJson;
        }

        public Integer getEffectOrder() { return effectOrder; }
        public String getEffectType() { return effectType; }
        public String getTargetType() { return targetType; }
        public BigDecimal getMultiplier() { return multiplier; }
        public BigDecimal getChance() { return chance; }
        public Integer getDuration() { return duration; }
        public String getConfigJson() { return configJson; }
    }
}
