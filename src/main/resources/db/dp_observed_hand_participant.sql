-- =============================================================================
-- 用户 ↔ 牌局（一手）多对多：每名上桌「非机器人」玩家至多一行。
-- hand_history_id 逻辑指向 dp_observed_hand_history.id（不设外键，由应用保证）。
--
-- user_id：可选（NULL）。前端可不传 userId；入库时优先用内存中的 dpUserId，
-- 否则按昵称在 dp_user 中解析（昵称全局唯一时即可补全）。仍无账号则仅保留 nickname_snapshot。
-- 唯一约束：(一手 × 昵称快照)，与「每座位一个昵称」一致。
-- =============================================================================

CREATE TABLE IF NOT EXISTS dp_observed_hand_participant (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    -- 与 dp_observed_hand_history.id 类型保持一致（若你库中 id 非 UNSIGNED，请改本列与之相同）
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

-- 若已从旧脚本建表（user_id NOT NULL 且 uk_hand_user），可手工迁移为可选 id，例如：
-- ALTER TABLE dp_observed_hand_participant
--   MODIFY user_id INT NULL DEFAULT NULL COMMENT 'dp_user.id，可选',
--   DROP INDEX uk_hand_user,
--   ADD UNIQUE KEY uk_hand_nick (hand_history_id, nickname_snapshot);
