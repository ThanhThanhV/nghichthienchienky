package vn.nghichtien.chienky.account.service;

import jakarta.transaction.Transactional;
import vn.nghichtien.chienky.account.dto.RegisterRequest;
import vn.nghichtien.chienky.account.entity.Account;
import vn.nghichtien.chienky.account.entity.AccountRole;
import vn.nghichtien.chienky.account.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Xử lý nghiệp vụ tài khoản.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Đăng ký tài khoản người chơi mới.
     */
    @Transactional
    public Account register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
        }

        if (accountRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username đã được sử dụng.");
        }

        if (accountRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được sử dụng.");
        }

        Account account = new Account();
        account.setUsername(username);
        account.setEmail(email);
        account.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        // Người đăng ký thông thường luôn là USER.
        // Không nhận role từ dữ liệu phía trình duyệt.
        account.setRole(AccountRole.USER);
        account.setEnabled(true);

        return accountRepository.save(account);
    }
}