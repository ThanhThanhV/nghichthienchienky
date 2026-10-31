package vn.nghichtien.chienky.hero.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request nâng sao hoặc đột phá nhân vật.
 */
public class UpgradeHeroRequest {

    @NotBlank
    @Size(max = 100)
    private String transactionKey;

    public UpgradeHeroRequest() {
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
     * Thiết lập mã giao dịch.
     *
     * @param transactionKey Mã giao dịch
     */
    public void setTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }
}