package vn.nghichtien.chienky.web;

import jakarta.validation.Valid;
import vn.nghichtien.chienky.account.dto.RegisterRequest;
import vn.nghichtien.chienky.account.service.AccountService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller đăng ký, đăng nhập và trang dashboard.
 */
@Controller
public class AuthController {

    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Hiển thị trang đăng nhập.
     */
    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return "redirect:/dashboard";
        }

        return "auth/login";
    }

    /**
     * Hiển thị trang đăng ký.
     */
    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    /**
     * Tiếp nhận và xử lý đăng ký tài khoản.
     */
    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            accountService.register(request);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đăng ký thành công. Hãy đăng nhập."
            );
            return "redirect:/login";

        } catch (IllegalArgumentException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            return "auth/register";
        }
    }

    /**
     * Hiển thị trang chính sau khi đăng nhập.
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        return "dashboard/index";
    }
}