package vn.nghichtien.chienky.player.service;

import vn.nghichtien.chienky.account.entity.Account;
import vn.nghichtien.chienky.formation.domain.PlayerFormationSlot;
import vn.nghichtien.chienky.formation.repository.PlayerFormationSlotRepository;
import vn.nghichtien.chienky.hero.domain.HeroDefinition;
import vn.nghichtien.chienky.hero.domain.HeroInstance;
import vn.nghichtien.chienky.hero.domain.HeroSkillDefinition;
import vn.nghichtien.chienky.hero.repository.HeroDefinitionRepository;
import vn.nghichtien.chienky.hero.repository.HeroInstanceRepository;
import vn.nghichtien.chienky.hero.repository.HeroSkillDefinitionRepository;
import vn.nghichtien.chienky.hero.service.HeroAcquisitionService;
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

    private static final int REQUIRED_STARTER_SKILLS = 5;

    private final PlayerProfileRepository playerProfileRepository;
    private final ResourceDefinitionRepository resourceDefinitionRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final HeroDefinitionRepository heroDefinitionRepository;
    private final HeroSkillDefinitionRepository heroSkillDefinitionRepository;
    private final HeroAcquisitionService heroAcquisitionService;
    private final HeroInstanceRepository heroInstanceRepository;
    private final PlayerFormationSlotRepository formationSlotRepository;

    public PlayerInitializationService(
            PlayerProfileRepository playerProfileRepository,
            ResourceDefinitionRepository resourceDefinitionRepository,
            PlayerResourceRepository playerResourceRepository,
            HeroDefinitionRepository heroDefinitionRepository,
            HeroSkillDefinitionRepository heroSkillDefinitionRepository,
            HeroAcquisitionService heroAcquisitionService,
            HeroInstanceRepository heroInstanceRepository,
            PlayerFormationSlotRepository formationSlotRepository
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.resourceDefinitionRepository = resourceDefinitionRepository;
        this.playerResourceRepository = playerResourceRepository;
        this.heroDefinitionRepository = heroDefinitionRepository;
        this.heroSkillDefinitionRepository = heroSkillDefinitionRepository;
        this.heroAcquisitionService = heroAcquisitionService;
        this.heroInstanceRepository = heroInstanceRepository;
        this.formationSlotRepository = formationSlotRepository;
    }

    /**
     * Khởi tạo hồ sơ, tài nguyên và nhân vật khởi đầu cho tài khoản mới.
     */
    @Transactional
    public PlayerProfile initialize(Account account) {
        if (account == null || account.getId() == null) {
            throw new IllegalArgumentException("Tài khoản không hợp lệ");
        }

        if (playerProfileRepository.findByAccountId(account.getId()).isPresent()) {
            throw new IllegalStateException("Tài khoản đã có hồ sơ người chơi");
        }

        PlayerProfile profile = new PlayerProfile(account, account.getUsername());
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

        ensureStarterFormation(savedProfile.getId());

        return savedProfile;
    }

    /**
     * Bảo đảm người chơi có ít nhất một nhân vật và một vị trí đội hình.
     * Được gọi khi mở màn hình đội hình để hỗ trợ cả tài khoản đã tạo trước đó.
     */
    @Transactional
    public void ensureStarterFormation(Long playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("Player ID không hợp lệ");
        }

        PlayerProfile profile = playerProfileRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy hồ sơ người chơi"
                ));

        List<HeroInstance> ownedHeroes =
                heroInstanceRepository.findAllByPlayerProfileIdOrderByObtainedAtDesc(
                        playerId
                );

        if (ownedHeroes.isEmpty()) {
            HeroDefinition starterDefinition = findStarterDefinition();

            HeroInstance starter = heroAcquisitionService.acquireHero(
                    playerId,
                    starterDefinition.getHeroCode()
            );

            ownedHeroes = List.of(starter);
        }

        List<PlayerFormationSlot> currentSlots =
                formationSlotRepository
                        .findAllByPlayerProfileIdOrderByRowAscColumnAsc(playerId);

        if (currentSlots.isEmpty()) {
            formationSlotRepository.save(
                    new PlayerFormationSlot(profile, ownedHeroes.get(0), 1, 1)
            );
        }
    }

    /**
     * Chọn nhân vật đang bật có đủ 5 kỹ năng theo thứ tự 1..5.
     */
    private HeroDefinition findStarterDefinition() {
        return heroDefinitionRepository
                .findAllByEnabledTrueOrderBySortOrderAscHeroNameAsc()
                .stream()
                .filter(definition -> definition.getSpineSkeleton() != null
                        && !definition.getSpineSkeleton().isBlank())
                .filter(this::hasValidStarterSkills)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa có nhân vật khởi đầu hợp lệ. "
                                + "Cần ít nhất một nhân vật đang bật với đủ 5 kỹ năng theo thứ tự 1..5."
                ));
    }

    private boolean hasValidStarterSkills(HeroDefinition definition) {
        List<HeroSkillDefinition> skills =
                heroSkillDefinitionRepository
                        .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                                definition.getHeroCode()
                        );

        if (skills.size() != REQUIRED_STARTER_SKILLS) {
            return false;
        }

        for (int i = 0; i < skills.size(); i++) {
            Integer skillOrder = skills.get(i).getSkillOrder();
            if (skillOrder == null || skillOrder != i + 1) {
                return false;
            }
        }

        return true;
    }
}