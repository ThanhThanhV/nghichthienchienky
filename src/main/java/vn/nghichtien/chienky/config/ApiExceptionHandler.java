package vn.nghichtien.chienky.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(
            IllegalArgumentException ex
    ) {
        log.warn("Business validation error: {}", ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "message",
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : "Dữ liệu không hợp lệ."
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {

        // Ghi đầy đủ stack trace
        log.error("UNHANDLED EXCEPTION: ", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message",
                        "Đã xảy ra lỗi hệ thống."
                ));
    }
}