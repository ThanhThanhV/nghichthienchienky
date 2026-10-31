package vn.nghichtien.chienky.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpineTestController {

    /**
     * Hiển thị trang kiểm tra Spine Renderer.
     *
     * @return tên template kiểm tra Spine
     */
    @GetMapping("/spine-test")
    public String spineTest() {
        return "spine/test";
    }
}