package vn.nghichtien.chienky.hero.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Định nghĩa nhân vật gốc trong hệ thống game.
 * Đây là dữ liệu dùng chung, không đại diện cho nhân vật người chơi sở hữu.
 */
@Entity
@Table(name = "hero_definitions")
public class HeroDefinition {

    @Id
    @Column(name = "hero_code", length = 50, nullable = false)
    private String heroCode;

    @Column(name = "hero_name", length = 100, nullable = false)
    private String heroName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "rarity", length = 20, nullable = false)
    private String rarity;

    @Column(name = "faction", length = 50)
    private String faction;

    @Column(name = "role", length = 30, nullable = false)
    private String role;

    @Column(name = "avatar_code", length = 50, nullable = false)
    private String avatarCode;

    @Column(name = "spine_skeleton", length = 255)
    private String spineSkeleton;

    @Column(name = "spine_atlas", length = 255)
    private String spineAtlas;

    @Column(name = "spine_textures", columnDefinition = "LONGTEXT")
    private String spineTextures;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Constructor mặc định phục vụ JPA.
     */
    protected HeroDefinition() {
    }

    /**
     * Khởi tạo định nghĩa nhân vật mới.
     *
     * @param heroCode Mã nhân vật
     * @param heroName Tên nhân vật
     * @param rarity Độ hiếm
     * @param role Vai trò chiến đấu
     * @param avatarCode Mã ảnh đại diện
     */
    public HeroDefinition(
            String heroCode,
            String heroName,
            String rarity,
            String role,
            String avatarCode
    ) {
        this.heroCode = heroCode;
        this.heroName = heroName;
        this.rarity = rarity;
        this.role = role;
        this.avatarCode = avatarCode;
        this.enabled = true;
        this.sortOrder = 0;
    }

    /**
     * Cập nhật thời gian tạo trước khi lưu bản ghi.
     */
    @jakarta.persistence.PrePersist
    protected void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Cập nhật thời gian chỉnh sửa trước khi cập nhật bản ghi.
     */
    @jakarta.persistence.PreUpdate
    protected void preUpdate() {
        this.updatedAt = LocalDateTime.now();
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
     * Lấy tên nhân vật.
     *
     * @return Tên nhân vật
     */
    public String getHeroName() {
        return heroName;
    }

    /**
     * Lấy mô tả nhân vật.
     *
     * @return Mô tả
     */
    public String getDescription() {
        return description;
    }

    /**
     * Lấy độ hiếm nhân vật.
     *
     * @return Độ hiếm
     */
    public String getRarity() {
        return rarity;
    }

    /**
     * Lấy phe phái nhân vật.
     *
     * @return Phe phái
     */
    public String getFaction() {
        return faction;
    }

    /**
     * Lấy vai trò chiến đấu.
     *
     * @return Vai trò
     */
    public String getRole() {
        return role;
    }

    /**
     * Lấy mã ảnh đại diện.
     *
     * @return Mã ảnh
     */
    public String getAvatarCode() {
        return avatarCode;
    }

    /**
     * Lấy mã tài nguyên Spine Skeleton.
     *
     * @return Đường dẫn hoặc mã tài nguyên Spine
     */
    public String getSpineSkeleton() {
        return spineSkeleton;
    }

    /**
     * Lấy đường dẫn atlas Spine.
     *
     * @return URL atlas
     */
    public String getSpineAtlas() {
        return spineAtlas;
    }

    /**
     * Lấy danh sách URL texture Spine dưới dạng JSON.
     *
     * @return JSON texture URLs
     */
    public String getSpineTextures() {
        return spineTextures;
    }

    /**
     * Cập nhật đầy đủ bộ tài nguyên Spine của nhân vật.
     *
     * @param skeletonUrl URL skeleton JSON
     * @param atlasUrl URL atlas
     * @param texturesJson Danh sách texture URL dạng JSON
     */
    public void updateSpineAssets(
            String skeletonUrl,
            String atlasUrl,
            String texturesJson
    ) {
        this.spineSkeleton = skeletonUrl;
        this.spineAtlas = atlasUrl;
        this.spineTextures = texturesJson;
    }

    /**
     * Kiểm tra nhân vật có đang được kích hoạt hay không.
     *
     * @return Trạng thái kích hoạt
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Lấy thứ tự hiển thị.
     *
     * @return Thứ tự hiển thị
     */
    public Integer getSortOrder() {
        return sortOrder;
    }

    /**
     * Cập nhật thông tin mô tả nhân vật.
     *
     * @param description Mô tả mới
     */
    public void updateDescription(String description) {
        this.description = description;
    }

    /**
     * Cập nhật phe phái nhân vật.
     *
     * @param faction Phe phái mới
     */
    public void updateFaction(String faction) {
        this.faction = faction;
    }

    /**
     * Cập nhật tài nguyên hiển thị của nhân vật.
     *
     * @param avatarCode Mã ảnh đại diện
     * @param spineSkeleton Mã tài nguyên Spine
     */
    public void updateAssets(String avatarCode, String spineSkeleton) {
        this.avatarCode = avatarCode;
        this.spineSkeleton = spineSkeleton;
    }

    /**
     * Thay đổi trạng thái kích hoạt nhân vật.
     *
     * @param enabled Trạng thái mới
     */
    public void changeEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Cập nhật thứ tự hiển thị nhân vật.
     *
     * @param sortOrder Thứ tự mới
     */
    public void changeSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}