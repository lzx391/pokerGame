-- Flyway V4: dp_user_stats — 生涯牌局荣誉统计（纯战绩，非积分/货币）
CREATE TABLE dp_user_stats (
    user_id              INT NOT NULL PRIMARY KEY COMMENT 'dp_user.id，1:1',
    royal_flush_wins     INT NOT NULL DEFAULT 0 COMMENT '皇家同花顺获胜次数',
    straight_flush_wins  INT NOT NULL DEFAULT 0 COMMENT '同花顺获胜次数',
    four_of_a_kind_wins  INT NOT NULL DEFAULT 0 COMMENT '四条获胜次数',
    largest_pot_won      INT NOT NULL DEFAULT 0 COMMENT '单局最多赢取BC',
    largest_room_net     INT NOT NULL DEFAULT 0 COMMENT '单房间最高净赢BC',
    total_hands_played   INT NOT NULL DEFAULT 0 COMMENT '生涯总局数',
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='玩家生涯牌局荣誉统计（与 dp_user 垂直分表）';
