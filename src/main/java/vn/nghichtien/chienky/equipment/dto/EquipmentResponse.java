package vn.nghichtien.chienky.equipment.dto;

import java.math.BigDecimal;
import java.util.Map;

public class EquipmentResponse {

    private final Long equipmentId;
    private final String equipmentCode;
    private final String equipmentName;
    private final String equipmentType;
    private final String rarity;
    private final String iconCode;
    private final Integer enhancementLevel;
    private final Map<String, BigDecimal> stats;
    private final String equippedSlot;

    public EquipmentResponse(
            Long equipmentId,
            String equipmentCode,
            String equipmentName,
            String equipmentType,
            String rarity,
            String iconCode,
            Integer enhancementLevel,
            Map<String, BigDecimal> stats,
            String equippedSlot
    ) {
        this.equipmentId = equipmentId;
        this.equipmentCode = equipmentCode;
        this.equipmentName = equipmentName;
        this.equipmentType = equipmentType;
        this.rarity = rarity;
        this.iconCode = iconCode;
        this.enhancementLevel = enhancementLevel;
        this.stats = Map.copyOf(stats);
        this.equippedSlot = equippedSlot;
    }

    public Long getEquipmentId() {
        return equipmentId;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public String getEquipmentName() {
        return equipmentName;
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

    public Integer getEnhancementLevel() {
        return enhancementLevel;
    }

    public Map<String, BigDecimal> getStats() {
        return stats;
    }

    public String getEquippedSlot() {
        return equippedSlot;
    }
}