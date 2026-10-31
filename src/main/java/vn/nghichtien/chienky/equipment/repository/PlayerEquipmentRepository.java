package vn.nghichtien.chienky.equipment.repository;

import vn.nghichtien.chienky.equipment.domain.PlayerEquipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlayerEquipmentRepository
        extends JpaRepository<PlayerEquipment, Long> {

    @Query("""
            SELECT e
            FROM PlayerEquipment e
            JOIN FETCH e.equipmentDefinition
            WHERE e.playerProfile.id = :playerId
            ORDER BY e.obtainedAt DESC
            """)
    List<PlayerEquipment> findAllByPlayerIdWithDefinition(
            @Param("playerId") Long playerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e
            FROM PlayerEquipment e
            JOIN FETCH e.equipmentDefinition
            WHERE e.id = :equipmentId
              AND e.playerProfile.id = :playerId
            """)
    Optional<PlayerEquipment> findByIdAndPlayerIdForUpdate(
            @Param("equipmentId") Long equipmentId,
            @Param("playerId") Long playerId
    );
}