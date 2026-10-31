package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Chi phí tài nguyên của một cấu hình nâng cấp nhân vật.
 */
@Entity
@Table(name = "hero_progression_costs")
public class HeroProgressionCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "progression_config_id", nullable = false)
    private HeroProgressionConfig progressionConfig;

    @Column(name = "resource_code", nullable = false, length = 50)
    private String resourceCode;

    @Column(name = "required_amount", nullable = false, precision = 24, scale = 6)
    private BigDecimal requiredAmount;

    protected HeroProgressionCost() {
    }

    /**
     * Lấy mã tài nguyên cần tiêu hao.
     *
     * @return Mã tài nguyên
     */
    public String getResourceCode() {
        return resourceCode;
    }

    /**
     * Lấy số lượng tài nguyên cần tiêu hao.
     *
     * @return Số lượng yêu cầu
     */
    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }
}