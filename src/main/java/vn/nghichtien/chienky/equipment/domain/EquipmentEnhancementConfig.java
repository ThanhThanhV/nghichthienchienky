package vn.nghichtien.chienky.equipment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "equipment_enhancement_configs")
public class EquipmentEnhancementConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_level", nullable = false, unique = true)
    private Integer targetLevel;

    @Column(
            name = "stat_bonus_percent",
            nullable = false,
            precision = 10,
            scale = 6
    )
    private BigDecimal statBonusPercent;

    @Column(nullable = false)
    private Boolean enabled = true;

    @OneToMany(mappedBy = "enhancementConfig", fetch = FetchType.LAZY)
    private List<EquipmentEnhancementCost> costs = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EquipmentEnhancementConfig() {
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

    public Integer getTargetLevel() {
        return targetLevel;
    }

    public BigDecimal getStatBonusPercent() {
        return statBonusPercent;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public List<EquipmentEnhancementCost> getCosts() {
        return costs;
    }
}