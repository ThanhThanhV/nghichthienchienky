package vn.nghichtien.chienky.hero.controller;

import vn.nghichtien.chienky.hero.dto.*;
import vn.nghichtien.chienky.hero.service.HeroService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


import java.util.List;

/**
 * REST API truy vấn danh sách và chi tiết nhân vật người chơi.
 */
@RestController
@RequestMapping("/api/player/heroes")
public class HeroController {

    private final HeroService heroService;

    /**
     * Khởi tạo controller nhân vật.
     *
     * @param heroService Service xử lý nhân vật
     */
    public HeroController(HeroService heroService) {
        this.heroService = heroService;
    }

    /**
     * Lấy danh sách nhân vật của tài khoản đang đăng nhập.
     *
     * @param authentication Thông tin xác thực
     * @return Danh sách nhân vật
     */
    @GetMapping
    public List<HeroResponse> getMyHeroes(
            Authentication authentication
    ) {
        return heroService.getMyHeroes(authentication.getName());
    }

    /**
     * Lấy danh sách vật phẩm EXP đang được bật.
     *
     * @return Danh sách vật phẩm EXP
     */
    @GetMapping("/exp-items")
    public List<HeroExpItemResponse> getEnabledExpItems() {
        return heroService.getEnabledExpItems();
    }

    /**
     * Lấy điều kiện và chi phí nâng sao, đột phá kế tiếp.
     *
     * @param authentication Thông tin xác thực
     * @param instanceId ID bản sao nhân vật
     * @return Các lựa chọn nâng cấp
     */
    @GetMapping("/{instanceId}/progression-options")
    public List<HeroProgressionOptionResponse> getProgressionOptions(
            Authentication authentication,
            @PathVariable Long instanceId
    ) {
        return heroService.getProgressionOptions(
                authentication.getName(),
                instanceId
        );
    }

    /**
     * Lấy điều kiện và chi phí nâng cấp kế tiếp của các kỹ năng.
     *
     * @param authentication Thông tin xác thực
     * @param instanceId ID bản sao nhân vật
     * @return Các lựa chọn nâng kỹ năng
     */
    @GetMapping("/{instanceId}/skill-upgrade-options")
    public List<HeroSkillUpgradeOptionResponse> getSkillUpgradeOptions(
            Authentication authentication,
            @PathVariable Long instanceId
    ) {
        return heroService.getSkillUpgradeOptions(
                authentication.getName(),
                instanceId
        );
    }

    /**
     * Lấy chi tiết một nhân vật thuộc tài khoản đang đăng nhập.
     *
     * @param authentication Thông tin xác thực
     * @param instanceId ID bản sao nhân vật
     * @return Chi tiết nhân vật
     */
    @GetMapping("/{instanceId}")
    public HeroResponse getMyHeroDetail(
            Authentication authentication,
            @PathVariable Long instanceId
    ) {
        return heroService.getMyHeroDetail(
                authentication.getName(),
                instanceId
        );
    }

    /**
     * Cộng kinh nghiệm cho nhân vật thuộc tài khoản đang đăng nhập.
     *
     * @param authentication Thông tin xác thực
     * @param instanceId ID bản sao nhân vật
     * @param request Request chứa lượng EXP
     * @return Thông tin nhân vật sau khi tăng EXP
     */
    @PostMapping("/{instanceId}/experience")
    public HeroResponse addHeroExperience(
            Authentication authentication,
            @PathVariable Long instanceId,
            @Valid @RequestBody AddHeroExperienceRequest request
    ) {
        return heroService.addHeroExperience(
                authentication.getName(),
                instanceId,
                request.getAmount()
        );
    }

    /**
     * Sử dụng vật phẩm EXP để tăng cấp nhân vật của tài khoản hiện tại.
     *
     * @param authentication Thông tin xác thực
     * @param instanceId ID bản sao nhân vật
     * @param request Thông tin vật phẩm sử dụng
     * @return Thông tin nhân vật sau khi sử dụng
     */
    @PostMapping("/{instanceId}/use-exp-item")
    public HeroResponse useHeroExpItem(
            Authentication authentication,
            @PathVariable Long instanceId,
            @Valid @RequestBody UseHeroExpItemRequest request
    ) {
        return heroService.useHeroExpItem(
                authentication.getName(),
                instanceId,
                request
        );
    }

    /**
     * Nâng sao nhân vật của tài khoản đang đăng nhập.
     *
     * @param authentication Thông tin xác thực
     * @param instanceId ID bản sao nhân vật
     * @param request Request giao dịch
     * @return Nhân vật sau nâng sao
     */
    @PostMapping("/{instanceId}/upgrade-star")
    public HeroResponse upgradeHeroStar(
            Authentication authentication,
            @PathVariable Long instanceId,
            @Valid @RequestBody UpgradeHeroRequest request
    ) {
        return heroService.upgradeHeroStar(
                authentication.getName(),
                instanceId,
                request
        );
    }

    /**
     * Đột phá nhân vật của tài khoản đang đăng nhập.
     *
     * @param authentication Thông tin xác thực
     * @param instanceId ID bản sao nhân vật
     * @param request Request giao dịch
     * @return Nhân vật sau đột phá
     */
    @PostMapping("/{instanceId}/breakthrough")
    public HeroResponse breakthroughHero(
            Authentication authentication,
            @PathVariable Long instanceId,
            @Valid @RequestBody UpgradeHeroRequest request
    ) {
        return heroService.breakthroughHero(
                authentication.getName(),
                instanceId,
                request
        );
    }

    /**
     * Tiếp nhận yêu cầu nâng cấp một kỹ năng của tướng đang thuộc sở hữu người chơi.
     *
     * @param authentication thông tin tài khoản đăng nhập
     * @param instanceId ID tướng
     * @param skillId ID kỹ năng
     * @param request thông tin transactionKey
     * @return thông tin tướng sau khi nâng cấp kỹ năng
     */
    @PostMapping("/{instanceId}/skills/{skillId}/upgrade")
    public HeroResponse upgradeSkill(
            Authentication authentication,
            @PathVariable Long instanceId,
            @PathVariable Long skillId,
            @Valid @RequestBody UpgradeHeroSkillRequest request
    ) {
        return heroService.upgradeHeroSkill(
                authentication.getName(),
                instanceId,
                skillId,
                request
        );
    }
}