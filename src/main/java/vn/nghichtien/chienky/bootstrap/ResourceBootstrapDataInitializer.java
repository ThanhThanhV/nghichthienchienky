package vn.nghichtien.chienky.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.player.domain.PlayerLevelConfig;
import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerLevelConfigRepository;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;
import vn.nghichtien.chienky.resource.domain.PlayerResource;
import vn.nghichtien.chienky.resource.domain.ResourceDefinition;
import vn.nghichtien.chienky.resource.repository.PlayerResourceRepository;
import vn.nghichtien.chienky.resource.repository.ResourceDefinitionRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Khởi tạo danh mục tài nguyên mặc định và bù số dư còn thiếu cho người chơi cũ.
 *
 * Dữ liệu được quản lý bằng JPA Entity, không phụ thuộc Flyway hay SQL migration.
 */
@Component
@Order(1)
public class ResourceBootstrapDataInitializer implements ApplicationRunner {

    private final ResourceDefinitionRepository resourceDefinitionRepository;
    private final PlayerLevelConfigRepository playerLevelConfigRepository;
    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerResourceRepository playerResourceRepository;

    public ResourceBootstrapDataInitializer(
            ResourceDefinitionRepository resourceDefinitionRepository,
            PlayerLevelConfigRepository playerLevelConfigRepository,
            PlayerProfileRepository playerProfileRepository,
            PlayerResourceRepository playerResourceRepository
    ) {
        this.resourceDefinitionRepository = resourceDefinitionRepository;
        this.playerLevelConfigRepository = playerLevelConfigRepository;
        this.playerProfileRepository = playerProfileRepository;
        this.playerResourceRepository = playerResourceRepository;
    }

    /**
     * Bảo đảm danh mục tài nguyên mặc định tồn tại và mọi hồ sơ người chơi
     * đều có bản ghi số dư cho các tài nguyên đang được kích hoạt.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedResourceDefinitions();
        seedPlayerLevelConfigs();
        backfillPlayerResources();
    }

    /**
     * Tạo các loại tài nguyên mặc định nếu database chưa có.
     * Không cập nhật cấu hình hoặc số dư của các loại tài nguyên đã tồn tại.
     */
    private void seedResourceDefinitions() {
        List<ResourceDefinition> defaults = List.of(
                new ResourceDefinition(
                        "GOLD", "Vàng", "CURRENCY",
                        new BigDecimal("1000"), null
                ),
                new ResourceDefinition(
                        "PLAYER_EXP", "Kinh nghiệm người chơi", "EXPERIENCE",
                        BigDecimal.ZERO, null
                ),
                new ResourceDefinition(
                        "STAMINA", "Thể lực", "CONSUMABLE",
                        new BigDecimal("100"), new BigDecimal("100")
                ),
                new ResourceDefinition(
                        "PREMIUM_CURRENCY", "Nguyên bảo", "CURRENCY",
                        BigDecimal.ZERO, null
                )
        );

        for (ResourceDefinition definition : defaults) {
            if (!resourceDefinitionRepository.existsById(
                    definition.getResourceCode()
            )) {
                resourceDefinitionRepository.save(definition);
            }
        }
    }


    /**
     * Seed đường cong EXP mẫu cho 100 cấp nếu cấu hình cấp đó chưa tồn tại.
     * Ngưỡng khởi đầu: 100 EXP tổng cho mỗi cấp kế tiếp; admin có thể chỉnh DB.
     */
    private void seedPlayerLevelConfigs() {
        for (int level = 1; level <= 100; level++) {
            if (!playerLevelConfigRepository.existsById(level)) {
                BigDecimal requiredTotalExp = BigDecimal.valueOf((long) (level - 1) * 100);
                playerLevelConfigRepository.save(
                        new PlayerLevelConfig(level, requiredTotalExp)
                );
            }
        }
    }

    /**
     * Bổ sung các bản ghi số dư còn thiếu cho cả người chơi cũ.
     * Số dư đã tồn tại luôn được giữ nguyên.
     */
    private void backfillPlayerResources() {
        List<ResourceDefinition> enabledDefinitions =
                resourceDefinitionRepository.findAllByEnabledTrue();

        if (enabledDefinitions.isEmpty()) {
            return;
        }

        List<PlayerResource> missingResources = new ArrayList<>();

        for (PlayerProfile player : playerProfileRepository.findAll()) {
            Set<String> existingCodes = new HashSet<>(
                    playerResourceRepository
                            .findAllByPlayerIdWithDefinition(player.getId())
                            .stream()
                            .map(PlayerResource::getResourceCode)
                            .toList()
            );

            for (ResourceDefinition definition : enabledDefinitions) {
                if (existingCodes.add(definition.getResourceCode())) {
                    missingResources.add(new PlayerResource(
                            player,
                            definition,
                            definition.getInitialBalance()
                    ));
                }
            }
        }

        if (!missingResources.isEmpty()) {
            playerResourceRepository.saveAll(missingResources);
        }
    }
}
