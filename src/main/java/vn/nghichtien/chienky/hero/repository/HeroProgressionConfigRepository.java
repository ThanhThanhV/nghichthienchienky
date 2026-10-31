package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroProgressionConfig;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository truy vấn cấu hình nâng sao và đột phá.
 */
public interface HeroProgressionConfigRepository
        extends JpaRepository<HeroProgressionConfig, Long> {

    /**
     * Tìm cấu hình nâng cấp đang hoạt động và tải kèm chi phí.
     *
     * @param heroCode Mã nhân vật
     * @param progressionType Loại nâng cấp
     * @param targetLevel Cấp mục tiêu
     * @param enabled Trạng thái
     * @return Cấu hình nâng cấp
     */
    @EntityGraph(attributePaths = "costs")
    Optional<HeroProgressionConfig>
    findByHeroCodeAndProgressionTypeAndTargetLevelAndEnabled(
            String heroCode,
            String progressionType,
            Integer targetLevel,
            Boolean enabled
    );
}