CREATE TABLE IF NOT EXISTS settlement_invitation (
                                                     invitation_id BIGINT NOT NULL AUTO_INCREMENT,
                                                     settlement_id BIGINT NOT NULL,
                                                     user_id BIGINT NOT NULL,

                                                     invitation_status ENUM(
                                                     'INVITED',
                                                     'ACCEPTED',
                                                     'REJECTED',
                                                     'EXPIRED'
) NOT NULL DEFAULT 'INVITED',

    invited_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at DATETIME NULL,

    PRIMARY KEY (invitation_id),

    CONSTRAINT uk_settlement_invitation_settlement_user
    UNIQUE (
               settlement_id,
               user_id
           ),

    INDEX idx_settlement_invitation_settlement (
                                                   settlement_id
                                               ),

    INDEX idx_settlement_invitation_user (
                                             user_id
                                         ),

    INDEX idx_settlement_invitation_user_status (
                                                    user_id,
                                                    invitation_status
                                                ),

    CONSTRAINT fk_settlement_invitation_settlement
    FOREIGN KEY (settlement_id)
    REFERENCES settlement (settlement_id),

    CONSTRAINT fk_settlement_invitation_user
    FOREIGN KEY (user_id)
    REFERENCES users (user_id)
    )
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;