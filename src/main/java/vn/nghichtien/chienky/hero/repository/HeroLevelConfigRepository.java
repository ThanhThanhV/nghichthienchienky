package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroLevelConfig;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HeroLevelConfigRepository
        extends JpaRepository<HeroLevelConfig, Integer> {
}