package vn.nghichtien.chienky.equipment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "equipment_enhancement_costs")
public class EquipmentEnhancementCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enhancement_config_id", nullable = false)
    private EquipmentEnhancementConfig enhancementConfig;

    @Column(name = "resource_code", nullable = false, length = 50)
    private String resourceCode;

    @Column(name = "required_amount", nullable = false, precision = 24, scale = 6)
    private BigDecimal requiredAmount;

    protected EquipmentEnhancementCost() {
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }
}