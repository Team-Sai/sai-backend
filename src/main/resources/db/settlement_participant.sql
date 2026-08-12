CREATE TABLE IF NOT EXISTS settlement_participant (
participant_id BIGINT NOT NULL AUTO_INCREMENT,
settlement_id BIGINT NOT NULL,
user_id BIGINT NOT NULL,
 participant_role ENUM( 'OWNER','MEMBER') NOT NULL,

    participant_status ENUM(
                               'ACTIVE',
                               'LEFT',
                               'REMOVED'
                           ) NOT NULL DEFAULT 'ACTIVE',

    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (participant_id),

    UNIQUE KEY uk_settlement_participant_settlement_user ( settlement_id,user_id),

    CONSTRAINT fk_settlement_participant_settlement
    FOREIGN KEY (settlement_id)
    REFERENCES settlement (settlement_id),

    CONSTRAINT fk_settlement_participant_user
    FOREIGN KEY (user_id)
    REFERENCES users (user_id)
    );