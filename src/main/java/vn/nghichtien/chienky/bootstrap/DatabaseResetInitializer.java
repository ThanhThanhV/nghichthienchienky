package vn.nghichtien.chienky.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reset catalog cấu hình tướng trước mỗi lần chạy.
 *
 * Reset chỉ số gốc và tăng trưởng của toàn bộ hero để seed lại từ TAB.
 * Skill catalog/effect của các hero được đồng bộ tại chỗ bởi GameBootstrapDataInitializer;
 * riêng catalog Gojo được làm mới do mã skill đã được chuẩn hóa lại theo Lua.
 * Account, profile, hero instances, đội hình, trang bị, tài nguyên và cấp skill
 * của các hero khác được giữ nguyên.
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
     * Xóa cấu hình chỉ số để seed lại. Các skill khác được cập nhật tại chỗ;
     * chỉ riêng liên kết skill Gojo được xóa vì skillCode đã đổi theo Lua.
     */
    private static final List<String> HERO_TABLES_DELETE_ORDER = List.of(
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

            // Refuse to delete skill catalog while legacy ID foreign keys still exist.
            // Apply V11__use_hero_and_skill_codes_for_effects_and_upgrades.txt once first.
            if (hasColumn(connection, "hero_instance_skills", "skill_id")
                    || hasColumn(connection, "hero_skill_effects", "skill_id")
                    || hasColumn(connection, "hero_skill_upgrade_configs", "skill_id")) {
                throw new IllegalStateException(
                        "Hero schema vẫn còn skill_id. Hãy chạy migration V11 "
                                + "trước khi bật reset hero data."
                );
            }

            // Gojo skillCode đã được chuẩn hóa theo TAB Lua. Chỉ xóa liên kết
            // kỹ năng Gojo để tránh FK trỏ vào catalog cũ; các hero khác giữ nguyên
            // cấp kỹ năng và được seed cập nhật định nghĩa/effect tại chỗ.
            resetGojoSkillCatalog(connection, existingTables);

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

            int staleHeroCount = deleteStaleHeroCatalog(
                    connection,
                    existingTables,
                    HeroSeedCodes.current()
            );

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Hero reset completed. Cleared {0} hero tables and removed {1} "
                            + "obsolete hero definitions. Current hero catalog will be re-seeded "
                            + "by heroCode/skillCode. Accounts, profiles, hero instances, "
                            + "formations, equipment, resources and existing skill levels "
                            + "for non-Gojo heroes were preserved.",
                    clearedTables,
                    staleHeroCount
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Không thể reset trạng thái hero sở hữu trước khi seed game",
                    exception
            );
        }
    }

    /**
     * Xóa catalog skill Gojo cũ sau khi Lua xác nhận lại thứ tự/mã kỹ năng.
     * Các bản sao Gojo vẫn được giữ; liên kết skill được GameBootstrap tạo lại
     * theo 5 skillCode mới. Các hero khác không bị ảnh hưởng.
     */
    private void resetGojoSkillCatalog(
            Connection connection,
            Set<String> existingTables
    ) throws SQLException {
        if (!existingTables.contains("hero_skill_definitions")) {
            return;
        }

        if (existingTables.contains("hero_instance_skills")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM hero_instance_skills WHERE hero_code = ?"
            )) {
                statement.setString(1, "gojo");
                statement.executeUpdate();
            }
        }

        if (existingTables.contains("hero_skill_effects")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM hero_skill_effects WHERE hero_code = ?"
            )) {
                statement.setString(1, "gojo");
                statement.executeUpdate();
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM hero_skill_definitions WHERE hero_code = ?"
        )) {
            statement.setString(1, "gojo");
            statement.executeUpdate();
        }
    }

    /**
     * Xóa catalog và cấu hình chỉ thuộc hero đã bị loại khỏi seed hiện tại.
     * Không xóa dữ liệu sở hữu tướng của người chơi.
     */
    private int deleteStaleHeroCatalog(
            Connection connection,
            Set<String> existingTables,
            List<String> currentHeroCodes
    ) throws SQLException {
        if (currentHeroCodes == null || currentHeroCodes.isEmpty()) {
            throw new IllegalStateException(
                    "Danh sách hero seed không được rỗng; từ chối xóa catalog"
            );
        }

        Set<String> staleHeroCodes = new java.util.LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT hero_code FROM hero_definitions"
             )) {
            while (rows.next()) {
                String heroCode = rows.getString("hero_code");
                if (heroCode != null && !currentHeroCodes.contains(heroCode)) {
                    staleHeroCodes.add(heroCode);
                }
            }
        }

        if (staleHeroCodes.isEmpty()) {
            return 0;
        }

        // Xóa các bảng không cascade trước khi xóa hero_definitions.
        // Chi phí của config được xóa bằng ON DELETE CASCADE trên bảng cost.
        List<String> staleCatalogTables = List.of(
                "hero_skill_upgrade_configs",
                "hero_stat_progression_bonuses",
                "hero_progression_configs",
                "hero_base_stats",
                "hero_growth_configs",
                "hero_skill_effects",
                "hero_skill_definitions",
                "hero_definitions"
        );

        for (String table : staleCatalogTables) {
            if (!existingTables.contains(table)
                    || !hasColumn(connection, table, "hero_code")) {
                continue;
            }

            String placeholders = String.join(
                    ",", java.util.Collections.nCopies(staleHeroCodes.size(), "?")
            );
            String sql = "DELETE FROM " + quoteIdentifier(table)
                    + " WHERE hero_code IN (" + placeholders + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int parameter = 1;
                for (String heroCode : staleHeroCodes) {
                    statement.setString(parameter++, heroCode);
                }
                statement.executeUpdate();
            }
        }

        return staleHeroCodes.size();
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

    private boolean hasColumn(
            Connection connection,
            String table,
            String column
    ) throws SQLException {
        if (table == null || column == null) {
            return false;
        }
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(), null, table, column
        )) {
            return columns.next();
        }
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
