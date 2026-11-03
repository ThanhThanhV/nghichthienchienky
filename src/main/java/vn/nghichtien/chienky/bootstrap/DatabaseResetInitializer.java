package vn.nghichtien.chienky.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
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
 * Development bootstrap: clear all application data before resource and game
 * seeders run. The schema itself is retained and managed by Hibernate.
 *
 * Migration metadata tables are deliberately preserved so a future migration
 * runner does not replay already-applied migrations.
 */
@Component
@Order(0)
public class DatabaseResetInitializer implements ApplicationRunner {

    private static final System.Logger LOGGER =
            System.getLogger(DatabaseResetInitializer.class.getName());

    private final DataSource dataSource;

    public DatabaseResetInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            List<String> tables = findApplicationTables(connection);

            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : tables) {
                    if (isMigrationMetadataTable(table)) {
                        continue;
                    }
                    statement.execute("TRUNCATE TABLE " + quoteIdentifier(table));
                }
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Database reset completed. Cleared {0} application tables.",
                    tables.stream().filter(table -> !isMigrationMetadataTable(table)).count()
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Không thể xóa dữ liệu database trước khi seed game",
                    exception
            );
        }
    }

    private List<String> findApplicationTables(Connection connection)
            throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet result = connection.getMetaData().getTables(
                connection.getCatalog(), null, "%", new String[]{"TABLE"}
        )) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private boolean isMigrationMetadataTable(String table) {
        String normalized = table.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("flyway_")
                || normalized.startsWith("databasechangelog")
                || normalized.equals("schema_version");
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
