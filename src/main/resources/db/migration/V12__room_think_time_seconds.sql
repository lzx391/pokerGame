-- V12: 大厅房间摘要 — 真人行动思考时间（秒）
ALTER TABLE dp_room_lobby
    ADD COLUMN think_time_seconds INT NOT NULL DEFAULT 30 COMMENT '真人每步行动思考上限（秒，15～180）';
