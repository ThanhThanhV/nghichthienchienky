package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Định nghĩa một hiệu ứng thành phần của kỹ năng.
 * Một kỹ năng có thể chứa nhiều hiệu ứng được thực hiện tuần tự.
 */
@Entity
@Table(name = "hero_skill_effects")
public class HeroSkillEffect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hero_code", length = 50, nullable = false)
    private String heroCode;

    @Column(name = "skill_code", length = 50, nullable = false)
    private String skillCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns(
            value = {
            @JoinColumn(
                    name = "hero_code",
                    referencedColumnName = "hero_code",
                    insertable = false,
                    updatable = false
            ),
            @JoinColumn(
                    name = "skill_code",
                    referencedColumnName = "skill_code",
                    insertable = false,
                    updatable = false
            )
            },
            foreignKey = @ForeignKey(name = "fk_hero_skill_effect_skill_code")
    )
    private HeroSkillDefinition skillDefinition;

    @Column(name = "effect_order", nullable = false)
    private Integer effectOrder;

    @Column(name = "effect_type", length = 30, nullable = false)
    private String effectType;

    @Column(name = "target_type", length = 50, nullable = false)
    private String targetType;

    @Column(name = "multiplier", precision = 24, scale = 6)
    private BigDecimal multiplier;

    @Column(name = "chance", precision = 10, scale = 6)
    private BigDecimal chance;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "config_json", columnDefinition = "LONGTEXT")
    private String configJson;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Constructor mặc định phục vụ JPA.
     */
    protected HeroSkillEffect() {
    }

    /**
     * Khởi tạo hiệu ứng kỹ năng.
     *
     * @param effectOrder Thứ tự thực hiện hiệu ứng
     * @param effectType Loại hiệu ứng
     * @param targetType Quy tắc lựa chọn mục tiêu
     */
    public HeroSkillEffect(
            Integer effectOrder,
            String effectType,
            String targetType
    ) {
        this.effectOrder = effectOrder;
        this.effectType = effectType;
        this.targetType = targetType;
        this.enabled = true;
    }

    /**
     * Gán kỹ năng sở hữu hiệu ứng.
     *
     * @param skillDefinition Định nghĩa kỹ năng
     */
    void setSkillDefinition(HeroSkillDefinition skillDefinition) {
        this.skillDefinition = skillDefinition;
        this.heroCode = skillDefinition == null
                ? null : skillDefinition.getHeroDefinition().getHeroCode();
        this.skillCode = skillDefinition == null
                ? null : skillDefinition.getSkillCode();
    }

    /**
     * Cập nhật định nghĩa và mục tiêu của hiệu ứng.
     */
    public void updateDefinition(
            Integer effectOrder,
            String effectType,
            String targetType
    ) {
        this.effectOrder = effectOrder;
        this.effectType = effectType;
        this.targetType = targetType;
    }

    /**
     * Cập nhật cấu hình tính toán của hiệu ứng.
     *
     * @param multiplier Hệ số tác động
     * @param chance Xác suất áp dụng, từ 0 đến 1
     * @param duration Thời gian tồn tại
     */
    public void updateParameters(
            BigDecimal multiplier,
            BigDecimal chance,
            Integer duration
    ) {
        this.multiplier = multiplier;
        this.chance = chance;
        this.duration = duration;
    }

    /**
     * Cập nhật cấu hình mở rộng của hiệu ứng.
     *
     * @param configJson Nội dung cấu hình JSON
     */
    public void updateConfig(String configJson) {
        this.configJson = configJson;
    }

    /**
     * Thay đổi trạng thái kích hoạt hiệu ứng.
     *
     * @param enabled Trạng thái kích hoạt
     */
    public void changeEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Khởi tạo thời gian tạo trước khi lưu.
     */
    @jakarta.persistence.PrePersist
    protected void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Lấy ID hiệu ứng.
     *
     * @return ID hiệu ứng
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy kỹ năng sở hữu hiệu ứng.
     *
     * @return Định nghĩa kỹ năng
     */
    public HeroSkillDefinition getSkillDefinition() {
        return skillDefinition;
    }

    /**
     * Lấy thứ tự thực hiện.
     *
     * @return Thứ tự hiệu ứng
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
     * Lấy quy tắc lựa chọn mục tiêu.
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
     * @return Xác suất từ 0 đến 1
     */
    public BigDecimal getChance() {
        return chance;
    }

    /**
     * Lấy thời gian tồn tại của hiệu ứng.
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

    /**
     * Kiểm tra hiệu ứng có đang được kích hoạt không.
     *
     * @return Trạng thái kích hoạt
     */
    public Boolean getEnabled() {
        return enabled;
    }
}