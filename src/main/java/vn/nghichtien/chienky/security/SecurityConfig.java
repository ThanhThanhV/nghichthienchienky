package vn.nghichtien.chienky.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình xác thực web, quyền truy cập trang và mã hóa mật khẩu.
 * Trang chủ, đăng nhập, đăng ký và static assets công khai; dashboard yêu cầu đăng nhập.
 */
@Configuration
public class SecurityConfig {

    /**
     * Khai báo BCrypt encoder dùng chung khi đăng ký và xác thực mật khẩu.
     *
     * @return PasswordEncoder sử dụng BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Thiết lập luật bảo vệ URL, form login và logout cho ứng dụng web.
     *
     * @param http đối tượng cấu hình bảo mật HTTP của Spring Security
     * @return filter chain được Spring Security sử dụng
     * @throws Exception nếu cấu hình security không hợp lệ
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/dashboard").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }
}
