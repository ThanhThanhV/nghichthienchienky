package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroInstance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository truy vấn nhân vật mà người chơi sở hữu.
 */
public interface HeroInstanceRepository extends JpaRepository<HeroInstance, Long> {

    /**
     * Lấy danh sách nhân vật của người chơi, kèm định nghĩa nhân vật.
     *
     * @param playerId ID người chơi
     * @return Danh sách nhân vật sở hữu
     */
    @EntityGraph(attributePaths = "heroDefinition")
    List<HeroInstance> findAllByPlayerProfileIdOrderByObtainedAtDesc(
            Long playerId
    );

    /**
     * Lấy chi tiết nhân vật thuộc đúng người chơi.
     *
     * @param id ID bản sao nhân vật
     * @param playerId ID người chơi
     * @return Nhân vật nếu thuộc người chơi
     */
    @EntityGraph(attributePaths = "heroDefinition")
    Optional<HeroInstance> findByIdAndPlayerProfileId(
            Long id,
            Long playerId
    );

    /**
     * Đếm số nhân vật người chơi đang sở hữu.
     *
     * @param playerId ID người chơi
     * @return Số lượng nhân vật
     */
    long countByPlayerProfileId(Long playerId);

    /**
     * Lấy toàn bộ bản sao người chơi đang sở hữu thuộc danh sách tướng seed.
     */
    @EntityGraph(attributePaths = "heroDefinition")
    List<HeroInstance> findAllByHeroDefinitionHeroCodeIn(List<String> heroCodes);

    /**
     * Lấy nhân vật thuộc người chơi và khóa bản ghi trong transaction hiện tại.
     *
     * @param instanceId ID bản sao nhân vật
     * @param playerId ID người chơi
     * @return Nhân vật đã được khóa
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
       SELECT h
       FROM HeroInstance h
       WHERE h.id = :instanceId
         AND h.playerProfile.id = :playerId
       """)
    Optional<HeroInstance> findByIdAndPlayerProfileIdForUpdate(
            @Param("instanceId") Long instanceId,
            @Param("playerId") Long playerId
    );
}