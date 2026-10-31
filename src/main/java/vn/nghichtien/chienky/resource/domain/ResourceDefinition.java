
package vn.nghichtien.chienky.resource.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity định nghĩa các loại tài nguyên có trong game.
 * Đây là cấu hình dùng chung, không lưu số dư của từng người chơi.
 */
@Entity
@Table(name = "resource_definitions")
public class ResourceDefinition {

    @Id
    @Column(name = "resource_code", nullable = false, length = 50)
    private String resourceCode;

    @Column(name = "resource_name", nullable = false, length = 100)
    private String resourceName;

    @Column(name = "resource_type", nullable = false, length = 30)
    private String resourceType;

    @Column(name = "initial_balance", nullable = false, precision = 24, scale = 6)
    private BigDecimal initialBalance = BigDecimal.ZERO;

    @Column(name = "max_balance", precision = 24, scale = 6)
    private BigDecimal maxBalance;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ResourceDefinition() {
    }

    /**
     * Khởi tạo cấu hình một loại tài nguyên.
     *
     * @param resourceCode mã tài nguyên
     * @param resourceName tên tài nguyên
     * @param resourceType nhóm tài nguyên
     * @param initialBalance số dư khởi tạo
     * @param maxBalance giới hạn số dư, null nếu không giới hạn
     */
    public ResourceDefinition(
            String resourceCode,
            String resourceName,
            String resourceType,
            BigDecimal initialBalance,
            BigDecimal maxBalance
    ) {
        this.resourceCode = resourceCode;
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        this.initialBalance = initialBalance;
        this.maxBalance = maxBalance;
        this.enabled = true;
    }

    /**
     * Thiết lập thời gian tạo cấu hình tài nguyên.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    /** Lấy mã tài nguyên. */
    public String getResourceCode() {
        return resourceCode;
    }

    /** Lấy tên tài nguyên. */
    public String getResourceName() {
        return resourceName;
    }

    /** Lấy nhóm tài nguyên. */
    public String getResourceType() {
        return resourceType;
    }

    /** Lấy số dư khởi tạo cho người chơi mới. */
    public BigDecimal getInitialBalance() {
        return initialBalance;
    }

    /** Lấy giới hạn số dư tối đa. */
    public BigDecimal getMaxBalance() {
        return maxBalance;
    }

    /** Kiểm tra tài nguyên có đang được kích hoạt không. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Lấy thời điểm tạo cấu hình. */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
