-- Flyway V10: 生涯「上榜次数」+ 周榜结算幂等标记
ALTER TABLE dp_user_stats
    ADD COLUMN leaderboard_top_count INT NOT NULL DEFAULT 0 COMMENT '周榜前三累计次数（hand/room 各榜独立计次）';

CREATE TABLE dp_leaderboard_weekly_finalized (
    week_monday  DATE      NOT NULL PRIMARY KEY COMMENT '已结算上榜次数的周（周一）',
    finalized_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '结算完成时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='周榜上榜次数结算标记（防重复计次）';
