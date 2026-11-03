package vn.nghichtien.chienky.battle.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.nghichtien.chienky.battle.dto.BattlePlaybackDTO;
import vn.nghichtien.chienky.battle.service.BattleSessionService;

/**
 * API phục vụ khởi tạo và phát lại trận chiến.
 */
@RestController
@RequestMapping("/api/battle")
public class BattleApiController {

    private final BattleSessionService battleSessionService;

    public BattleApiController(BattleSessionService battleSessionService) {
        this.battleSessionService = battleSessionService;
    }

    /**
     * Tạo trận PvE tự do từ đội hình hiện tại của người chơi.
     */
    @PostMapping("/start")
    public BattlePlaybackDTO start(Authentication authentication) {
        try {
            return battleSessionService.startBattle(authentication.getName());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Tạo trận Campaign tại ải đã được mở khóa.
     */
    @PostMapping("/campaign/{stageNumber}/start")
    public BattlePlaybackDTO startCampaign(
            Authentication authentication,
            @PathVariable int stageNumber
    ) {
        try {
            return battleSessionService.startCampaignBattle(
                    authentication.getName(), stageNumber
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    exception.getMessage(),
                    exception
            );
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }
    }


    /** Tạo trận PvP với đội hình chính của người chơi được chọn trên BXH. */
    @PostMapping("/challenge/{playerId}/start")
    public BattlePlaybackDTO challenge(
            Authentication authentication,
            @PathVariable Long playerId
    ) {
        try {
            return battleSessionService.startPlayerChallenge(
                    authentication.getName(), playerId
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    exception.getMessage(),
                    exception
            );
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Giải phóng phiên chiến đấu của chính người chơi hiện tại.
     */
    @DeleteMapping("/{battleId}")
    public void close(
            Authentication authentication,
            @PathVariable String battleId
    ) {
        battleSessionService.closeBattle(battleId, authentication.getName());
    }

    /**
     * Xử lý một round và trả về các sự kiện mới để frontend phát lại.
     */
    @PostMapping("/{battleId}/round")
    public BattlePlaybackDTO advanceRound(
            Authentication authentication,
            @PathVariable String battleId
    ) {
        try {
            return battleSessionService.advanceRound(
                    battleId, authentication.getName()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    exception.getMessage(),
                    exception
            );
        }
    }
}
