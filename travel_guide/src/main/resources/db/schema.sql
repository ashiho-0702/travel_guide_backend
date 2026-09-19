CREATE DATABASE IF NOT EXISTS travel_guide DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE travel_guide;

CREATE TABLE IF NOT EXISTS `user` (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid      VARCHAR(64)  NOT NULL UNIQUE,
    nickname    VARCHAR(64),
    avatar_url  VARCHAR(255),
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS trip (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    city               VARCHAR(64)  NOT NULL,
    start_date         VARCHAR(16),
    preferences        VARCHAR(255),
    budget             VARCHAR(64),
    days               INT          NOT NULL,
    people_count       INT,
    energy_level       VARCHAR(16),
    transportation     VARCHAR(64),
    extra_requirements VARCHAR(255),
    share_token        VARCHAR(64),
    status             VARCHAR(16)  DEFAULT 'done',
    result             JSON,
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8m
