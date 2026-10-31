package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroBaseStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository truy vấn chỉ số cơ bản của nhân vật.
 */
public interface HeroBaseStatsRepository extends JpaRepository<HeroBaseStats, String> {

    /**
     * Tìm bộ chỉ số cơ bản theo mã nhân vật.
     *
     * @param heroCode Mã nhân vật
     * @return Bộ chỉ số nếu tồn tại
     */
    Optional<HeroBaseStats> findByHeroCode(String heroCode);
}