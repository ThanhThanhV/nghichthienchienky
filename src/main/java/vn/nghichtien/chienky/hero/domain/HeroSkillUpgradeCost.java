package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "hero_skill_upgrade_costs")
public class HeroSkillUpgradeCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upgrade_config_id", nullable = false)
    private HeroSkillUpgradeConfig upgradeConfig;

    @Column(name = "resource_code", nullable = false, length = 50)
    private String resourceCode;

    @Column(name = "required_amount", nullable = false, precision = 24, scale = 6)
    private BigDecimal requiredAmount;

    protected HeroSkillUpgradeCost() {
    }

    public Long getId() {
        return id;
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }
}