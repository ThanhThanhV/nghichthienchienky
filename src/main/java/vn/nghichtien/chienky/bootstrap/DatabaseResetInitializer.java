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
 * Reset dữ liệu sở hữu hero và catalog skill/effect trước mỗi lần chạy.
 *
 * Hero definitions và các cấu hình progression được giữ theo heroCode.
 * Skill definitions/effects được xóa rồi seed lại từ TAB/Spine theo
 * heroCode + skillCode. Cấu hình nâng cấp kỹ năng vẫn được giữ vì đã chuyển
 * sang định danh code, không còn phụ thuộc ID tự tăng của skill.
 *
 * Accounts, player profiles, resources, resource transactions và dữ liệu
 * không thuộc hero luôn được giữ nguyên.
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
     * Xóa từ các bảng phụ thuộc đến catalog skill.
     * Các cấu hình nghiệp vụ được giữ lại khi đã định danh bằng code.
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
                            + "by heroCode/skillCode. Accounts, player profiles, resources and "
                            + "progression configs for current hero codes were preserved.",
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
     * Xóa cấu hình và catalog chỉ thuộc hero đã bị loại khỏi seed hiện tại.
     * Config của các hero còn seed được giữ lại vì đây là dữ liệu nghiệp vụ
     * (chi phí nâng sao/đột phá và stat bonus), không phải dữ liệu sở hữu.
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
