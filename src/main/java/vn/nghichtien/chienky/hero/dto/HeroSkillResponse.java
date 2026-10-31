package vn.nghichtien.chienky.hero.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO thông tin kỹ năng và các hiệu ứng thành phần.
 */
public class HeroSkillResponse {

    private final Long skillId;
    private final String skillCode;
    private final String skillName;
    private final String skillType;
    private final Integer skillOrder;
    private final String description;
    private final String iconCode;
    private final Integer skillLevel;
    private final List<EffectResponse> effects;

    /**
     * Khởi tạo thông tin kỹ năng.
     *
     * @param skillId ID kỹ năng
     * @param skillCode Mã kỹ năng
     * @param skillName Tên kỹ năng
     * @param skillType Loại kỹ năng
     * @param skillOrder Thứ tự kỹ năng
     * @param description Mô tả
     * @param iconCode Mã biểu tượng
     * @param skillLevel Cấp độ kỹ năng
     * @param effects Danh sách hiệu ứng
     */
    public HeroSkillResponse(
            Long skillId,
            String skillCode,
            String skillName,
            String skillType,
            Integer skillOrder,
            String description,
            String iconCode,
            Integer skillLevel,
            List<EffectResponse> effects
    ) {
        this.skillId = skillId;
        this.skillCode = skillCode;
        this.skillName = skillName;
        this.skillType = skillType;
        this.skillOrder = skillOrder;
        this.description = description;
        this.iconCode = iconCode;
        this.skillLevel = skillLevel;
        this.effects = List.copyOf(effects);
    }

    /**
     * Lấy ID kỹ năng.
     *
     * @return ID kỹ năng
     */
    public Long getSkillId() {
        return skillId;
    }

    /**
     * Lấy mã kỹ năng.
     *
     * @return Mã kỹ năng
     */
    public String getSkillCode() {
        return skillCode;
    }

    /**
     * Lấy tên kỹ năng.
     *
     * @return Tên kỹ năng
     */
    public String getSkillName() {
        return skillName;
    }

    /**
     * Lấy loại kỹ năng.
     *
     * @return Loại kỹ năng
     */
    public String getSkillType() {
        return skillType;
    }

    /**
     * Lấy thứ tự kỹ năng.
     *
     * @return Thứ tự kỹ năng
     */
    public Integer getSkillOrder() {
        return skillOrder;
    }

    /**
     * Lấy mô tả kỹ năng.
     *
     * @return Mô tả
     */
    public String getDescription() {
        return description;
    }

    /**
     * Lấy mã biểu tượng.
     *
     * @return Mã biểu tượng
     */
    public String getIconCode() {
        return iconCode;
    }

    /**
     * Lấy cấp độ kỹ năng của bản sao nhân vật.
     *
     * @return Cấp độ kỹ năng
     */
    public Integer getSkillLevel() {
        return skillLevel;
    }

    /**
     * Lấy danh sách hiệu ứng của kỹ năng.
     *
     * @return Danh sách hiệu ứng
     */
    public List<EffectResponse> getEffects() {
        return effects;
    }

    /**
     * DTO cấu hình một hiệu ứng kỹ năng.
     */
    public static class EffectResponse {

        private final Long effectId;
        private final Integer effectOrder;
        private final String effectType;
        private final String targetType;
        private final BigDecimal multiplier;
        private final BigDecimal chance;
        private final Integer duration;
        private final String configJson;

        /**
         * Khởi tạo thông tin hiệu ứng.
         *
         * @param effectId ID hiệu ứng
         * @param effectOrder Thứ tự thực hiện
         * @param effectType Loại hiệu ứng
         * @param targetType Quy tắc mục tiêu
         * @param multiplier Hệ số tác động
         * @param chance Xác suất áp dụng
         * @param duration Thời gian tồn tại
         * @param configJson Cấu hình mở rộng
         */
        public EffectResponse(
                Long effectId,
                Integer effectOrder,
                String effectType,
                String targetType,
                BigDecimal multiplier,
                BigDecimal chance,
                Integer duration,
                String configJson
        ) {
            this.effectId = effectId;
            this.effectOrder = effectOrder;
            this.effectType = effectType;
            this.targetType = targetType;
            this.multiplier = multiplier;
            this.chance = chance;
            this.duration = duration;
            this.configJson = configJson;
        }

        /**
         * Lấy ID hiệu ứng.
         *
         * @return ID hiệu ứng
         */
        public Long getEffectId() {
            return effectId;
        }

        /**
         * Lấy thứ tự hiệu ứng.
         *
         * @return Thứ tự
         */
        public Integer getEffectOrder() {
            return effectOrder;
        }

        /**
         * Lấy loại hiệu ứng.
         *
         * @return Loại hiệu ứng
         */
        public String getEffectType() {
            return effectType;
        }

        /**
         * Lấy loại mục tiêu.
         *
         * @return Loại mục tiêu
         */
        public String getTargetType() {
            return targetType;
        }

        /**
         * Lấy hệ số tác động.
         *
         * @return Hệ số
         */
        public BigDecimal getMultiplier() {
            return multiplier;
        }

        /**
         * Lấy xác suất áp dụng.
         *
         * @return Xác suất
         */
        public BigDecimal getChance() {
            return chance;
        }

        /**
         * Lấy thời gian tồn tại.
         *
         * @return Thời gian
         */
        public Integer getDuration() {
            return duration;
        }

        /**
         * Lấy cấu hình JSON mở rộng.
         *
         * @return Cấu hình JSON
         */
        public String getConfigJson() {
            return configJson;
        }
    }
}