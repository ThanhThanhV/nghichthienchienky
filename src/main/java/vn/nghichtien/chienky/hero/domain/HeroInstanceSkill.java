package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Lưu cấp độ kỹ năng riêng của từng bản sao nhân vật.
 */
@Entity
@Table(
        name = "hero_instance_skills",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hero_instance_skill",
                columnNames = {"hero_instance_id", "skill_id"}
        )
)
public class HeroInstanceSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hero_instance_id", nullable = false)
    private HeroInstance heroInstance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private HeroSkillDefinition skillDefinition;

    @Column(name = "skill_level", nullable = false)
    private Integer skillLevel = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Constructor mặc định phục vụ JPA.
     */
    protected HeroInstanceSkill() {
    }

    /**
     * Khởi tạo kỹ năng cho một bản sao nhân vật.
     *
     * @param heroInstance Bản sao nhân vật
     * @param skillDefinition Định nghĩa kỹ năng
     */
    public HeroInstanceSkill(
            HeroInstance heroInstance,
            HeroSkillDefinition skillDefinition
    ) {
        this.heroInstance = heroInstance;
        this.skillDefinition = skillDefinition;
        this.skillLevel = 1;
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
     * Nâng cấp cấp độ kỹ năng lên một cấp.
     */
    public void upgrade() {
        this.skillLevel = Math.addExact(this.skillLevel, 1);
    }

    /**
     * Thiết lập cấp độ kỹ năng.
     *
     * @param skillLevel Cấp độ mới
     */
    public void changeLevel(Integer skillLevel) {
        if (skillLevel == null || skillLevel < 1) {
            throw new IllegalArgumentException(
                    "Cấp độ kỹ năng không hợp lệ"
            );
        }

        this.skillLevel = skillLevel;
    }

    /**
     * Lấy ID bản ghi kỹ năng.
     *
     * @return ID
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy bản sao nhân vật sở hữu kỹ năng.
     *
     * @return Bản sao nhân vật
     */
    public HeroInstance getHeroInstance() {
        return heroInstance;
    }

    /**
     * Lấy định nghĩa kỹ năng.
     *
     * @return Định nghĩa kỹ năng
     */
    public HeroSkillDefinition getSkillDefinition() {
        return skillDefinition;
    }

    /**
     * Lấy cấp độ kỹ năng.
     *
     * @return Cấp độ kỹ năng
     */
    public Integer getSkillLevel() {
        return skillLevel;
    }

    /**
     * Lấy thời điểm tạo bản ghi.
     *
     * @return Thời điểm tạo
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Lấy thời điểm cập nhật bản ghi.
     *
     * @return Thời điểm cập nhật
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}