package vn.nghichtien.chienky.resource.service;

import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;
import vn.nghichtien.chienky.resource.domain.PlayerResource;
import vn.nghichtien.chienky.resource.domain.ResourceDefinition;
import vn.nghichtien.chienky.resource.domain.ResourceTransactionDetail;
import vn.nghichtien.chienky.resource.domain.ResourceTransactionHeader;
import vn.nghichtien.chienky.resource.dto.ResourceChangeRequest;
import vn.nghichtien.chienky.resource.dto.ResourceTransactionResult;
import vn.nghichtien.chienky.resource.repository.PlayerResourceRepository;
import vn.nghichtien.chienky.resource.repository.ResourceDefinitionRepository;
import vn.nghichtien.chienky.resource.repository.ResourceTransactionDetailRepository;
import vn.nghichtien.chienky.resource.repository.ResourceTransactionHeaderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ResourceTransactionService {

    private final PlayerProfileRepository playerProfileRepository;
    private final PlayerResourceRepository playerResourceRepository;
    private final ResourceDefinitionRepository resourceDefinitionRepository;
    private final ResourceTransactionHeaderRepository transactionHeaderRepository;
    private final ResourceTransactionDetailRepository transactionDetailRepository;

    public ResourceTransactionService(
            PlayerProfileRepository playerProfileRepository,
            PlayerResourceRepository playerResourceRepository,
            ResourceDefinitionRepository resourceDefinitionRepository,
            ResourceTransactionHeaderRepository transactionHeaderRepository,
            ResourceTransactionDetailRepository transactionDetailRepository
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.playerResourceRepository = playerResourceRepository;
        this.resourceDefinitionRepository = resourceDefinitionRepository;
        this.transactionHeaderRepository = transactionHeaderRepository;
        this.transactionDetailRepository = transactionDetailRepository;
    }

    /**
     * Thực hiện cộng/trừ một hoặc nhiều loại tài nguyên cho người chơi.
     * Khóa hồ sơ và số dư để tránh cập nhật đồng thời, kiểm tra idempotency,
     * số dư tối thiểu và giới hạn tối đa trước khi lưu toàn bộ giao dịch.
     *
     * @param playerId ID hồ sơ người chơi
     * @param request Yêu cầu thay đổi tài nguyên
     * @return Kết quả giao dịch, có thể là kết quả của giao dịch đã xử lý trước đó
     */
    @Transactional
    public ResourceTransactionResult changeResources(
            Long playerId,
            ResourceChangeRequest request
    ) {
        validateRequest(playerId, request);

        // Khóa hồ sơ trước để tuần tự hóa các giao dịch của cùng người chơi.
        PlayerProfile player = playerProfileRepository
                .findByIdForUpdate(playerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy hồ sơ người chơi"
                ));

        // Kiểm tra giao dịch đã được xử lý trước đó hay chưa.
        var existingTransaction = transactionHeaderRepository
                .findByPlayerIdAndTransactionKey(
                        playerId,
                        request.getTransactionKey()
                );

        if (existingTransaction.isPresent()) {
            ResourceTransactionHeader header = existingTransaction.get();

            List<ResourceTransactionDetail> details =
                    transactionDetailRepository
                            .findAllByTransactionIdWithDefinition(header.getId());

            return new ResourceTransactionResult(
                    header,
                    details,
                    true
            );
        }

        // Khóa các bản ghi số dư của người chơi.
        List<PlayerResource> playerResources =
                playerResourceRepository.findAllByPlayerIdForUpdate(playerId);

        Map<String, PlayerResource> resourceMap = new HashMap<>();

        for (PlayerResource resource : playerResources) {
            resourceMap.put(
                    resource.getResourceCode(),
                    resource
            );
        }

        List<ResourceTransactionDetail> details = new ArrayList<>();

        // Kiểm tra và tính toán toàn bộ số dư trước khi lưu.
        for (Map.Entry<String, BigDecimal> entry : request.getChanges().entrySet()) {
            String resourceCode = entry.getKey();
            BigDecimal amount = entry.getValue();

            PlayerResource playerResource = resourceMap.get(resourceCode);

            if (playerResource == null) {
                throw new IllegalArgumentException(
                        "Người chơi chưa có tài nguyên: " + resourceCode
                );
            }

            ResourceDefinition definition =
                    playerResource.getResourceDefinition();

            if (!definition.isEnabled()) {
                throw new IllegalArgumentException(
                        "Loại tài nguyên đã bị vô hiệu hóa: " + resourceCode
                );
            }

            BigDecimal balanceBefore = playerResource.getBalance();
            BigDecimal balanceAfter = balanceBefore.add(amount);

            // Không cho phép số dư âm.
            if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Không đủ tài nguyên: " + resourceCode
                );
            }

            // Kiểm tra giới hạn số dư tối đa nếu tài nguyên có cấu hình.
            BigDecimal maxBalance = definition.getMaxBalance();

            if (maxBalance != null
                    && balanceAfter.compareTo(maxBalance) > 0) {
                throw new IllegalArgumentException(
                        "Vượt giới hạn tài nguyên: " + resourceCode
                );
            }

            details.add(new ResourceTransactionDetail(
                    null,
                    definition,
                    amount,
                    balanceBefore,
                    balanceAfter
            ));
        }

        // Tạo bản ghi giao dịch cha.
        ResourceTransactionHeader header = new ResourceTransactionHeader(
                player,
                request.getTransactionKey(),
                request.getSourceType(),
                request.getSourceId(),
                request.getDescription()
        );

        header = transactionHeaderRepository.save(header);

        List<ResourceTransactionDetail> savedDetails = new ArrayList<>();

        // Cập nhật số dư và gắn từng chi tiết vào giao dịch cha.
        for (ResourceTransactionDetail detail : details) {
            PlayerResource playerResource =
                    resourceMap.get(detail.getResourceCode());

            playerResource.updateBalance(detail.getBalanceAfter());

            ResourceTransactionDetail savedDetail =
                    new ResourceTransactionDetail(
                            header,
                            detail.getResourceDefinition(),
                            detail.getAmount(),
                            detail.getBalanceBefore(),
                            detail.getBalanceAfter()
                    );

            savedDetails.add(savedDetail);
        }

        playerResourceRepository.saveAll(playerResources);

        savedDetails = transactionDetailRepository.saveAll(savedDetails);

        return new ResourceTransactionResult(
                header,
                savedDetails,
                false
        );
    }

    /**
     * Kiểm tra tính hợp lệ của yêu cầu trước khi truy cập dữ liệu.
     *
     * @param playerId ID hồ sơ người chơi
     * @param request Yêu cầu thay đổi tài nguyên
     */
    private void validateRequest(
            Long playerId,
            ResourceChangeRequest request
    ) {
        if (playerId == null || playerId <= 0) {
            throw new IllegalArgumentException("Player ID không hợp lệ");
        }

        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu giao dịch không được để trống");
        }

        if (request.getTransactionKey() == null
                || request.getTransactionKey().isBlank()
                || request.getTransactionKey().length() > 100) {
            throw new IllegalArgumentException("Transaction key không hợp lệ");
        }

        if (request.getSourceType() == null
                || request.getSourceType().isBlank()
                || request.getSourceType().length() > 50) {
            throw new IllegalArgumentException("Source type không hợp lệ");
        }

        if (request.getDescription() != null
                && request.getDescription().length() > 500) {
            throw new IllegalArgumentException("Mô tả giao dịch quá dài");
        }

        if (request.getChanges() == null
                || request.getChanges().isEmpty()) {
            throw new IllegalArgumentException("Danh sách tài nguyên thay đổi đang trống");
        }

        if (request.getChanges().size() > 50) {
            throw new IllegalArgumentException("Một giao dịch không được thay đổi quá 50 loại tài nguyên");
        }

        for (Map.Entry<String, BigDecimal> entry : request.getChanges().entrySet()) {
            if (entry.getKey() == null
                    || entry.getKey().isBlank()
                    || entry.getKey().length() > 50) {
                throw new IllegalArgumentException("Mã tài nguyên không hợp lệ");
            }

            BigDecimal amount = entry.getValue();

            if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException(
                        "Số lượng thay đổi phải khác 0: " + entry.getKey()
                );
            }

            if (amount.precision() > 24 || amount.scale() > 6) {
                throw new IllegalArgumentException(
                        "Số lượng tài nguyên vượt độ chính xác cho phép: " + entry.getKey()
                );
            }
        }
    }

    /**
     * Tìm giao dịch tài nguyên đã tồn tại theo người chơi và khóa giao dịch.
     *
     * @param playerId ID người chơi
     * @param transactionKey khóa giao dịch
     * @return kết quả giao dịch nếu đã tồn tại
     */
    @Transactional(readOnly = true)
    public Optional<ResourceTransactionResult> findExistingTransaction(
            Long playerId,
            String transactionKey
    ) {
        ResourceTransactionHeader header = transactionHeaderRepository
                .findByPlayerIdAndTransactionKey(playerId, transactionKey)
                .orElse(null);

        if (header == null) {
            return Optional.empty();
        }

        List<ResourceTransactionDetail> details =
                transactionDetailRepository
                        .findAllByTransactionIdWithDefinition(header.getId());

        return Optional.of(
                new ResourceTransactionResult(header, details, true)
        );
    }

    /**
     * Kiểm tra giao dịch có tồn tại và có đúng nguồn phát sinh hay không.
     *
     * @param playerId ID người chơi
     * @param transactionKey khóa giao dịch
     * @param sourceType loại giao dịch cần kiểm tra
     * @param sourceId ID đối tượng phát sinh giao dịch
     * @return true nếu giao dịch tồn tại và khớp thông tin
     */
    @Transactional(readOnly = true)
    public boolean isExistingTransactionFromSource(
            Long playerId,
            String transactionKey,
            String sourceType,
            Long sourceId
    ) {
        return transactionHeaderRepository
                .findByPlayerIdAndTransactionKey(playerId, transactionKey)
                .map(header ->
                        sourceType.equals(header.getSourceType())
                                && Objects.equals(sourceId, header.getSourceId())
                )
                .orElse(false);
    }


}