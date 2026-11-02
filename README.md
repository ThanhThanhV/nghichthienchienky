# Nghịch Thiên Chiến Ký

## Công nghệ

- Java 21.
- Spring Boot 3.5.7.
- Spring MVC và Thymeleaf.
- Spring Security.
- Spring Data JPA và Hibernate.
- MySQL.

## Cấu hình database

Ứng dụng sử dụng MySQL. Tạo database trước khi chạy:

```sql
CREATE DATABASE nghich_thien_chien_ky
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

Các biến môi trường kết nối database:

| Biến | Mặc định |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/nghich_thien_chien_ky?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | `123456` |

Hibernate tự đồng bộ schema từ các JPA Entity nhờ cấu hình `spring.jpa.hibernate.ddl-auto: update`. Không sử dụng Flyway để quản lý migration.

## Chạy ứng dụng

1. Cài JDK 21 và MySQL.
2. Tạo database `nghich_thien_chien_ky` theo hướng dẫn phía trên.
3. Thiết lập `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` nếu thông tin kết nối khác mặc định.
4. Chạy ứng dụng:

   ```bash
   ./gradlew bootRun
   ```

   Trên Windows:

   ```bat
   gradlew.bat bootRun
   ```

5. Mở `http://localhost:8080`.

## Account System

- Đăng ký tài khoản bằng username, email, password.
- BCrypt password hashing.
- Đăng nhập/đăng xuất bằng Spring Security form login.
- Phân quyền `USER` / `ADMIN`; đăng ký công khai luôn nhận `USER`.
- Dashboard yêu cầu đăng nhập.

### API/UI route tài khoản

- `GET /` trang chủ.
- `GET /register` form đăng ký.
- `POST /register` tạo tài khoản.
- `GET /login` form đăng nhập.
- `POST /login` do Spring Security xử lý.
- `POST /logout` đăng xuất.
- `GET /dashboard` yêu cầu đăng nhập.

## Lưu ý

- Schema được cập nhật từ JPA Entity bằng `ddl-auto: update`; cần sao lưu database trước khi triển khai thay đổi schema trên môi trường có dữ liệu quan trọng.
- Không có chức năng tự đăng ký ADMIN.
- Chưa triển khai xác minh email, quên mật khẩu, rate limit hoặc CAPTCHA.
- Các hàm Java nghiệp vụ có comment tiếng Việt ở đầu hàm theo quy ước dự án.
