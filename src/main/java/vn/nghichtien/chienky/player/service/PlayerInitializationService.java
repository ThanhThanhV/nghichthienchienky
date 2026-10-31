package vn.nghichtien.chienky.player.service;

import vn.nghichtien.chienky.account.domain.Account;
import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;
import vn.nghichtien.chienky.resource.domain.PlayerResource;
import vn.nghichtien.chienky.resource.domain.ResourceDefinition;
import vn.nghichtien.chienky.resource.repository.PlayerResourceRepository;
import vn.nghichtien.chienky.resource.repository.ResourceDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlayerInitializationService {

    private final PlayerProfileRepository playerProfileRepository;
    private final ResourceDefinitionRepository resourceDefinitionRepository;
    private final PlayerResourceRepository playerResourceRepository;

    public PlayerInitializationService(
            PlayerProfileRepository playerProfileRepository,
            ResourceDefinitionRepository resourceDefinitionRepository,
            PlayerResourceRepository playerResourceRepository
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.resourceDefinitionRepository = resourceDefinitionRepository;
        this.playerResourceRepository = playerResourceRepository;
    }

    /**
     * Khởi tạo hồ sơ và toàn bộ số dư tài nguyên mặc định cho tài khoản mới.
     * Hồ sơ và tài nguyên được lưu trong cùng transaction với thao tác đăng ký tài khoản.
     *
     * @param account Tài khoản vừa được tạo và đã có ID
     * @return Hồ sơ người chơi vừa khởi tạo
     */
    @Transactional
    public PlayerProfile initialize(Account account) {
        if (account == null || account.getId() == null) {
            throw new IllegalArgumentException("Tài khoản không hợp lệ");
        }

        if (playerProfileRepository.findByAccountId(account.getId()).isPresent()) {
            throw new IllegalStateException("Tài khoản đã có hồ sơ người chơi");
        }

        PlayerProfile profile = new PlayerProfile(
                account,
                account.getUsername()
        );

        PlayerProfile savedProfile = playerProfileRepository.save(profile);

        List<ResourceDefinition> definitions =
                resourceDefinitionRepository.findAllByEnabledTrue();

        List<PlayerResource> resources = definitions.stream()
                .map(definition -> new PlayerResource(
                        savedProfile,
                        definition,
                        definition.getInitialBalance()
                ))
                .toList();

        playerResourceRepository.saveAll(resources);

        return savedProfile;
    }
}