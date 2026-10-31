package vn.nghichtien.chienky.equipment.repository;

import vn.nghichtien.chienky.equipment.domain.EquipmentBaseStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentBaseStatRepository
        extends JpaRepository<EquipmentBaseStat, Long> {

    List<EquipmentBaseStat> findAllByEquipmentDefinitionEquipmentCodeIn(
            List<String> equipmentCodes
    );
}