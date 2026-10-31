
package vn.nghichtien.chienky.player.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.nghichtien.chienky.player.domain.PlayerProfile;

import java.util.Optional;

public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, Long> {

    /**
     * Tìm hồ sơ người chơi theo ID tài khoản.
     *
     * @param accountId ID tài khoản đăng nhập
     * @return hồ sơ người chơi nếu tồn tại
     */
    Optional<PlayerProfile> findByAccountId(Long accountId);

    /**
     * Tìm hồ sơ theo username tài khoản.
     *
     * @param username tên đăng nhập
     * @return hồ sơ người chơi nếu tồn tại
     */
    Optional<PlayerProfile> findByAccountUsername(String username);

    /**
     * Khóa hồ sơ người chơi trong transaction hiện tại.
     * Dùng để tuần tự hóa các giao dịch tài nguyên của cùng một người chơi.
     *
     * @param playerId ID hồ sơ người chơi
     * @return hồ sơ đã được khóa nếu tồn tại
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PlayerProfile p WHERE p.id = :playerId")
    Optional<PlayerProfile> findByIdForUpdate(@Param("playerId") Long playerId);
}
