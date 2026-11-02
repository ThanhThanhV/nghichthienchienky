package vn.nghichtien.chienky.hero.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO thông tin nhân vật người chơi sở hữu.
 */
public class HeroResponse {

    private final Long instanceId;
    private final String heroCode;
    private final String heroName;
    private final String description;
    private final String rarity;
    private final String faction;
    private final String role;
    private final String avatarCode;
    private final String spineSkeleton;
    private final String spineAtlas;
    private final String spineTextures;
    private final String spineSkillAnimations;

    private final Integer heroLevel;
    private final Integer starLevel;
    private final Integer breakthroughLevel;
    private final Long experience;
    private final LocalDateTime obtainedAt;

    private final HeroStatsResponse stats;
    private final List<HeroSkillResponse> skills;

    /**
     * Khởi tạo DTO nhân vật.
     *
     * @param instanceId ID bản sao nhân vật
     * @param heroCode Mã nhân vật
     * @param heroName Tên nhân vật
     * @param description Mô tả
     * @param rarity Độ hiếm
     * @param faction Phe phái
     * @param role Vai trò
     * @param avatarCode Mã ảnh đại diện
     * @param spineSkeleton URL skeleton JSON
     * @param spineAtlas URL atlas
     * @param spineTextures Danh sách URL texture dạng JSON
     * @param spineSkillAnimations Mapping animation kỹ năng dạng JSON
     * @param heroLevel Cấp độ
     * @param starLevel Cấp sao
     * @param breakthroughLevel Cấp đột phá
     * @param experience Kinh nghiệm
     * @param obtainedAt Thời điểm nhận
     * @param stats Chỉ số nhân vật
     * @param skills Danh sách kỹ năng
     */
    public HeroResponse(
            Long instanceId,
            String heroCode,
            String heroName,
            String description,
            String rarity,
            String faction,
            String role,
            String avatarCode,
            String spineSkeleton,
            String spineAtlas,
            String spineTextures,
            String spineSkillAnimations,
            Integer heroLevel,
            Integer starLevel,
            Integer breakthroughLevel,
            Long experience,
            LocalDateTime obtainedAt,
            HeroStatsResponse stats,
            List<HeroSkillResponse> skills
    ) {
        this.instanceId = instanceId;
        this.heroCode = heroCode;
        this.heroName = heroName;
        this.description = description;
        this.rarity = rarity;
        this.faction = faction;
        this.role = role;
        this.avatarCode = avatarCode;
        this.spineSkeleton = spineSkeleton;
        this.spineAtlas = spineAtlas;
        this.spineTextures = spineTextures;
        this.spineSkillAnimations = spineSkillAnimations;
        this.heroLevel = heroLevel;
        this.starLevel = starLevel;
        this.breakthroughLevel = breakthroughLevel;
        this.experience = experience;
        this.obtainedAt = obtainedAt;
        this.stats = stats;
        this.skills = List.copyOf(skills);
    }

    /**
     * Lấy ID bản sao nhân vật.
     *
     * @return ID nhân vật
     */
    public Long getInstanceId() {
        return instanceId;
    }

    /**
     * Lấy mã nhân vật gốc.
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
     * Lấy độ hiếm.
     *
     * @return Độ hiếm
     */
    public String getRarity() {
        return rarity;
    }

    /**
     * Lấy phe phái.
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
     * Lấy mã tài nguyên Spine.
     *
     * @return Mã tài nguyên
     */
    public String getSpineSkeleton() {
        return spineSkeleton;
    }

    public String getSpineAtlas() {
        return spineAtlas;
    }

    public String getSpineTextures() {
        return spineTextures;
    }

    public String getSpineSkillAnimations() {
        return spineSkillAnimations;
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
     * Lấy cấp sao.
     *
     * @return Cấp sao
     */
    public Integer getStarLevel() {
        return starLevel;
    }

    /**
     * Lấy cấp đột phá.
     *
     * @return Cấp đột phá
     */
    public Integer getBreakthroughLevel() {
        return breakthroughLevel;
    }

    /**
     * Lấy kinh nghiệm.
     *
     * @return Kinh nghiệm
     */
    public Long getExperience() {
        return experience;
    }

    /**
     * Lấy thời điểm nhận nhân vật.
     *
     * @return Thời điểm nhận
     */
    public LocalDateTime getObtainedAt() {
        return obtainedAt;
    }

    /**
     * Lấy chỉ số nhân vật.
     *
     * @return Chỉ số
     */
    public HeroStatsResponse getStats() {
        return stats;
    }

    /**
     * Lấy danh sách kỹ năng.
     *
     * @return Danh sách kỹ năng
     */
    public List<HeroSkillResponse> getSkills() {
        return skills;
    }
}