package vn.nghichtien.chienky.hero.service;

import vn.nghichtien.chienky.hero.domain.HeroDefinition;
import vn.nghichtien.chienky.hero.domain.HeroInstance;
import vn.nghichtien.chienky.hero.domain.HeroInstanceSkill;
import vn.nghichtien.chienky.hero.domain.HeroSkillDefinition;
import vn.nghichtien.chienky.hero.repository.HeroDefinitionRepository;
import vn.nghichtien.chienky.hero.repository.HeroInstanceRepository;
import vn.nghichtien.chienky.hero.repository.HeroInstanceSkillRepository;
import vn.nghichtien.chienky.hero.repository.HeroSkillDefinitionRepository;
import vn.nghichtien.chienky.player.domain.PlayerProfile;
import vn.nghichtien.chienky.player.repository.PlayerProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service cấp nhân vật cho người chơi từ các luồng phần thưởng nội bộ.
 */
@Service
public class HeroAcquisitionService {

    private static final int REQUIRED_SKILL_COUNT = 5;

    private final PlayerProfileRepository playerProfileRepository;
    private final HeroDefinitionRepository heroDefinitionRepository;
    private final HeroSkillDefinitionRepository heroSkillDefinitionRepository;
    private final HeroInstanceRepository heroInstanceRepository;
    private final HeroInstanceSkillRepository heroInstanceSkillRepository;

    /**
     * Khởi tạo service nhận nhân vật.
     *
     * @param playerProfileRepository Repository hồ sơ người chơi
     * @param heroDefinitionRepository Repository định nghĩa nhân vật
     * @param heroSkillDefinitionRepository Repository định nghĩa kỹ năng
     * @param heroInstanceRepository Repository nhân vật sở hữu
     * @param heroInstanceSkillRepository Repository kỹ năng của nhân vật sở hữu
     */
    public HeroAcquisitionService(
            PlayerProfileRepository playerProfileRepository,
            HeroDefinitionRepository heroDefinitionRepository,
            HeroSkillDefinitionRepository heroSkillDefinitionRepository,
            HeroInstanceRepository heroInstanceRepository,
            HeroInstanceSkillRepository heroInstanceSkillRepository
    ) {
        this.playerProfileRepository = playerProfileRepository;
        this.heroDefinitionRepository = heroDefinitionRepository;
        this.heroSkillDefinitionRepository = heroSkillDefinitionRepository;
        this.heroInstanceRepository = heroInstanceRepository;
        this.heroInstanceSkillRepository = heroInstanceSkillRepository;
    }

    /**
     * Cấp một nhân vật cho người chơi và khởi tạo toàn bộ kỹ năng cấp 1.
     * Phương thức chỉ được gọi từ các luồng nghiệp vụ nội bộ đã xác định phần thưởng.
     *
     * @param playerId ID hồ sơ người chơi nhận nhân vật
     * @param heroCode Mã nhân vật được cấp
     * @return Bản sao nhân vật vừa được tạo
     */
    @Transactional
    public HeroInstance acquireHero(Long playerId, String heroCode) {
        if (playerId == null || playerId <= 0) {
            throw new IllegalArgumentException("Player ID không hợp lệ");
        }

        if (heroCode == null || heroCode.isBlank()) {
            throw new IllegalArgumentException("Hero code không hợp lệ");
        }

        PlayerProfile player = playerProfileRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy hồ sơ người chơi"
                ));

        HeroDefinition definition = heroDefinitionRepository
                .findByHeroCodeAndEnabled(heroCode, true)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nhân vật không tồn tại hoặc đã bị vô hiệu hóa"
                ));

        List<HeroSkillDefinition> skills =
                heroSkillDefinitionRepository
                        .findAllByHeroDefinitionHeroCodeAndEnabledTrueOrderBySkillOrderAsc(
                                heroCode
                        );

        validateSkills(skills);

        HeroInstance instance = new HeroInstance(player, definition);
        HeroInstance savedInstance = heroInstanceRepository.save(instance);

        List<HeroInstanceSkill> instanceSkills = skills.stream()
                .map(skill -> new HeroInstanceSkill(savedInstance, skill))
                .toList();

        heroInstanceSkillRepository.saveAll(instanceSkills);

        return savedInstance;
    }

    /**
     * Kiểm tra bộ kỹ năng của nhân vật có đầy đủ và đúng cấu trúc hay không.
     *
     * @param skills Danh sách kỹ năng đang hoạt động
     */
    private void validateSkills(List<HeroSkillDefinition> skills) {
        if (skills == null || skills.size() != REQUIRED_SKILL_COUNT) {
            throw new IllegalStateException(
                    "Nhân vật phải có đúng 5 kỹ năng đang hoạt động"
            );
        }

        for (int i = 0; i < skills.size(); i++) {
            HeroSkillDefinition skill = skills.get(i);

            if (skill.getSkillOrder() == null
                    || skill.getSkillOrder() != i + 1) {
                throw new IllegalStateException(
                        "Thứ tự 5 kỹ năng phải liên tục từ 1 đến 5"
                );
            }
        }
    }
}