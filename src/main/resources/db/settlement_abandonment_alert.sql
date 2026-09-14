CREATE TABLE IF NOT EXISTS settlement_abandonment_alert (
                                              settlement_id BIGINT NOT NULL,
                                              reference_date DATE NOT NULL,
                                              notified_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              PRIMARY KEY (settlement_id, reference_date)
)ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;