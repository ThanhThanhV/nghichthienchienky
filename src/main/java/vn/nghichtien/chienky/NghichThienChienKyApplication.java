package vn.nghichtien.chienky;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi chạy ứng dụng Nghịch Thiên Chiến Ký.
 * Khởi tạo Spring Context và toàn bộ các module backend được đăng ký.
 */
@SpringBootApplication
public class NghichThienChienKyApplication {

    /**
     * Khởi chạy Spring Boot application.
     *
     * @param args tham số truyền vào khi chạy ứng dụng
     */
    public static void main(String[] args) {
        SpringApplication.run(NghichThienChienKyApplication.class, args);
        System.out.println("======NghichThienChienKyApplication RUNNING======");
    }
}
