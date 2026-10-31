package vn.nghichtien.chienky.account.service;

import vn.nghichtien.chienky.account.domain.Account;
import vn.nghichtien.chienky.account.dto.RegisterRequest;
import vn.nghichtien.chienky.account.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.player.service.PlayerInitializationService;

/**
 * Xử lý nghiệp vụ đăng ký và tra cứu tài khoản.
 * Quyền USER được gán mặc định; service không cung cấp chức năng tự đăng ký ADMIN.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlayerInitializationService playerInitializationService;

    /**
     * Khởi tạo service đăng ký tài khoản và khởi tạo hồ sơ người chơi.
     *
     * @param accountRepository Repository tài khoản
     * @param passwordEncoder Bộ mã hóa mật khẩu
     * @param playerInitializationService Service khởi tạo dữ liệu người chơi
     */
    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            PlayerInitializationService playerInitializationService
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.playerInitializationService = playerInitializationService;
    }

    /**
     * Đăng ký tài khoản mới và tự động khởi tạo hồ sơ cùng tài nguyên mặc định.
     * Toàn bộ quá trình chạy trong một transaction để đảm bảo dữ liệu nhất quán.
     *
     * @param request Dữ liệu đăng ký đã qua validation cơ bản
     * @return Tài khoản vừa tạo
     */
    @Transactional
    public Account register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp");
        }

        if (accountRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Tên đăng nhập đã được sử dụng");
        }

        if (accountRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được sử dụng");
        }

        Account account = new Account(
                username,
                email,
                passwordEncoder.encode(request.getPassword())
        );

        account = accountRepository.save(account);

        playerInitializationService.initialize(account);

        return account;
    }
}
