package vn.nghichtien.chienky.resource.repository;

import vn.nghichtien.chienky.resource.domain.ResourceTransactionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResourceTransactionDetailRepository
        extends JpaRepository<ResourceTransactionDetail, Long> {

    /**
     * Lấy danh sách chi tiết tài nguyên của một giao dịch.
     * Đồng thời tải định nghĩa tài nguyên để có thể đọc mã và tên tài nguyên.
     *
     * @param transactionId ID giao dịch
     * @return Danh sách chi tiết giao dịch
     */
    @Query("""
            SELECT d
            FROM ResourceTransactionDetail d
            JOIN FETCH d.resourceDefinition
            WHERE d.transaction.id = :transactionId
            ORDER BY d.resourceDefinition.resourceCode
            """)
    List<ResourceTransactionDetail> findAllByTransactionIdWithDefinition(
            @Param("transactionId") Long transactionId
    );
}