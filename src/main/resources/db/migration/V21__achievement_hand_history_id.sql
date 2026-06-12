-- Flyway V21: 成就首次解锁关联牌谱（成就墙「查看回放」）

ALTER TABLE dp_user_achievement
    ADD COLUMN hand_history_id BIGINT UNSIGNED NULL DEFAULT NULL
        COMMENT '首次解锁该成就时的 dp_observed_hand_history.id' AFTER unlocked_at;

CREATE INDEX idx_dp_user_achievement_hand_history
    ON dp_user_achievement (hand_history_id);
