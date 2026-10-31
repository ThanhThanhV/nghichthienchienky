package vn.nghichtien.chienky.resource.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "resource_transaction_details",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_resource_transaction_detail",
                        columnNames = {"transaction_id", "resource_code"}
                )
        }
)
public class ResourceTransactionDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private ResourceTransactionHeader transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_code", nullable = false)
    private ResourceDefinition resourceDefinition;

    @Column(name = "amount", nullable = false, precision = 24, scale = 6)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 24, scale = 6)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 24, scale = 6)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ResourceTransactionDetail() {
    }

    /**
     * Khởi tạo chi tiết biến động của một loại tài nguyên.
     *
     * @param transaction Giao dịch tài nguyên cha
     * @param resourceDefinition Định nghĩa loại tài nguyên
     * @param amount Số lượng thay đổi, dương là cộng và âm là trừ
     * @param balanceBefore Số dư trước giao dịch
     * @param balanceAfter Số dư sau giao dịch
     */
    public ResourceTransactionDetail(
            ResourceTransactionHeader transaction,
            ResourceDefinition resourceDefinition,
            BigDecimal amount,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter
    ) {
        this.transaction = transaction;
        this.resourceDefinition = resourceDefinition;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
    }

    /**
     * Gán thời điểm tạo khi lưu chi tiết giao dịch lần đầu.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * Lấy ID chi tiết giao dịch.
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy giao dịch cha.
     */
    public ResourceTransactionHeader getTransaction() {
        return transaction;
    }

    /**
     * Lấy định nghĩa tài nguyên được thay đổi.
     */
    public ResourceDefinition getResourceDefinition() {
        return resourceDefinition;
    }

    /**
     * Lấy mã loại tài nguyên.
     */
    public String getResourceCode() {
        return resourceDefinition.getResourceCode();
    }

    /**
     * Lấy số lượng tài nguyên thay đổi.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Lấy số dư trước giao dịch.
     */
    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    /**
     * Lấy số dư sau giao dịch.
     */
    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    /**
     * Lấy thời điểm tạo chi tiết giao dịch.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
}