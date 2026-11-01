CREATE TABLE accounts (
                          id BIGINT NOT NULL AUTO_INCREMENT,
                          username VARCHAR(30) NOT NULL,
                          email VARCHAR(254) NOT NULL,
                          password_hash VARCHAR(100) NOT NULL,
                          role VARCHAR(20) NOT NULL DEFAULT 'USER',
                          enabled BOOLEAN NOT NULL DEFAULT TRUE,
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          PRIMARY KEY (id),
                          CONSTRAINT uk_accounts_username UNIQUE (username),
                          CONSTRAINT uk_accounts_email UNIQUE (email),
                          CONSTRAINT ck_accounts_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_accounts_created_at ON accounts(created_at);