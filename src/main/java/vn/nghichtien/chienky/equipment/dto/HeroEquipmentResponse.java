package vn.nghichtien.chienky.equipment.dto;

import java.util.List;

public class HeroEquipmentResponse {

    private final Long heroInstanceId;
    private final List<EquipmentResponse> equipment;

    public HeroEquipmentResponse(
            Long heroInstanceId,
            List<EquipmentResponse> equipment
    ) {
        this.heroInstanceId = heroInstanceId;
        this.equipment = List.copyOf(equipment);
    }

    public Long getHeroInstanceId() {
        return heroInstanceId;
    }

    public List<EquipmentResponse> getEquipment() {
        return equipment;
    }
}