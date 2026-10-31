package vn.nghichtien.chienky.equipment.repository;

import vn.nghichtien.chienky.equipment.domain.EquipmentEnhancementConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentEnhancementConfigRepository
        extends JpaRepository<EquipmentEnhancementConfig, Long> {

    @EntityGraph(attributePaths = "costs")
    Optional<EquipmentEnhancementConfig>
    findByTargetLevelAndEnabledTrue(Integer targetLevel);
}