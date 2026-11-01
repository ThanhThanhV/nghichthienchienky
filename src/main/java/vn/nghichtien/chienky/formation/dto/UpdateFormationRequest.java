package vn.nghichtien.chienky.formation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Danh sách vị trí đội hình cần lưu.
 */
public record UpdateFormationRequest(
        @NotNull @Size(min = 1, max = 5)
        List<@Valid FormationSlotRequest> slots
) {
}