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
 * Lưu trữ chỉ số cơ bản của một nhân vật.
 * Các chỉ số này là nền tảng để tính sức mạnh nhân vật theo cấp độ.
 */
@Entity
@Table(name = "hero_base_stats")
public class HeroBaseStats {

    @Id
    @Column(name = "hero_code", length = 50, nullable = false)
    private String heroCode;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hero_code", nullable = false)
    private HeroDefinition heroDefinition;

    @Column(name = "base_hp", nullable = false, precision = 24, scale = 6)
    private BigDecimal hp = BigDecimal.ZERO;

    @Column(name = "base_atk", nullable = false, precision = 24, scale = 6)
    private BigDecimal atk = BigDecimal.ZERO;

    @Column(name = "base_def", nullable = false, precision = 24, scale = 6)
    private BigDecimal def = BigDecimal.ZERO;

    @Column(name = "base_speed", nullable = false, precision = 24, scale = 6)
    private BigDecimal speed = BigDecimal.ZERO;

    @Column(name = "base_crit", nullable = false, precision = 24, scale = 6)
    private BigDecimal crit = BigDecimal.ZERO;

    @Column(name = "base_crit_damage", nullable = false, precision = 24, scale = 6)
    private BigDecimal critDamage = BigDecimal.ZERO;

    @Column(name = "base_accuracy", nullable = false, precision = 24, scale = 6)
    private BigDecimal accuracy = BigDecimal.ZERO;

    @Column(name = "base_resistance", nullable = false, precision = 24, scale = 6)
    private BigDecimal resistance = BigDecimal.ZERO;

    @Column(name = "base_pen_flat", nullable = false, precision = 24, scale = 6)
    private BigDecimal penFlat = BigDecimal.ZERO;

    @Column(name = "base_pen_percent", nullable = false, precision = 24, scale = 6)
    private BigDecimal penPercent = BigDecimal.ZERO;

    @Column(name = "base_lifesteal", nullable = false, precision = 24, scale = 6)
    private BigDecimal lifesteal = BigDecimal.ZERO;

    /**
     * Constructor mặc định phục vụ JPA.
     */
    protected HeroBaseStats() {
    }

    /**
     * Khởi tạo bộ chỉ số cơ bản cho nhân vật.
     *
     * @param heroDefinition Định nghĩa nhân vật
     * @param hp Máu cơ bản
     * @param atk Tấn công cơ bản
     * @param def Phòng thủ cơ bản
     * @param speed Tốc độ cơ bản
     */
    public HeroBaseStats(
            HeroDefinition heroDefinition,
            BigDecimal hp,
            BigDecimal atk,
            BigDecimal def,
            BigDecimal speed
    ) {
        this.heroDefinition = heroDefinition;
        this.hp = hp;
        this.atk = atk;
        this.def = def;
        this.speed = speed;
        this.crit = BigDecimal.ZERO;
        this.critDamage = BigDecimal.ZERO;
        this.accuracy = BigDecimal.ZERO;
        this.resistance = BigDecimal.ZERO;
        this.penFlat = BigDecimal.ZERO;
        this.penPercent = BigDecimal.ZERO;
        this.lifesteal = BigDecimal.ZERO;
    }

    /**
     * Cập nhật toàn bộ chỉ số cơ bản của nhân vật.
     *
     * @param hp Máu
     * @param atk Tấn công
     * @param def Phòng thủ
     * @param speed Tốc độ
     * @param crit Tỷ lệ chí mạng
     * @param critDamage Sát thương chí mạng
     * @param accuracy Chính xác
     * @param resistance Kháng hiệu ứng
     * @param penFlat Xuyên giáp cố định
     * @param penPercent Xuyên giáp phần trăm
     * @param lifesteal Hút máu
     */
    public void updateStats(
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
     * Lấy chỉ số máu cơ bản.
     *
     * @return Máu
     */
    public BigDecimal getHp() {
        return hp;
    }

    /**
     * Lấy chỉ số tấn công cơ bản.
     *
     * @return Tấn công
     */
    public BigDecimal getAtk() {
        return atk;
    }

    /**
     * Lấy chỉ số phòng thủ cơ bản.
     *
     * @return Phòng thủ
     */
    public BigDecimal getDef() {
        return def;
    }

    /**
     * Lấy tốc độ cơ bản.
     *
     * @return Tốc độ
     */
    public BigDecimal getSpeed() {
        return speed;
    }

    /**
     * Lấy tỷ lệ chí mạng cơ bản.
     *
     * @return Tỷ lệ chí mạng
     */
    public BigDecimal getCrit() {
        return crit;
    }

    /**
     * Lấy sát thương chí mạng cơ bản.
     *
     * @return Sát thương chí mạng
     */
    public BigDecimal getCritDamage() {
        return critDamage;
    }

    /**
     * Lấy độ chính xác cơ bản.
     *
     * @return Độ chính xác
     */
    public BigDecimal getAccuracy() {
        return accuracy;
    }

    /**
     * Lấy kháng hiệu ứng cơ bản.
     *
     * @return Kháng hiệu ứng
     */
    public BigDecimal getResistance() {
        return resistance;
    }

    /**
     * Lấy xuyên giáp cố định cơ bản.
     *
     * @return Xuyên giáp cố định
     */
    public BigDecimal getPenFlat() {
        return penFlat;
    }

    /**
     * Lấy xuyên giáp phần trăm cơ bản.
     *
     * @return Xuyên giáp phần trăm
     */
    public BigDecimal getPenPercent() {
        return penPercent;
    }

    /**
     * Lấy hút máu cơ bản.
     *
     * @return Hút máu
     */
    public BigDecimal getLifesteal() {
        return lifesteal;
    }
}