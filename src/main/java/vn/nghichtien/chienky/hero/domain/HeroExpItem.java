package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Cấu hình lượng kinh nghiệm nhận được khi sử dụng một vật phẩm EXP.
 */
@Entity
@Table(name = "hero_exp_items")
public class HeroExpItem {

    @Id
    @Column(name = "resource_code", length = 50)
    private String resourceCode;

    @Column(name = "exp_per_item", nullable = false)
    private Long expPerItem;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected HeroExpItem() {
    }

    /**
     * Lấy mã tài nguyên của vật phẩm.
     *
     * @return Mã tài nguyên
     */
    public String getResourceCode() {
        return resourceCode;
    }

    /**
     * Lấy lượng EXP của một vật phẩm.
     *
     * @return EXP mỗi vật phẩm
     */
    public Long getExpPerItem() {
        return expPerItem;
    }

    /**
     * Kiểm tra vật phẩm có đang được phép sử dụng.
     *
     * @return Trạng thái hoạt động
     */
    public Boolean getEnabled() {
        return enabled;
    }
}