-- 房间路由/大厅列表元数据（对局状态仍在各节点内存 DpRoom / roomMap）
-- 方案二：每实例配置 dp.game.public-ws-url 为客户端可直连的完整 WebSocket URL（含路径 /ws/dp-game）

CREATE TABLE IF NOT EXISTS `dp_room_registry` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `room_id`         VARCHAR(64)  NOT NULL COMMENT '对外房间号，与内存 DpRoom.roomId 一致',
  `game_code`       VARCHAR(32)  NOT NULL DEFAULT 'DP' COMMENT '游戏类型',
  `shard_id`        VARCHAR(64)  NOT NULL COMMENT '持有该房间内存的游戏实例 ID',
  `ws_route`        VARCHAR(512) NOT NULL COMMENT '客户端直连 WebSocket 完整 URL（方案二）',
  `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=等待 1=游戏中 2=已关闭',
  `has_password`    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否设密码（不落库明文）',
  `max_seats`       INT          NOT NULL DEFAULT 9 COMMENT '最大上桌人数',
  `player_count`    INT          NOT NULL DEFAULT 0 COMMENT '桌上人数（展示用）',
  `spectator_count` INT          NOT NULL DEFAULT 0 COMMENT '观众人数',
  `owner_nickname`  VARCHAR(64)  NULL,
  `small_blind_chips` INT NOT NULL DEFAULT 5,
  `big_blind_chips`   INT NOT NULL DEFAULT 10,
  `starting_stack_bb` INT NOT NULL DEFAULT 50,
  `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `closed_at`       DATETIME(3)  NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_id` (`room_id`),
  KEY `idx_status_updated` (`status`, `updated_at`),
  KEY `idx_shard` (`shard_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DP 房间路由表';
