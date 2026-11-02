package vn.nghichtien.chienky.pve.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cấu hình một ải Campaign PvE.
 */
public record PveCampaignStage(
        int stageNumber,
        String stageCode,
        String stageName,
        String description,
        List<String> enemyHeroCodes,
        BigDecimal goldReward,
        BigDecimal playerExpReward
) {
}
