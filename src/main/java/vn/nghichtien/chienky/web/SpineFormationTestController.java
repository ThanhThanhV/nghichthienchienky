package vn.nghichtien.chienky.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Điều hướng đến trang kiểm tra hiển thị đội hình Spine.
 */
@Controller
public class SpineFormationTestController {

    /**
     * Mở trang demo đội hình chiến đấu.
     *
     * @return tên Thymeleaf template
     */
    @GetMapping("/spine-formation-test")
    public String formationTest() {
        return "spine/formation-test";
    }
}