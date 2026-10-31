
package vn.nghichtien.chienky.resource.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.nghichtien.chienky.resource.domain.ResourceDefinition;

import java.util.List;

public interface ResourceDefinitionRepository
        extends JpaRepository<ResourceDefinition, String> {

    /**
     * Lấy danh sách loại tài nguyên đang được kích hoạt.
     * Dùng khi khởi tạo tài nguyên cho người chơi mới.
     *
     * @return danh sách cấu hình tài nguyên đang hoạt động
     */
    List<ResourceDefinition> findAllByEnabledTrue();
}
