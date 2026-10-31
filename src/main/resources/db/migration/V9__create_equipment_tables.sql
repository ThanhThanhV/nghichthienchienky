CREATE TABLE equipment_definitions (
                                       equipment_code VARCHAR(50) NOT NULL,
                                       equipment_name VARCHAR(100) NOT NULL,
                                       description VARCHAR(1000) NULL,
                                       equipment_type VARCHAR(30) NOT NULL,
                                       rarity VARCHAR(20) NOT NULL,
                                       icon_code VARCHAR(50) NULL,
                                       enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                       sort_order INT NOT NULL DEFAULT 0,
                                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                           ON UPDATE CURRENT_TIMESTAMP,

                                       PRIMARY KEY (equipment_code),

                                       CONSTRAINT ck_equipment_type
                                           CHECK (equipment_type IN (
                                                                     'WEAPON',
                                                                     'HELMET',
                                                                     'CHEST',
                                                                     'BOOTS',
                                                                     'ACCESSORY_1',
                                                                     'ACCESSORY_2'
                                               )),

                                       CONSTRAINT ck_equipment_rarity
                                           CHECK (rarity IN (
                                                             'COMMON',
                                                             'RARE',
                                                             'EPIC',
                                                             'LEGENDARY',
                                                             'MYTHIC'
                                               ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE equipment_base_stats (
                                      id BIGINT NOT NULL AUTO_INCREMENT,
                                      equipment_code VARCHAR(50) NOT NULL,
                                      stat_code VARCHAR(30) NOT NULL,
                                      stat_value DECIMAL(24,6) NOT NULL DEFAULT 0,

                                      PRIMARY KEY (id),

                                      CONSTRAINT fk_equipment_base_stats_definition
                                          FOREIGN KEY (equipment_code)
                                              REFERENCES equipment_definitions(equipment_code)
                                              ON DELETE CASCADE,

                                      CONSTRAINT uk_equipment_base_stat
                                          UNIQUE (equipment_code, stat_code),

                                      CONSTRAINT ck_equipment_base_stat_code
                                          CHECK (stat_code IN (
                                                               'HP',
                                                               'ATK',
                                                               'DEF',
                                                               'SPEED',
                                                               'CRIT',
                                                               'CRIT_DAMAGE',
                                                               'ACCURACY',
                                                               'RESISTANCE',
                                                               'PEN_FLAT',
                                                               'PEN_PERCENT',
                                                               'LIFESTEAL'
                                              ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE player_equipment (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  player_id BIGINT NOT NULL,
                                  equipment_code VARCHAR(50) NOT NULL,
                                  enhancement_level INT NOT NULL DEFAULT 0,
                                  obtained_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

                                  PRIMARY KEY (id),

                                  CONSTRAINT fk_player_equipment_player
                                      FOREIGN KEY (player_id)
                                          REFERENCES player_profiles(id),

                                  CONSTRAINT fk_player_equipment_definition
                                      FOREIGN KEY (equipment_code)
                                          REFERENCES equipment_definitions(equipment_code),

                                  CONSTRAINT ck_player_equipment_enhancement
                                      CHECK (enhancement_level >= 0),

                                  INDEX idx_player_equipment_owner (player_id, obtained_at),
                                  INDEX idx_player_equipment_code (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE hero_equipment_slots (
                                      id BIGINT NOT NULL AUTO_INCREMENT,
                                      hero_instance_id BIGINT NOT NULL,
                                      equipment_type VARCHAR(30) NOT NULL,
                                      player_equipment_id BIGINT NOT NULL,
                                      equipped_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                      PRIMARY KEY (id),

                                      CONSTRAINT fk_hero_equipment_slot_hero
                                          FOREIGN KEY (hero_instance_id)
                                              REFERENCES hero_instances(id)
                                              ON DELETE CASCADE,

                                      CONSTRAINT fk_hero_equipment_slot_equipment
                                          FOREIGN KEY (player_equipment_id)
                                              REFERENCES player_equipment(id),

                                      CONSTRAINT uk_hero_equipment_slot_type
                                          UNIQUE (hero_instance_id, equipment_type),

                                      CONSTRAINT uk_hero_equipment_item
                                          UNIQUE (player_equipment_id),

                                      CONSTRAINT ck_hero_equipment_slot_type
                                          CHECK (equipment_type IN (
                                                                    'WEAPON',
                                                                    'HELMET',
                                                                    'CHEST',
                                                                    'BOOTS',
                                                                    'ACCESSORY_1',
                                                                    'ACCESSORY_2'
                                              ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE INDEX idx_hero_equipment_hero
    ON hero_equipment_slots(hero_instance_id);