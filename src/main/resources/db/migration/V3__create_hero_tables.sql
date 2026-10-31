-- =========================================================
-- 1. Danh mục nhân vật
-- =========================================================
CREATE TABLE hero_definitions (
                                  hero_code VARCHAR(50) NOT NULL,
                                  hero_name VARCHAR(100) NOT NULL,
                                  description TEXT NULL,

                                  rarity VARCHAR(20) NOT NULL,
                                  faction VARCHAR(50) NULL,
                                  role VARCHAR(30) NOT NULL,

                                  avatar_code VARCHAR(50) NOT NULL,
                                  spine_skeleton VARCHAR(255) NULL,

                                  enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                  sort_order INT NOT NULL DEFAULT 0,

                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

                                  PRIMARY KEY (hero_code),

                                  CONSTRAINT ck_hero_rarity
                                      CHECK (rarity IN ('COMMON', 'RARE', 'EPIC', 'LEGENDARY', 'MYTHIC')),

                                  CONSTRAINT ck_hero_role
                                      CHECK (role IN ('WARRIOR', 'ASSASSIN', 'MAGE', 'TANK', 'SUPPORT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 2. Chỉ số cơ bản của nhân vật
-- =========================================================
CREATE TABLE hero_base_stats (
                                 hero_code VARCHAR(50) NOT NULL,

                                 base_hp DECIMAL(24,6) NOT NULL DEFAULT 0,
                                 base_atk DECIMAL(24,6) NOT NULL DEFAULT 0,
                                 base_def DECIMAL(24,6) NOT NULL DEFAULT 0,
                                 base_speed DECIMAL(24,6) NOT NULL DEFAULT 0,

                                 base_crit DECIMAL(24,6) NOT NULL DEFAULT 0,
                                 base_crit_damage DECIMAL(24,6) NOT NULL DEFAULT 0,
                                 base_accuracy DECIMAL(24,6) NOT NULL DEFAULT 0,
                                 base_resistance DECIMAL(24,6) NOT NULL DEFAULT 0,

                                 base_pen_flat DECIMAL(24,6) NOT NULL DEFAULT 0,
                                 base_pen_percent DECIMAL(24,6) NOT NULL DEFAULT 0,
                                 base_lifesteal DECIMAL(24,6) NOT NULL DEFAULT 0,

                                 PRIMARY KEY (hero_code),

                                 CONSTRAINT fk_hero_base_stats_definition
                                     FOREIGN KEY (hero_code)
                                         REFERENCES hero_definitions(hero_code)
                                         ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 3. Chỉ số tăng theo cấp nhân vật
-- =========================================================
CREATE TABLE hero_growth_configs (
                                     hero_code VARCHAR(50) NOT NULL,

                                     growth_hp DECIMAL(24,6) NOT NULL DEFAULT 0,
                                     growth_atk DECIMAL(24,6) NOT NULL DEFAULT 0,
                                     growth_def DECIMAL(24,6) NOT NULL DEFAULT 0,
                                     growth_speed DECIMAL(24,6) NOT NULL DEFAULT 0,

                                     growth_crit DECIMAL(24,6) NOT NULL DEFAULT 0,
                                     growth_crit_damage DECIMAL(24,6) NOT NULL DEFAULT 0,
                                     growth_accuracy DECIMAL(24,6) NOT NULL DEFAULT 0,
                                     growth_resistance DECIMAL(24,6) NOT NULL DEFAULT 0,

                                     growth_pen_flat DECIMAL(24,6) NOT NULL DEFAULT 0,
                                     growth_pen_percent DECIMAL(24,6) NOT NULL DEFAULT 0,
                                     growth_lifesteal DECIMAL(24,6) NOT NULL DEFAULT 0,

                                     PRIMARY KEY (hero_code),

                                     CONSTRAINT fk_hero_growth_definition
                                         FOREIGN KEY (hero_code)
                                             REFERENCES hero_definitions(hero_code)
                                             ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 4. Danh sách kỹ năng của nhân vật
-- Mỗi nhân vật có 5 kỹ năng:
-- 1 BASIC, 1 ACTIVE, 1 ULTIMATE, 2 PASSIVE
-- =========================================================
CREATE TABLE hero_skill_definitions (
                                        id BIGINT NOT NULL AUTO_INCREMENT,

                                        hero_code VARCHAR(50) NOT NULL,
                                        skill_code VARCHAR(50) NOT NULL,
                                        skill_name VARCHAR(100) NOT NULL,

                                        skill_type VARCHAR(20) NOT NULL,
                                        skill_order TINYINT NOT NULL,

                                        description TEXT NULL,
                                        icon_code VARCHAR(50) NULL,

                                        enabled BOOLEAN NOT NULL DEFAULT TRUE,

                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                            ON UPDATE CURRENT_TIMESTAMP,

                                        PRIMARY KEY (id),

                                        CONSTRAINT uk_hero_skill_code
                                            UNIQUE (hero_code, skill_code),

                                        CONSTRAINT uk_hero_skill_order
                                            UNIQUE (hero_code, skill_order),

                                        CONSTRAINT fk_hero_skill_definition
                                            FOREIGN KEY (hero_code)
                                                REFERENCES hero_definitions(hero_code)
                                                ON DELETE CASCADE,

                                        CONSTRAINT ck_hero_skill_type
                                            CHECK (skill_type IN ('BASIC', 'ACTIVE', 'ULTIMATE', 'PASSIVE')),

                                        CONSTRAINT ck_hero_skill_order
                                            CHECK (skill_order BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 5. Các hiệu ứng cấu thành kỹ năng
-- Một kỹ năng có thể có nhiều hiệu ứng thực hiện tuần tự.
-- config_json lưu cấu hình riêng của từng hiệu ứng.
-- =========================================================
CREATE TABLE hero_skill_effects (
                                    id BIGINT NOT NULL AUTO_INCREMENT,

                                    skill_id BIGINT NOT NULL,
                                    effect_order INT NOT NULL,

                                    effect_type VARCHAR(30) NOT NULL,
                                    target_type VARCHAR(50) NOT NULL,

                                    multiplier DECIMAL(24,6) NULL,
                                    chance DECIMAL(10,6) NULL,
                                    duration INT NULL,

                                    config_json LONGTEXT NULL,
                                    enabled BOOLEAN NOT NULL DEFAULT TRUE,

                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    PRIMARY KEY (id),

                                    CONSTRAINT uk_hero_skill_effect_order
                                        UNIQUE (skill_id, effect_order),

                                    CONSTRAINT fk_hero_skill_effect_definition
                                        FOREIGN KEY (skill_id)
                                            REFERENCES hero_skill_definitions(id)
                                            ON DELETE CASCADE,

                                    CONSTRAINT ck_hero_skill_effect_chance
                                        CHECK (chance IS NULL OR (chance >= 0 AND chance <= 1)),

                                    CONSTRAINT ck_hero_skill_effect_duration
                                        CHECK (duration IS NULL OR duration >= 0),

                                    CONSTRAINT ck_hero_skill_effect_order
                                        CHECK (effect_order >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 6. Nhân vật người chơi đang sở hữu
-- Một người chơi có thể sở hữu nhiều bản sao cùng hero_code.
-- =========================================================
CREATE TABLE hero_instances (
                                id BIGINT NOT NULL AUTO_INCREMENT,

                                player_id BIGINT NOT NULL,
                                hero_code VARCHAR(50) NOT NULL,

                                hero_level INT NOT NULL DEFAULT 1,
                                star_level INT NOT NULL DEFAULT 0,
                                breakthrough_level INT NOT NULL DEFAULT 0,
                                experience BIGINT NOT NULL DEFAULT 0,

                                obtained_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,

                                PRIMARY KEY (id),

                                CONSTRAINT fk_hero_instance_player
                                    FOREIGN KEY (player_id)
                                        REFERENCES player_profiles(id),

                                CONSTRAINT fk_hero_instance_definition
                                    FOREIGN KEY (hero_code)
                                        REFERENCES hero_definitions(hero_code),

                                CONSTRAINT ck_hero_instance_level
                                    CHECK (hero_level >= 1),

                                CONSTRAINT ck_hero_instance_star
                                    CHECK (star_level >= 0),

                                CONSTRAINT ck_hero_instance_breakthrough
                                    CHECK (breakthrough_level >= 0),

                                CONSTRAINT ck_hero_instance_experience
                                    CHECK (experience >= 0),

                                INDEX idx_hero_instance_player (player_id),
                                INDEX idx_hero_instance_hero (hero_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 7. Cấp độ kỹ năng của từng nhân vật người chơi sở hữu
-- =========================================================
CREATE TABLE hero_instance_skills (
                                      id BIGINT NOT NULL AUTO_INCREMENT,

                                      hero_instance_id BIGINT NOT NULL,
                                      skill_id BIGINT NOT NULL,

                                      skill_level INT NOT NULL DEFAULT 1,

                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,

                                      PRIMARY KEY (id),

                                      CONSTRAINT uk_hero_instance_skill
                                          UNIQUE (hero_instance_id, skill_id),

                                      CONSTRAINT fk_instance_skill_hero
                                          FOREIGN KEY (hero_instance_id)
                                              REFERENCES hero_instances(id)
                                              ON DELETE CASCADE,

                                      CONSTRAINT fk_instance_skill_definition
                                          FOREIGN KEY (skill_id)
                                              REFERENCES hero_skill_definitions(id),

                                      CONSTRAINT ck_hero_instance_skill_level
                                          CHECK (skill_level >= 1),

                                      INDEX idx_instance_skill_hero (hero_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;