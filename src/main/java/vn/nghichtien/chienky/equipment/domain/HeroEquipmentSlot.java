package vn.nghichtien.chienky.equipment.domain;

import vn.nghichtien.chienky.hero.domain.HeroInstance;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "hero_equipment_slots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_hero_equipment_slot_type",
                        columnNames = {"hero_instance_id", "equipment_type"}
                ),
                @UniqueConstraint(
                        name = "uk_hero_equipment_item",
                        columnNames = {"player_equipment_id"}
                )
        }
)
public class HeroEquipmentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hero_instance_id", nullable = false)
    private HeroInstance heroInstance;

    @Column(name = "equipment_type", nullable = false, length = 30)
    private String equipmentType;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_equipment_id", nullable = false)
    private PlayerEquipment playerEquipment;

    @Column(name = "equipped_at", nullable = false, updatable = false)
    private LocalDateTime equippedAt;

    protected HeroEquipmentSlot() {
    }

    public HeroEquipmentSlot(
            HeroInstance heroInstance,
            String equipmentType,
            PlayerEquipment playerEquipment
    ) {
        this.heroInstance = heroInstance;
        this.equipmentType = equipmentType;
        this.playerEquipment = playerEquipment;
    }

    @PrePersist
    protected void onCreate() {
        equippedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public HeroInstance getHeroInstance() {
        return heroInstance;
    }

    public String getEquipmentType() {
        return equipmentType;
    }

    public PlayerEquipment getPlayerEquipment() {
        return playerEquipment;
    }
}