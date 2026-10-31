package vn.nghichtien.chienky.equipment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "equipment_base_stats")
public class EquipmentBaseStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_code", nullable = false)
    private EquipmentDefinition equipmentDefinition;

    @Column(name = "stat_code", nullable = false, length = 30)
    private String statCode;

    @Column(name = "stat_value", nullable = false, precision = 24, scale = 6)
    private BigDecimal statValue;

    protected EquipmentBaseStat() {
    }

    public String getStatCode() {
        return statCode;
    }

    public BigDecimal getStatValue() {
        return statValue;
    }

    /**
     * Lấy định nghĩa trang bị chứa chỉ số này.
     *
     * @return định nghĩa trang bị
     */
    public EquipmentDefinition getEquipmentDefinition() {
        return equipmentDefinition;
    }
}