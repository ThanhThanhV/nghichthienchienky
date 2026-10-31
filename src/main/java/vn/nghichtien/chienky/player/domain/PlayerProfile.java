
package vn.nghichtien.chienky.player.domain;

import jakarta.persistence.*;
import vn.nghichtien.chienky.account.domain.Account;

import java.time.Instant;

/**
 * Entity lưu hồ sơ nhân vật của người chơi.
 * Mỗi tài khoản có duy nhất một PlayerProfile.
 * Thông tin đăng nhập thuộc Account, thông tin game thuộc PlayerProfile.
 */
@Entity
@Table(name = "player_profiles")
public class PlayerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            unique = true
    )
    private Account account;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "avatar_code", length = 100)
    private String avatarCode;

    @Column(name = "player_level", nullable = false)
    private Integer playerLevel = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerProfile() {
    }

    /**
     * Khởi tạo hồ sơ người chơi mới cho tài khoản vừa đăng ký.
     *
     * @param account tài khoản sở hữu hồ sơ
     * @param nickname tên hiển thị trong game
     */
    public PlayerProfile(Account account, String nickname) {
        this.account = account;
        this.nickname = nickname;
        this.playerLevel = 1;
    }

    /**
     * Thiết lập thời gian tạo và cập nhật khi tạo hồ sơ mới.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Cập nhật thời gian chỉnh sửa hồ sơ.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Thay đổi tên hiển thị của người chơi.
     *
     * @param nickname tên mới
     */
    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * Cập nhật mã avatar của người chơi.
     *
     * @param avatarCode mã avatar mới
     */
    public void changeAvatar(String avatarCode) {
        this.avatarCode = avatarCode;
    }

    /**
     * Cập nhật cấp độ người chơi sau khi hệ thống tính toán kinh nghiệm.
     *
     * @param playerLevel cấp độ mới, phải lớn hơn hoặc bằng 1
     */
    public void updateLevel(Integer playerLevel) {
        if (playerLevel == null || playerLevel < 1) {
            throw new IllegalArgumentException("Player level must be at least 1");
        }
        this.playerLevel = playerLevel;
    }

    /** Lấy ID hồ sơ người chơi. */
    public Long getId() {
        return id;
    }

    /** Lấy tài khoản sở hữu hồ sơ. */
    public Account getAccount() {
        return account;
    }

    /** Lấy tên hiển thị trong game. */
    public String getNickname() {
        return nickname;
    }

    /** Lấy mã avatar. */
    public String getAvatarCode() {
        return avatarCode;
    }

    /** Lấy cấp độ người chơi. */
    public Integer getPlayerLevel() {
        return playerLevel;
    }

    /** Lấy thời điểm tạo hồ sơ. */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Lấy thời điểm cập nhật hồ sơ gần nhất. */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
