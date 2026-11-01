package vn.nghichtien.chienky.hero.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Điều hướng màn hình quản lý nhân vật.
 */
@Controller
@RequestMapping("/heroes")
public class HeroPageController {

    @GetMapping
    public String index() {
        return "hero/index";
    }
}
