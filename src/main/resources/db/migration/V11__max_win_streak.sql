-- V11: 生涯最高连胜手数（离座/退房时 flush 本段房间连胜）
ALTER TABLE dp_user_stats
    ADD COLUMN max_win_streak INT NOT NULL DEFAULT 0 COMMENT '生涯最高连胜手数';
