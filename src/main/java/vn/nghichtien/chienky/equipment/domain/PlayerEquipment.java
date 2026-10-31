package vn.nghichtien.chienky.equipment.domain;

import vn.nghichtien.chienky.player.domain.PlayerProfile;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "player_equipment")
public class PlayerEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerProfile playerProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_code", nullable = false)
    private EquipmentDefinition equipmentDefinition;

    @Column(name = "enhancement_level", nullable = false)
    private Integer enhancementLevel = 0;

    @Column(name = "obtained_at", nullable = false, updatable = false)
    private LocalDateTime obtainedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PlayerEquipment() {
    }

    public PlayerEquipment(
            PlayerProfile playerProfile,
            EquipmentDefinition equipmentDefinition
    ) {
        this.playerProfile = playerProfile;
        this.equipmentDefinition = equipmentDefinition;
        this.enhancementLevel = 0;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        obtainedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public PlayerProfile getPlayerProfile() {
        return playerProfile;
    }

    public EquipmentDefinition getEquipmentDefinition() {
        return equipmentDefinition;
    }

    public Integer getEnhancementLevel() {
        return enhancementLevel;
    }

    /**
     * Cập nhật cấp cường hóa trang bị sau khi đã kiểm tra chi phí và điều kiện.
     *
     * @param targetLevel cấp cường hóa mới
     */
    public void enhanceTo(int targetLevel) {
        if (targetLevel != this.enhancementLevel + 1) {
            throw new IllegalArgumentException(
                    "Equipment must be enhanced one level at a time."
            );
        }

        if (targetLevel < 1) {
            throw new IllegalArgumentException("Invalid enhancement level.");
        }

        this.enhancementLevel = targetLevel;
    }
}