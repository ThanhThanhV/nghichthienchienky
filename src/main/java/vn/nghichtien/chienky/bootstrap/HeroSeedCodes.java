package vn.nghichtien.chienky.bootstrap;

import java.util.List;

/**
 * Danh sách mã hero đang được seed trong game.
 *
 * Dùng chung cho reset database và kiểm tra đồng bộ bootstrap để không giữ
 * lại hero catalog đã bị loại khỏi seed, ví dụ test-hero.
 */
public final class HeroSeedCodes {

    private static final List<String> CODES = List.of(
            "dark_tohka",
            "asuna_dark",
            "gojo",
            "iori",
            "iori_dark",
            "kirito",
            "leloud",
            "leloud_dark",
            "thor",
            "thor_rak"
    );

    private HeroSeedCodes() {
    }

    public static List<String> current() {
        return CODES;
    }
}
