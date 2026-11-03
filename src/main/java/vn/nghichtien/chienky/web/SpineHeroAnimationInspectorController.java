package vn.nghichtien.chienky.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Trang kiểm tra toàn bộ animation nằm trong assets/spine/heroes.
 * Chỉ đọc metadata asset; không thay đổi mapping kỹ năng hoặc dữ liệu chiến đấu.
 */
@Controller
public class SpineHeroAnimationInspectorController {

    private final Path heroesRoot;

    public SpineHeroAnimationInspectorController(
            @Value("${game.assets.spine-heroes-path:src/main/resources/static/assets/spine/heroes}")
            String heroesPath
    ) {
        this.heroesRoot = Path.of(heroesPath).toAbsolutePath().normalize();
    }

    @GetMapping("/admin/spine-animations")
    public String inspector() {
        return "spine/hero-animation-inspector";
    }

    /**
     * Trả về các thư mục tướng và các bộ skeleton/atlas có thể preview.
     * Thứ tự sắp xếp animation được thực hiện ở client theo tên tự nhiên,
     * sau đó đánh số hiển thị từ 1.
     */
    @ResponseBody
    @GetMapping("/admin/api/spine-animations")
    public List<Map<String, Object>> listHeroAssets() throws IOException {
        if (!Files.isDirectory(heroesRoot)) {
            return List.of();
        }

        List<Map<String, Object>> heroes = new ArrayList<>();
        try (Stream<Path> directories = Files.list(heroesRoot)) {
            for (Path directory : directories
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList()) {

                List<Map<String, String>> skeletons = new ArrayList<>();
                try (Stream<Path> files = Files.list(directory)) {
                    for (Path json : files
                            .filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                            .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER))
                            .toList()) {

                        String assetId = json.getFileName().toString();
                        assetId = assetId.substring(0, assetId.length() - 5);
                        Path atlas = directory.resolve(assetId + ".atlas");
                        if (!Files.isRegularFile(atlas)) {
                            continue;
                        }

                        Map<String, String> asset = new LinkedHashMap<>();
                        asset.put("id", assetId);
                        asset.put("skeleton", assetUrl(directory, assetId + ".json"));
                        asset.put("atlas", assetUrl(directory, assetId + ".atlas"));
                        skeletons.add(asset);
                    }
                }

                if (!skeletons.isEmpty()) {
                    Map<String, Object> hero = new LinkedHashMap<>();
                    hero.put("code", directory.getFileName().toString());
                    hero.put("assets", skeletons);
                    heroes.add(hero);
                }
            }
        }
        return heroes;
    }

    private String assetUrl(Path directory, String fileName) {
        return "/assets/spine/heroes/" + directory.getFileName() + "/" + fileName;
    }
}
