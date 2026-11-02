package vn.nghichtien.chienky.hero.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Trang giao diện quản lý và khởi tạo nhân vật.
 * Phân quyền quản trị sẽ được bổ sung ở giai đoạn riêng.
 */
@Controller
@RequestMapping("/hero-management")
public class HeroManagementPageController {

    @GetMapping("/new")
    public String create() {
        return "hero-management/create";
    }
}
