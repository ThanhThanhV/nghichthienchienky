package vn.nghichtien.chienky.power.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Điều hướng trang xếp hạng lực chiến. */
@Controller
@RequestMapping("/ranking")
public class PowerRankingPageController {

    @GetMapping
    public String index() {
        return "ranking/index";
    }
}