package vn.nghichtien.chienky.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Điều phối các trang giao diện công khai của game.
 * Giai đoạn đầu chỉ cung cấp trang Home để kiểm tra Thymeleaf và static assets.
 */
@Controller
public class HomeController {

    /**
     * Hiển thị trang chủ của Nghịch Thiên Chiến Ký.
     *
     * @param model dữ liệu được truyền sang Thymeleaf template
     * @return tên template trang chủ
     */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("gameName", "Nghịch Thiên Chiến Ký");
        model.addAttribute("buildVersion", "0.1.0-SNAPSHOT");
        return "home";
    }
}
