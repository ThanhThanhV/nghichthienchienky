package vn.nghichtien.chienky.power.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.nghichtien.chienky.power.dto.PowerRankingDetailResponse;
import vn.nghichtien.chienky.power.dto.PowerRankingEntryResponse;
import vn.nghichtien.chienky.power.service.PowerRankingService;

import java.util.List;

/** API bảng xếp hạng và đội hình chính công khai. */
@RestController
@RequestMapping("/api/power-ranking")
public class PowerRankingApiController {

    private final PowerRankingService powerRankingService;

    public PowerRankingApiController(PowerRankingService powerRankingService) {
        this.powerRankingService = powerRankingService;
    }

    @GetMapping
    public List<PowerRankingEntryResponse> getRanking() {
        return powerRankingService.getRanking();
    }

    @GetMapping("/me")
    public PowerRankingDetailResponse getMyPower(Authentication authentication) {
        return powerRankingService.getMyPower(authentication.getName());
    }

    @GetMapping("/{playerId}")
    public PowerRankingDetailResponse getPlayerDetail(@PathVariable Long playerId) {
        try {
            return powerRankingService.getPlayerDetail(playerId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    exception.getMessage(),
                    exception
            );
        }
    }
}