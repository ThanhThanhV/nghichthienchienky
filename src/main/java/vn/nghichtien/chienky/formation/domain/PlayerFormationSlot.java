
package vn.nghichtien.chienky.formation.domain;

import jakarta.persistence.*;
import vn.nghichtien.chienky.hero.domain.HeroInstance;
import vn.nghichtien.chienky.player.domain.PlayerProfile;

import java.time.LocalDateTime;

/**
 * Lưu vị trí của một nhân vật trong đội hình người chơi.
 */
@Entity
@Table(
        name = "player_formation_slots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_formation_player_position",
                        columnNames = {"player_id", "row_position", "column_position"}
                ),
                @UniqueConstraint(
                        name = "uk_formation_hero_instance",
                        columnNames = {"player_id", "hero_instance_id"}
                )
        }
)
public class PlayerFormationSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerProfile playerProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hero_instance_id", nullable = false)
    private HeroInstance heroInstance;

    @Column(name = "row_position", nullable = false)
    private Integer row;

    @Column(name = "column_position", nullable = false)
    private Integer column;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PlayerFormationSlot() {
    }

    public PlayerFormationSlot(
            PlayerProfile playerProfile,
            HeroInstance heroInstance,
            Integer row,
            Integer column
    ) {
        this.playerProfile = playerProfile;
        this.heroInstance = heroInstance;
        changePosition(row, column);
    }

    /**
     * Thay đổi vị trí nhân vật trong đội hình.
     *
     * @param row Hàng từ 0 đến 2
     * @param column Cột từ 0 đến 2
     */
    public void changePosition(Integer row, Integer column) {
        if (row == null || column == null
                || row < 0 || row > 2
                || column < 0 || column > 2) {
            throw new IllegalArgumentException(
                    "Formation position must be within 3x3"
            );
        }

        this.row = row;
        this.column = column;
    }

    @PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public PlayerProfile getPlayerProfile() {
        return playerProfile;
    }

    public HeroInstance getHeroInstance() {
        return heroInstance;
    }

    public Integer getRow() {
        return row;
    }

    public Integer getColumn() {
        return column;
    }
}
