-- Shark 对手长期记忆：按玩家昵称（全局唯一）跨房间持久化 PlayerStats 快照 + LearningLab 旋钮。
-- 要求：MySQL 5.7.8+（需 JSON 类型）或 MariaDB 10.2.7+（JSON 为别名）。
-- 若仍报 1064：请整段复制执行，确认上一列末尾有英文逗号。

CREATE TABLE IF NOT EXISTS dp_shark_opponent_profile (
    player_nickname VARCHAR(128) NOT NULL PRIMARY KEY COMMENT '玩家昵称，与进房 DpPlayer.nickname 一致',
    stats_json JSON NOT NULL COMMENT 'PlayerStats 累计与最近窗口手牌',
    learned_json JSON NOT NULL COMMENT 'DpNpcSharkLearningLab 旋钮与分桶样本',
    payload_version SMALLINT NOT NULL DEFAULT 1 COMMENT '结构版本',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='BOT_Shark 对手习惯与旋钮（跨房间）';
