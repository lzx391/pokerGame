-- Flyway V1: initial schema (single source of truth; former src/main/resources/db/*.sql merged here).
-- Order: base user → hand history → participants → independent / optional user refs → social → lobby.

-- -----------------------------------------------------------------------------
-- 1) dp_user — accounts (referenced logically by other tables; no FKs in DDL)
-- -----------------------------------------------------------------------------
CREATE TABLE `dp_user` (
    `id` int NOT NULL AUTO_INCREMENT,
    `nickname` varchar(10) NOT NULL,
    `password` varchar(64) NOT NULL,
    `avatar_url` varchar(255) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 2) dp_observed_hand_history — full observed hand payloads (JSON)
-- -----------------------------------------------------------------------------
CREATE TABLE dp_observed_hand_history (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    room_id             VARCHAR(64)     NOT NULL COMMENT '房间 ID，对应 DpRoom.roomId',
    hand_seed           BIGINT          NOT NULL COMMENT '本手种子，对应 DpRoom.currentHandSeed',
    started_at_ms       BIGINT          NOT NULL COMMENT '本手开始时间（毫秒时间戳）',
    ended_at_ms         BIGINT          NOT NULL COMMENT '本手结束/归档时间（毫秒时间戳）',
    small_blind_chips   INT             NOT NULL DEFAULT 0 COMMENT '小盲筹码',
    big_blind_chips     INT             NOT NULL DEFAULT 0 COMMENT '大盲筹码',
    dealer_nickname     VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '庄家昵称',
    main_pot_before_settlement INT      NOT NULL DEFAULT 0 COMMENT '结算前主池总额（与 Java 字段一致）',
    payload_version     SMALLINT        NOT NULL DEFAULT 1 COMMENT 'payload_json 结构版本，便于以后演进',
    payload_json        JSON            NOT NULL COMMENT '整手快照：seats、boards、actions、pots、holes、netChips 等',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_hand_seed (room_id, hand_seed),
    KEY idx_room_ended (room_id, ended_at_ms DESC),
    KEY idx_room_started (room_id, started_at_ms DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='BOT_Shark 在场时归档的完整牌谱（服务端全知，含弃牌者动作与洞牌）';

-- -----------------------------------------------------------------------------
-- 3) dp_observed_hand_participant — per-hand participants (logical ref to history + user)
-- -----------------------------------------------------------------------------
CREATE TABLE dp_observed_hand_participant (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    hand_history_id     BIGINT UNSIGNED NOT NULL COMMENT 'dp_observed_hand_history.id',
    user_id             INT             NULL DEFAULT NULL COMMENT 'dp_user.id，可选；未知则为 NULL',
    nickname_snapshot   VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '入库时昵称快照（每手去重主维度）',
    seat_index          INT             NOT NULL DEFAULT 0 COMMENT '本手座位下标',
    is_dealer           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否庄家',
    blind_pos           TINYINT         NOT NULL DEFAULT 0 COMMENT '0=无 1=小盲 2=大盲，与 DpPlayer.blind 一致',
    net_chips           INT             NOT NULL DEFAULT 0 COMMENT '本手筹码盈亏（相对盲注后）',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_hand_nick (hand_history_id, nickname_snapshot),
    KEY idx_user_id (user_id),
    KEY idx_hand_history (hand_history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='牌谱与参与者；无外键；user_id 可空';

-- -----------------------------------------------------------------------------
-- 4) dp_shark_opponent_profile — cross-room opponent memory
-- -----------------------------------------------------------------------------
CREATE TABLE dp_shark_opponent_profile (
    player_nickname VARCHAR(128) NOT NULL PRIMARY KEY COMMENT '玩家昵称，与进房 DpPlayer.nickname 一致',
    stats_json JSON NOT NULL COMMENT 'PlayerStats 累计与最近窗口手牌',
    learned_json JSON NOT NULL COMMENT 'DpNpcSharkLearningLab 旋钮与分桶样本',
    payload_version SMALLINT NOT NULL DEFAULT 1 COMMENT '结构版本',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='BOT_Shark 对手习惯与旋钮（跨房间）';

-- -----------------------------------------------------------------------------
-- 5) dp_music_track — BGM metadata (optional uploader_user_id → dp_user)
-- -----------------------------------------------------------------------------
CREATE TABLE `dp_music_track` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stored_filename` varchar(512) NOT NULL COMMENT '磁盘文件名（建议含 uuid 前缀，避免重名）',
    `display_name` varchar(256) NOT NULL COMMENT '界面展示标题',
    `web_path` varchar(512) NOT NULL COMMENT '浏览器访问路径，如 /music/xxx.mp3',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '列表排序，越大越靠前或反之由业务约定',
    `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1=上架 0=下架（不删文件）',
    `uploader_user_id` int DEFAULT NULL COMMENT '可选：上传人 dp_user.id',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_dp_music_track_enabled_sort` (`enabled`, `sort_order`),
    KEY `idx_dp_music_track_web_path` (`web_path`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DP 对局 BGM 曲库（元数据）';

-- -----------------------------------------------------------------------------
-- 6) Friend / invite MVP (logical refs to dp_user.id)
-- -----------------------------------------------------------------------------
CREATE TABLE `dp_friend_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `from_user_id` INT NOT NULL,
    `to_user_id` INT NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING,ACCEPTED,REJECTED,EXPIRED',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_friend_req_from_to` (`from_user_id`, `to_user_id`),
    KEY `ix_friend_req_to_status` (`to_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `dp_friend_link` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_low_id` INT NOT NULL,
    `user_high_id` INT NOT NULL COMMENT '无序对：always user_low_id < user_high_id',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_friend_pair` (`user_low_id`, `user_high_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `dp_room_invite` (
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

-- -----------------------------------------------------------------------------
-- 7) dp_room_lobby — public lobby mirror of in-memory rooms
-- -----------------------------------------------------------------------------
CREATE TABLE dp_room_lobby (
    room_id              VARCHAR(32)     NOT NULL COMMENT '房间 ID，对应 DpRoom.roomId',
    owner_nickname       VARCHAR(128)    NOT NULL COMMENT '房主昵称',
    room_status          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '房间状态：0 游戏中，1 已结束',
    player_count         INT             NOT NULL DEFAULT 0 COMMENT '桌上非僵尸位人数（与 getAllRooms2 一致）',
    small_blind_chips    INT             NOT NULL,
    big_blind_chips      INT             NOT NULL,
    starting_stack_bb    INT             NOT NULL DEFAULT 50,
    password_protected   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1 需要密码',
    max_seat_count       INT             NOT NULL DEFAULT 9 COMMENT '一桌人数上限（2～9），与内存房间 maxSeatCount 一致',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次创建时间，用于排序',
    PRIMARY KEY (room_id),
    KEY idx_lobby_status_created (room_status, created_at DESC),
    KEY idx_lobby_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DP 大厅房间摘要';
