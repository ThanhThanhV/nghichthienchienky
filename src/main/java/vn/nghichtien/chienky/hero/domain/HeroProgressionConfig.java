package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình điều kiện và chi phí nâng sao hoặc đột phá nhân vật.
 */
@Entity
@Table(name = "hero_progression_configs")
public class HeroProgressionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hero_code", nullable = false, length = 50)
    private String heroCode;

    @Column(name = "progression_type", nullable = false, length = 20)
    private String progressionType;

    @Column(name = "target_level", nullable = false)
    private Integer targetLevel;

    @Column(name = "required_hero_level", nullable = false)
    private Integer requiredHeroLevel;

    @Column(name = "required_star_level", nullable = false)
    private Integer requiredStarLevel;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @OneToMany(mappedBy = "progressionConfig", fetch = FetchType.LAZY)
    private List<HeroProgressionCost> costs = new ArrayList<>();

    protected HeroProgressionConfig() {
    }

    /**
     * Lấy ID cấu hình.
     *
     * @return ID cấu hình
     */
    public Long getId() {
        return id;
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
     * Lấy loại nâng cấp STAR hoặc BREAKTHROUGH.
     *
     * @return Loại nâng cấp
     */
    public String getProgressionType() {
        return progressionType;
    }

    /**
     * Lấy cấp mục tiêu sau nâng cấp.
     *
     * @return Cấp mục tiêu
     */
    public Integer getTargetLevel() {
        return targetLevel;
    }

    /**
     * Lấy cấp nhân vật tối thiểu.
     *
     * @return Cấp nhân vật yêu cầu
     */
    public Integer getRequiredHeroLevel() {
        return requiredHeroLevel;
    }

    /**
     * Lấy cấp sao tối thiểu.
     *
     * @return Cấp sao yêu cầu
     */
    public Integer getRequiredStarLevel() {
        return requiredStarLevel;
    }

    /**
     * Lấy trạng thái cấu hình.
     *
     * @return Trạng thái hoạt động
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Lấy danh sách chi phí nâng cấp.
     *
     * @return Danh sách chi phí
     */
    public List<HeroProgressionCost> getCosts() {
        return costs;
    }
}