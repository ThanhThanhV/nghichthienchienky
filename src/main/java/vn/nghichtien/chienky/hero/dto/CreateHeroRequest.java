package vn.nghichtien.chienky.hero.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dữ liệu tạo mới một định nghĩa tướng từ màn hình quản lý.
 */
public record CreateHeroRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[a-zA-Z0-9_-]+")
        String heroCode,

        @NotBlank @Size(max = 100)
        String heroName,

        @NotBlank @Size(max = 20)
        String rarity,

        @NotBlank @Size(max = 30)
        String role,

        @NotBlank @Size(max = 50)
        String faction,

        @NotBlank @Size(max = 50)
        String avatarCode,

        @NotBlank @Size(max = 2000)
        String description,

        @NotNull @Valid
        BaseStatsRequest baseStats,

        @NotNull @Valid
        GrowthStatsRequest growthStats,

        @NotNull @Size(min = 5, max = 5)
        List<@NotNull @Valid SkillRequest> skills
) {
    public record BaseStatsRequest(
            @NotNull @DecimalMin("0.000001") BigDecimal hp,
            @NotNull @DecimalMin("0.000001") BigDecimal atk,
            @NotNull @DecimalMin("0.000001") BigDecimal def,
            @NotNull @DecimalMin("0.000001") BigDecimal speed,
            @NotNull @DecimalMin("0") BigDecimal crit,
            @NotNull @DecimalMin("0") BigDecimal critDamage,
            @NotNull @DecimalMin("0") BigDecimal accuracy,
            @NotNull @DecimalMin("0") BigDecimal resistance,
            @NotNull @DecimalMin("0") BigDecimal penFlat,
            @NotNull @DecimalMin("0") BigDecimal penPercent,
            @NotNull @DecimalMin("0") BigDecimal lifesteal
    ) {
    }

    public record GrowthStatsRequest(
            @NotNull @DecimalMin("0") BigDecimal hp,
            @NotNull @DecimalMin("0") BigDecimal atk,
            @NotNull @DecimalMin("0") BigDecimal def,
            @NotNull @DecimalMin("0") BigDecimal speed,
            @NotNull @DecimalMin("0") BigDecimal crit,
            @NotNull @DecimalMin("0") BigDecimal critDamage,
            @NotNull @DecimalMin("0") BigDecimal accuracy,
            @NotNull @DecimalMin("0") BigDecimal resistance,
            @NotNull @DecimalMin("0") BigDecimal penFlat,
            @NotNull @DecimalMin("0") BigDecimal penPercent,
            @NotNull @DecimalMin("0") BigDecimal lifesteal
    ) {
    }

    public record SkillRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 100) String animation,
            @NotBlank @Pattern(regexp = "DAMAGE|HEAL|DOT|HOT") String effectType,
            @NotBlank @Size(max = 50) String targetType,
            @NotNull @DecimalMin("0.000001") BigDecimal multiplier,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal chance,
            @NotNull @Min(0) Integer duration,
            @NotNull @Min(1) Integer animationDurationMs,
            @NotNull @Min(0) Integer impactTimeMs
    ) {
    }
}
