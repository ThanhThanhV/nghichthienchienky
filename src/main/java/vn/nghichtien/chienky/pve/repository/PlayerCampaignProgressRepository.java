package vn.nghichtien.chienky.pve.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.nghichtien.chienky.pve.domain.PlayerCampaignProgress;

import java.util.List;
import java.util.Optional;

public interface PlayerCampaignProgressRepository
        extends JpaRepository<PlayerCampaignProgress, Long> {

    List<PlayerCampaignProgress> findAllByPlayerIdOrderByStageNumberAsc(Long playerId);

    Optional<PlayerCampaignProgress> findByPlayerIdAndStageNumber(
            Long playerId,
            Integer stageNumber
    );

    boolean existsByPlayerIdAndStageNumber(Long playerId, Integer stageNumber);
}
