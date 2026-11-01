CREATE TABLE hero_stat_progression_bonuses (
                                               id BIGINT NOT NULL AUTO_INCREMENT,
                                               hero_code VARCHAR(50) NOT NULL,
                                               progression_type VARCHAR(20) NOT NULL,
                                               target_level INT NOT NULL,
                                               stat_code VARCHAR(30) NOT NULL,
                                               bonus_value DECIMAL(24,6) NOT NULL DEFAULT 0,
                                               enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                                   ON UPDATE CURRENT_TIMESTAMP,

                                               PRIMARY KEY (id),

                                               CONSTRAINT fk_hero_stat_progression_hero
                                                   FOREIGN KEY (hero_code)
                                                       REFERENCES hero_definitions(hero_code),

                                               CONSTRAINT uk_hero_stat_progression_bonus
                                                   UNIQUE (hero_code, progression_type, target_level, stat_code),

                                               CONSTRAINT ck_hero_stat_progression_type
                                                   CHECK (progression_type IN ('STAR', 'BREAKTHROUGH')),

                                               CONSTRAINT ck_hero_stat_progression_level
                                                   CHECK (target_level >= 1),

                                               CONSTRAINT ck_hero_stat_progression_stat
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

CREATE INDEX idx_hero_stat_progression_lookup
    ON hero_stat_progression_bonuses(
                                     hero_code,
                                     progression_type,
                                     target_level,
                                     enabled
        );