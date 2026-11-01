CREATE TABLE hero_level_configs (
                                    hero_level INT NOT NULL,
                                    required_exp BIGINT NOT NULL,
                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    PRIMARY KEY (hero_level),
                                    CONSTRAINT ck_hero_level_configs_level CHECK (hero_level >= 1),
                                    CONSTRAINT ck_hero_level_configs_exp CHECK (required_exp > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- required_exp là lượng EXP cần có để đi từ cấp hiện tại lên cấp kế tiếp.
-- Cấp 100 là cấp tối đa, không cần cấu hình EXP để lên cấp tiếp theo.
INSERT INTO hero_level_configs (hero_level, required_exp)
WITH RECURSIVE levels AS (
    SELECT 1 AS hero_level
    UNION ALL
    SELECT hero_level + 1
    FROM levels
    WHERE hero_level < 99
)
SELECT
    hero_level,
    CAST(hero_level * hero_level * 100 AS UNSIGNED)
FROM levels;