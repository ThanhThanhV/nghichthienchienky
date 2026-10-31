package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import vn.nghichtien.chienky.player.domain.PlayerProfile;

import java.time.LocalDateTime;

/**
 * Đại diện cho một bản sao nhân vật mà người chơi đang sở hữu.
 * Mỗi bản ghi có cấp độ, sao, đột phá và kinh nghiệm độc lập.
 */
@Entity
@Table(name = "hero_instances")
public class HeroInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerProfile playerProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hero_code", nullable = false)
    private HeroDefinition heroDefinition;

    @Column(name = "hero_level", nullable = false)
    private Integer heroLevel = 1;

    @Column(name = "star_level", nullable = false)
    private Integer starLevel = 0;

    @Column(name = "breakthrough_level", nullable = false)
    private Integer breakthroughLevel = 0;

    @Column(name = "experience", nullable = false)
    private Long experience = 0L;

    @Column(name = "obtained_at", nullable = false, updatable = false)
    private LocalDateTime obtainedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Constructor mặc định phục vụ JPA.
     */
    protected HeroInstance() {
    }

    /**
     * Khởi tạo bản sao nhân vật mới cho người chơi.
     *
     * @param playerProfile Hồ sơ người chơi sở hữu
     * @param heroDefinition Định nghĩa nhân vật
     */
    public HeroInstance(
            PlayerProfile playerProfile,
            HeroDefinition heroDefinition
    ) {
        this.playerProfile = playerProfile;
        this.heroDefinition = heroDefinition;
        this.heroLevel = 1;
        this.starLevel = 0;
        this.breakthroughLevel = 0;
        this.experience = 0L;
    }

    /**
     * Khởi tạo thời gian tạo và cập nhật trước khi lưu.
     */
    @jakarta.persistence.PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.obtainedAt = now;
        this.updatedAt = now;
    }

    /**
     * Cập nhật thời gian chỉnh sửa trước khi cập nhật.
     */
    @jakarta.persistence.PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Cộng kinh nghiệm cho nhân vật.
     *
     * @param amount Số kinh nghiệm cần cộng
     */
    public void addExperience(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Kinh nghiệm cộng thêm phải lớn hơn 0"
            );
        }

        this.experience = Math.addExact(this.experience, amount);
    }

    /**
     * Cập nhật cấp độ nhân vật.
     *
     * @param heroLevel Cấp độ mới
     */
    public void changeLevel(Integer heroLevel) {
        if (heroLevel == null || heroLevel < 1) {
            throw new IllegalArgumentException(
                    "Cấp độ nhân vật không hợp lệ"
            );
        }

        this.heroLevel = heroLevel;
    }

    /**
     * Cập nhật cấp sao nhân vật.
     *
     * @param starLevel Cấp sao mới
     */
    public void changeStarLevel(Integer starLevel) {
        if (starLevel == null || starLevel < 0) {
            throw new IllegalArgumentException(
                    "Cấp sao không hợp lệ"
            );
        }

        this.starLevel = starLevel;
    }

    /**
     * Cập nhật cấp đột phá nhân vật.
     *
     * @param breakthroughLevel Cấp đột phá mới
     */
    public void changeBreakthroughLevel(Integer breakthroughLevel) {
        if (breakthroughLevel == null || breakthroughLevel < 0) {
            throw new IllegalArgumentException(
                    "Cấp đột phá không hợp lệ"
            );
        }

        this.breakthroughLevel = breakthroughLevel;
    }

    /**
     * Lấy ID bản sao nhân vật.
     *
     * @return ID nhân vật
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy hồ sơ người chơi sở hữu nhân vật.
     *
     * @return Hồ sơ người chơi
     */
    public PlayerProfile getPlayerProfile() {
        return playerProfile;
    }

    /**
     * Lấy định nghĩa nhân vật.
     *
     * @return Định nghĩa nhân vật
     */
    public HeroDefinition getHeroDefinition() {
        return heroDefinition;
    }

    /**
     * Lấy cấp độ nhân vật.
     *
     * @return Cấp độ
     */
    public Integer getHeroLevel() {
        return heroLevel;
    }

    /**
     * Lấy cấp sao nhân vật.
     *
     * @return Cấp sao
     */
    public Integer getStarLevel() {
        return starLevel;
    }

    /**
     * Lấy cấp đột phá nhân vật.
     *
     * @return Cấp đột phá
     */
    public Integer getBreakthroughLevel() {
        return breakthroughLevel;
    }

    /**
     * Lấy kinh nghiệm hiện tại.
     *
     * @return Kinh nghiệm
     */
    public Long getExperience() {
        return experience;
    }

    /**
     * Lấy thời điểm người chơi nhận nhân vật.
     *
     * @return Thời điểm nhận
     */
    public LocalDateTime getObtainedAt() {
        return obtainedAt;
    }

    /**
     * Lấy thời điểm cập nhật gần nhất.
     *
     * @return Thời điểm cập nhật
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Lấy mã nhân vật gốc.
     *
     * @return Mã nhân vật
     */
    public String getHeroCode() {
        return heroDefinition.getHeroCode();
    }
}