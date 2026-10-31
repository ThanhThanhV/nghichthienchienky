package vn.nghichtien.chienky.resource.dto;

import vn.nghichtien.chienky.resource.domain.ResourceTransactionDetail;
import vn.nghichtien.chienky.resource.domain.ResourceTransactionHeader;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class ResourceTransactionResult {

    private Long transactionId;
    private String transactionKey;
    private String sourceType;
    private String description;
    private Instant createdAt;
    private boolean alreadyProcessed;
    private List<ResourceChangeResult> details;

    protected ResourceTransactionResult() {
    }

    /**
     * Chuyển thông tin giao dịch Entity thành DTO trả về cho module gọi service.
     *
     * @param header Giao dịch tài nguyên
     * @param details Danh sách chi tiết giao dịch
     * @param alreadyProcessed Cho biết giao dịch đã được xử lý từ trước
     */
    public ResourceTransactionResult(
            ResourceTransactionHeader header,
            List<ResourceTransactionDetail> details,
            boolean alreadyProcessed
    ) {
        this.transactionId = header.getId();
        this.transactionKey = header.getTransactionKey();
        this.sourceType = header.getSourceType();
        this.description = header.getDescription();
        this.createdAt = header.getCreatedAt();
        this.alreadyProcessed = alreadyProcessed;

        this.details = details.stream()
                .map(detail -> new ResourceChangeResult(
                        detail.getResourceCode(),
                        detail.getResourceDefinition().getResourceName(),
                        detail.getAmount(),
                        detail.getBalanceBefore(),
                        detail.getBalanceAfter()
                ))
                .toList();
    }

    /**
     * Lấy ID giao dịch.
     */
    public Long getTransactionId() {
        return transactionId;
    }

    /**
     * Lấy mã giao dịch.
     */
    public String getTransactionKey() {
        return transactionKey;
    }

    /**
     * Lấy loại nguồn phát sinh.
     */
    public String getSourceType() {
        return sourceType;
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

    /**
     * Kiểm tra giao dịch có phải kết quả lấy lại từ lần xử lý trước hay không.
     */
    public boolean isAlreadyProcessed() {
        return alreadyProcessed;
    }

    /**
     * Lấy danh sách tài nguyên đã thay đổi.
     */
    public List<ResourceChangeResult> getDetails() {
        return details;
    }

    public static class ResourceChangeResult {

        private String resourceCode;
        private String resourceName;
        private BigDecimal amount;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;

        protected ResourceChangeResult() {
        }

        /**
         * Khởi tạo kết quả thay đổi số dư của một loại tài nguyên.
         *
         * @param resourceCode Mã tài nguyên
         * @param resourceName Tên tài nguyên
         * @param amount Số lượng thay đổi
         * @param balanceBefore Số dư trước giao dịch
         * @param balanceAfter Số dư sau giao dịch
         */
        public ResourceChangeResult(
                String resourceCode,
                String resourceName,
                BigDecimal amount,
                BigDecimal balanceBefore,
                BigDecimal balanceAfter
        ) {
            this.resourceCode = resourceCode;
            this.resourceName = resourceName;
            this.amount = amount;
            this.balanceBefore = balanceBefore;
            this.balanceAfter = balanceAfter;
        }

        /**
         * Lấy mã tài nguyên.
         */
        public String getResourceCode() {
            return resourceCode;
        }

        /**
         * Lấy tên tài nguyên.
         */
        public String getResourceName() {
            return resourceName;
        }

        /**
         * Lấy lượng tài nguyên thay đổi.
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
    }
}