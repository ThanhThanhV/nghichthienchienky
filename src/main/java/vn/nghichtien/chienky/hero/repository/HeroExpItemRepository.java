package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroExpItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository truy vấn cấu hình vật phẩm kinh nghiệm nhân vật.
 */
public interface HeroExpItemRepository
        extends JpaRepository<HeroExpItem, String> {

    /**
     * Tìm cấu hình vật phẩm EXP đang hoạt động theo mã tài nguyên.
     *
     * @param resourceCode Mã tài nguyên
     * @param enabled Trạng thái hoạt động
     * @return Cấu hình vật phẩm nếu tồn tại
     */
    Optional<HeroExpItem> findByResourceCodeAndEnabled(
            String resourceCode,
            Boolean enabled
    );
}