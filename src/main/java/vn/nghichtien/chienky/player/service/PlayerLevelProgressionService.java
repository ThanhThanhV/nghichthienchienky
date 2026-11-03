package vn.nghichtien.chienky.player.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.player.domain.PlayerLevelConfig;
import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerLevelConfigRepository;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;
import vn.nghichtien.chienky.resource.domain.PlayerResource;
import vn.nghichtien.chienky.resource.repository.PlayerResourceRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tính cấp người chơi từ tổng số PLAYER_EXP đã tích lũy.
 * Không trừ EXP khi lên cấp; ngưỡng được cấu hình bằng player_level_configs.
 */
@Service
public class PlayerLevelProgressionService {

    private static final String PLAYER_EXP_CODE = "PLAYER_EXP";

    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final PlayerLevelConfigRepository playerLevelConfigRepository;

    public PlayerLevelProgressionService(
            PlayerProfileRepository playerProfileRepository,
            PlayerResourceRepository playerResourceRepository,
            PlayerLevelConfigRepository playerLevelConfigRepository
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.playerResourceRepository = playerResourceRepository;
        this.playerLevelConfigRepository = playerLevelConfigRepository;
    }

    /** Đồng bộ playerLevel theo số dư tích lũy PLAYER_EXP. */
    @Transactional
    public int refreshLevel(Long playerId) {
        if (playerId == null || playerId <= 0) {
            throw new IllegalArgumentException("Player ID không hợp lệ");
        }

        PlayerProfile player = playerProfileRepository.findByIdForUpdate(playerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy hồ sơ người chơi"
                ));

        List<PlayerResource> resources =
                playerResourceRepository.findAllByPlayerIdWithDefinition(playerId);

        BigDecimal totalExp = resources.stream()
                .filter(resource -> PLAYER_EXP_CODE.equals(resource.getResourceCode()))
                .map(PlayerResource::getBalance)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        List<PlayerLevelConfig> configs =
                playerLevelConfigRepository.findAllByOrderByPlayerLevelAsc();

        int calculatedLevel = 1;
        for (PlayerLevelConfig config : configs) {
            if (config.getRequiredTotalExp().compareTo(totalExp) <= 0) {
                calculatedLevel = Math.max(calculatedLevel, config.getPlayerLevel());
            } else {
                break;
            }
        }

        if (calculatedLevel > player.getPlayerLevel()) {
            player.updateLevel(calculatedLevel);
        }

        return player.getPlayerLevel();
    }
}
