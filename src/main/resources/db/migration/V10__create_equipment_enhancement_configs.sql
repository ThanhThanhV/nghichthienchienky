CREATE TABLE equipment_enhancement_configs (
                                               id BIGINT NOT NULL AUTO_INCREMENT,
                                               target_level INT NOT NULL,
                                               stat_bonus_percent DECIMAL(10,6) NOT NULL DEFAULT 0,
                                               enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   ON UPDATE CURRENT_TIMESTAMP,

                                               PRIMARY KEY (id),
                                               CONSTRAINT uk_equipment_enhancement_level UNIQUE (target_level),
                                               CONSTRAINT ck_equipment_enhancement_level CHECK (target_level >= 1),
                                               CONSTRAINT ck_equipment_enhancement_bonus CHECK (stat_bonus_percent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE equipment_enhancement_costs (
                                             id BIGINT NOT NULL AUTO_INCREMENT,
                                             enhancement_config_id BIGINT NOT NULL,
                                             resource_code VARCHAR(50) NOT NULL,
                                             required_amount DECIMAL(24,6) NOT NULL,

                                             PRIMARY KEY (id),

                                             CONSTRAINT fk_equipment_enhancement_cost_config
                                                 FOREIGN KEY (enhancement_config_id)
                                                     REFERENCES equipment_enhancement_configs(id)
                                                     ON DELETE CASCADE,

                                             CONSTRAINT fk_equipment_enhancement_cost_resource
                                                 FOREIGN KEY (resource_code)
                                                     REFERENCES resource_definitions(resource_code),

                                             CONSTRAINT uk_equipment_enhancement_cost
                                                 UNIQUE (enhancement_config_id, resource_code),

                                             CONSTRAINT ck_equipment_enhancement_cost_amount
                                                 CHECK (required_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;