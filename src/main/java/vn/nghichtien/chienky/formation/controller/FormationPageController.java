package vn.nghichtien.chienky.formation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Điều hướng màn hình quản lý đội hình.
 */
@Controller
@RequestMapping("/formation")
public class FormationPageController {

    @GetMapping
    public String index() {
        return "formation/index";
    }
}