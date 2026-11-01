CREATE TABLE hero_progression_configs (
                                          id BIGINT NOT NULL AUTO_INCREMENT,
                                          hero_code VARCHAR(50) NOT NULL,
                                          progression_type VARCHAR(20) NOT NULL,
                                          target_level INT NOT NULL,
                                          required_hero_level INT NOT NULL DEFAULT 1,
                                          required_star_level INT NOT NULL DEFAULT 0,
                                          enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                              ON UPDATE CURRENT_TIMESTAMP,

                                          PRIMARY KEY (id),

                                          CONSTRAINT fk_hero_progression_definition
                                              FOREIGN KEY (hero_code)
                                                  REFERENCES hero_definitions(hero_code),

                                          CONSTRAINT uk_hero_progression_level
                                              UNIQUE (hero_code, progression_type, target_level),

                                          CONSTRAINT ck_hero_progression_type
                                              CHECK (progression_type IN ('STAR', 'BREAKTHROUGH')),

                                          CONSTRAINT ck_hero_progression_target
                                              CHECK (target_level >= 1),

                                          CONSTRAINT ck_hero_progression_hero_level
                                              CHECK (required_hero_level >= 1),

                                          CONSTRAINT ck_hero_progression_star
                                              CHECK (required_star_level >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE hero_progression_costs (
                                        id BIGINT NOT NULL AUTO_INCREMENT,
                                        progression_config_id BIGINT NOT NULL,
                                        resource_code VARCHAR(50) NOT NULL,
                                        required_amount DECIMAL(24,6) NOT NULL,

                                        PRIMARY KEY (id),

                                        CONSTRAINT fk_hero_progression_cost_config
                                            FOREIGN KEY (progression_config_id)
                                                REFERENCES hero_progression_configs(id)
                                                ON DELETE CASCADE,

                                        CONSTRAINT fk_hero_progression_cost_resource
                                            FOREIGN KEY (resource_code)
                                                REFERENCES resource_definitions(resource_code),

                                        CONSTRAINT uk_hero_progression_cost_resource
                                            UNIQUE (progression_config_id, resource_code),

                                        CONSTRAINT ck_hero_progression_cost_amount
                                            CHECK (required_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_hero_progression_lookup
    ON hero_progression_configs(hero_code, progression_type, enabled);