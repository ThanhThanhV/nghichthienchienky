package vn.nghichtien.chienky.formation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Một nhân vật và vị trí tương ứng trong đội hình.
 */
public record FormationSlotRequest(
        @NotNull @Positive Long instanceId,
        @NotNull @Min(0) @Max(2) Integer row,
        @NotNull @Min(0) @Max(2) Integer column
) {
}