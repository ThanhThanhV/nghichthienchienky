package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroSkillUpgradeConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn cấu hình nâng cấp theo mã hero và mã skill ổn định.
 */
public interface HeroSkillUpgradeConfigRepository
        extends JpaRepository<HeroSkillUpgradeConfig, Long> {

    List<HeroSkillUpgradeConfig> findAllByHeroCode(String heroCode);

    @EntityGraph(attributePaths = "costs")
    Optional<HeroSkillUpgradeConfig>
    findByHeroCodeAndSkillCodeAndTargetSkillLevelAndEnabled(
            String heroCode,
            String skillCode,
            Integer targetSkillLevel,
            Boolean enabled
    );

    long deleteAllByHeroCodeIn(List<String> heroCodes);

    /**
     * Loại bỏ cấu hình nâng cấp trỏ tới skillCode không còn thuộc catalog hero.
     */
    long deleteAllByHeroCodeAndSkillCodeNotIn(
            String heroCode,
            List<String> currentSkillCodes
    );
}
