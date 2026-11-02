package vn.nghichtien.chienky.giftcode.dto;

import java.util.List;

public record GiftCodeRedemptionResponse(
        String message,
        int grantedCount,
        int totalOwned,
        List<String> grantedHeroNames
) {
}
