CREATE TABLE IF NOT EXISTS settlement_participant (
                                                      participant_id BIGINT NOT NULL AUTO_INCREMENT,
                                                      invitation_id BIGINT NOT NULL,

                                                      participant_role ENUM(
                                                      'OWNER',
                                                      'MEMBER'
) NOT NULL,

    participant_status ENUM(
                               'ACTIVE',
                               'LEFT',
                               'REMOVED'
                           ) NOT NULL DEFAULT 'ACTIVE',

    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (participant_id),

    UNIQUE KEY uk_settlement_participant_invitation (
                                                        invitation_id
                                                    ),

    CONSTRAINT fk_settlement_participant_invitation
    FOREIGN KEY (invitation_id)
    REFERENCES settlement_invitation (invitation_id)
    )
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;