package vn.nghichtien.chienky.hero.service;

import vn.nghichtien.chienky.equipment.domain.*;
import vn.nghichtien.chienky.equipment.repository.*;
import vn.nghichtien.chienky.hero.domain.HeroBaseStats;
import vn.nghichtien.chienky.hero.domain.HeroDefinition;
import vn.nghichtien.chienky.hero.domain.HeroGrowthConfig;
import vn.nghichtien.chienky.hero.dto.HeroStatsResponse;
import vn.nghichtien.chienky.hero.repository.HeroBaseStatsRepository;
import vn.nghichtien.chienky.hero.repository.HeroDefinitionRepository;
import vn.nghichtien.chienky.hero.repository.HeroGrowthConfigRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.hero.domain.HeroInstance;
import vn.nghichtien.chienky.hero.domain.HeroStatProgressionBonus;
import vn.nghichtien.chienky.hero.repository.HeroStatProgressionBonusRepository;
import vn.nghichtien.chienky.equipment.repository.EquipmentBaseStatRepository;
import vn.nghichtien.chienky.equipment.repository.HeroEquipmentSlotRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service tính toán chỉ số nền của nhân vật theo cấp độ.
 */
@Service
public class HeroStatsService {

    private final HeroDefinitionRepository heroDefinitionRepository;
    private final HeroBaseStatsRepository heroBaseStatsRepository;
    private final HeroGrowthConfigRepository heroGrowthConfigRepository;
    private final HeroStatProgressionBonusRepository heroStatProgressionBonusRepository;
    private final HeroEquipmentSlotRepository heroEquipmentSlotRepository;
    private final EquipmentBaseStatRepository equipmentBaseStatRepository;
    private final EquipmentEnhancementConfigRepository enhancementConfigRepository;


    /**
     * Khởi tạo service tính chỉ số nhân vật.
     *
     * @param heroDefinitionRepository Repository nhân vật
     * @param heroBaseStatsRepository Repository chỉ số cơ bản
     * @param heroGrowthConfigRepository Repository tăng trưởng
     */
    public HeroStatsService(
            HeroDefinitionRepository heroDefinitionRepository,
            HeroBaseStatsRepository heroBaseStatsRepository,
            HeroGrowthConfigRepository heroGrowthConfigRepository,
            HeroStatProgressionBonusRepository heroStatProgressionBonusRepository,
            HeroEquipmentSlotRepository heroEquipmentSlotRepository,
            EquipmentBaseStatRepository equipmentBaseStatRepository,
            EquipmentEnhancementConfigRepository enhancementConfigRepository
    ) {
        this.heroDefinitionRepository = heroDefinitionRepository;
        this.heroBaseStatsRepository = heroBaseStatsRepository;
        this.heroGrowthConfigRepository = heroGrowthConfigRepository;
        this.heroStatProgressionBonusRepository = heroStatProgressionBonusRepository;
        this.heroEquipmentSlotRepository  = heroEquipmentSlotRepository;
        this.equipmentBaseStatRepository = equipmentBaseStatRepository;
        this.enhancementConfigRepository  = enhancementConfigRepository;
    }

    /**
     * Tính toàn bộ chỉ số nền của nhân vật tại cấp độ được yêu cầu.
     *
     * Công thức:
     * Chỉ số tại cấp N = Chỉ số cơ bản + Tăng trưởng * (N - 1).
     *
     * @param heroCode Mã nhân vật
     * @param heroLevel Cấp độ cần tính
     * @return Kết quả chỉ số nhân vật
     */
    @Transactional(readOnly = true)
    public HeroStatsResponse calculateStats(
            String heroCode,
            Integer heroLevel
    ) {
        if (heroCode == null || heroCode.isBlank()) {
            throw new IllegalArgumentException("Mã nhân vật không hợp lệ");
        }

        if (heroLevel == null || heroLevel < 1) {
            throw new IllegalArgumentException("Cấp độ nhân vật không hợp lệ");
        }

        HeroDefinition heroDefinition = heroDefinitionRepository
                .findByHeroCodeAndEnabled(heroCode, true)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy nhân vật hoặc nhân vật đã bị khóa"
                ));

        HeroBaseStats baseStats = heroBaseStatsRepository
                .findByHeroCode(heroDefinition.getHeroCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Nhân vật chưa được cấu hình chỉ số cơ bản"
                ));

        HeroGrowthConfig growthConfig = heroGrowthConfigRepository
                .findByHeroCode(heroDefinition.getHeroCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Nhân vật chưa được cấu hình tăng trưởng"
                ));

        Map<String, BigDecimal> stats = new LinkedHashMap<>();

        BigDecimal levelMultiplier = BigDecimal.valueOf(heroLevel - 1L);

        stats.put("HP", calculate(
                baseStats.getHp(),
                growthConfig.getHp(),
                levelMultiplier
        ));

        stats.put("ATK", calculate(
                baseStats.getAtk(),
                growthConfig.getAtk(),
                levelMultiplier
        ));

        stats.put("DEF", calculate(
                baseStats.getDef(),
                growthConfig.getDef(),
                levelMultiplier
        ));

        stats.put("SPEED", calculate(
                baseStats.getSpeed(),
                growthConfig.getSpeed(),
                levelMultiplier
        ));

        stats.put("CRIT", calculate(
                baseStats.getCrit(),
                growthConfig.getCrit(),
                levelMultiplier
        ));

        stats.put("CRIT_DAMAGE", calculate(
                baseStats.getCritDamage(),
                growthConfig.getCritDamage(),
                levelMultiplier
        ));

        stats.put("ACCURACY", calculate(
                baseStats.getAccuracy(),
                growthConfig.getAccuracy(),
                levelMultiplier
        ));

        stats.put("RESISTANCE", calculate(
                baseStats.getResistance(),
                growthConfig.getResistance(),
                levelMultiplier
        ));

        stats.put("PEN_FLAT", calculate(
                baseStats.getPenFlat(),
                growthConfig.getPenFlat(),
                levelMultiplier
        ));

        stats.put("PEN_PERCENT", calculate(
                baseStats.getPenPercent(),
                growthConfig.getPenPercent(),
                levelMultiplier
        ));

        stats.put("LIFESTEAL", calculate(
                baseStats.getLifesteal(),
                growthConfig.getLifesteal(),
                levelMultiplier
        ));

        return new HeroStatsResponse(
                heroDefinition.getHeroCode(),
                heroLevel,
                stats
        );
    }

    /**
     * Tính một chỉ số theo cấp độ nhân vật.
     *
     * @param baseValue Giá trị cơ bản
     * @param growthValue Giá trị tăng mỗi cấp
     * @param levelMultiplier Số cấp tăng thêm
     * @return Giá trị chỉ số tại cấp độ yêu cầu
     */
    private BigDecimal calculate(
            BigDecimal baseValue,
            BigDecimal growthValue,
            BigDecimal levelMultiplier
    ) {
        BigDecimal base = baseValue == null
                ? BigDecimal.ZERO
                : baseValue;

        BigDecimal growth = growthValue == null
                ? BigDecimal.ZERO
                : growthValue;

        return base.add(growth.multiply(levelMultiplier));
    }

    /**
     * Tính toàn bộ chỉ số tướng theo cấp độ, cấp sao và cấp đột phá hiện tại.
     *
     * @param instance tướng của người chơi
     * @return thông tin chỉ số tổng hợp của tướng
     */
    @Transactional(readOnly = true)
    public HeroStatsResponse calculateStats(HeroInstance instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Hero instance is required.");
        }

        String heroCode = instance.getHeroCode();

        Map<String, BigDecimal> stats = calculateBaseStats(
                heroCode,
                instance.getHeroLevel()
        );

        applyProgressionBonuses(
                stats,
                heroCode,
                "STAR",
                instance.getStarLevel()
        );

        applyProgressionBonuses(
                stats,
                heroCode,
                "BREAKTHROUGH",
                instance.getBreakthroughLevel()
        );

        applyEquipmentBonuses(instance, stats);

        return new HeroStatsResponse(
                heroCode,
                instance.getHeroLevel(),
                stats
        );
    }

    /**
     * Lấy chỉ số cơ bản theo cấp tướng từ cấu hình tăng trưởng hiện có.
     *
     * @param heroCode mã tướng
     * @param heroLevel cấp hiện tại
     * @return bản đồ chỉ số cơ bản
     */
    private Map<String, BigDecimal> calculateBaseStats(
            String heroCode,
            Integer heroLevel
    ) {
        HeroStatsResponse response = calculateStats(heroCode, heroLevel);
        return new HashMap<>(response.getStats());
    }

    /**
     * Cộng bonus sao hoặc đột phá vào các chỉ số hiện tại.
     *
     * @param stats bản đồ chỉ số cần cập nhật
     * @param heroCode mã tướng
     * @param progressionType loại tiến trình STAR hoặc BREAKTHROUGH
     * @param targetLevel cấp tiến trình hiện tại
     */
    private void applyProgressionBonuses(
            Map<String, BigDecimal> stats,
            String heroCode,
            String progressionType,
            Integer targetLevel
    ) {
        if (targetLevel == null || targetLevel <= 0) {
            return;
        }

        List<HeroStatProgressionBonus> bonuses =
                heroStatProgressionBonusRepository
                        .findAllByHeroCodeAndProgressionTypeAndTargetLevelLessThanEqualAndEnabledTrue(
                                heroCode,
                                progressionType,
                                targetLevel
                        );

        for (HeroStatProgressionBonus bonus : bonuses) {
            String statCode = bonus.getStatCode();

            if (!stats.containsKey(statCode)) {
                throw new IllegalStateException(
                        "Unknown hero stat code: " + statCode
                );
            }

            stats.merge(
                    statCode,
                    bonus.getBonusValue(),
                    BigDecimal::add
            );
        }
    }

    /**
     * Cộng chỉ số của từng món trang bị đang mặc vào chỉ số tướng.
     * Mỗi món được tính cấp cường hóa độc lập, kể cả khi trùng equipmentCode.
     *
     * @param instance tướng cần tính chỉ số
     * @param stats bản đồ chỉ số hiện tại
     */
    private void applyEquipmentBonuses(
            HeroInstance instance,
            Map<String, BigDecimal> stats
    ) {
        List<HeroEquipmentSlot> slots =
                heroEquipmentSlotRepository.findAllByHeroInstanceAndPlayer(
                        instance.getId(),
                        instance.getPlayerProfile().getId()
                );

        if (slots.isEmpty()) {
            return;
        }

        for (HeroEquipmentSlot slot : slots) {
            PlayerEquipment equipment = slot.getPlayerEquipment();

            if (equipment == null) {
                continue;
            }

            EquipmentDefinition definition = equipment.getEquipmentDefinition();

            List<EquipmentBaseStat> baseStats =
                    equipmentBaseStatRepository
                            .findAllByEquipmentDefinitionEquipmentCodeIn(
                                    List.of(definition.getEquipmentCode())
                            );

            BigDecimal bonusPercent = getEnhancementBonusPercent(
                    equipment.getEnhancementLevel()
            );

            BigDecimal multiplier = BigDecimal.ONE.add(
                    bonusPercent.divide(
                            BigDecimal.valueOf(100),
                            6,
                            RoundingMode.HALF_UP
                    )
            );

            for (EquipmentBaseStat equipmentStat : baseStats) {
                BigDecimal finalValue = equipmentStat.getStatValue()
                        .multiply(multiplier);

                stats.merge(
                        equipmentStat.getStatCode(),
                        finalValue,
                        BigDecimal::add
                );
            }
        }
    }

    /**
     * Lấy phần trăm tăng chỉ số tương ứng với cấp cường hóa.
     *
     * @param enhancementLevel cấp cường hóa
     * @return phần trăm tăng thêm, bằng 0 nếu chưa cường hóa
     */
    private BigDecimal getEnhancementBonusPercent(Integer enhancementLevel) {
        if (enhancementLevel == null || enhancementLevel <= 0) {
            return BigDecimal.ZERO;
        }

        EquipmentEnhancementConfig config = enhancementConfigRepository
                .findByTargetLevelAndEnabledTrue(enhancementLevel)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing equipment enhancement configuration."
                ));

        return config.getStatBonusPercent();
    }


}