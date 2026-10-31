package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Cấu hình lượng chỉ số nhân vật tăng thêm khi lên cấp.
 */
@Entity
@Table(name = "hero_growth_configs")
public class HeroGrowthConfig {

    @Id
    @Column(name = "hero_code", length = 50, nullable = false)
    private String heroCode;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hero_code", nullable = false)
    private HeroDefinition heroDefinition;

    @Column(name = "growth_hp", nullable = false, precision = 24, scale = 6)
    private BigDecimal hp = BigDecimal.ZERO;

    @Column(name = "growth_atk", nullable = false, precision = 24, scale = 6)
    private BigDecimal atk = BigDecimal.ZERO;

    @Column(name = "growth_def", nullable = false, precision = 24, scale = 6)
    private BigDecimal def = BigDecimal.ZERO;

    @Column(name = "growth_speed", nullable = false, precision = 24, scale = 6)
    private BigDecimal speed = BigDecimal.ZERO;

    @Column(name = "growth_crit", nullable = false, precision = 24, scale = 6)
    private BigDecimal crit = BigDecimal.ZERO;

    @Column(name = "growth_crit_damage", nullable = false, precision = 24, scale = 6)
    private BigDecimal critDamage = BigDecimal.ZERO;

    @Column(name = "growth_accuracy", nullable = false, precision = 24, scale = 6)
    private BigDecimal accuracy = BigDecimal.ZERO;

    @Column(name = "growth_resistance", nullable = false, precision = 24, scale = 6)
    private BigDecimal resistance = BigDecimal.ZERO;

    @Column(name = "growth_pen_flat", nullable = false, precision = 24, scale = 6)
    private BigDecimal penFlat = BigDecimal.ZERO;

    @Column(name = "growth_pen_percent", nullable = false, precision = 24, scale = 6)
    private BigDecimal penPercent = BigDecimal.ZERO;

    @Column(name = "growth_lifesteal", nullable = false, precision = 24, scale = 6)
    private BigDecimal lifesteal = BigDecimal.ZERO;

    /**
     * Constructor mặc định phục vụ JPA.
     */
    protected HeroGrowthConfig() {
    }

    /**
     * Khởi tạo cấu hình tăng trưởng nhân vật.
     *
     * @param heroDefinition Định nghĩa nhân vật
     */
    public HeroGrowthConfig(HeroDefinition heroDefinition) {
        this.heroDefinition = heroDefinition;
    }

    /**
     * Cập nhật toàn bộ chỉ số tăng trưởng theo cấp.
     *
     * @param hp Lượng máu tăng mỗi cấp
     * @param atk Lượng tấn công tăng mỗi cấp
     * @param def Lượng phòng thủ tăng mỗi cấp
     * @param speed Lượng tốc độ tăng mỗi cấp
     * @param crit Lượng chí mạng tăng mỗi cấp
     * @param critDamage Lượng sát thương chí mạng tăng mỗi cấp
     * @param accuracy Lượng chính xác tăng mỗi cấp
     * @param resistance Lượng kháng hiệu ứng tăng mỗi cấp
     * @param penFlat Lượng xuyên giáp cố định tăng mỗi cấp
     * @param penPercent Lượng xuyên giáp phần trăm tăng mỗi cấp
     * @param lifesteal Lượng hút máu tăng mỗi cấp
     */
    public void updateGrowth(
            BigDecimal hp,
            BigDecimal atk,
            BigDecimal def,
            BigDecimal speed,
            BigDecimal crit,
            BigDecimal critDamage,
            BigDecimal accuracy,
            BigDecimal resistance,
            BigDecimal penFlat,
            BigDecimal penPercent,
            BigDecimal lifesteal
    ) {
        this.hp = hp;
        this.atk = atk;
        this.def = def;
        this.speed = speed;
        this.crit = crit;
        this.critDamage = critDamage;
        this.accuracy = accuracy;
        this.resistance = resistance;
        this.penFlat = penFlat;
        this.penPercent = penPercent;
        this.lifesteal = lifesteal;
    }

    /**
     * Lấy mã nhân vật.
     *
     * @return Mã nhân vật
     */
    public String getHeroCode() {
        return heroCode;
    }

    /**
     * Lấy lượng máu tăng mỗi cấp.
     *
     * @return Máu tăng trưởng
     */
    public BigDecimal getHp() {
        return hp;
    }

    /**
     * Lấy lượng tấn công tăng mỗi cấp.
     *
     * @return Tấn công tăng trưởng
     */
    public BigDecimal getAtk() {
        return atk;
    }

    /**
     * Lấy lượng phòng thủ tăng mỗi cấp.
     *
     * @return Phòng thủ tăng trưởng
     */
    public BigDecimal getDef() {
        return def;
    }

    /**
     * Lấy lượng tốc độ tăng mỗi cấp.
     *
     * @return Tốc độ tăng trưởng
     */
    public BigDecimal getSpeed() {
        return speed;
    }

    /**
     * Lấy lượng chí mạng tăng mỗi cấp.
     *
     * @return Chí mạng tăng trưởng
     */
    public BigDecimal getCrit() {
        return crit;
    }

    /**
     * Lấy lượng sát thương chí mạng tăng mỗi cấp.
     *
     * @return Sát thương chí mạng tăng trưởng
     */
    public BigDecimal getCritDamage() {
        return critDamage;
    }

    /**
     * Lấy lượng chính xác tăng mỗi cấp.
     *
     * @return Chính xác tăng trưởng
     */
    public BigDecimal getAccuracy() {
        return accuracy;
    }

    /**
     * Lấy lượng kháng hiệu ứng tăng mỗi cấp.
     *
     * @return Kháng hiệu ứng tăng trưởng
     */
    public BigDecimal getResistance() {
        return resistance;
    }

    /**
     * Lấy lượng xuyên giáp cố định tăng mỗi cấp.
     *
     * @return Xuyên giáp cố định tăng trưởng
     */
    public BigDecimal getPenFlat() {
        return penFlat;
    }

    /**
     * Lấy lượng xuyên giáp phần trăm tăng mỗi cấp.
     *
     * @return Xuyên giáp phần trăm tăng trưởng
     */
    public BigDecimal getPenPercent() {
        return penPercent;
    }

    /**
     * Lấy lượng hút máu tăng mỗi cấp.
     *
     * @return Hút máu tăng trưởng
     */
    public BigDecimal getLifesteal() {
        return lifesteal;
    }
}