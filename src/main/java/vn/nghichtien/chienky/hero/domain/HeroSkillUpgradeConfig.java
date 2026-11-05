package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình nâng cấp kỹ năng được định danh bằng heroCode + skillCode.
 * Không phụ thuộc ID tự tăng của hero_skill_definitions.
 */
@Entity
@Table(
        name = "hero_skill_upgrade_configs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hero_skill_upgrade_code_level",
                columnNames = {"hero_code", "skill_code", "target_skill_level"}
        )
)
public class HeroSkillUpgradeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hero_code", nullable = false, length = 50)
    private String heroCode;

    @Column(name = "skill_code", nullable = false, length = 50)
    private String skillCode;

    @Column(name = "target_skill_level", nullable = false)
    private Integer targetSkillLevel;

    @Column(name = "required_hero_level", nullable = false)
    private Integer requiredHeroLevel = 1;

    @Column(nullable = false)
    private Boolean enabled = true;

    @OneToMany(mappedBy = "upgradeConfig", fetch = FetchType.LAZY)
    private List<HeroSkillUpgradeCost> costs = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected HeroSkillUpgradeConfig() {
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getHeroCode() {
        return heroCode;
    }

    public String getSkillCode() {
        return skillCode;
    }

    public Integer getTargetSkillLevel() {
        return targetSkillLevel;
    }

    public Integer getRequiredHeroLevel() {
        return requiredHeroLevel;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public List<HeroSkillUpgradeCost> getCosts() {
        return costs;
    }
}
