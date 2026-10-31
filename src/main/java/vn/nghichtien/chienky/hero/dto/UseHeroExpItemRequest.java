package vn.nghichtien.chienky.hero.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request sử dụng vật phẩm kinh nghiệm cho nhân vật.
 */
public class UseHeroExpItemRequest {

    @NotBlank
    @Size(max = 50)
    private String resourceCode;

    @Positive
    private Integer quantity;

    @NotBlank
    @Size(max = 100)
    private String transactionKey;

    public UseHeroExpItemRequest() {
    }

    /**
     * Lấy mã vật phẩm.
     *
     * @return Mã tài nguyên
     */
    public String getResourceCode() {
        return resourceCode;
    }

    /**
     * Thiết lập mã vật phẩm.
     *
     * @param resourceCode Mã tài nguyên
     */
    public void setResourceCode(String resourceCode) {
        this.resourceCode = resourceCode;
    }

    /**
     * Lấy số lượng vật phẩm sử dụng.
     *
     * @return Số lượng
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * Thiết lập số lượng vật phẩm sử dụng.
     *
     * @param quantity Số lượng
     */
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    /**
     * Lấy mã giao dịch chống xử lý trùng.
     *
     * @return Mã giao dịch
     */
    public String getTransactionKey() {
        return transactionKey;
    }

    /**
     * Thiết lập mã giao dịch chống xử lý trùng.
     *
     * @param transactionKey Mã giao dịch
     */
    public void setTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }
}