CREATE TABLE IF NOT EXISTS users (
                                     user_id BIGINT NOT NULL AUTO_INCREMENT,

                                     user_token VARCHAR(36) NOT NULL,
    user_key VARCHAR(100) NULL,

    email VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    birth_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_user_token (user_token),
    UNIQUE KEY uk_users_user_key (user_key),
    UNIQUE KEY uk_users_email (email)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;


-- 기존 테이블 마이그레이션
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS user_token VARCHAR(36) NULL
    AFTER user_id;

ALTER TABLE users
    MODIFY COLUMN user_key VARCHAR(100) NULL;

UPDATE users
SET user_token = user_key
WHERE user_token IS NULL
  AND user_key IS NOT NULL
  AND user_key LIKE 'SAI-%';

UPDATE users
SET user_key = NULL
WHERE user_token IS NOT NULL
  AND user_key = user_token
  AND user_token LIKE 'SAI-%';

UPDATE users
SET user_token = CONCAT(
        'MIG-',
        user_id,
        '-',
    LEFT(REPLACE(UUID(), '-', ''), 11)
    )
WHERE user_token IS NULL;

ALTER TABLE users
    MODIFY COLUMN user_token VARCHAR(36) NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_user_token
    ON users (user_token);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_user_key
    ON users (user_key);