package vn.nghichtien.chienky.giftcode.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.giftcode.dto.GiftCodeRedemptionResponse;
import vn.nghichtien.chienky.hero.domain.HeroDefinition;
import vn.nghichtien.chienky.hero.domain.HeroInstance;
import vn.nghichtien.chienky.hero.repository.HeroDefinitionRepository;
import vn.nghichtien.chienky.hero.repository.HeroInstanceRepository;
import vn.nghichtien.chienky.hero.service.HeroAcquisitionService;
import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GiftCodeService {

    public static final String ALL_HEROES_CODE = "NTCK-ALL-HEROES";

    private static final Set<String> SPINE_ASSET_HERO_CODES = Set.of(
            "asuna_dark", "gojo", "iori", "iori_dark", "kirito",
            "leloud", "leloud_dark", "thor", "thor_rak", "tohka_dark"
    );

    private final PlayerProfileRepository playerProfileRepository;
    private final HeroDefinitionRepository heroDefinitionRepository;
    private final HeroInstanceRepository heroInstanceRepository;
    private final HeroAcquisitionService heroAcquisitionService;

    public GiftCodeService(
            PlayerProfileRepository playerProfileRepository,
            HeroDefinitionRepository heroDefinitionRepository,
            HeroInstanceRepository heroInstanceRepository,
            HeroAcquisitionService heroAcquisitionService
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.heroDefinitionRepository = heroDefinitionRepository;
        this.heroInstanceRepository = heroInstanceRepository;
        this.heroAcquisitionService = heroAcquisitionService;
    }

    private boolean hasManagedSpineAssets(HeroDefinition definition) {
        return definition.getSpineSkeleton() != null
                && !definition.getSpineSkeleton().isBlank()
                && definition.getSpineAtlas() != null
                && !definition.getSpineAtlas().isBlank()
                && definition.getSpineTextures() != null
                && !definition.getSpineTextures().isBlank();
    }

    @Transactional
    public GiftCodeRedemptionResponse redeem(String username, String submittedCode) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Không xác định được tài khoản đăng nhập.");
        }
        if (submittedCode == null || submittedCode.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập GIFTCODE.");
        }

        String normalizedCode = submittedCode.trim().toUpperCase();
        if (!ALL_HEROES_CODE.equals(normalizedCode)) {
            throw new IllegalArgumentException("GIFTCODE không hợp lệ hoặc đã hết hạn.");
        }

        PlayerProfile profile = playerProfileRepository
                .findByAccountUsername(username)
                .map(value -> playerProfileRepository.findByIdForUpdate(value.getId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Không tìm thấy hồ sơ người chơi.")))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tài khoản chưa có hồ sơ người chơi."));

        Set<String> ownedCodes = heroInstanceRepository
                .findAllByPlayerProfileIdOrderByObtainedAtDesc(profile.getId())
                .stream()
                .map(HeroInstance::getHeroCode)
                .collect(Collectors.toSet());

        List<HeroDefinition> assetHeroes = heroDefinitionRepository
                .findAllByEnabledTrueOrderBySortOrderAscHeroNameAsc()
                .stream()
                .filter(definition -> SPINE_ASSET_HERO_CODES.contains(
                        definition.getHeroCode()
                ) || hasManagedSpineAssets(definition))
                .toList();

        List<String> grantedNames = new ArrayList<>();
        for (HeroDefinition definition : assetHeroes) {
            if (ownedCodes.contains(definition.getHeroCode())) {
                continue;
            }

            heroAcquisitionService.acquireHero(profile.getId(), definition.getHeroCode());
            ownedCodes.add(definition.getHeroCode());
            grantedNames.add(definition.getHeroName());
        }

        String message = grantedNames.isEmpty()
                ? "GIFTCODE hợp lệ. Bạn đã sở hữu toàn bộ tướng trong bộ asset."
                : "Đổi GIFTCODE thành công! Đã nhận " + grantedNames.size() + " tướng.";

        return new GiftCodeRedemptionResponse(
                message,
                grantedNames.size(),
                ownedCodes.size(),
                List.copyOf(grantedNames)
        );
    }
}
