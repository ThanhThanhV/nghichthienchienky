package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy vấn danh mục nhân vật gốc.
 */
public interface HeroDefinitionRepository extends JpaRepository<HeroDefinition, String> {

    /**
     * Lấy danh sách nhân vật đang được kích hoạt theo thứ tự hiển thị.
     *
     * @return Danh sách nhân vật
     */
    List<HeroDefinition> findAllByEnabledTrueOrderBySortOrderAscHeroNameAsc();

    /**
     * Tìm nhân vật theo mã và trạng thái kích hoạt.
     *
     * @param heroCode Mã nhân vật
     * @param enabled Trạng thái kích hoạt
     * @return Nhân vật nếu tồn tại
     */
    Optional<HeroDefinition> findByHeroCodeAndEnabled(
            String heroCode,
            Boolean enabled
    );

    /**
     * Kiểm tra mã nhân vật đã tồn tại hay chưa.
     *
     * @param heroCode Mã nhân vật
     * @return true nếu đã tồn tại
     */
    boolean existsByHeroCode(String heroCode);
}