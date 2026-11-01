
package vn.nghichtien.chienky.formation.repository;

import vn.nghichtien.chienky.formation.domain.PlayerFormationSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerFormationSlotRepository
        extends JpaRepository<PlayerFormationSlot, Long> {

    /**
     * Lấy đội hình của người chơi theo thứ tự vị trí.
     *
     * @param playerId ID người chơi
     * @return Danh sách vị trí đội hình
     */
    List<PlayerFormationSlot>
    findAllByPlayerProfileIdOrderByRowAscColumnAsc(Long playerId);

    /**
     * Tìm vị trí đội hình của một nhân vật.
     *
     * @param playerId ID người chơi
     * @param heroInstanceId ID nhân vật
     * @return Vị trí đội hình
     */
    Optional<PlayerFormationSlot>
    findByPlayerProfileIdAndHeroInstanceId(
            Long playerId,
            Long heroInstanceId
    );

    /**
     * Đếm số nhân vật đang được xếp.
     *
     * @param playerId ID người chơi
     * @return Số nhân vật
     */
    long countByPlayerProfileId(Long playerId);

    /**
     * Xóa đội hình của người chơi.
     *
     * @param playerId ID người chơi
     */
    void deleteAllByPlayerProfileId(Long playerId);
}
