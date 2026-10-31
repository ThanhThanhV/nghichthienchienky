package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroSkillDefinition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy vấn định nghĩa kỹ năng và hiệu ứng của nhân vật.
 */
public interface HeroSkillDefinitionRepository
        extends JpaRepository<HeroSkillDefinition, Long> {

    /**
     * Lấy toàn bộ kỹ năng đang kích hoạt của một nhân vật,
     * đồng thời tải danh sách hiệu ứng theo từng kỹ năng.
     *
     * @param heroCode Mã nhân vật
     * @return Danh sách kỹ năng
     */
    @EntityGraph(attributePaths = "effects")
    List<HeroSkillDefinition> findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
            String heroCode
    );

    /**
     * Lấy chi tiết một kỹ năng theo mã nhân vật và mã kỹ năng.
     *
     * @param heroCode Mã nhân vật
     * @param skillCode Mã kỹ năng
     * @return Kỹ năng nếu tồn tại
     */
    @EntityGraph(attributePaths = "effects")
    Optional<HeroSkillDefinition> findByHeroDefinitionHeroCodeAndSkillCodeAndEnabledTrue(
            String heroCode,
            String skillCode
    );

    /**
     * Kiểm tra nhân vật đã có kỹ năng với mã tương ứng chưa.
     *
     * @param heroCode Mã nhân vật
     * @param skillCode Mã kỹ năng
     * @return true nếu kỹ năng đã tồn tại
     */
    boolean existsByHeroDefinitionHeroCodeAndSkillCode(
            String heroCode,
            String skillCode
    );

    /**
     * Đếm số kỹ năng đang kích hoạt của nhân vật.
     *
     * @param heroCode Mã nhân vật
     * @return Số lượng kỹ năng
     */
    long countByHeroDefinitionHeroCodeAndEnabledTrue(String heroCode);
}