CREATE TABLE `user` (
                        user_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
                        user_key    VARCHAR(64)  UNIQUE,
                        email       VARCHAR(100) NOT NULL UNIQUE,
                        password    VARCHAR(255) NOT NULL,
                        name        VARCHAR(50)  NOT NULL,
                        birth_date  DATE,
                        created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);