package vn.nghichtien.chienky.resource.repository;

import vn.nghichtien.chienky.resource.domain.PlayerResource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayerResourceRepository extends JpaRepository<PlayerResource, Long> {

    /**
     * Lấy toàn bộ tài nguyên của người chơi, đồng thời tải thông tin định nghĩa
     * tài nguyên để tránh lỗi LazyInitializationException khi trả dữ liệu.
     *
     * @param playerId ID hồ sơ người chơi
     * @return Danh sách tài nguyên kèm thông tin định nghĩa
     */
    @Query("""
            SELECT pr
            FROM PlayerResource pr
            JOIN FETCH pr.resourceDefinition
            WHERE pr.player.id = :playerId
            ORDER BY pr.resourceDefinition.resourceCode
            """)
    List<PlayerResource> findAllByPlayerIdWithDefinition(
            @Param("playerId") Long playerId
    );

    /**
     * Lấy và khóa toàn bộ bản ghi tài nguyên của người chơi trong transaction hiện tại.
     * Dùng khi cộng hoặc trừ tài nguyên để tránh cập nhật số dư đồng thời.
     *
     * @param playerId ID hồ sơ người chơi
     * @return Danh sách tài nguyên đã được khóa
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT pr
            FROM PlayerResource pr
            WHERE pr.player.id = :playerId
            ORDER BY pr.id
            """)
    List<PlayerResource> findAllByPlayerIdForUpdate(
            @Param("playerId") Long playerId
    );

    /**
     * Kiểm tra người chơi đã có bản ghi của loại tài nguyên tương ứng hay chưa.
     *
     * @param playerId ID hồ sơ người chơi
     * @param resourceCode Mã tài nguyên
     * @return true nếu đã tồn tại
     */
    boolean existsByPlayerIdAndResourceDefinitionResourceCode(
            Long playerId,
            String resourceCode
    );
}