-- Flyway V2: friend DM + room chat archive (P0).

CREATE TABLE `dp_friend_message` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `sender_user_id`      INT          NOT NULL COMMENT 'dp_user.id',
    `recipient_user_id`   INT          NOT NULL COMMENT 'dp_user.id',
    `body`                VARCHAR(500) NOT NULL,
    `created_at`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `ix_friend_msg_recipient_id` (`recipient_user_id`, `id`),
    KEY `ix_friend_msg_pair_id` (`sender_user_id`, `recipient_user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='好友私信（双向会话，每对最多保留 500 条由应用层裁剪）';

CREATE TABLE `dp_friend_chat_read` (
    `user_id`                 INT          NOT NULL COMMENT '读侧用户 dp_user.id',
    `peer_user_id`            INT          NOT NULL COMMENT '会话对端',
    `last_read_message_id`    BIGINT       NOT NULL DEFAULT 0,
    `updated_at`              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`, `peer_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='好友私信已读游标';

CREATE TABLE `dp_room_chat_message` (
    `id`                BIGINT       NOT NULL COMMENT '与摘房前内存缓冲单调 id 一致',
    `room_id`           VARCHAR(32)  NOT NULL,
    `sender_user_id`    INT          NULL DEFAULT NULL,
    `sender_nickname`   VARCHAR(128) NOT NULL DEFAULT '',
    `body`              VARCHAR(200) NOT NULL,
    `server_time_ms`    BIGINT       NOT NULL,
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `ix_room_chat_room_id` (`room_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='局内聊天摘房归档（存活期间仅内存）';
