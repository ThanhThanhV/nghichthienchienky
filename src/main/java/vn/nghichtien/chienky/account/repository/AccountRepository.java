package vn.nghichtien.chienky.account.repository;

import vn.nghichtien.chienky.account.domain.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Truy cập dữ liệu tài khoản và cung cấp các phép kiểm tra định danh đăng nhập.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
