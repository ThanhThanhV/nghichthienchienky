CREATE TABLE hero_exp_items (
                                resource_code VARCHAR(50) NOT NULL,
                                exp_per_item BIGINT NOT NULL,
                                enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,

                                PRIMARY KEY (resource_code),

                                CONSTRAINT fk_hero_exp_items_resource
                                    FOREIGN KEY (resource_code)
                                        REFERENCES resource_definitions(resource_code),

                                CONSTRAINT ck_hero_exp_items_exp
                                    CHECK (exp_per_item > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;