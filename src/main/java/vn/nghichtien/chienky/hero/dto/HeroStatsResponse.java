package vn.nghichtien.chienky.hero.dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTO chứa toàn bộ chỉ số nền của nhân vật tại một cấp độ.
 */
public class HeroStatsResponse {

    private final String heroCode;
    private final Integer heroLevel;
    private final Map<String, BigDecimal> stats;

    /**
     * Khởi tạo kết quả tính chỉ số nhân vật.
     *
     * @param heroCode Mã nhân vật
     * @param heroLevel Cấp độ nhân vật
     * @param stats Danh sách chỉ số đã tính
     */
    public HeroStatsResponse(
            String heroCode,
            Integer heroLevel,
            Map<String, BigDecimal> stats
    ) {
        this.heroCode = heroCode;
        this.heroLevel = heroLevel;
        this.stats = new LinkedHashMap<>(stats);
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
     * Lấy cấp độ nhân vật.
     *
     * @return Cấp độ
     */
    public Integer getHeroLevel() {
        return heroLevel;
    }

    /**
     * Lấy danh sách chỉ số đã tính.
     *
     * @return Map tên chỉ số và giá trị
     */
    public Map<String, BigDecimal> getStats() {
        return new LinkedHashMap<>(stats);
    }
}