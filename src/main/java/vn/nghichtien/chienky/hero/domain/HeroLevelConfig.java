package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Entity cấu hình lượng kinh nghiệm cần thiết để tăng cấp nhân vật.
 */
@Entity
@Table(name = "hero_level_configs")
public class HeroLevelConfig {

    @Id
    @Column(name = "hero_level")
    private Integer heroLevel;

    @Column(name = "required_exp", nullable = false)
    private Long requiredExp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected HeroLevelConfig() {
    }

    /**
     * Lấy cấp nhân vật áp dụng cấu hình.
     *
     * @return Cấp nhân vật
     */
    public Integer getHeroLevel() {
        return heroLevel;
    }

    /**
     * Lấy lượng EXP cần để lên cấp tiếp theo.
     *
     * @return Lượng EXP yêu cầu
     */
    public Long getRequiredExp() {
        return requiredExp;
    }
}