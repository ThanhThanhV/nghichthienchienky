package vn.nghichtien.chienky.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Điều hướng đến công cụ duyệt và xem trước toàn bộ Spine effect assets.
 */
@Controller
public class SpineEffectLabController {

    /**
     * Mở công cụ kiểm tra effect theo nhóm ID tướng.
     *
     * @return tên Thymeleaf template
     */
    @GetMapping("/spine-effects")
    public String effectLab() {
        return "spine/effect-lab";
    }
}
