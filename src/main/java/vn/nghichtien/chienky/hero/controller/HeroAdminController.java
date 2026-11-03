package vn.nghichtien.chienky.hero.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.nghichtien.chienky.hero.dto.HeroAdminUpdateRequest;
import vn.nghichtien.chienky.hero.service.HeroAdminService;

import java.util.List;
import java.util.Map;

/**
 * API quản trị danh mục tướng, chỉ ADMIN được phép truy cập.
 */
@RestController
@RequestMapping("/admin/api/heroes")
public class HeroAdminController {

    private final HeroAdminService heroAdminService;

    public HeroAdminController(HeroAdminService heroAdminService) {
        this.heroAdminService = heroAdminService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return heroAdminService.listHeroes();
    }

    @GetMapping("/{heroCode}")
    public Map<String, Object> detail(@PathVariable String heroCode) {
        return heroAdminService.getHero(heroCode);
    }

    @PutMapping("/{heroCode}")
    public Map<String, Object> update(
            @PathVariable String heroCode,
            @Valid @RequestBody HeroAdminUpdateRequest request
    ) {
        return heroAdminService.updateHero(heroCode, request);
    }
}
