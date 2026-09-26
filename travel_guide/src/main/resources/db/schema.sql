CREATE DATABASE IF NOT EXISTS travel_guide DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE travel_guide;

-- ============================================================
-- 迁移说明：以下建表语句都带 IF NOT EXISTS，对「已存在的库」不会自动加新列。
-- 若你的库是早期创建的，请手动执行对应 ALTER 补列（重复执行会报错，先确认列不存在）：
--   ALTER TABLE `user` ADD COLUMN points INT NOT NULL DEFAULT 0;
--   ALTER TABLE `user` ADD COLUMN growth INT NOT NULL DEFAULT 0;
--   ALTER TABLE `user` ADD COLUMN level INT NOT NULL DEFAULT 1;
--   ALTER TABLE `user` ADD COLUMN is_admin TINYINT(1) NOT NULL DEFAULT 0;
-- ============================================================

CREATE TABLE IF NOT EXISTS `user` (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid      VARCHAR(64)  NOT NULL UNIQUE,
    nickname    VARCHAR(64),
    avatar_url  VARCHAR(255),
    points      INT          NOT NULL DEFAULT 0,
    growth      INT          NOT NULL DEFAULT 0,
    level       INT          NOT NULL DEFAULT 1,
    is_admin    TINYINT(1)   NOT NULL DEFAULT 0,
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
    is_favorite        TINYINT(1)   NOT NULL DEFAULT 0,
    result             JSON,
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS point_flow (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    points     INT          NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    remark     VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_user_time (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS invite_relation (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    inviter_id BIGINT NOT NULL,
    invitee_id BIGINT NOT NULL UNIQUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_inviter (inviter_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
