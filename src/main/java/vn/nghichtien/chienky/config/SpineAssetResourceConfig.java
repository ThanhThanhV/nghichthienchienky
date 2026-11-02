package vn.nghichtien.chienky.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Phục vụ Spine assets từ thư mục filesystem có thể cấu hình.
 */
@Configuration
public class SpineAssetResourceConfig implements WebMvcConfigurer {

    private final String resourceLocation;

    public SpineAssetResourceConfig(
            @Value("${game.assets.spine-heroes-path:src/main/resources/static/assets/spine/heroes}")
            String assetPath
    ) {
        String location = Path.of(assetPath).toAbsolutePath().normalize().toUri().toString();
        this.resourceLocation = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/spine/heroes/**")
                .addResourceLocations(resourceLocation);
    }
}
