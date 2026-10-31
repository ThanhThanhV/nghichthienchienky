package vn.nghichtien.chienky.resource.domain;

import vn.nghichtien.chienky.player.domain.PlayerProfile;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "resource_transaction_headers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_resource_transaction_player_key",
                        columnNames = {"player_id", "transaction_key"}
                )
        }
)
public class ResourceTransactionHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerProfile player;

    @Column(name = "transaction_key", nullable = false, length = 100)
    private String transactionKey;

    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ResourceTransactionHeader() {
    }

    /**
     * Khởi tạo thông tin giao dịch tài nguyên của người chơi.
     *
     * @param player Người chơi thực hiện giao dịch
     * @param transactionKey Mã giao dịch duy nhất trên mỗi người chơi
     * @param sourceType Nguồn phát sinh giao dịch
     * @param sourceId ID đối tượng nguồn, có thể null
     * @param description Mô tả giao dịch
     */
    public ResourceTransactionHeader(
            PlayerProfile player,
            String transactionKey,
            String sourceType,
            Long sourceId,
            String description
    ) {
        this.player = player;
        this.transactionKey = transactionKey;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.description = description;
    }

    /**
     * Gán thời điểm tạo khi lưu giao dịch lần đầu.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * Lấy ID giao dịch.
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy người chơi sở hữu giao dịch.
     */
    public PlayerProfile getPlayer() {
        return player;
    }

    /**
     * Lấy mã giao dịch dùng để chống xử lý trùng lặp.
     */
    public String getTransactionKey() {
        return transactionKey;
    }

    /**
     * Lấy loại nguồn phát sinh giao dịch.
     */
    public String getSourceType() {
        return sourceType;
    }

    /**
     * Lấy ID đối tượng nguồn phát sinh giao dịch.
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * Lấy mô tả giao dịch.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Lấy thời điểm tạo giao dịch.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}