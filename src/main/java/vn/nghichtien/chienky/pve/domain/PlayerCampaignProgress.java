package vn.nghichtien.chienky.pve.domain;

import jakarta.persistence.*;
import vn.nghichtien.chienky.player.domain.PlayerProfile;

import java.time.Instant;

/**
 * Lưu dấu mốc vượt ải của người chơi. Mỗi ải chỉ được ghi nhận một lần.
 */
@Entity
@Table(
        name = "player_campaign_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_player_campaign_stage",
                columnNames = {"player_id", "stage_number"}
        ),
        indexes = @Index(
                name = "idx_campaign_player_stage",
                columnList = "player_id, stage_number"
        )
)
public class PlayerCampaignProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerProfile player;

    @Column(name = "stage_number", nullable = false)
    private Integer stageNumber;

    @Column(name = "cleared_at", nullable = false, updatable = false)
    private Instant clearedAt;

    protected PlayerCampaignProgress() {
    }

    public PlayerCampaignProgress(PlayerProfile player, Integer stageNumber) {
        if (player == null || stageNumber == null || stageNumber < 1) {
            throw new IllegalArgumentException("Campaign progress is invalid");
        }
        this.player = player;
        this.stageNumber = stageNumber;
    }

    @PrePersist
    protected void onCreate() {
        this.clearedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public PlayerProfile getPlayer() {
        return player;
    }

    public Integer getStageNumber() {
        return stageNumber;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }
}
