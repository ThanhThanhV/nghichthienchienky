package vn.nghichtien.chienky.hero.service;

import vn.nghichtien.chienky.hero.domain.*;
import vn.nghichtien.chienky.hero.dto.HeroResponse;
import vn.nghichtien.chienky.hero.dto.HeroExpItemResponse;
import vn.nghichtien.chienky.hero.dto.HeroSkillResponse;
import vn.nghichtien.chienky.hero.dto.HeroStatsResponse;
import vn.nghichtien.chienky.hero.repository.*;

import java.util.*;

import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.hero.domain.HeroExpItem;
import vn.nghichtien.chienky.hero.dto.UseHeroExpItemRequest;
import vn.nghichtien.chienky.resource.dto.ResourceChangeRequest;
import vn.nghichtien.chienky.resource.dto.ResourceTransactionResult;
import vn.nghichtien.chienky.resource.service.ResourceTransactionService;
import vn.nghichtien.chienky.hero.domain.HeroProgressionConfig;
import vn.nghichtien.chienky.hero.domain.HeroProgressionCost;
import vn.nghichtien.chienky.hero.dto.UpgradeHeroRequest;
import vn.nghichtien.chienky.hero.domain.HeroSkillUpgradeConfig;
import vn.nghichtien.chienky.hero.domain.HeroSkillUpgradeCost;
import vn.nghichtien.chienky.hero.dto.UpgradeHeroSkillRequest;
import vn.nghichtien.chienky.hero.repository.HeroSkillUpgradeConfigRepository;

import java.math.BigDecimal;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service xử lý danh sách và chi tiết nhân vật người chơi sở hữu.
 */
@Service
public class HeroService {

    private final PlayerProfileRepository playerProfileRepository;
    private final HeroInstanceRepository heroInstanceRepository;
    private final HeroInstanceSkillRepository heroInstanceSkillRepository;
    private final HeroSkillDefinitionRepository heroSkillDefinitionRepository;
    private final HeroStatsService heroStatsService;
    private final HeroLevelConfigRepository heroLevelConfigRepository;
    private final HeroExpItemRepository heroExpItemRepository;
    private final ResourceTransactionService resourceTransactionService;
    private final HeroProgressionConfigRepository heroProgressionConfigRepository;
    private final HeroSkillUpgradeConfigRepository heroSkillUpgradeConfigRepository;

    /**
     * Khởi tạo service quản lý nhân vật người chơi.
     *
     * @param playerProfileRepository Repository hồ sơ người chơi
     * @param heroInstanceRepository Repository nhân vật sở hữu
     * @param heroInstanceSkillRepository Repository cấp kỹ năng
     * @param heroSkillDefinitionRepository Repository định nghĩa kỹ năng
     * @param heroStatsService Service tính chỉ số nhân vật
     */
    public HeroService(
            PlayerProfileRepository playerProfileRepository,
            HeroInstanceRepository heroInstanceRepository,
            HeroInstanceSkillRepository heroInstanceSkillRepository,
            HeroSkillDefinitionRepository heroSkillDefinitionRepository,
            HeroStatsService heroStatsService,
            HeroLevelConfigRepository heroLevelConfigRepository,
            HeroExpItemRepository heroExpItemRepository,
            ResourceTransactionService resourceTransactionService,
            HeroProgressionConfigRepository heroProgressionConfigRepository,
            HeroSkillUpgradeConfigRepository heroSkillUpgradeConfigRepository
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.heroInstanceRepository = heroInstanceRepository;
        this.heroInstanceSkillRepository = heroInstanceSkillRepository;
        this.heroSkillDefinitionRepository = heroSkillDefinitionRepository;
        this.heroStatsService = heroStatsService;
        this.heroLevelConfigRepository = heroLevelConfigRepository;
        this.heroExpItemRepository = heroExpItemRepository;
        this.resourceTransactionService = resourceTransactionService;
        this.heroProgressionConfigRepository = heroProgressionConfigRepository;
        this.heroSkillUpgradeConfigRepository = heroSkillUpgradeConfigRepository;
    }

    /**
     * Lấy danh sách vật phẩm EXP đang được phép sử dụng.
     *
     * @return Danh sách mã vật phẩm và lượng EXP mỗi vật phẩm
     */
    @Transactional(readOnly = true)
    public List<HeroExpItemResponse> getEnabledExpItems() {
        return heroExpItemRepository.findAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .sorted(Comparator.comparing(HeroExpItem::getResourceCode))
                .map(item -> new HeroExpItemResponse(
                        item.getResourceCode(),
                        item.getExpPerItem()
                ))
                .toList();
    }

    /**
     * Lấy toàn bộ nhân vật mà tài khoản hiện tại sở hữu.
     *
     * @param username Username tài khoản đăng nhập
     * @return Danh sách nhân vật
     */
    @Transactional(readOnly = true)
    public List<HeroResponse> getMyHeroes(String username) {
        PlayerProfile player = getPlayer(username);

        return heroInstanceRepository
                .findAllByPlayerProfileIdOrderByObtainedAtDesc(player.getId())
                .stream()
                .map(this::toHeroResponse)
                .toList();
    }

    /**
     * Lấy chi tiết một nhân vật thuộc tài khoản hiện tại.
     *
     * @param username Username tài khoản đăng nhập
     * @param instanceId ID bản sao nhân vật
     * @return Chi tiết nhân vật
     */
    @Transactional(readOnly = true)
    public HeroResponse getMyHeroDetail(String username, Long instanceId) {
        if (instanceId == null || instanceId <= 0) {
            throw new IllegalArgumentException("ID nhân vật không hợp lệ");
        }

        PlayerProfile player = getPlayer(username);

        HeroInstance heroInstance = heroInstanceRepository
                .findByIdAndPlayerProfileId(instanceId, player.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy nhân vật thuộc tài khoản hiện tại"
                ));

        return toHeroResponse(heroInstance);
    }

    /**
     * Tìm hồ sơ người chơi theo tài khoản đang đăng nhập.
     *
     * @param username Username tài khoản
     * @return Hồ sơ người chơi
     */
    private PlayerProfile getPlayer(String username) {
        return playerProfileRepository
                .findByAccountUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy hồ sơ người chơi"
                ));
    }

    /**
     * Chuyển dữ liệu nhân vật sở hữu thành DTO đầy đủ.
     *
     * @param instance Bản sao nhân vật
     * @return DTO nhân vật
     */
    private HeroResponse toHeroResponse(HeroInstance instance) {
        HeroDefinition definition = instance.getHeroDefinition();

        if (!Boolean.TRUE.equals(definition.getEnabled())) {
            throw new IllegalStateException(
                    "Nhân vật đã bị vô hiệu hóa: " + definition.getHeroCode()
            );
        }

        HeroStatsResponse stats = heroStatsService.calculateStats(instance);

        List<HeroSkillDefinition> skillDefinitions =
                heroSkillDefinitionRepository
                        .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                                definition.getHeroCode()
                        );

        List<HeroInstanceSkill> instanceSkills =
                heroInstanceSkillRepository
                        .findAllByHeroInstanceIdOrderBySkillDefinitionSkillOrderAsc(
                                instance.getId()
                        );

        Map<Long, Integer> skillLevels = instanceSkills.stream()
                .collect(Collectors.toMap(
                        skill -> skill.getSkillDefinition().getId(),
                        HeroInstanceSkill::getSkillLevel,
                        (first, second) -> first
                ));

        List<HeroSkillResponse> skills = skillDefinitions.stream()
                .map(skill -> toSkillResponse(
                        skill,
                        skillLevels.getOrDefault(skill.getId(), 1)
                ))
                .toList();

        return new HeroResponse(
                instance.getId(),
                definition.getHeroCode(),
                definition.getHeroName(),
                definition.getDescription(),
                definition.getRarity(),
                definition.getFaction(),
                definition.getRole(),
                definition.getAvatarCode(),
                definition.getSpineSkeleton(),
                instance.getHeroLevel(),
                instance.getStarLevel(),
                instance.getBreakthroughLevel(),
                instance.getExperience(),
                instance.getObtainedAt(),
                stats,
                skills
        );
    }

    /**
     * Chuyển định nghĩa kỹ năng và hiệu ứng thành DTO.
     *
     * @param skill Định nghĩa kỹ năng
     * @param skillLevel Cấp kỹ năng của bản sao nhân vật
     * @return DTO kỹ năng
     */
    private HeroSkillResponse toSkillResponse(
            HeroSkillDefinition skill,
            Integer skillLevel
    ) {
        List<HeroSkillResponse.EffectResponse> effects =
                skill.getEffects().stream()
                        .filter(effect -> Boolean.TRUE.equals(effect.getEnabled()))
                        .map(this::toEffectResponse)
                        .toList();

        return new HeroSkillResponse(
                skill.getId(),
                skill.getSkillCode(),
                skill.getSkillName(),
                skill.getSkillType(),
                skill.getSkillOrder(),
                skill.getDescription(),
                skill.getIconCode(),
                skillLevel,
                effects
        );
    }

    /**
     * Chuyển hiệu ứng kỹ năng thành DTO.
     *
     * @param effect Hiệu ứng kỹ năng
     * @return DTO hiệu ứng
     */
    private HeroSkillResponse.EffectResponse toEffectResponse(
            HeroSkillEffect effect
    ) {
        return new HeroSkillResponse.EffectResponse(
                effect.getId(),
                effect.getEffectOrder(),
                effect.getEffectType(),
                effect.getTargetType(),
                effect.getMultiplier(),
                effect.getChance(),
                effect.getDuration(),
                effect.getConfigJson()
        );
    }

    /**
     * Tính tổng lượng EXP tích lũy cần có để đạt cấp mục tiêu.
     *
     * @param targetLevel Cấp nhân vật cần đạt
     * @return Tổng EXP tích lũy cần có
     */
    private long calculateTotalRequiredExp(int targetLevel) {
        if (targetLevel <= 1) {
            return 0L;
        }

        long totalExp = 0L;

        for (int level = 1; level < targetLevel; level++) {
            HeroLevelConfig config = heroLevelConfigRepository
                    .findById(level)
                    .orElse(null);

            if (config == null || config.getRequiredExp() == null
                    || config.getRequiredExp() <= 0) {
                throw new IllegalStateException(
                        "Cấu hình EXP cấp " + level + " không hợp lệ"
                );
            }

            totalExp = Math.addExact(totalExp, config.getRequiredExp());
        }

        return totalExp;
    }

    /**
     * Cộng EXP cho nhân vật và tự động tăng cấp khi đạt ngưỡng.
     *
     * @param username Username tài khoản đang đăng nhập
     * @param instanceId ID bản sao nhân vật
     * @param amount Lượng EXP nhận được
     * @return Thông tin nhân vật sau khi cộng EXP
     */
    @Transactional
    public HeroResponse addHeroExperience(
            String username,
            Long instanceId,
            long amount
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException("EXP phải lớn hơn 0");
        }

        PlayerProfile player = getPlayer(username);

        HeroInstance instance = heroInstanceRepository
                .findByIdAndPlayerProfileIdForUpdate(instanceId, player.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy nhân vật thuộc tài khoản hiện tại"
                ));

        int maxLevel = heroLevelConfigRepository.findAll().stream()
                .map(HeroLevelConfig::getHeroLevel)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa có cấu hình cấp độ nhân vật"
                )) + 1;

        if (instance.getHeroLevel() >= maxLevel) {
            throw new IllegalStateException("Nhân vật đã đạt cấp tối đa");
        }

        long newExperience = Math.addExact(instance.getExperience(), amount);
        instance.addExperience(amount);

        int newLevel = instance.getHeroLevel();

        while (newLevel < maxLevel) {
            long requiredTotalExp = calculateTotalRequiredExp(newLevel + 1);

            if (newExperience < requiredTotalExp) {
                break;
            }

            newLevel++;
        }

        if (newLevel > instance.getHeroLevel()) {
            instance.changeLevel(newLevel);
        }

        return toHeroResponse(instance);
    }

    /**
     * Sử dụng vật phẩm EXP để tăng kinh nghiệm cho nhân vật.
     * Việc trừ vật phẩm và cộng EXP được thực hiện trong cùng transaction.
     *
     * @param username Username tài khoản đang đăng nhập
     * @param instanceId ID bản sao nhân vật
     * @param request Thông tin vật phẩm và số lượng sử dụng
     * @return Thông tin nhân vật sau khi sử dụng vật phẩm
     */
    @Transactional
    public HeroResponse useHeroExpItem(
            String username,
            Long instanceId,
            UseHeroExpItemRequest request
    ) {
        if (request == null
                || request.getResourceCode() == null
                || request.getResourceCode().isBlank()
                || request.getQuantity() == null
                || request.getQuantity() <= 0
                || request.getTransactionKey() == null
                || request.getTransactionKey().isBlank()) {
            throw new IllegalArgumentException("Thông tin sử dụng vật phẩm không hợp lệ");
        }

        PlayerProfile player = getPlayer(username);

        HeroInstance instance = heroInstanceRepository
                .findByIdAndPlayerProfileIdForUpdate(instanceId, player.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy nhân vật thuộc tài khoản hiện tại"
                ));

        HeroExpItem expItem = heroExpItemRepository
                .findByResourceCodeAndEnabled(request.getResourceCode(), true)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vật phẩm EXP không tồn tại hoặc đã bị vô hiệu hóa"
                ));

        int maxLevel = heroLevelConfigRepository.findAll().stream()
                .map(HeroLevelConfig::getHeroLevel)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException(
                        "Chưa có cấu hình cấp độ nhân vật"
                )) + 1;

        if (instance.getHeroLevel() >= maxLevel) {
            throw new IllegalStateException("Nhân vật đã đạt cấp tối đa");
        }

        long expAmount = Math.multiplyExact(
                expItem.getExpPerItem(),
                request.getQuantity().longValue()
        );

        Math.addExact(instance.getExperience(), expAmount);

        String description = "Use hero EXP item for hero instance " + instanceId;

        ResourceChangeRequest resourceRequest = new ResourceChangeRequest(
                request.getTransactionKey(),
                "HERO_EXP_ITEM",
                instanceId,
                description,
                Map.of(
                        request.getResourceCode(),
                        BigDecimal.valueOf(request.getQuantity()).negate()
                )
        );

        ResourceTransactionResult transactionResult =
                resourceTransactionService.changeResources(
                        player.getId(),
                        resourceRequest
                );

        if (transactionResult.isAlreadyProcessed()) {
            validateRepeatedExpTransaction(
                    transactionResult,
                    request,
                    instanceId
            );

            return toHeroResponse(instance);
        }

        instance.addExperience(expAmount);

        int currentLevel = instance.getHeroLevel();
        int newLevel = currentLevel;

        while (newLevel < maxLevel) {
            long requiredTotalExp = calculateTotalRequiredExp(newLevel + 1);

            if (instance.getExperience() < requiredTotalExp) {
                break;
            }

            newLevel++;
        }

        if (newLevel > currentLevel) {
            instance.changeLevel(newLevel);
        }

        return toHeroResponse(instance);
    }

    /**
     * Kiểm tra giao dịch sử dụng vật phẩm EXP đã xử lý có trùng nội dung hay không.
     *
     * @param result Kết quả giao dịch cũ
     * @param request Request hiện tại
     * @param instanceId ID nhân vật hiện tại
     */
    private void validateRepeatedExpTransaction(
            ResourceTransactionResult result,
            UseHeroExpItemRequest request,
            Long instanceId
    ) {
        String expectedDescription =
                "Use hero EXP item for hero instance " + instanceId;

        BigDecimal expectedAmount = BigDecimal.valueOf(
                request.getQuantity()
        ).negate();

        boolean sameTransaction =
                "HERO_EXP_ITEM".equals(result.getSourceType())
                        && expectedDescription.equals(result.getDescription())
                        && result.getDetails().size() == 1
                        && request.getResourceCode().equals(
                        result.getDetails().get(0).getResourceCode()
                )
                        && expectedAmount.compareTo(
                        result.getDetails().get(0).getAmount()
                ) == 0;

        if (!sameTransaction) {
            throw new IllegalArgumentException(
                    "Transaction key đã được sử dụng cho một giao dịch khác"
            );
        }
    }

    /**
     * Nâng sao nhân vật theo cấu hình trong database.
     *
     * @param username Username tài khoản đang đăng nhập
     * @param instanceId ID bản sao nhân vật
     * @param request Request giao dịch
     * @return Nhân vật sau nâng sao
     */
    @Transactional
    public HeroResponse upgradeHeroStar(
            String username,
            Long instanceId,
            UpgradeHeroRequest request
    ) {
        return upgradeHeroProgression(
                username,
                instanceId,
                request,
                "STAR"
        );
    }

    /**
     * Đột phá nhân vật theo cấu hình trong database.
     *
     * @param username Username tài khoản đang đăng nhập
     * @param instanceId ID bản sao nhân vật
     * @param request Request giao dịch
     * @return Nhân vật sau đột phá
     */
    @Transactional
    public HeroResponse breakthroughHero(
            String username,
            Long instanceId,
            UpgradeHeroRequest request
    ) {
        return upgradeHeroProgression(
                username,
                instanceId,
                request,
                "BREAKTHROUGH"
        );
    }

    /**
     * Thực hiện nâng sao hoặc đột phá, kiểm tra điều kiện và trừ tài nguyên.
     *
     * @param username Username tài khoản
     * @param instanceId ID bản sao nhân vật
     * @param request Request giao dịch
     * @param progressionType Loại nâng cấp
     * @return Nhân vật sau nâng cấp
     */
    private HeroResponse upgradeHeroProgression(
            String username,
            Long instanceId,
            UpgradeHeroRequest request,
            String progressionType
    ) {
        if (request == null || request.getTransactionKey() == null
                || request.getTransactionKey().isBlank()) {
            throw new IllegalArgumentException("Transaction key không hợp lệ");
        }

        PlayerProfile player = getPlayer(username);

        HeroInstance instance = heroInstanceRepository
                .findByIdAndPlayerProfileIdForUpdate(instanceId, player.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy nhân vật thuộc tài khoản hiện tại"
                ));

        HeroDefinition definition = instance.getHeroDefinition();

        if (!Boolean.TRUE.equals(definition.getEnabled())) {
            throw new IllegalStateException("Nhân vật đã bị vô hiệu hóa");
        }

        int currentLevel = "STAR".equals(progressionType)
                ? instance.getStarLevel()
                : instance.getBreakthroughLevel();

        int targetLevel = currentLevel + 1;

        HeroProgressionConfig config = heroProgressionConfigRepository
                .findByHeroCodeAndProgressionTypeAndTargetLevelAndEnabled(
                        definition.getHeroCode(),
                        progressionType,
                        targetLevel,
                        true
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Không có cấu hình " + progressionType
                                + " cấp " + targetLevel
                ));

        if (instance.getHeroLevel() < config.getRequiredHeroLevel()) {
            throw new IllegalStateException(
                    "Cấp nhân vật chưa đạt yêu cầu: "
                            + config.getRequiredHeroLevel()
            );
        }

        if (instance.getStarLevel() < config.getRequiredStarLevel()) {
            throw new IllegalStateException(
                    "Cấp sao chưa đạt yêu cầu: "
                            + config.getRequiredStarLevel()
            );
        }

        if (config.getCosts() == null || config.getCosts().isEmpty()) {
            throw new IllegalStateException(
                    "Cấu hình nâng cấp chưa có chi phí tài nguyên"
            );
        }

        Map<String, BigDecimal> changes = config.getCosts().stream()
                .collect(Collectors.toMap(
                        HeroProgressionCost::getResourceCode,
                        cost -> cost.getRequiredAmount().negate()
                ));

        String sourceType = "HERO_" + progressionType;
        String description = "Hero " + instanceId
                + " " + progressionType
                + " to level " + targetLevel;

        ResourceChangeRequest resourceRequest = new ResourceChangeRequest(
                request.getTransactionKey(),
                sourceType,
                instanceId,
                description,
                changes
        );

        ResourceTransactionResult result =
                resourceTransactionService.changeResources(
                        player.getId(),
                        resourceRequest
                );

        if (result.isAlreadyProcessed()) {
            validateRepeatedProgressionTransaction(
                    result,
                    request,
                    instanceId,
                    sourceType,
                    description,
                    changes
            );

            return toHeroResponse(instance);
        }

        if ("STAR".equals(progressionType)) {
            instance.changeStarLevel(targetLevel);
        } else {
            instance.changeBreakthroughLevel(targetLevel);
        }

        return toHeroResponse(instance);
    }

    /**
     * Kiểm tra giao dịch nâng cấp cũ có trùng toàn bộ thông tin hay không.
     *
     * @param result Kết quả giao dịch cũ
     * @param request Request hiện tại
     * @param instanceId ID nhân vật
     * @param sourceType Loại giao dịch
     * @param description Mô tả giao dịch
     * @param expectedChanges Chi phí dự kiến
     */
    private void validateRepeatedProgressionTransaction(
            ResourceTransactionResult result,
            UpgradeHeroRequest request,
            Long instanceId,
            String sourceType,
            String description,
            Map<String, BigDecimal> expectedChanges
    ) {
        Map<String, BigDecimal> actualChanges = result.getDetails().stream()
                .collect(Collectors.toMap(
                        detail -> detail.getResourceCode(),
                        detail -> detail.getAmount()
                ));

        boolean sameTransaction =
                sourceType.equals(result.getSourceType())
                        && description.equals(result.getDescription())
                        && actualChanges.size() == expectedChanges.size()
                        && expectedChanges.entrySet().stream().allMatch(entry ->
                        actualChanges.containsKey(entry.getKey())
                                && entry.getValue().compareTo(
                                actualChanges.get(entry.getKey())
                        ) == 0
                );

        if (!sameTransaction) {
            throw new IllegalArgumentException(
                    "Transaction key đã được sử dụng cho giao dịch khác"
            );
        }
    }

    /**
     * Nâng cấp kỹ năng của tướng bằng tài nguyên theo cấu hình server.
     *
     * @param username tên tài khoản người chơi
     * @param instanceId ID tướng thuộc sở hữu người chơi
     * @param skillId ID định nghĩa kỹ năng cần nâng cấp
     * @param request thông tin transactionKey
     * @return thông tin tướng sau khi nâng cấp
     */
    @Transactional
    public HeroResponse upgradeHeroSkill(
            String username,
            Long instanceId,
            Long skillId,
            UpgradeHeroSkillRequest request
    ) {
        if (request == null || request.getTransactionKey() == null
                || request.getTransactionKey().isBlank()) {
            throw new IllegalArgumentException("Transaction key is required.");
        }

        PlayerProfile player = getPlayer(username);

        HeroInstance instance = heroInstanceRepository
                .findByIdAndPlayerProfileIdForUpdate(instanceId, player.getId())
                .orElseThrow(() -> new IllegalArgumentException("Hero not found."));

        HeroInstanceSkill instanceSkill = heroInstanceSkillRepository
                .findByHeroInstanceIdAndSkillDefinitionId(instanceId, skillId)
                .orElseThrow(() -> new IllegalArgumentException("Hero skill not found."));

        int targetLevel = instanceSkill.getSkillLevel() + 1;

        HeroSkillUpgradeConfig config = heroSkillUpgradeConfigRepository
                .findBySkillDefinitionIdAndTargetSkillLevelAndEnabled(
                        skillId, targetLevel, true
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "Skill upgrade configuration not found."
                ));

        if (instance.getHeroLevel() < config.getRequiredHeroLevel()) {
            throw new IllegalArgumentException("Hero level is not sufficient.");
        }

        if (config.getCosts() == null || config.getCosts().isEmpty()) {
            throw new IllegalArgumentException("Skill upgrade cost is not configured.");
        }

        Map<String, BigDecimal> expectedChanges = new HashMap<>();

        for (HeroSkillUpgradeCost cost : config.getCosts()) {
            expectedChanges.put(
                    cost.getResourceCode(),
                    cost.getRequiredAmount().negate()
            );
        }

        String sourceType = "HERO_SKILL_UPGRADE";
        String description = "Hero " + instanceId
                + " skill " + skillId + " to level " + targetLevel;

        ResourceChangeRequest resourceRequest = new ResourceChangeRequest(
                request.getTransactionKey(),
                sourceType,
                instanceId,
                description,
                expectedChanges
        );

        ResourceTransactionResult result = resourceTransactionService.changeResources(
                player.getId(),
                resourceRequest
        );

        if (result.isAlreadyProcessed()) {
            validateRepeatedProgressionTransaction(
                    result,
                    sourceType,
                    description,
                    expectedChanges
            );
            return toHeroResponse(instance);
        }

        instanceSkill.changeLevel(targetLevel);

        return toHeroResponse(instance);
    }

    /**
     * Kiểm tra giao dịch nâng cấp kỹ năng đã xử lý có trùng khớp với yêu cầu hiện tại không.
     *
     * @param result kết quả giao dịch tài nguyên đã tồn tại
     * @param sourceType loại nguồn giao dịch
     * @param description mô tả giao dịch
     * @param expectedChanges tài nguyên dự kiến tiêu hao
     */
    private void validateRepeatedProgressionTransaction(
            ResourceTransactionResult result,
            String sourceType,
            String description,
            Map<String, BigDecimal> expectedChanges
    ) {
        Map<String, BigDecimal> actualChanges = result.getDetails().stream()
                .collect(Collectors.toMap(
                        ResourceTransactionResult.ResourceChangeResult::getResourceCode,
                        ResourceTransactionResult.ResourceChangeResult::getAmount
                ));

        boolean sameTransaction =
                sourceType.equals(result.getSourceType())
                        && description.equals(result.getDescription())
                        && actualChanges.size() == expectedChanges.size()
                        && expectedChanges.entrySet().stream().allMatch(entry ->
                        actualChanges.containsKey(entry.getKey())
                                && entry.getValue().compareTo(
                                actualChanges.get(entry.getKey())
                        ) == 0
                );

        if (!sameTransaction) {
            throw new IllegalArgumentException(
                    "Transaction key has already been used for another transaction."
            );
        }
    }
}