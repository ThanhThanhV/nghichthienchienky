package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hero_skill_upgrade_configs")
public class HeroSkillUpgradeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private HeroSkillDefinition skillDefinition;

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

    public HeroSkillDefinition getSkillDefinition() {
        return skillDefinition;
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