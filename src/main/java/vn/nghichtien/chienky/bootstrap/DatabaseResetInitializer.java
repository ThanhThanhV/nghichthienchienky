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
 * Xóa dữ liệu cấu hình hero/skill cũ trước khi GameBootstrapDataInitializer
 * đồng bộ và seed lại từ TAB Lua + Spine catalog.
 *
 * Chỉ xóa dữ liệu catalog dùng chung: chỉ số, skill effects và các JSON mapping
 * được sinh lại khi bootstrap. Không xóa hero definitions, hero instances,
 * skill levels, account, profile, formation, equipment hoặc tài nguyên.
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
     * Xóa effect trước khi seedSkills() tái tạo từ TAB.
     * Các bảng stats được seed lại theo dữ liệu TAB/role trong bootstrap.
     */
    private static final List<String> HERO_TABLES_DELETE_ORDER = List.of(
            "hero_skill_effects",
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

            // Không cho phép reset nếu DB chưa chạy migration đổi skill_id sang code.
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

            // Xóa mapping runtime cũ. GameBootstrapDataInitializer sẽ tạo lại
            // từ hero-skill-mapping.json, TAB Lua và Spine assets trước khi app phục vụ request.
            int clearedSpineMappings = 0;
            if (existingTables.contains("hero_definitions")
                    && hasColumn(connection, "hero_definitions", "spine_skill_animations")) {
                clearedSpineMappings = statement.executeUpdate(
                        "UPDATE hero_definitions SET spine_skill_animations = NULL"
                );
            }

            // level_rows_json chứa bản hydrate TAB của từng skill; buộc bootstrap
            // ghi lại toàn bộ để không giữ JSON level/effect từ lần seed cũ.
            int clearedSkillRows = 0;
            if (existingTables.contains("hero_skill_definitions")
                    && hasColumn(connection, "hero_skill_definitions", "level_rows_json")) {
                clearedSkillRows = statement.executeUpdate(
                        "UPDATE hero_skill_definitions SET level_rows_json = NULL"
                );
            }

            int staleHeroCount = deleteStaleHeroCatalog(
                    connection,
                    existingTables,
                    HeroSeedCodes.current()
            );

            LOGGER.log(
                    System.Logger.Level.INFO,
                    "Hero config reset completed: cleared {0} catalog tables, "
                            + "{1} Spine mappings, {2} TAB skill row payloads, "
                            + "and removed {3} obsolete hero definitions. "
                            + "The next bootstrap runner rebuilds these configurations. "
                            + "Accounts, profiles, owned heroes, skill levels, formations, "
                            + "equipment and player resources were preserved.",
                    clearedTables,
                    clearedSpineMappings,
                    clearedSkillRows,
                    staleHeroCount
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Không thể reset cấu hình hero/skill trước khi seed game",
                    exception
            );
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
        if (!existingTables.contains("hero_definitions")) {
            return 0;
        }

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

        // Xóa bảng con trước bảng cha để không vi phạm khóa ngoại.
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
            try (PreparedStatement delete = connection.prepareStatement(sql)) {
                int parameter = 1;
                for (String heroCode : staleHeroCodes) {
                    delete.setString(parameter++, heroCode);
                }
                delete.executeUpdate();
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
