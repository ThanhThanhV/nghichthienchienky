package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroSkillUpgradeCost;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository chi phí nâng cấp kỹ năng.
 */
public interface HeroSkillUpgradeCostRepository
        extends JpaRepository<HeroSkillUpgradeCost, Long> {

    /**
     * Xóa chi phí trước khi xóa cấu hình nâng cấp của các kỹ năng seed.
     */
    long deleteAllByUpgradeConfigHeroCodeIn(
            java.util.List<String> heroCodes
    );
}
