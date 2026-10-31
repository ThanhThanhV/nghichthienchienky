package vn.nghichtien.chienky.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    /**
     * Hiển thị trang đăng nhập.
     *
     * @return tên template đăng nhập
     */
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    /**
     * Hiển thị trang đăng ký tài khoản.
     *
     * @return tên template đăng ký
     */
    @GetMapping("/register")
    public String register() {
        return "auth/register";
    }

    /**
     * Hiển thị Dashboard sau khi người chơi đăng nhập thành công.
     *
     * @return tên template Dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard/index";
    }
}