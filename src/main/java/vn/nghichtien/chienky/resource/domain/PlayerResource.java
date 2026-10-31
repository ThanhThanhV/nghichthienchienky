
package vn.nghichtien.chienky.resource.domain;

import jakarta.persistence.*;
import vn.nghichtien.chienky.player.domain.PlayerProfile;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity lưu số dư của một loại tài nguyên thuộc một người chơi.
 * Mỗi người chơi chỉ có một bản ghi cho mỗi resourceCode.
 */
@Entity
@Table(
        name = "player_resources",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_player_resource",
                        columnNames = {"player_id", "resource_code"}
                )
        }
)
public class PlayerResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerProfile player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_code", nullable = false)
    private ResourceDefinition resourceDefinition;

    @Column(nullable = false, precision = 24, scale = 6)
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerResource() {
    }

    /**
     * Khởi tạo số dư ban đầu của một loại tài nguyên cho người chơi.
     *
     * @param player người chơi sở hữu tài nguyên
     * @param resourceDefinition cấu hình loại tài nguyên
     * @param initialBalance số dư khởi tạo
     */
    public PlayerResource(
            PlayerProfile player,
            ResourceDefinition resourceDefinition,
            BigDecimal initialBalance
    ) {
        this.player = player;
        this.resourceDefinition = resourceDefinition;
        this.balance = initialBalance == null
                ? BigDecimal.ZERO
                : initialBalance;
    }

    /**
     * Thiết lập thời gian tạo và cập nhật khi tạo bản ghi.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Cập nhật thời gian thay đổi số dư.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Thay đổi số dư sau khi ResourceTransactionService đã kiểm tra hợp lệ.
     *
     * @param newBalance số dư mới
     */
    public void updateBalance(BigDecimal newBalance) {
        if (newBalance == null || newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Resource balance cannot be negative");
        }
        this.balance = newBalance;
    }

    /** Lấy ID bản ghi số dư. */
    public Long getId() {
        return id;
    }

    /** Lấy người chơi sở hữu tài nguyên. */
    public PlayerProfile getPlayer() {
        return player;
    }

    /** Lấy cấu hình loại tài nguyên. */
    public ResourceDefinition getResourceDefinition() {
        return resourceDefinition;
    }

    /** Lấy mã tài nguyên. */
    public String getResourceCode() {
        return resourceDefinition.getResourceCode();
    }

    /** Lấy số dư hiện tại. */
    public BigDecimal getBalance() {
        return balance;
    }

    /** Lấy phiên bản dữ liệu phục vụ kiểm soát cập nhật đồng thời. */
    public Long getVersion() {
        return version;
    }

    /** Lấy thời điểm tạo bản ghi. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Lấy thời điểm cập nhật số dư gần nhất. */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
