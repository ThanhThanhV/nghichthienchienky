package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Định nghĩa một kỹ năng thuộc nhân vật.
 * Mỗi nhân vật có tối đa 5 kỹ năng theo thứ tự đã quy định.
 */
@Entity
@Table(
        name = "hero_skill_definitions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_hero_skill_code",
                        columnNames = {"hero_code", "skill_code"}
                ),
                @UniqueConstraint(
                        name = "uk_hero_skill_order",
                        columnNames = {"hero_code", "skill_order"}
                )
        }
)
public class HeroSkillDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hero_code", nullable = false)
    private HeroDefinition heroDefinition;

    @Column(name = "skill_code", length = 50, nullable = false)
    private String skillCode;

    @Column(name = "skill_name", length = 100, nullable = false)
    private String skillName;

    @Column(name = "skill_type", length = 20, nullable = false)
    private String skillType;

    @Column(name = "skill_order", nullable = false)
    private Integer skillOrder;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_code", length = 50)
    private String iconCode;

    @Column(name = "level_rows_json", columnDefinition = "LONGTEXT")
    private String levelRowsJson;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "skillDefinition",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("effectOrder ASC")
    private List<HeroSkillEffect> effects = new ArrayList<>();

    /**
     * Constructor mặc định phục vụ JPA.
     */
    protected HeroSkillDefinition() {
    }

    /**
     * Khởi tạo định nghĩa kỹ năng cho nhân vật.
     *
     * @param heroDefinition Nhân vật sở hữu kỹ năng
     * @param skillCode Mã kỹ năng
     * @param skillName Tên kỹ năng
     * @param skillType Loại kỹ năng BASIC, ACTIVE, ULTIMATE hoặc PASSIVE
     * @param skillOrder Thứ tự kỹ năng từ 1 đến 5
     */
    public HeroSkillDefinition(
            HeroDefinition heroDefinition,
            String skillCode,
            String skillName,
            String skillType,
            Integer skillOrder
    ) {
        this.heroDefinition = heroDefinition;
        this.skillCode = skillCode;
        this.skillName = skillName;
        this.skillType = skillType;
        this.skillOrder = skillOrder;
        this.enabled = true;
    }

    /**
     * Khởi tạo thời gian tạo và cập nhật trước khi lưu.
     */
    @jakarta.persistence.PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Cập nhật thời gian chỉnh sửa trước khi cập nhật.
     */
    @jakarta.persistence.PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Cập nhật thông tin hiển thị của kỹ năng.
     *
     * @param skillName Tên kỹ năng
     * @param description Mô tả kỹ năng
     * @param iconCode Mã biểu tượng
     */
    public void updateInformation(
            String skillName,
            String description,
            String iconCode
    ) {
        this.skillName = skillName;
        this.description = description;
        this.iconCode = iconCode;
    }

    /**
     * Cập nhật nhóm kỹ năng BASIC, ACTIVE, ULTIMATE hoặc PASSIVE.
     *
     * @param skillType Loại kỹ năng mới
     */
    public void changeSkillType(String skillType) {
        if (skillType == null || skillType.isBlank()) {
            throw new IllegalArgumentException("Skill type không được để trống");
        }
        this.skillType = skillType.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Đổi mã định danh kỹ năng trong migration có chủ đích.
     * Giữ nguyên ID entity để không làm đứt liên kết cấp kỹ năng của người chơi.
     *
     * @param skillCode Mã kỹ năng mới
     */
    public void changeSkillCode(String skillCode) {
        if (skillCode == null || skillCode.isBlank()) {
            throw new IllegalArgumentException("Skill code không được để trống");
        }
        this.skillCode = skillCode.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Lưu toàn bộ levelRows TAB để runtime chọn đúng hiệu ứng theo cấp kỹ năng.
     *
     * @param levelRowsJson JSON array chứa dữ liệu các cấp kỹ năng
     */
    public void updateLevelRowsJson(String levelRowsJson) {
        this.levelRowsJson = levelRowsJson;
    }

    /**
     * Lấy dữ liệu các cấp kỹ năng đã đồng bộ từ TAB.
     *
     * @return JSON array hoặc null nếu chưa có dữ liệu
     */
    public String getLevelRowsJson() {
        return levelRowsJson;
    }

    /**
     * Thay đổi trạng thái kích hoạt kỹ năng.
     *
     * @param enabled Trạng thái kích hoạt
     */
    public void changeEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Thêm hiệu ứng vào kỹ năng và thiết lập quan hệ hai chiều.
     *
     * @param effect Hiệu ứng cần thêm
     */
    public void addEffect(HeroSkillEffect effect) {
        if (effect == null) {
            throw new IllegalArgumentException("Hiệu ứng kỹ năng không được null");
        }

        effect.setSkillDefinition(this);
        this.effects.add(effect);
    }

    /**
     * Xóa hiệu ứng khỏi kỹ năng.
     *
     * @param effect Hiệu ứng cần xóa
     */
    public void removeEffect(HeroSkillEffect effect) {
        if (effect != null && this.effects.remove(effect)) {
            effect.setSkillDefinition(null);
        }
    }

    /**
     * Đồng bộ hiệu ứng theo catalog nguồn mà không thay đổi ID của các hiệu ứng
     * đã tồn tại ở cùng effectOrder.
     *
     * Cập nhật entity hiện có thay vì orphan-delete rồi INSERT entity mới.
     * Điều này tránh xung đột unique key
     * (hero_code, skill_code, effect_order) khi Hibernate
     * thực hiện flush theo thứ tự INSERT trước DELETE.
     *
     * @param replacementEffects Danh sách hiệu ứng theo catalog mới
     */
    public void replaceEffects(List<HeroSkillEffect> replacementEffects) {
        List<HeroSkillEffect> currentEffects = new ArrayList<>(this.effects);
        List<HeroSkillEffect> retainedEffects = new ArrayList<>();

        if (replacementEffects != null) {
            for (HeroSkillEffect replacement : replacementEffects) {
                if (replacement == null) {
                    throw new IllegalArgumentException(
                            "Hiệu ứng kỹ năng không được null"
                    );
                }

                HeroSkillEffect existing = currentEffects.stream()
                        .filter(effect -> effect.getEffectOrder() != null
                                && effect.getEffectOrder().equals(
                                        replacement.getEffectOrder()
                                ))
                        .findFirst()
                        .orElse(null);

                if (existing == null) {
                    addEffect(replacement);
                    retainedEffects.add(replacement);
                    continue;
                }

                existing.updateDefinition(
                        replacement.getEffectOrder(),
                        replacement.getEffectType(),
                        replacement.getTargetType()
                );
                existing.updateParameters(
                        replacement.getMultiplier(),
                        replacement.getChance(),
                        replacement.getDuration()
                );
                existing.updateConfig(replacement.getConfigJson());
                existing.changeEnabled(replacement.getEnabled());
                retainedEffects.add(existing);
            }
        }

        for (HeroSkillEffect existing : currentEffects) {
            if (!retainedEffects.contains(existing)) {
                removeEffect(existing);
            }
        }
    }

    /**
     * Lấy khóa tự tăng nội bộ của kỹ năng (không dùng để mapping nghiệp vụ).
     *
     * @return ID nội bộ JPA
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy định nghĩa nhân vật sở hữu kỹ năng.
     *
     * @return Định nghĩa nhân vật
     */
    public HeroDefinition getHeroDefinition() {
        return heroDefinition;
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
     * @return BASIC, ACTIVE, ULTIMATE hoặc PASSIVE
     */
    public String getSkillType() {
        return skillType;
    }

    /**
     * Lấy thứ tự kỹ năng.
     *
     * @return Thứ tự từ 1 đến 5
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
     * Lấy mã biểu tượng kỹ năng.
     *
     * @return Mã biểu tượng
     */
    public String getIconCode() {
        return iconCode;
    }

    /**
     * Kiểm tra kỹ năng có đang được kích hoạt không.
     *
     * @return Trạng thái kích hoạt
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Lấy danh sách hiệu ứng theo thứ tự thực hiện.
     *
     * @return Danh sách hiệu ứng
     */
    public List<HeroSkillEffect> getEffects() {
        return effects;
    }
}