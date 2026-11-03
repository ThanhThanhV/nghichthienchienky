package vn.nghichtien.chienky.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Reset toàn bộ dữ liệu trong schema game trước khi chạy các bootstrap seed.
 *
 * Chỉ xóa dữ liệu của các bảng thuộc database đang được cấu hình trong
 * datasource; không drop database, không drop bảng và không thay đổi schema.
 * Phải chạy trước ResourceBootstrapDataInitializer và GameBootstrapDataInitializer.
 *
 * LƯU Ý: mọi tài khoản, hồ sơ người chơi, đội hình, vật phẩm và tiến trình đều
 * bị xóa sau mỗi lần khởi động ứng dụng. Đây là chế độ seed lại dành cho môi
 * trường phát triển theo yêu cầu hiện tại của dự án.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseResetApplicationRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DatabaseResetApplicationRunner.class);

    private final DataSource dataSource;

    public DatabaseResetApplicationRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            boolean foreignKeyChecksDisabled = true;

            try {
                List<String> tableNames = findCurrentSchemaTables(statement);

                for (String tableName : tableNames) {
                    String escapedName = tableName.replace("`", "``");
                    statement.execute("TRUNCATE TABLE `" + escapedName + "`");
                }

                log.warn(
                        "Đã reset dữ liệu {} bảng trong database hiện tại trước khi seed.",
                        tableNames.size()
                );
            } finally {
                if (foreignKeyChecksDisabled) {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Không thể reset dữ liệu database trước khi seed game.",
                    exception
            );
        }
    }

    private List<String> findCurrentSchemaTables(Statement statement)
            throws SQLException {
        List<String> tableNames = new ArrayList<>();

        try (ResultSet resultSet = statement.executeQuery("""
                SELECT TABLE_NAME
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """)) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("TABLE_NAME"));
            }
        }

        return tableNames;
    }
}
