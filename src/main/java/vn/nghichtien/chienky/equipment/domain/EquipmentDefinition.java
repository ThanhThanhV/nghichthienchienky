package vn.nghichtien.chienky.equipment.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipment_definitions")
public class EquipmentDefinition {

    @Id
    @Column(name = "equipment_code", length = 50)
    private String equipmentCode;

    @Column(name = "equipment_name", nullable = false, length = 100)
    private String equipmentName;

    @Column(length = 1000)
    private String description;

    @Column(name = "equipment_type", nullable = false, length = 30)
    private String equipmentType;

    @Column(nullable = false, length = 20)
    private String rarity;

    @Column(name = "icon_code", length = 50)
    private String iconCode;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EquipmentDefinition() {
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

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public String getDescription() {
        return description;
    }

    public String getEquipmentType() {
        return equipmentType;
    }

    public String getRarity() {
        return rarity;
    }

    public String getIconCode() {
        return iconCode;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }
}