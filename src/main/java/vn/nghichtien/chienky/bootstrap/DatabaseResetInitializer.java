package vn.nghichtien.chienky.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reset trạng thái hero do người chơi sở hữu trước mỗi lần chạy.
 *
 * Catalog hero/skill/effect được đồng bộ lại theo heroCode/skillCode bởi
 * GameBootstrapDataInitializer. Không xóa catalog vì các bảng progression,
 * skill-upgrade và level config hiện có dữ liệu quản trị riêng, chưa có nguồn
 * seed đầy đủ để tái tạo an toàn.
 *
 * Accounts, player profiles, resources, resource transactions và túi trang bị
 * của người chơi luôn được giữ nguyên.
 */
@Component
@ConditionalOnProperty(
        prefix = "game.bootstrap.reset-hero-data",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Order(0)
public class DatabaseResetInitializer implements ApplicationRunner {

    private static final System.Logger LOGGER =
            System.getLogger(DatabaseResetInitializer.class.getName());

    /**
     * Chỉ xóa dữ liệu phụ thuộc vào ID của hero instance.
     * Catalog và cấu hình quản trị được giữ lại, sau đó seed đồng bộ theo code.
     */
    private static final List<String> HERO_TABLES_DELETE_ORDER = List.of(
            // Player-owned hero references first.
            "hero_equipment_slots",
            "player_formation_slots",
            "hero_instance_skills",
            "hero_instances",

            // Seeded skill catalog. Upgrade configs remain because they now use codes.
            "hero_skill_effects",
            "hero_skill_definitions",

            // Hero stats are keyed by hero_code and are re-seeded at startup.
            "hero_base_stats",
            "hero_growth_configs"
    );

    private final DataSource dataSource;

    public DatabaseResetInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            Set<String> existingTables = findApplicationTables(connection);
            int clearedTables = 0;

            for (String table : HERO_TABLES_DELETE_ORDER) {
                if (!existingTables.contains(table)) {
                    continue;
                }

                statement.executeUpdate("DELETE FROM " + quoteIdentifier(table));
                if (hasAutoIncrementColumn(connection, table)) {
                    statement.execute(
                            "ALTER TABLE " + quoteIdentifier(table)
                                    + " AUTO_INCREMENT = 1"
                    );
                }
                clearedTables++;
            }

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Hero reset completed. Cleared {0} hero tables. "
                            + "Hero/skill/effect catalog will be re-seeded by heroCode/skillCode. "
                            + "Accounts, player profiles, resources and code-based progression configs were preserved.",
                    clearedTables
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Không thể reset trạng thái hero sở hữu trước khi seed game",
                    exception
            );
        }
    }

    private Set<String> findApplicationTables(Connection connection)
            throws SQLException {
        Set<String> tables = new HashSet<>();
        try (ResultSet result = connection.getMetaData().getTables(
                connection.getCatalog(), null, "%", new String[]{"TABLE"}
        )) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private boolean hasAutoIncrementColumn(
            Connection connection,
            String table
    ) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(), null, table, "%"
        )) {
            while (columns.next()) {
                if ("YES".equalsIgnoreCase(columns.getString("IS_AUTOINCREMENT"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }
}
