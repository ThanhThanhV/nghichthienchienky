package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroSkillUpgradeConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HeroSkillUpgradeConfigRepository
        extends JpaRepository<HeroSkillUpgradeConfig, Long> {

    @EntityGraph(attributePaths = "costs")
    Optional<HeroSkillUpgradeConfig>
    findBySkillDefinitionIdAndTargetSkillLevelAndEnabled(
            Long skillId,
            Integer targetSkillLevel,
            Boolean enabled
    );

    /** Xóa cấu hình nâng cấp gắn với các kỹ năng seed cũ. */
    long deleteAllBySkillDefinitionHeroDefinitionHeroCodeIn(java.util.List<String> heroCodes);
}