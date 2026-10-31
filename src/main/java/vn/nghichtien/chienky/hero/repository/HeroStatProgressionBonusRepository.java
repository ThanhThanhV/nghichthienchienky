package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroStatProgressionBonus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HeroStatProgressionBonusRepository
        extends JpaRepository<HeroStatProgressionBonus, Long> {

    List<HeroStatProgressionBonus>
    findAllByHeroCodeAndProgressionTypeAndTargetLevelLessThanEqualAndEnabledTrue(
            String heroCode,
            String progressionType,
            Integer targetLevel
    );
}