package vn.nghichtien.chienky.battle.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller hiển thị màn hình chiến đấu.
 */
@Controller
@RequestMapping("/battle")
public class BattlePageController {

    /**
     * Mở trang giao diện chiến đấu.
     *
     * @return template battle/index.html
     */
    @GetMapping
    public String index() {
        return "battle/index";
    }
}