package vn.nghichtien.chienky.hero.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Trang điều hướng riêng của khu vực quản trị.
 * Việc phân quyền backend được triển khai ở giai đoạn sau.
 */
@Controller
public class AdminPageController {

    @GetMapping("/admin")
    public String index() {
        return "admin/index";
    }
}
