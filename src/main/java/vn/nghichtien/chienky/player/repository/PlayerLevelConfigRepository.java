package vn.nghichtien.chienky.player.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.nghichtien.chienky.player.domain.PlayerLevelConfig;

import java.util.List;

public interface PlayerLevelConfigRepository
        extends JpaRepository<PlayerLevelConfig, Integer> {

    List<PlayerLevelConfig> findAllByOrderByPlayerLevelAsc();
}
