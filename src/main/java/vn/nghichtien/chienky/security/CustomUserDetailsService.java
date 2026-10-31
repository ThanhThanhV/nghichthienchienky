package vn.nghichtien.chienky.security;

import vn.nghichtien.chienky.account.domain.Account;
import vn.nghichtien.chienky.account.repository.AccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Nạp tài khoản từ database cho Spring Security khi xác thực đăng nhập.
 * Người chơi đăng nhập bằng username; quyền được chuyển thành ROLE_*.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public CustomUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Tìm tài khoản theo username và chuyển thành UserDetails của Spring Security.
     *
     * @param username tên đăng nhập
     * @return thông tin xác thực Spring Security
     * @throws UsernameNotFoundException nếu không tồn tại tài khoản
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));

        return new User(
                account.getUsername(),
                account.getPasswordHash(),
                account.isEnabled(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))
        );
    }
}
