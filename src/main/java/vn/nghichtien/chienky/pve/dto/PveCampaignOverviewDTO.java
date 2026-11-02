package vn.nghichtien.chienky.pve.dto;

import java.math.BigDecimal;
import java.util.List;

public record PveCampaignOverviewDTO(
        int highestUnlockedStage,
        List<StageDTO> stages
) {
    public record StageDTO(
            int stageNumber,
            String stageCode,
            String stageName,
            String description,
            boolean unlocked,
            boolean cleared,
            BigDecimal goldReward,
            BigDecimal playerExpReward
    ) {
    }
}
