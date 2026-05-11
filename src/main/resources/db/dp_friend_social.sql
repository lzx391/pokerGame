-- DP 好友双向申请 / 局内成员进房邀请（MVP）；与仓库 db 惯例一致 — Docker mysql-init 会 SOURCE 本文件。

CREATE TABLE IF NOT EXISTS `dp_friend_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `from_user_id` INT NOT NULL,
    `to_user_id` INT NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING,ACCEPTED,REJECTED,EXPIRED',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_friend_req_from_to` (`from_user_id`, `to_user_id`),
    KEY `ix_friend_req_to_status` (`to_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `dp_friend_link` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_low_id` INT NOT NULL,
    `user_high_id` INT NOT NULL COMMENT '无序对：always user_low_id < user_high_id',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_friend_pair` (`user_low_id`, `user_high_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `dp_room_invite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `inviter_user_id` INT NOT NULL,
    `invitee_user_id` INT NOT NULL,
    `room_id` VARCHAR(32) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING,ACCEPTED,REJECTED,EXPIRED,CANCELLED',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `expires_at` DATETIME(3) NOT NULL COMMENT '邀约：创建时刻 + 60 秒',
    PRIMARY KEY (`id`),
    KEY `ix_room_inv_invitee_status_exp` (`invitee_user_id`, `status`, `expires_at`),
    KEY `ix_room_inv_inviter_room` (`inviter_user_id`, `room_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
