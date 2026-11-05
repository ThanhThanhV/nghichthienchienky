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
 * Xóa riêng dữ liệu catalog và trạng thái sở hữu hero trước mỗi lần seed.
 *
 * Tuyệt đối không xóa accounts, player_profiles, số dư/giao dịch tài nguyên,
 * túi trang bị của người chơi hoặc metadata migration.
 *
 * Có thể tắt bằng game.bootstrap.reset-hero-data.enabled=false.
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
     * Thứ tự xóa từ bảng phụ thuộc đến bảng gốc.
     * Chỉ các bảng trong allowlist này mới có thể bị xóa.
     */
    private static final List<String> HERO_TABLES_DELETE_ORDER = List.of(
            "hero_equipment_slots",
            "player_formation_slots",
            "hero_instance_skills",
            "hero_instances",
            "hero_skill_upgrade_costs",
            "hero_skill_upgrade_configs",
            "hero_skill_effects",
            "hero_skill_definitions",
            "hero_progression_costs",
            "hero_progression_configs",
            "hero_stat_progression_bonuses",
            "hero_base_stats",
            "hero_growth_configs",
            "hero_level_configs",
            "hero_exp_items",
            "hero_definitions"
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

            // Không tắt FK checks: thứ tự allowlist phải tuân thủ quan hệ thật.
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
                    "Hero data reset completed. Cleared {0} hero tables; "
                            + "accounts, player profiles, resources and player equipment were preserved.",
                    clearedTables
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Không thể xóa dữ liệu hero trước khi nạp seed mới",
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
