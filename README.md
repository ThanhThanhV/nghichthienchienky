# Nghịch Thiên Chiến Ký

## Phần 2 – Account System

### Chức năng
- Flyway migration tạo bảng `accounts`.
- Đăng ký tài khoản bằng username, email, password.
- BCrypt password hashing.
- Đăng nhập/đăng xuất bằng Spring Security form login.
- Phân quyền `USER` / `ADMIN`; đăng ký công khai luôn nhận `USER`.
- Dashboard yêu cầu đăng nhập.

### Chạy
1. Cài JDK 21 và PostgreSQL.
2. Tạo database `nghich_thien_chien_ky`.
3. Cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` nếu khác mặc định.
4. Chạy `gradle bootRun`.
5. Mở `http://localhost:8080/register` để tạo tài khoản.
6. Đăng nhập tại `http://localhost:8080/login`.

### API/UI route hiện có
- `GET /` trang chủ.
- `GET /register` form đăng ký.
- `POST /register` tạo tài khoản.
- `GET /login` form đăng nhập.
- `POST /login` do Spring Security xử lý.
- `POST /logout` đăng xuất.
- `GET /dashboard` yêu cầu đăng nhập.

### Lưu ý
- `ddl-auto: validate`; Flyway chịu trách nhiệm tạo schema.
- Không có chức năng tự đăng ký ADMIN.
- Chưa triển khai xác minh email, quên mật khẩu, rate limit hoặc CAPTCHA.
- Các hàm Java nghiệp vụ có comment tiếng Việt ở đầu hàm theo quy ước dự án.
