package vn.nghichtien.chienky.pve.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.pve.domain.PlayerCampaignProgress;
import vn.nghichtien.chienky.pve.dto.PveCampaignOverviewDTO;
import vn.nghichtien.chienky.pve.repository.PlayerCampaignProgressRepository;
import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;

import java.util.HashSet;
import java.util.Set;

@Service
public class PveCampaignService {

    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerCampaignProgressRepository progressRepository;
    private final PveCampaignCatalog campaignCatalog;

    public PveCampaignService(
            PlayerProfileRepository playerProfileRepository,
            PlayerCampaignProgressRepository progressRepository,
            PveCampaignCatalog campaignCatalog
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.progressRepository = progressRepository;
        this.campaignCatalog = campaignCatalog;
    }

    @Transactional(readOnly = true)
    public PveCampaignOverviewDTO getOverview(String username) {
        PlayerProfile player = playerProfileRepository
                .findByAccountUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy hồ sơ người chơi"
                ));

        Set<Integer> clearedStages = new HashSet<>();
        for (PlayerCampaignProgress progress :
                progressRepository.findAllByPlayerIdOrderByStageNumberAsc(player.getId())) {
            clearedStages.add(progress.getStageNumber());
        }

        int highestUnlocked = Math.min(
                campaignCatalog.getAll().size(),
                clearedStages.stream().mapToInt(Integer::intValue).max().orElse(0) + 1
        );

        var stages = campaignCatalog.getAll().stream()
                .map(stage -> new PveCampaignOverviewDTO.StageDTO(
                        stage.stageNumber(),
                        stage.stageCode(),
                        stage.stageName(),
                        stage.description(),
                        stage.stageNumber() <= highestUnlocked,
                        clearedStages.contains(stage.stageNumber()),
                        stage.goldReward(),
                        stage.playerExpReward()
                ))
                .toList();

        return new PveCampaignOverviewDTO(highestUnlocked, stages);
    }
}
