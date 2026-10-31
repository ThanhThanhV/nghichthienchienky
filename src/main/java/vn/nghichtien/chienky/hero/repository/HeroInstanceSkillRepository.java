package vn.nghichtien.chienky.hero.repository;

import vn.nghichtien.chienky.hero.domain.HeroInstanceSkill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository truy vấn cấp độ kỹ năng của từng bản sao nhân vật.
 */
public interface HeroInstanceSkillRepository
        extends JpaRepository<HeroInstanceSkill, Long> {

    /**
     * Lấy toàn bộ kỹ năng của một bản sao nhân vật,
     * kèm định nghĩa kỹ năng.
     *
     * @param heroInstanceId ID bản sao nhân vật
     * @return Danh sách kỹ năng
     */
    @EntityGraph(attributePaths = "skillDefinition")
    List<HeroInstanceSkill> findAllByHeroInstanceIdOrderBySkillDefinitionSkillOrderAsc(
            Long heroInstanceId
    );

    /**
     * Tìm cấp độ một kỹ năng của bản sao nhân vật.
     *
     * @param heroInstanceId ID bản sao nhân vật
     * @param skillId ID định nghĩa kỹ năng
     * @return Bản ghi cấp độ kỹ năng
     */
    Optional<HeroInstanceSkill> findByHeroInstanceIdAndSkillDefinitionId(
            Long heroInstanceId,
            Long skillId
    );
}