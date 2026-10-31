package vn.nghichtien.chienky.account.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity lưu thông tin xác thực và trạng thái tài khoản đăng nhập.
 * Không lưu mật khẩu dạng rõ; passwordHash chứa kết quả BCrypt.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, unique = true)
    private String username;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role = AccountRole.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    /**
     * Khởi tạo tài khoản mới từ dữ liệu đã được service kiểm tra và mã hóa mật khẩu.
     *
     * @param username tên đăng nhập
     * @param email email tài khoản
     * @param passwordHash mật khẩu đã mã hóa
     */
    public Account(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = AccountRole.USER;
        this.enabled = true;
    }

    /**
     * Gán thời điểm tạo và cập nhật trước khi insert bản ghi.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Cập nhật thời điểm chỉnh sửa trước khi update bản ghi.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public AccountRole getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
