package vn.nghichtien.chienky.formation.dto;

/**
 * Vị trí một nhân vật đang được xếp trong đội hình.
 */
public record FormationSlotResponse(
        Long instanceId,
        Integer row,
        Integer column
) {
}