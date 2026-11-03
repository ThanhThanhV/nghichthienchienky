package vn.nghichtien.chienky.hero.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Các trang thuộc khu vực Admin quản lý tướng.
 */
@Controller
@RequestMapping("/admin/heroes")
public class HeroAdminPageController {

    @GetMapping
    public String index() {
        return "admin/heroes";
    }

    @GetMapping("/{heroCode}")
    public String edit(@PathVariable String heroCode) {
        return "admin/hero-edit";
    }

    @GetMapping("/new")
    public String create() {
        return "hero-management/create";
    }
}
