package vn.nghichtien.chienky.power.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.formation.domain.PlayerFormationSlot;
import vn.nghichtien.chienky.formation.repository.PlayerFormationSlotRepository;
import vn.nghichtien.chienky.hero.domain.HeroDefinition;
import vn.nghichtien.chienky.hero.domain.HeroInstance;
import vn.nghichtien.chienky.hero.dto.HeroStatsResponse;
import vn.nghichtien.chienky.hero.service.HeroStatsService;
import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;
import vn.nghichtien.chienky.power.dto.PowerRankingDetailResponse;
import vn.nghichtien.chienky.power.dto.PowerRankingEntryResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Tính lực chiến trực tiếp từ đội hình chính, không lưu số lực chiến
 * độc lập để tránh dữ liệu bị cũ khi tướng tăng cấp hoặc thay trang bị.
 */
@Service
public class PowerRankingService {

    private static final BigDecimal ATK_WEIGHT = BigDecimal.TEN;
    private static final BigDecimal DEF_WEIGHT = BigDecimal.TEN;
    private static final BigDecimal SPEED_WEIGHT = BigDecimal.valueOf(5);

    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerFormationSlotRepository formationSlotRepository;
    private final HeroStatsService heroStatsService;

    public PowerRankingService(
            PlayerProfileRepository playerProfileRepository,
            PlayerFormationSlotRepository formationSlotRepository,
            HeroStatsService heroStatsService
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.formationSlotRepository = formationSlotRepository;
        this.heroStatsService = heroStatsService;
    }

    @Transactional(readOnly = true)
    public List<PowerRankingEntryResponse> getRanking() {
        List<RankingCandidate> candidates = playerProfileRepository.findAll().stream()
                .filter(profile -> profile.getAccount() != null
                        && profile.getAccount().isEnabled())
                .map(profile -> new RankingCandidate(
                        profile,
                        calculateFormation(profile.getId(), false).totalPower(),
                        formationSlotRepository.countByPlayerProfileId(profile.getId())
                ))
                .filter(candidate -> candidate.teamSize() > 0)
                .sorted(Comparator
                        .comparing(RankingCandidate::totalPower).reversed()
                        .thenComparing(candidate -> candidate.profile().getId()))
                .toList();

        List<PowerRankingEntryResponse> result = new ArrayList<>(candidates.size());
        BigDecimal previousPower = null;
        int currentRank = 0;
        for (int i = 0; i < candidates.size(); i++) {
            RankingCandidate candidate = candidates.get(i);
            if (previousPower == null
                    || candidate.totalPower().compareTo(previousPower) != 0) {
                currentRank = i + 1;
                previousPower = candidate.totalPower();
            }
            PlayerProfile profile = candidate.profile();
            result.add(new PowerRankingEntryResponse(
                    currentRank,
                    profile.getId(),
                    profile.getNickname(),
                    profile.getPlayerLevel(),
                    candidate.totalPower(),
                    candidate.teamSize()
            ));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public PowerRankingDetailResponse getPlayerDetail(Long playerId) {
        PlayerProfile profile = playerProfileRepository.findById(playerId)
                .filter(player -> player.getAccount() != null
                        && player.getAccount().isEnabled())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy người chơi hoặc tài khoản đã bị khóa."
                ));

        FormationCalculation calculation = calculateFormation(playerId, true);
        return new PowerRankingDetailResponse(
                profile.getId(),
                profile.getNickname(),
                profile.getPlayerLevel(),
                calculation.totalPower(),
                calculation.heroes()
        );
    }

    private FormationCalculation calculateFormation(Long playerId, boolean includeHeroes) {
        List<PlayerFormationSlot> slots =
                formationSlotRepository.findAllByPlayerProfileIdOrderByRowAscColumnAsc(playerId);

        BigDecimal total = BigDecimal.ZERO;
        List<PowerRankingDetailResponse.FormationHero> heroes = new ArrayList<>();

        for (PlayerFormationSlot slot : slots) {
            HeroInstance instance = slot.getHeroInstance();
            HeroDefinition definition = instance.getHeroDefinition();
            if (!Boolean.TRUE.equals(definition.getEnabled())) {
                continue;
            }

            HeroStatsResponse response = heroStatsService.calculateStats(instance);
            Map<String, BigDecimal> stats = response.getStats();
            BigDecimal heroPower = calculateHeroPower(stats);
            total = total.add(heroPower);

            if (includeHeroes) {
                heroes.add(new PowerRankingDetailResponse.FormationHero(
                        instance.getId(),
                        definition.getHeroCode(),
                        definition.getHeroName(),
                        definition.getRarity(),
                        definition.getRole(),
                        definition.getAvatarCode(),
                        instance.getHeroLevel(),
                        instance.getStarLevel(),
                        instance.getBreakthroughLevel(),
                        slot.getRow(),
                        slot.getColumn(),
                        heroPower,
                        stats
                ));
            }
        }

        return new FormationCalculation(
                total.setScale(0, RoundingMode.HALF_UP),
                List.copyOf(heroes)
        );
    }

    /**
     * Lực chiến tướng = HP + 10*ATK + 10*DEF + 5*SPEED.
     * Chỉ số đã bao gồm cấp, sao, đột phá và trang bị theo HeroStatsService.
     */
    private BigDecimal calculateHeroPower(Map<String, BigDecimal> stats) {
        return value(stats, "HP")
                .add(value(stats, "ATK").multiply(ATK_WEIGHT))
                .add(value(stats, "DEF").multiply(DEF_WEIGHT))
                .add(value(stats, "SPEED").multiply(SPEED_WEIGHT))
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal value(Map<String, BigDecimal> stats, String key) {
        return stats.getOrDefault(key, BigDecimal.ZERO);
    }

    private record RankingCandidate(
            PlayerProfile profile,
            BigDecimal totalPower,
            int teamSize
    ) {}

    private record FormationCalculation(
            BigDecimal totalPower,
            List<PowerRankingDetailResponse.FormationHero> heroes
    ) {}
}