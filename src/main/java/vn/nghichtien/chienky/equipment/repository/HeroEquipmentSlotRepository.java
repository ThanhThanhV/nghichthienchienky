package vn.nghichtien.chienky.equipment.repository;

import vn.nghichtien.chienky.equipment.domain.HeroEquipmentSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HeroEquipmentSlotRepository
        extends JpaRepository<HeroEquipmentSlot, Long> {

    @Query("""
            SELECT s
            FROM HeroEquipmentSlot s
            JOIN FETCH s.playerEquipment e
            JOIN FETCH e.equipmentDefinition
            WHERE s.heroInstance.id = :heroInstanceId
            ORDER BY s.equipmentType
            """)
    List<HeroEquipmentSlot> findAllByHeroInstanceIdWithEquipment(
            @Param("heroInstanceId") Long heroInstanceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM HeroEquipmentSlot s
            WHERE s.heroInstance.id = :heroInstanceId
              AND s.equipmentType = :equipmentType
            """)
    Optional<HeroEquipmentSlot> findByHeroAndTypeForUpdate(
            @Param("heroInstanceId") Long heroInstanceId,
            @Param("equipmentType") String equipmentType
    );

    boolean existsByPlayerEquipmentId(Long playerEquipmentId);

    /**
     * Lấy các slot trang bị của tướng, chỉ trả về trang bị thuộc người chơi chỉ định.
     *
     * @param heroInstanceId ID tướng
     * @param playerId ID người chơi sở hữu
     * @return danh sách slot trang bị kèm vật phẩm và định nghĩa
     */
    @Query("""
        SELECT slot
        FROM HeroEquipmentSlot slot
        JOIN FETCH slot.playerEquipment equipment
        JOIN FETCH equipment.equipmentDefinition definition
        WHERE slot.heroInstance.id = :heroInstanceId
          AND slot.heroInstance.playerProfile.id = :playerId
        """)
    List<HeroEquipmentSlot> findAllByHeroInstanceAndPlayer(
            @Param("heroInstanceId") Long heroInstanceId,
            @Param("playerId") Long playerId
    );


}