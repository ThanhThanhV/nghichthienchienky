package vn.nghichtien.chienky.formation.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.nghichtien.chienky.formation.dto.FormationResponse;
import vn.nghichtien.chienky.formation.dto.UpdateFormationRequest;
import vn.nghichtien.chienky.formation.service.PlayerFormationService;

/**
 * API đọc và cập nhật đội hình người chơi.
 */
@RestController
@RequestMapping("/api/player/formation")
public class PlayerFormationController {

    private final PlayerFormationService playerFormationService;

    public PlayerFormationController(PlayerFormationService playerFormationService) {
        this.playerFormationService = playerFormationService;
    }

    @GetMapping
    public FormationResponse getMyFormation(Authentication authentication) {
        return playerFormationService.getMyFormation(authentication.getName());
    }

    @PutMapping
    public FormationResponse updateMyFormation(
            Authentication authentication,
            @Valid @RequestBody UpdateFormationRequest request
    ) {
        return playerFormationService.updateMyFormation(
                authentication.getName(),
                request
        );
    }
}