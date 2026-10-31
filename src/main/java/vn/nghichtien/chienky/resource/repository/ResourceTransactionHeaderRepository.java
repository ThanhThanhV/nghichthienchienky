package vn.nghichtien.chienky.resource.repository;

import vn.nghichtien.chienky.resource.domain.ResourceTransactionHeader;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResourceTransactionHeaderRepository
        extends JpaRepository<ResourceTransactionHeader, Long> {

    /**
     * Tìm giao dịch theo người chơi và mã giao dịch.
     * Dùng để kiểm tra một yêu cầu cộng/trừ tài nguyên đã được xử lý hay chưa.
     *
     * @param playerId ID hồ sơ người chơi
     * @param transactionKey Mã giao dịch chống xử lý trùng
     * @return Giao dịch nếu đã tồn tại
     */
    Optional<ResourceTransactionHeader> findByPlayerIdAndTransactionKey(
            Long playerId,
            String transactionKey
    );
}