package vn.nghichtien.chienky.hero.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpgradeHeroSkillRequest {

    @NotBlank
    @Size(max = 100)
    private String transactionKey;

    public String getTransactionKey() {
        return transactionKey;
    }

    public void setTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }
}