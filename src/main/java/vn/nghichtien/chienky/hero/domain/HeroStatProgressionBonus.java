package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "hero_stat_progression_bonuses")
public class HeroStatProgressionBonus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hero_code", nullable = false, length = 50)
    private String heroCode;

    @Column(name = "progression_type", nullable = false, length = 20)
    private String progressionType;

    @Column(name = "target_level", nullable = false)
    private Integer targetLevel;

    @Column(name = "stat_code", nullable = false, length = 30)
    private String statCode;

    @Column(name = "bonus_value", nullable = false, precision = 24, scale = 6)
    private BigDecimal bonusValue;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected HeroStatProgressionBonus() {
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

    public String getHeroCode() {
        return heroCode;
    }

    public String getProgressionType() {
        return progressionType;
    }

    public Integer getTargetLevel() {
        return targetLevel;
    }

    public String getStatCode() {
        return statCode;
    }

    public BigDecimal getBonusValue() {
        return bonusValue;
    }

    public Boolean getEnabled() {
        return enabled;
    }
}