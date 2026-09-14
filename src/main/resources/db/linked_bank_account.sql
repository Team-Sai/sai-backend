CREATE TABLE IF NOT EXISTS linked_bank_account (
                                       linked_account_id bigint NOT NULL AUTO_INCREMENT,
                                       user_id bigint NOT NULL,
                                       bank_code varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                       account_number varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                       account_alias varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
                                       account_holder_name varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                                       connection_status varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AVAILABLE',
                                       created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                       account_id bigint DEFAULT NULL,
                                       balance decimal(10,0) DEFAULT NULL,
                                       last_synced_transaction_id bigint DEFAULT '0',
                                       PRIMARY KEY (linked_account_id),
                                       UNIQUE KEY uq_user_account (user_id,account_id),
                                       KEY idx_user_id (user_id)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;