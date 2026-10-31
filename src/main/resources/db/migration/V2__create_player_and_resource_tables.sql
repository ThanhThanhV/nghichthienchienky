
-- =====================================================
-- 1. PLAYER PROFILE
-- Mỗi tài khoản có duy nhất một hồ sơ người chơi.
-- =====================================================

CREATE TABLE player_profiles (
                                 id BIGINT NOT NULL AUTO_INCREMENT,
                                 account_id BIGINT NOT NULL,
                                 nickname VARCHAR(50) NOT NULL,
                                 avatar_code VARCHAR(100) NULL,
                                 player_level INT NOT NULL DEFAULT 1,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (id),
                                 CONSTRAINT uk_player_profiles_account UNIQUE (account_id),
                                 CONSTRAINT fk_player_profiles_account
                                     FOREIGN KEY (account_id) REFERENCES accounts(id),
                                 CONSTRAINT ck_player_profiles_level CHECK (player_level >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================
-- 2. RESOURCE DEFINITIONS
-- Danh mục tài nguyên của game.
-- =====================================================

CREATE TABLE resource_definitions (
                                      resource_code VARCHAR(50) NOT NULL,
                                      resource_name VARCHAR(100) NOT NULL,
                                      resource_type VARCHAR(30) NOT NULL,
                                      initial_balance DECIMAL(24,6) NOT NULL DEFAULT 0,
                                      max_balance DECIMAL(24,6) NULL,
                                      enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      PRIMARY KEY (resource_code),
                                      CONSTRAINT ck_resource_initial_balance CHECK (initial_balance >= 0),
                                      CONSTRAINT ck_resource_max_balance
                                          CHECK (max_balance IS NULL OR max_balance >= initial_balance)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================
-- 3. PLAYER RESOURCES
-- Số dư tài nguyên hiện tại của từng người chơi.
-- =====================================================

CREATE TABLE player_resources (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  player_id BIGINT NOT NULL,
                                  resource_code VARCHAR(50) NOT NULL,
                                  balance DECIMAL(24,6) NOT NULL DEFAULT 0,
                                  version BIGINT NOT NULL DEFAULT 0,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  PRIMARY KEY (id),
                                  CONSTRAINT uk_player_resource UNIQUE (player_id, resource_code),
                                  CONSTRAINT fk_player_resource_player
                                      FOREIGN KEY (player_id) REFERENCES player_profiles(id),
                                  CONSTRAINT fk_player_resource_definition
                                      FOREIGN KEY (resource_code) REFERENCES resource_definitions(resource_code),
                                  CONSTRAINT ck_player_resource_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================
-- 4. RESOURCE TRANSACTION HEADERS
-- Mỗi giao dịch có một idempotency key riêng theo player.
-- =====================================================

CREATE TABLE resource_transaction_headers (
                                              id BIGINT NOT NULL AUTO_INCREMENT,
                                              player_id BIGINT NOT NULL,
                                              transaction_key VARCHAR(100) NOT NULL,
                                              source_type VARCHAR(50) NOT NULL,
                                              source_id VARCHAR(100) NULL,
                                              description VARCHAR(255) NULL,
                                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              PRIMARY KEY (id),
                                              CONSTRAINT uk_resource_transaction_key
                                                  UNIQUE (player_id, transaction_key),
                                              CONSTRAINT fk_resource_transaction_player
                                                  FOREIGN KEY (player_id) REFERENCES player_profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================
-- 5. RESOURCE TRANSACTION DETAILS
-- amount > 0: cộng tài nguyên.
-- amount < 0: trừ tài nguyên.
-- =====================================================

CREATE TABLE resource_transaction_details (
                                              id BIGINT NOT NULL AUTO_INCREMENT,
                                              transaction_id BIGINT NOT NULL,
                                              resource_code VARCHAR(50) NOT NULL,
                                              amount DECIMAL(24,6) NOT NULL,
                                              balance_before DECIMAL(24,6) NOT NULL,
                                              balance_after DECIMAL(24,6) NOT NULL,
                                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              PRIMARY KEY (id),
                                              CONSTRAINT uk_resource_transaction_detail
                                                  UNIQUE (transaction_id, resource_code),
                                              CONSTRAINT fk_resource_detail_header
                                                  FOREIGN KEY (transaction_id)
                                                      REFERENCES resource_transaction_headers(id),
                                              CONSTRAINT fk_resource_detail_definition
                                                  FOREIGN KEY (resource_code)
                                                      REFERENCES resource_definitions(resource_code),
                                              CONSTRAINT ck_resource_detail_amount CHECK (amount <> 0),
                                              CONSTRAINT ck_resource_detail_balance_before CHECK (balance_before >= 0),
                                              CONSTRAINT ck_resource_detail_balance_after CHECK (balance_after >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================
-- 6. INITIAL RESOURCE CONFIGURATION
-- Giá trị khởi đầu là cấu hình mẫu, có thể điều chỉnh.
-- =====================================================

INSERT INTO resource_definitions
(resource_code, resource_name, resource_type, initial_balance, max_balance)
VALUES
    ('GOLD', 'Vàng', 'CURRENCY', 1000, NULL),
    ('PLAYER_EXP', 'Kinh nghiệm người chơi', 'EXPERIENCE', 0, NULL),
    ('STAMINA', 'Thể lực', 'CONSUMABLE', 100, 100),
    ('PREMIUM_CURRENCY', 'Nguyên bảo', 'CURRENCY', 0, NULL);
