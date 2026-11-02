package vn.nghichtien.chienky.giftcode.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.nghichtien.chienky.giftcode.dto.GiftCodeRedemptionResponse;
import vn.nghichtien.chienky.giftcode.service.GiftCodeService;

import java.util.Map;

@RestController
@RequestMapping("/api/player/gift-code")
public class GiftCodeController {

    private final GiftCodeService giftCodeService;

    public GiftCodeController(GiftCodeService giftCodeService) {
        this.giftCodeService = giftCodeService;
    }

    @PostMapping("/redeem")
    public GiftCodeRedemptionResponse redeem(
            Authentication authentication,
            @RequestBody Map<String, String> request
    ) {
        String code = request == null ? null : request.get("code");
        return giftCodeService.redeem(authentication.getName(), code);
    }
}
