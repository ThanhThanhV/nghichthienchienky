package vn.nghichtien.chienky.account.repository;

import vn.nghichtien.chienky.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Truy vấn tài khoản.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Tìm tài khoản theo username.
     */
    Optional<Account> findByUsername(String username);

    /**
     * Kiểm tra username đã tồn tại.
     */
    boolean existsByUsername(String username);

    /**
     * Kiểm tra email đã tồn tại.
     */
    boolean existsByEmail(String email);
}