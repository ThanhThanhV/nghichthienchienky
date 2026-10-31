package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroGrowthConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository truy vấn cấu hình tăng trưởng chỉ số nhân vật.
 */
public interface HeroGrowthConfigRepository extends JpaRepository<HeroGrowthConfig, String> {

    /**
     * Tìm cấu hình tăng trưởng theo mã nhân vật.
     *
     * @param heroCode Mã nhân vật
     * @return Cấu hình tăng trưởng nếu tồn tại
     */
    Optional<HeroGrowthConfig> findByHeroCode(String heroCode);
}