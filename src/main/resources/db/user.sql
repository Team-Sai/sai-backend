CREATE TABLE `users` (
                         `user_id` bigint NOT NULL AUTO_INCREMENT,
                         `user_key` varchar(64) DEFAULT NULL,
                         `email` varchar(100) NOT NULL,
                         `password` varchar(255) NOT NULL,
                         `name` varchar(50) NOT NULL,
                         `birth_date` date DEFAULT NULL,
                         `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         `user_token` varchar(50) DEFAULT NULL,
                         PRIMARY KEY (`user_id`),
                         UNIQUE KEY `email` (`email`),
                         UNIQUE KEY `user_token` (`user_token`),
                         UNIQUE KEY `uk_users_user_key` (`user_key`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci