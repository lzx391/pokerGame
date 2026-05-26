CREATE TABLE dp_leaderboard_weekly (
    week_monday            DATE           NOT NULL COMMENT '本周周一(Asia/Shanghai)',
    user_id                INT            NOT NULL COMMENT 'dp_user.id',
    best_hand_multiplier   DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    best_room_multiplier   DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    updated_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (week_monday, user_id),
    KEY idx_week_hand (week_monday, best_hand_multiplier DESC),
    KEY idx_week_room (week_monday, best_room_multiplier DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='周榜最高分（写库源；读榜走 Redis）';
