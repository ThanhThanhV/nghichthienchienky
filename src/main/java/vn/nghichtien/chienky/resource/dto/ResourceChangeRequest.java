package vn.nghichtien.chienky.resource.dto;

import java.math.BigDecimal;
import java.util.Map;

public class ResourceChangeRequest {

    private String transactionKey;
    private String sourceType;
    private Long sourceId;
    private String description;
    private Map<String, BigDecimal> changes;

    protected ResourceChangeRequest() {
    }

    /**
     * Khởi tạo yêu cầu thay đổi tài nguyên của người chơi.
     *
     * @param transactionKey Mã duy nhất để chống xử lý giao dịch trùng
     * @param sourceType Loại nguồn phát sinh giao dịch
     * @param sourceId ID đối tượng nguồn, có thể null
     * @param description Mô tả giao dịch
     * @param changes Danh sách tài nguyên và số lượng thay đổi
     */
    public ResourceChangeRequest(
            String transactionKey,
            String sourceType,
            Long sourceId,
            String description,
            Map<String, BigDecimal> changes
    ) {
        this.transactionKey = transactionKey;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.description = description;
        this.changes = changes;
    }

    /**
     * Lấy mã giao dịch chống xử lý trùng.
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
     * Lấy ID đối tượng nguồn.
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
     * Lấy danh sách tài nguyên cần thay đổi.
     */
    public Map<String, BigDecimal> getChanges() {
        return changes;
    }
}