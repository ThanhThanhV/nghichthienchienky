package vn.nghichtien.chienky.equipment.service;

import vn.nghichtien.chienky.equipment.domain.*;
import vn.nghichtien.chienky.equipment.dto.*;
import vn.nghichtien.chienky.equipment.repository.*;
import vn.nghichtien.chienky.hero.domain.HeroInstance;
import vn.nghichtien.chienky.hero.repository.HeroInstanceRepository;
import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nghichtien.chienky.equipment.domain.EquipmentEnhancementConfig;
import vn.nghichtien.chienky.equipment.domain.EquipmentEnhancementCost;
import vn.nghichtien.chienky.equipment.repository.EquipmentEnhancementConfigRepository;
import vn.nghichtien.chienky.resource.dto.ResourceChangeRequest;
import vn.nghichtien.chienky.resource.service.ResourceTransactionService;
import vn.nghichtien.chienky.resource.dto.ResourceTransactionResult;
import java.math.BigDecimal;
import java.util.HashMap;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EquipmentService {

    private static final Set<String> EQUIPMENT_SLOTS = Set.of(
            "WEAPON",
            "HELMET",
            "CHEST",
            "BOOTS",
            "ACCESSORY_1",
            "ACCESSORY_2"
    );

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final PlayerProfileRepository playerProfileRepository;
    private final HeroInstanceRepository heroInstanceRepository;
    private final PlayerEquipmentRepository playerEquipmentRepository;
    private final HeroEquipmentSlotRepository heroEquipmentSlotRepository;
    private final EquipmentBaseStatRepository equipmentBaseStatRepository;
    private final EquipmentEnhancementConfigRepository enhancementConfigRepository;
    private final ResourceTransactionService resourceTransactionService;

    public EquipmentService(
            PlayerProfileRepository playerProfileRepository,
            HeroInstanceRepository heroInstanceRepository,
            PlayerEquipmentRepository playerEquipmentRepository,
            HeroEquipmentSlotRepository heroEquipmentSlotRepository,
            EquipmentBaseStatRepository equipmentBaseStatRepository,
            EquipmentEnhancementConfigRepository enhancementConfigRepository,
            ResourceTransactionService resourceTransactionService
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.heroInstanceRepository = heroInstanceRepository;
        this.playerEquipmentRepository = playerEquipmentRepository;
        this.heroEquipmentSlotRepository = heroEquipmentSlotRepository;
        this.equipmentBaseStatRepository = equipmentBaseStatRepository;
        this.enhancementConfigRepository = enhancementConfigRepository;
        this.resourceTransactionService = resourceTransactionService;
    }

    /**
     * Lấy kho trang bị của người chơi và trạng thái trang bị hiện tại.
     *
     * @param username tên tài khoản
     * @return danh sách trang bị trong kho
     */
    @Transactional(readOnly = true)
    public List<EquipmentResponse> getInventory(String username) {
        PlayerProfile player = getPlayer(username);

        List<PlayerEquipment> items =
                playerEquipmentRepository.findAllByPlayerIdWithDefinition(
                        player.getId()
                );

        Map<Long, String> equippedSlots = getEquippedItems(player.getId());

        return mapEquipment(items, equippedSlots);
    }

    /**
     * Lấy trang bị đang được sử dụng bởi một tướng thuộc người chơi.
     *
     * @param username tên tài khoản
     * @param instanceId ID tướng
     * @return danh sách trang bị theo slot
     */
    @Transactional(readOnly = true)
    public HeroEquipmentResponse getHeroEquipment(
            String username,
            Long instanceId
    ) {
        PlayerProfile player = getPlayer(username);

        heroInstanceRepository.findByIdAndPlayerProfileId(instanceId, player.getId())
                .orElseThrow(() -> new IllegalArgumentException("Hero not found."));

        List<HeroEquipmentSlot> slots =
                heroEquipmentSlotRepository.findAllByHeroInstanceIdWithEquipment(
                        instanceId
                );

        List<PlayerEquipment> items = slots.stream()
                .map(HeroEquipmentSlot::getPlayerEquipment)
                .toList();

        Map<Long, String> equippedSlots = slots.stream()
                .collect(Collectors.toMap(
                        slot -> slot.getPlayerEquipment().getId(),
                        HeroEquipmentSlot::getEquipmentType
                ));

        return new HeroEquipmentResponse(
                instanceId,
                mapEquipment(items, equippedSlots)
        );
    }

    /**
     * Trang bị một món đồ vào slot của tướng, tự động thay thế món cũ nếu có.
     *
     * @param username tên tài khoản
     * @param instanceId ID tướng
     * @param request thông tin trang bị và slot
     * @return trang bị hiện tại của tướng sau thao tác
     */
    @Transactional
    public HeroEquipmentResponse equip(
            String username,
            Long instanceId,
            EquipEquipmentRequest request
    ) {
        if (request == null || request.getEquipmentId() == null) {
            throw new IllegalArgumentException("Equipment is required.");
        }

        String slotType = normalizeSlot(request.getEquipmentType());

        PlayerProfile player = getPlayer(username);

        HeroInstance hero = heroInstanceRepository
                .findByIdAndPlayerProfileIdForUpdate(instanceId, player.getId())
                .orElseThrow(() -> new IllegalArgumentException("Hero not found."));

        PlayerEquipment equipment = playerEquipmentRepository
                .findByIdAndPlayerIdForUpdate(
                        request.getEquipmentId(),
                        player.getId()
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "Equipment not found in inventory."
                ));

        String definitionType = equipment.getEquipmentDefinition()
                .getEquipmentType();

        validateSlotCompatibility(definitionType, slotType);

        Optional<HeroEquipmentSlot> currentSlot =
                heroEquipmentSlotRepository.findByHeroAndTypeForUpdate(
                        hero.getId(),
                        slotType
                );

        if (currentSlot.isPresent()
                && currentSlot.get().getPlayerEquipment().getId()
                .equals(equipment.getId())) {
            return getHeroEquipment(username, instanceId);
        }

        if (heroEquipmentSlotRepository.existsByPlayerEquipmentId(equipment.getId())) {
            throw new IllegalArgumentException(
                    "Equipment is already equipped by another hero."
            );
        }

        currentSlot.ifPresent(heroEquipmentSlotRepository::delete);

        heroEquipmentSlotRepository.save(
                new HeroEquipmentSlot(hero, slotType, equipment)
        );

        return getHeroEquipment(username, instanceId);
    }

    /**
     * Tháo trang bị khỏi một slot của tướng.
     *
     * @param username tên tài khoản
     * @param instanceId ID tướng
     * @param equipmentType slot cần tháo
     * @return trang bị còn lại của tướng
     */
    @Transactional
    public HeroEquipmentResponse unequip(
            String username,
            Long instanceId,
            String equipmentType
    ) {
        String slotType = normalizeSlot(equipmentType);

        PlayerProfile player = getPlayer(username);

        HeroInstance hero = heroInstanceRepository
                .findByIdAndPlayerProfileIdForUpdate(instanceId, player.getId())
                .orElseThrow(() -> new IllegalArgumentException("Hero not found."));

        heroEquipmentSlotRepository.findByHeroAndTypeForUpdate(
                hero.getId(),
                slotType
        ).ifPresent(heroEquipmentSlotRepository::delete);

        return getHeroEquipment(username, instanceId);
    }

    /**
     * Lấy profile người chơi theo username.
     *
     * @param username tên tài khoản
     * @return profile người chơi
     */
    private PlayerProfile getPlayer(String username) {
        return playerProfileRepository.findByAccountUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Player profile not found."
                ));
    }

    /**
     * Chuẩn hóa và kiểm tra tên slot trang bị.
     *
     * @param equipmentType tên slot đầu vào
     * @return tên slot chuẩn
     */
    private String normalizeSlot(String equipmentType) {
        if (equipmentType == null) {
            throw new IllegalArgumentException("Equipment slot is required.");
        }

        String slot = equipmentType.trim().toUpperCase(Locale.ROOT);

        if (!EQUIPMENT_SLOTS.contains(slot)) {
            throw new IllegalArgumentException("Invalid equipment slot.");
        }

        return slot;
    }

    /**
     * Kiểm tra loại trang bị có tương thích với slot được chọn không.
     *
     * @param definitionType loại trang bị trong định nghĩa
     * @param slotType slot trên tướng
     */
    private void validateSlotCompatibility(
            String definitionType,
            String slotType
    ) {
        boolean compatible = definitionType.equals(slotType)
                || ("ACCESSORY".equals(definitionType)
                && slotType.startsWith("ACCESSORY_"));

        if (!compatible) {
            throw new IllegalArgumentException(
                    "Equipment type does not match the selected slot."
            );
        }
    }

    /**
     * Lấy bản đồ ID trang bị và slot đang được sử dụng trong toàn bộ người chơi.
     *
     * @param playerId ID người chơi
     * @return bản đồ equipmentId đến slot
     */
    private Map<Long, String> getEquippedItems(Long playerId) {
        List<PlayerEquipment> inventory =
                playerEquipmentRepository.findAllByPlayerIdWithDefinition(playerId);

        if (inventory.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> equipmentIds = inventory.stream()
                .map(PlayerEquipment::getId)
                .collect(Collectors.toSet());

        return heroEquipmentSlotRepository.findAll().stream()
                .filter(slot -> equipmentIds.contains(
                        slot.getPlayerEquipment().getId()
                ))
                .collect(Collectors.toMap(
                        slot -> slot.getPlayerEquipment().getId(),
                        HeroEquipmentSlot::getEquipmentType
                ));
    }

    /**
     * Chuyển danh sách vật phẩm thành DTO và gắn chỉ số cơ bản.
     *
     * @param items danh sách vật phẩm
     * @param equippedSlots trạng thái slot đang sử dụng
     * @return danh sách DTO trang bị
     */
    private List<EquipmentResponse> mapEquipment(
            List<PlayerEquipment> items,
            Map<Long, String> equippedSlots
    ) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> codes = items.stream()
                .map(item -> item.getEquipmentDefinition().getEquipmentCode())
                .distinct()
                .toList();

        return items.stream()
                .map(item -> {
                    EquipmentDefinition definition = item.getEquipmentDefinition();

                    return new EquipmentResponse(
                            item.getId(),
                            definition.getEquipmentCode(),
                            definition.getEquipmentName(),
                            definition.getEquipmentType(),
                            definition.getRarity(),
                            definition.getIconCode(),
                            item.getEnhancementLevel(),
                            calculateEquipmentStats(
                                    definition.getEquipmentCode(),
                                    item.getEnhancementLevel()
                            ),
                            equippedSlots.get(item.getId())
                    );
                })
                .toList();
    }

    /**
     * Cường hóa trang bị, đồng thời xử lý retry an toàn theo transactionKey.
     *
     * @param username tên tài khoản người chơi
     * @param equipmentId ID trang bị
     * @param transactionKey khóa giao dịch
     * @return trang bị sau xử lý
     */
    @Transactional
    public EquipmentResponse enhance(
            String username,
            Long equipmentId,
            String transactionKey
    ) {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new IllegalArgumentException("Transaction key is required.");
        }

        PlayerProfile player = getPlayer(username);

        PlayerEquipment equipment = playerEquipmentRepository
                .findByIdAndPlayerIdForUpdate(equipmentId, player.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Equipment not found in inventory."
                ));

        Optional<ResourceTransactionResult> existing =
                resourceTransactionService.findExistingTransaction(
                        player.getId(),
                        transactionKey
                );

        if (existing.isPresent()) {
            ResourceTransactionResult oldTransaction = existing.get();

            if (!resourceTransactionService.isExistingTransactionFromSource(
                    player.getId(),
                    transactionKey,
                    "EQUIPMENT_ENHANCEMENT",
                    equipmentId
            )) {
                throw new IllegalArgumentException(
                        "Transaction key has already been used."
                );
            }

            return mapSingleEquipment(
                    equipment,
                    getEquippedItems(player.getId())
            );
        }

        int targetLevel = equipment.getEnhancementLevel() + 1;

        EquipmentEnhancementConfig config = enhancementConfigRepository
                .findByTargetLevelAndEnabledTrue(targetLevel)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Enhancement configuration not found."
                ));

        if (config.getCosts() == null || config.getCosts().isEmpty()) {
            throw new IllegalArgumentException(
                    "Enhancement cost is not configured."
            );
        }

        Map<String, BigDecimal> expectedChanges = new HashMap<>();

        for (EquipmentEnhancementCost cost : config.getCosts()) {
            expectedChanges.put(
                    cost.getResourceCode(),
                    cost.getRequiredAmount().negate()
            );
        }

        String description = "Equipment " + equipmentId
                + " to enhancement level " + targetLevel;

        ResourceChangeRequest resourceRequest = new ResourceChangeRequest(
                transactionKey,
                "EQUIPMENT_ENHANCEMENT",
                equipmentId,
                description,
                expectedChanges
        );

        resourceTransactionService.changeResources(
                player.getId(),
                resourceRequest
        );

        equipment.enhanceTo(targetLevel);

        return mapSingleEquipment(
                equipment,
                getEquippedItems(player.getId())
        );
    }

    /**
     * Kiểm tra giao dịch cường hóa lặp có đúng nội dung yêu cầu hiện tại không.
     *
     * @param result giao dịch tài nguyên đã tồn tại
     * @param sourceType loại giao dịch
     * @param description mô tả giao dịch
     * @param expectedChanges tài nguyên dự kiến tiêu hao
     */
    private void validateEnhancementTransaction(
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

    /**
     * Chuyển một vật phẩm trang bị thành DTO phản hồi.
     *
     * @param item vật phẩm trang bị
     * @param equippedSlots bản đồ trang bị đang được sử dụng
     * @return DTO trang bị
     */
    private EquipmentResponse mapSingleEquipment(
            PlayerEquipment item,
            Map<Long, String> equippedSlots
    ) {
        EquipmentDefinition definition = item.getEquipmentDefinition();

        Map<String, BigDecimal> stats = calculateEquipmentStats(
                definition.getEquipmentCode(),
                item.getEnhancementLevel()
        );

        return new EquipmentResponse(
                item.getId(),
                definition.getEquipmentCode(),
                definition.getEquipmentName(),
                definition.getEquipmentType(),
                definition.getRarity(),
                definition.getIconCode(),
                item.getEnhancementLevel(),
                stats,
                equippedSlots.get(item.getId())
        );
    }

    /**
     * Tính chỉ số trang bị sau cường hóa theo phần trăm cấu hình của cấp hiện tại.
     *
     * @param equipmentCode mã trang bị
     * @param enhancementLevel cấp cường hóa
     * @return bản đồ chỉ số sau cường hóa
     */
    private Map<String, BigDecimal> calculateEquipmentStats(
            String equipmentCode,
            Integer enhancementLevel
    ) {
        Map<String, BigDecimal> stats =
                equipmentBaseStatRepository
                        .findAllByEquipmentDefinitionEquipmentCodeIn(
                                List.of(equipmentCode)
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                EquipmentBaseStat::getStatCode,
                                EquipmentBaseStat::getStatValue
                        ));

        if (enhancementLevel == null || enhancementLevel <= 0) {
            return stats;
        }

        EquipmentEnhancementConfig config = enhancementConfigRepository
                .findByTargetLevelAndEnabledTrue(enhancementLevel)
                .orElseThrow(() -> new IllegalStateException(
                        "Equipment enhancement configuration not found."
                ));

        BigDecimal multiplier = BigDecimal.ONE.add(
                config.getStatBonusPercent().divide(
                        ONE_HUNDRED,
                        6,
                        java.math.RoundingMode.HALF_UP
                )
        );

        stats.replaceAll((key, value) -> value.multiply(multiplier));

        return stats;
    }
}