CREATE TABLE hero_skill_upgrade_configs (
                                            id BIGINT NOT NULL AUTO_INCREMENT,
                                            skill_id BIGINT NOT NULL,
                                            target_skill_level INT NOT NULL,
                                            required_hero_level INT NOT NULL DEFAULT 1,
                                            enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                ON UPDATE CURRENT_TIMESTAMP,
                                            PRIMARY KEY (id),
                                            CONSTRAINT fk_hero_skill_upgrade_config_skill
                                                FOREIGN KEY (skill_id)
                                                    REFERENCES hero_skill_definitions(id),
                                            CONSTRAINT uk_hero_skill_upgrade_config
                                                UNIQUE (skill_id, target_skill_level),
                                            CONSTRAINT ck_hero_skill_upgrade_target
                                                CHECK (target_skill_level >= 2),
                                            CONSTRAINT ck_hero_skill_upgrade_hero_level
                                                CHECK (required_hero_level >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE hero_skill_upgrade_costs (
                                          id BIGINT NOT NULL AUTO_INCREMENT,
                                          upgrade_config_id BIGINT NOT NULL,
                                          resource_code VARCHAR(50) NOT NULL,
                                          required_amount DECIMAL(24,6) NOT NULL,
                                          PRIMARY KEY (id),
                                          CONSTRAINT fk_hero_skill_upgrade_cost_config
                                              FOREIGN KEY (upgrade_config_id)
                                                  REFERENCES hero_skill_upgrade_configs(id)
                                                  ON DELETE CASCADE,
                                          CONSTRAINT fk_hero_skill_upgrade_cost_resource
                                              FOREIGN KEY (resource_code)
                                                  REFERENCES resource_definitions(resource_code),
                                          CONSTRAINT uk_hero_skill_upgrade_cost
                                              UNIQUE (upgrade_config_id, resource_code),
                                          CONSTRAINT ck_hero_skill_upgrade_cost_amount
                                              CHECK (required_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_hero_skill_upgrade_lookup
    ON hero_skill_upgrade_configs(skill_id, target_skill_level, enabled);