package vn.nghichtien.chienky.hero.dto;

import jakarta.validation.constraints.Positive;

/**
 * Request cộng kinh nghiệm cho nhân vật.
 */
public class AddHeroExperienceRequest {

    @Positive
    private long amount;

    public AddHeroExperienceRequest() {
    }

    /**
     * Lấy lượng EXP yêu cầu cộng.
     *
     * @return Lượng EXP
     */
    public long getAmount() {
        return amount;
    }

    /**
     * Thiết lập lượng EXP yêu cầu cộng.
     *
     * @param amount Lượng EXP
     */
    public void setAmount(long amount) {
        this.amount = amount;
    }
}