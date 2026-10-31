package vn.nghichtien.chienky.player.controller;

import vn.nghichtien.chienky.player.dto.PlayerProfileResponse;
import vn.nghichtien.chienky.player.service.PlayerProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import vn.nghichtien.chienky.player.dto.UpdatePlayerProfileRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/player")
public class PlayerController {

    private final PlayerProfileService playerProfileService;

    public PlayerController(PlayerProfileService playerProfileService) {
        this.playerProfileService = playerProfileService;
    }

    /**
     * Lấy hồ sơ và tài nguyên của tài khoản đang đăng nhập.
     *
     * @param authentication Thông tin xác thực từ Spring Security
     * @return Hồ sơ người chơi và danh sách tài nguyên
     */
    @GetMapping("/me")
    public PlayerProfileResponse getMyProfile(Authentication authentication) {
        return playerProfileService.getMyProfile(authentication.getName());
    }

    /**
     * Cập nhật nickname và avatar của người chơi hiện tại.
     *
     * @param authentication Thông tin xác thực từ Spring Security
     * @param request Dữ liệu nickname và avatar mới
     * @return Hồ sơ người chơi sau khi cập nhật
     */
    @PutMapping("/me/profile")
    public PlayerProfileResponse updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdatePlayerProfileRequest request
    ) {
        return playerProfileService.updateMyProfile(
                authentication.getName(),
                request
        );
    }
}