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
 * API phục vụ giao diện phát lại trận chiến.
 */
@RestController
@RequestMapping("/api/battle")
public class BattleApiController {

    private final BattleSessionService battleSessionService;

    public BattleApiController(BattleSessionService battleSessionService) {
        this.battleSessionService = battleSessionService;
    }

    /**
     * Tạo trận PvE từ đội hình hiện tại của người chơi.
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
