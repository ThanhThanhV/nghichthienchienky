package vn.nghichtien.chienky.account.domain;

/**
 * Danh sách quyền truy cập cấp tài khoản.
 * USER là quyền mặc định khi đăng ký; ADMIN chỉ được cấp bởi quy trình quản trị.
 */
public enum AccountRole {
    USER,
    ADMIN
}
