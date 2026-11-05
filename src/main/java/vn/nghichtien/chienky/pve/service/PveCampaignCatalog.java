package vn.nghichtien.chienky.pve.service;

import org.springframework.stereotype.Component;
import vn.nghichtien.chienky.pve.domain.PveCampaignStage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Danh mục 10 ải đầu tiên của Campaign. Có thể chuyển cấu hình này sang DB
 * khi bổ sung màn hình quản trị nội dung.
 */
@Component
public class PveCampaignCatalog {

    private final List<PveCampaignStage> stages = List.of(
            stage(1, "Bước chân tu tiên", "dark_tohka", "asuna_dark", "gojo", 200, 50),
            stage(2, "Ma ảnh xuất hiện", "iori", "kirito", "leloud", 300, 75),
            stage(3, "Hắc khí trỗi dậy", "iori_dark", "thor", "dark_tohka", 400, 100),
            stage(4, "Kiếm vực hỗn mang", "kirito", "asuna_dark", "thor_rak", 500, 125),
            stage(5, "Geass thống lĩnh", "leloud", "leloud_dark", "gojo", 600, 150),
            stage(6, "Lôi đình giáng thế", "thor", "thor_rak", "iori", 700, 175),
            stage(7, "Vô hạn kết giới", "gojo", "iori_dark", "leloud_dark", 800, 200),
            stage(8, "Hắc ám thức tỉnh", "dark_tohka", "iori_dark", "leloud_dark", 900, 225),
            stage(9, "Thần chiến", "thor_rak", "gojo", "kirito", 1000, 250),
            stage(10, "Nghịch thiên chung cực", "dark_tohka", "gojo", "thor_rak", 1500, 400)
    );

    public List<PveCampaignStage> getAll() {
        return stages;
    }

    public PveCampaignStage get(int stageNumber) {
        return stages.stream()
                .filter(stage -> stage.stageNumber() == stageNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ải Campaign không tồn tại: " + stageNumber
                ));
    }

    private static PveCampaignStage stage(
            int number,
            String name,
            String enemyOne,
            String enemyTwo,
            String enemyThree,
            int gold,
            int playerExp
    ) {
        return new PveCampaignStage(
                number,
                "CAMPAIGN_" + String.format("%02d", number),
                name,
                "Đánh bại đội hình địch để nhận thưởng và mở khóa ải kế tiếp.",
                List.of(enemyOne, enemyTwo, enemyThree),
                BigDecimal.valueOf(gold),
                BigDecimal.valueOf(playerExp)
        );
    }
}
