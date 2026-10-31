package vn.nghichtien.chienky.equipment.repository;

import vn.nghichtien.chienky.equipment.domain.EquipmentDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentDefinitionRepository
        extends JpaRepository<EquipmentDefinition, String> {

    Optional<EquipmentDefinition>
    findByEquipmentCodeAndEnabledTrue(String equipmentCode);
}